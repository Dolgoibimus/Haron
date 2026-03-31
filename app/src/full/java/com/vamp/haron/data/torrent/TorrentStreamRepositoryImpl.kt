package com.vamp.haron.data.torrent

import android.content.Context
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.domain.model.TorrentFileInfo
import com.vamp.haron.domain.model.TorrentStreamState
import com.vamp.haron.domain.repository.TorrentStreamRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.libtorrent4j.AlertListener
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.alerts.AddTorrentAlert
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.MetadataReceivedAlert
import org.libtorrent4j.alerts.PieceFinishedAlert
import org.libtorrent4j.alerts.TorrentErrorAlert
import org.libtorrent4j.alerts.TorrentFinishedAlert
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val SUB = "Torrent"
private const val BUFFER_PERCENT = 3
private const val MIN_BUFFER_BYTES = 15 * 1024 * 1024L

@Singleton
class TorrentStreamRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : TorrentStreamRepository {

    private val _state = MutableStateFlow<TorrentStreamState>(TorrentStreamState.Idle)
    override val state: StateFlow<TorrentStreamState> = _state.asStateFlow()

    override val isAvailable: Boolean = true

    private var session: SessionManager? = null
    @Volatile private var currentHandle: TorrentHandle? = null
    @Volatile private var handleActive = false // guard against SIGSEGV on removed handle
    private var streamFileIndex: Int = -1
    private var lastLoggedPercent = -1
    private var totalPieces: Int = 0
    private var downloadedPieces: Int = 0
    private var _targetFileSize: Long = 0
    private var _targetFilePath: String = ""
    private var targetFileName: String = ""
    private var _pieceLength: Long = 0
    private var _firstPieceIndex: Int = 0

    // Channel for piece completion notifications (HTTP proxy waits on this)
    private val pieceCompleted = MutableSharedFlow<Int>(extraBufferCapacity = 256)
    // Track downloaded pieces in-process to avoid native havePiece() SIGSEGV
    private val downloadedPieceSet = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    // Queue of pieces to prioritize (HTTP thread → alert thread, avoids SIGSEGV)
    private val priorityRequestQueue = java.util.concurrent.ConcurrentLinkedQueue<Int>()

    override val pieceLength: Long get() = _pieceLength
    override val firstPieceIndex: Int get() = _firstPieceIndex
    override val streamFileSize: Long get() = _targetFileSize
    override val streamFilePath: String get() = _targetFilePath

    private val cacheDir: File
        get() = File(context.filesDir, "torrent_stream").apply { mkdirs() }

