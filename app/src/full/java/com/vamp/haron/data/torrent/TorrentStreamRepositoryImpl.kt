package com.vamp.haron.data.torrent

import android.content.Context
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.domain.model.TorrentFileInfo
import com.vamp.haron.domain.model.TorrentStreamState
import com.vamp.haron.domain.repository.TorrentStreamRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.libtorrent4j.AlertListener
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentFlags
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
private const val BUFFER_PERCENT = 3 // Start playback after 3% buffered
private const val MIN_BUFFER_BYTES = 15 * 1024 * 1024L // At least 15 MB before playback

@Singleton
class TorrentStreamRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : TorrentStreamRepository {

    private val _state = MutableStateFlow<TorrentStreamState>(TorrentStreamState.Idle)
    override val state: StateFlow<TorrentStreamState> = _state.asStateFlow()

    override val isAvailable: Boolean = true

    private var session: SessionManager? = null
    private var currentHandle: TorrentHandle? = null
    private var streamFileIndex: Int = -1
    private var lastLoggedPercent = -1
    private var totalPieces: Int = 0
    private var downloadedPieces: Int = 0
    private var targetFileSize: Long = 0
    private var targetFilePath: String = ""
    private var targetFileName: String = ""

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
            .activeDownloads(2)
            .activeSeeds(0)
            .connectionsLimit(500)
        val params = SessionParams(settings)
        val mgr = SessionManager(false)
        mgr.addListener(torrentListener)
        mgr.start(params)
        // Bootstrap DHT with well-known nodes
        mgr.startDht()
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
            _state.value = TorrentStreamState.FetchingMetadata
            streamFileIndex = fileIndex

            val mgr = ensureSession()

            if (uri.startsWith("magnet:")) {
                EcosystemLogger.i(HaronConstants.TAG, "$SUB: downloading magnet metadata...")
                mgr.download(uri, cacheDir, null)
            } else {
                // .torrent file
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
                // Need to fetch metadata first
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
        currentHandle?.let { handle ->
            try {
                handle.pause()
                session?.remove(handle)
            } catch (_: Exception) {}
        }
        currentHandle = null
        streamFileIndex = -1
        lastLoggedPercent = -1
        _state.value = TorrentStreamState.Idle
        EcosystemLogger.d(HaronConstants.TAG, "$SUB: stream stopped")
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

    private fun setupSequentialDownload(handle: TorrentHandle) {
        val ti = handle.torrentFile() ?: return
        val storage = ti.files()
        val numFiles = storage.numFiles()

        // Find target file
        val targetIndex = if (streamFileIndex >= 0 && streamFileIndex < numFiles) {
            streamFileIndex
        } else {
            // Auto-select largest video file
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
            // If no video found, pick largest file
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

        // Set priorities: target file = TOP_PRIORITY, others = IGNORE
        val priorities = Array(numFiles) { Priority.IGNORE }
        priorities[targetIndex] = Priority.TOP_PRIORITY
        handle.prioritizeFiles(priorities)

        // Use piece deadlines for streaming (better than sequential_download)
        // Prioritize first pieces + last piece (container metadata) with tight deadlines
        val firstPiece = (storage.fileOffset(targetIndex) / ti.pieceLength()).toInt()
        val lastPiece = ((storage.fileOffset(targetIndex) + storage.fileSize(targetIndex) - 1) / ti.pieceLength()).toInt()
        val bufferPieces = (MIN_BUFFER_BYTES / ti.pieceLength()).toInt().coerceAtLeast(5)

        // First N pieces — tight deadline for quick start
        for (i in firstPiece..(firstPiece + bufferPieces).coerceAtMost(lastPiece)) {
            handle.setPieceDeadline(i, (i - firstPiece + 1) * 500) // 500ms per piece
        }
        // Last piece — container needs it for duration/index
        handle.setPieceDeadline(lastPiece, 3000)

        // Cache info for crash-safe progress tracking (avoid handle.status() in alerts)
        totalPieces = ti.numPieces()
        downloadedPieces = 0
        targetFileSize = storage.fileSize(targetIndex)
        targetFilePath = File(cacheDir, storage.filePath(targetIndex)).absolutePath
        targetFileName = storage.fileName(targetIndex)
        EcosystemLogger.i(HaronConstants.TAG, "$SUB: streaming file[$targetIndex]: $targetFileName (${targetFileSize / 1_000_000}MB), pieces=$totalPieces")
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
            EcosystemLogger.d(HaronConstants.TAG, "$SUB: alert: ${alert.type()}")
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
                        // Have metadata already (.torrent file)
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
                    // Track progress WITHOUT calling handle.status() — avoids native SIGSEGV crash
                    downloadedPieces++
                    val progress = if (totalPieces > 0) (downloadedPieces * 100 / totalPieces) else 0
                    val downloaded = if (totalPieces > 0) (targetFileSize * downloadedPieces / totalPieces) else 0L

                    if (progress != lastLoggedPercent) {
                        lastLoggedPercent = progress
                        EcosystemLogger.d(HaronConstants.TAG, "$SUB: $progress% ($downloadedPieces/$totalPieces pieces, ${downloaded / 1_000_000}MB)")
                    }

                    val currentState = _state.value
                    if (currentState is TorrentStreamState.Buffering || currentState is TorrentStreamState.FetchingMetadata) {
                        if (progress >= BUFFER_PERCENT || downloaded >= MIN_BUFFER_BYTES) {
                            EcosystemLogger.i(HaronConstants.TAG, "$SUB: READY — $targetFileName, buffered ${downloaded / 1_000_000}MB")
                            _state.value = TorrentStreamState.Ready(targetFilePath, targetFileName)
                        } else {
                            _state.value = TorrentStreamState.Buffering(progress, downloaded / 1024, downloadedPieces)
                        }
                    } else if (currentState is TorrentStreamState.Ready || currentState is TorrentStreamState.Streaming) {
                        _state.value = TorrentStreamState.Streaming(targetFilePath, targetFileName, progress, downloaded / 1024, 0, 0)
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