    private fun ensureSession(): SessionManager {
        session?.let {
            EcosystemLogger.d(HaronConstants.TAG, "$SUB: reusing existing session")
            return it
        }
        EcosystemLogger.i(HaronConstants.TAG, "$SUB: creating new session...")
        val settings = SettingsPack()
            .listenInterfaces("0.0.0.0:6881,[::]:6881")
            .activeDownloads(4)
            .activeSeeds(0)
            .connectionsLimit(1500)
            .downloadRateLimit(0)
            .uploadRateLimit(0)
            .sendBufferWatermark(4 * 1024) // 4 MB (value in KB)
            .maxPeerlistSize(4000)

        val params = SessionParams(settings)
        val mgr = SessionManager(false)
        mgr.addListener(torrentListener)
        mgr.start(params)
        mgr.startDht()
        // Add DHT bootstrap nodes
        try {
            val swig = mgr.swig()
            swig?.add_dht_node(org.libtorrent4j.swig.string_int_pair("router.bittorrent.com", 6881))
            swig?.add_dht_node(org.libtorrent4j.swig.string_int_pair("dht.transmissionbt.com", 6881))
            swig?.add_dht_node(org.libtorrent4j.swig.string_int_pair("router.utorrent.com", 6881))
            swig?.add_dht_node(org.libtorrent4j.swig.string_int_pair("dht.libtorrent.org", 25401))
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "$SUB: DHT bootstrap error: ${e.message}")
        }
        session = mgr
        EcosystemLogger.i(HaronConstants.TAG, "$SUB: session started, isDht=${mgr.isDhtRunning}")
        return mgr
    }

    override suspend fun startStream(uri: String, fileIndex: Int) = withContext(Dispatchers.IO) {
        try {
            stopStream()
            // Clean old cache to avoid non-sequential piece holes from previous downloads
            cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            _state.value = TorrentStreamState.FetchingMetadata
            streamFileIndex = fileIndex

            val mgr = ensureSession()

            if (uri.startsWith("magnet:")) {
                EcosystemLogger.i(HaronConstants.TAG, "$SUB: downloading magnet metadata...")
                mgr.download(uri, cacheDir, null)
            } else {
                val torrentFile = File(uri)
                if (!torrentFile.exists()) {
                    _state.value = TorrentStreamState.Error("File not found: $uri")
                    return@withContext
                }
                val ti = TorrentInfo(torrentFile)
                EcosystemLogger.i(HaronConstants.TAG, "$SUB: loading .torrent: ${ti.name()}, files=${ti.numFiles()}")
                mgr.download(ti, cacheDir)
            }
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "$SUB: startStream error: ${e.message}")
            _state.value = TorrentStreamState.Error(e.message ?: "Unknown error")
        }
    }

    override suspend fun getFiles(uri: String): List<TorrentFileInfo> = withContext(Dispatchers.IO) {
        try {
            val ti = if (uri.startsWith("magnet:")) {
                val mgr = ensureSession()
                val bytes = mgr.fetchMagnet(uri, 30, cacheDir)
                if (bytes != null) TorrentInfo.bdecode(bytes) else null
            } else {
                TorrentInfo(File(uri))
            }

            if (ti == null) return@withContext emptyList()

            val storage = ti.files()
            val videoExtensions = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "ts", "mpg", "mpeg", "3gp")

            (0 until storage.numFiles()).map { i ->
                val name = storage.fileName(i)
                val size = storage.fileSize(i)
                val ext = name.substringAfterLast('.', "").lowercase()
                TorrentFileInfo(
                    index = i,
                    name = name,
                    size = size,
                    isVideo = ext in videoExtensions
                )
            }
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "$SUB: getFiles error: ${e.message}")
            emptyList()
        }
    }

    override fun stopStream() {
        handleActive = false // must be set BEFORE remove to prevent SIGSEGV in alert thread
        val handle = currentHandle
        currentHandle = null // clear reference BEFORE native calls
        handle?.let { h ->
            try {
                session?.remove(h)
            } catch (_: Exception) {}
        }
        streamFileIndex = -1
        lastLoggedPercent = -1
        downloadedPieceSet.clear()
        _state.value = TorrentStreamState.Idle
        // Keep session alive — reuse for next torrent (DHT, connections)
        EcosystemLogger.d(HaronConstants.TAG, "$SUB: stream stopped (session kept)")
    }

    override suspend fun cleanCache() = withContext(Dispatchers.IO) {
        stopStream()
        session?.let {
            it.stop()
            session = null
        }
        cacheDir.deleteRecursively()
        cacheDir.mkdirs()
        EcosystemLogger.i(HaronConstants.TAG, "$SUB: cache cleaned")
    }

    // --- HTTP proxy API ---

    override fun havePiece(pieceIndex: Int): Boolean {
        return pieceIndex in downloadedPieceSet
    }

    override fun pieceIndexForOffset(offset: Long): Int {
        if (_pieceLength <= 0) return -1
        return _firstPieceIndex + (offset / _pieceLength).toInt()
    }

    override suspend fun waitForPiece(pieceIndex: Int, timeoutMs: Long): Boolean {
        if (havePiece(pieceIndex)) return true
        // Request priority via queue (processed in alert thread — safe for native calls)
        priorityRequestQueue.add(pieceIndex)
        EcosystemLogger.d(HaronConstants.TAG, "$SUB: HTTP waiting for piece $pieceIndex")
        // Poll + flow hybrid: avoids race condition where piece arrives between havePiece() and flow subscription
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (havePiece(pieceIndex)) return true
            // Wait for any piece completion, then re-check
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) break
            withTimeoutOrNull(remaining.coerceAtMost(2000)) {
                pieceCompleted.first { true } // any piece
            }
        }
        return havePiece(pieceIndex)
    }

    // --- Internal ---

    private fun setupSequentialDownload(handle: TorrentHandle) {
        val ti = handle.torrentFile() ?: return
        val storage = ti.files()
        val numFiles = storage.numFiles()

        val targetIndex = if (streamFileIndex in 0 until numFiles) {
            streamFileIndex
        } else {
            val videoExts = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "ts")
            var bestIdx = 0
            var bestSize = 0L
            for (i in 0 until numFiles) {
                val ext = storage.fileName(i).substringAfterLast('.', "").lowercase()
                val size = storage.fileSize(i)
                if (ext in videoExts && size > bestSize) {
                    bestIdx = i
                    bestSize = size
                }
            }
            if (bestSize == 0L) {
                for (i in 0 until numFiles) {
                    if (storage.fileSize(i) > bestSize) {
                        bestIdx = i
                        bestSize = storage.fileSize(i)
                    }
                }
            }
            bestIdx
        }

        streamFileIndex = targetIndex

        // Cache piece info for HTTP proxy
        _pieceLength = ti.pieceLength().toLong()
        _firstPieceIndex = (storage.fileOffset(targetIndex) / ti.pieceLength()).toInt()
        val lastPiece = ((storage.fileOffset(targetIndex) + storage.fileSize(targetIndex) - 1) / ti.pieceLength()).toInt()

        // Set file + piece priorities (no setPieceDeadline — SIGSEGV in libtorrent4j 2.x)
        try {
            val priorities = Array(numFiles) { Priority.IGNORE }
            priorities[targetIndex] = Priority.TOP_PRIORITY
            handle.prioritizeFiles(priorities)

            val piecePriorities = Array(ti.numPieces()) { Priority.TOP_PRIORITY }
            handle.prioritizePieces(piecePriorities)
            handleActive = true
            EcosystemLogger.i(HaronConstants.TAG, "$SUB: all ${ti.numPieces()} pieces set to TOP_PRIORITY")
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "$SUB: error setting priorities: ${e.message}")
        }

        totalPieces = ti.numPieces()
        downloadedPieces = 0
        _targetFileSize = storage.fileSize(targetIndex)
        _targetFilePath = File(cacheDir, storage.filePath(targetIndex)).absolutePath
        targetFileName = storage.fileName(targetIndex)

        downloadedPieceSet.clear()
        downloadedPieces = 0

        EcosystemLogger.i(HaronConstants.TAG, "$SUB: streaming file[$targetIndex]: $targetFileName (${_targetFileSize / 1_000_000}MB), pieces=$totalPieces, pieceLen=${_pieceLength / 1024}KB")
    }

    private val torrentListener = object : AlertListener {
        override fun types(): IntArray = intArrayOf(
            AlertType.ADD_TORRENT.swig(),
            AlertType.METADATA_RECEIVED.swig(),
            AlertType.PIECE_FINISHED.swig(),
            AlertType.TORRENT_FINISHED.swig(),
            AlertType.TORRENT_ERROR.swig()
        )

        override fun alert(alert: Alert<*>) {
            when (alert) {
                is AddTorrentAlert -> {
                    EcosystemLogger.i(HaronConstants.TAG, "$SUB: AddTorrentAlert, error=${alert.error().isError}, hasInfo=${alert.handle().torrentFile() != null}")
                    if (alert.error().isError) {
                        EcosystemLogger.e(HaronConstants.TAG, "$SUB: AddTorrent error: ${alert.error().message}")
                        _state.value = TorrentStreamState.Error(alert.error().message)
                        return
                    }
                    val handle = alert.handle()
                    currentHandle = handle
                    EcosystemLogger.i(HaronConstants.TAG, "$SUB: AddTorrent OK, name=${alert.torrentName()}")
                    if (handle.isValid && handle.torrentFile() != null) {
                        setupSequentialDownload(handle)
                        _state.value = TorrentStreamState.Buffering(0, 0)
                    } else {
                        EcosystemLogger.d(HaronConstants.TAG, "$SUB: waiting for metadata (magnet)...")
                    }
                }

                is MetadataReceivedAlert -> {
                    val handle = currentHandle ?: return
                    EcosystemLogger.i(HaronConstants.TAG, "$SUB: metadata received: ${handle.torrentFile()?.name()}")
                    setupSequentialDownload(handle)
                    _state.value = TorrentStreamState.Buffering(0, 0)
                }

                is PieceFinishedAlert -> {
                    downloadedPieces++
                    val pieceIdx = alert.pieceIndex()
                    downloadedPieceSet.add(pieceIdx)
                    pieceCompleted.tryEmit(pieceIdx)
                    // Log first 20 pieces to debug ordering
                    if (downloadedPieces <= 20) {
                        EcosystemLogger.d(HaronConstants.TAG, "$SUB: piece[$pieceIdx] downloaded (#$downloadedPieces), set size=${downloadedPieceSet.size}")
                    }

                    // Drain priority queue (no native calls — SIGSEGV in libtorrent4j 2.x)
                    while (priorityRequestQueue.poll() != null) { /* discard */ }

                    val progress = if (totalPieces > 0) (downloadedPieces * 100 / totalPieces) else 0
                    val downloaded = if (totalPieces > 0) (_targetFileSize * downloadedPieces / totalPieces) else 0L

                    if (progress != lastLoggedPercent) {
                        lastLoggedPercent = progress
                        EcosystemLogger.d(HaronConstants.TAG, "$SUB: $progress% ($downloadedPieces/$totalPieces pieces, ${downloaded / 1_000_000}MB)")
                    }

                    val currentState = _state.value
                    if (currentState is TorrentStreamState.Buffering || currentState is TorrentStreamState.FetchingMetadata) {
                        val hasStartPieces = _firstPieceIndex in downloadedPieceSet &&
                                (_firstPieceIndex + 1) in downloadedPieceSet
                        if (hasStartPieces && (progress >= BUFFER_PERCENT || downloaded >= MIN_BUFFER_BYTES)) {
                            EcosystemLogger.i(HaronConstants.TAG, "$SUB: READY — $targetFileName, buffered ${downloaded / 1_000_000}MB, has start pieces")
                            _state.value = TorrentStreamState.Ready(_targetFilePath, targetFileName)
                        } else {
                            _state.value = TorrentStreamState.Buffering(progress, downloaded / 1024, downloadedPieces)
                        }
                    } else if (currentState is TorrentStreamState.Ready || currentState is TorrentStreamState.Streaming) {
                        _state.value = TorrentStreamState.Streaming(_targetFilePath, targetFileName, progress, downloaded / 1024, 0, 0)
                    }
                }

                is TorrentFinishedAlert -> {
                    EcosystemLogger.i(HaronConstants.TAG, "$SUB: download complete")
                }

                is TorrentErrorAlert -> {
                    EcosystemLogger.e(HaronConstants.TAG, "$SUB: error: ${alert.error().message}")
                    _state.value = TorrentStreamState.Error(alert.error().message)
                }
            }
        }
    }
}
