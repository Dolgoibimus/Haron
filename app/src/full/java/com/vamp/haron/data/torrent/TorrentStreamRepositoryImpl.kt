package com.vamp.haron.data.torrent

import android.content.Context
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.domain.model.TorrentFileInfo
import com.vamp.haron.domain.model.TorrentStreamState
import com.vamp.haron.domain.repository.TorrentStreamRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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
private const val BUFFER_PERCENT = 5 // Start playback after 5% buffered
private const val MIN_BUFFER_BYTES = 5 * 1024 * 1024L // At least 5 MB before playback

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

    private val cacheDir: File
        get() = File(context.filesDir, "torrent_stream").apply { mkdirs() }

    private fun ensureSession(): SessionManager {
        session?.let { return it }
        val settings = SettingsPack()
            .listenInterfaces("0.0.0.0:6881,[::]:6881")
            .activeDownloads(1)
            .activeSeeds(0)
            .connectionsLimit(200)
        val params = SessionParams(settings)
        val mgr = SessionManager(false)
        mgr.addListener(torrentListener)
        mgr.start(params)
        session = mgr
        EcosystemLogger.i(HaronConstants.TAG, "$SUB: session started")
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
                mgr.download(uri, cacheDir, TorrentFlags.SEQUENTIAL_DOWNLOAD)
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

        // Set priorities: target file = SEVEN (max), others = IGNORE
        val priorities = Array(numFiles) { Priority.IGNORE }
        priorities[targetIndex] = Priority.TOP_PRIORITY
        handle.prioritizeFiles(priorities)

        // Sequential download for streaming
        handle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)

        val fileName = storage.fileName(targetIndex)
        val fileSize = storage.fileSize(targetIndex)
        EcosystemLogger.i(HaronConstants.TAG, "$SUB: streaming file[$targetIndex]: $fileName (${fileSize / 1_000_000}MB)")
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
                    if (alert.error().isError) {
                        _state.value = TorrentStreamState.Error(alert.error().message)
                        return
                    }
                    val handle = alert.handle()
                    currentHandle = handle
                    if (handle.torrentFile() != null) {
                        // Have metadata already (.torrent file)
                        setupSequentialDownload(handle)
                        _state.value = TorrentStreamState.Buffering(0, 0)
                    }
                    // else: magnet — wait for MetadataReceivedAlert
                }

                is MetadataReceivedAlert -> {
                    val handle = currentHandle ?: return
                    EcosystemLogger.i(HaronConstants.TAG, "$SUB: metadata received: ${handle.torrentFile()?.name()}")
                    setupSequentialDownload(handle)
                    _state.value = TorrentStreamState.Buffering(0, 0)
                }

                is PieceFinishedAlert -> {
                    val handle = currentHandle ?: return
                    val status = handle.status()
                    val progress = (status.progress() * 100).toInt()
                    val speed = status.downloadRate().toLong()
                    val seeds = status.numSeeds()
                    val peers = status.numPeers()

                    // Log only on percent change
                    if (progress != lastLoggedPercent) {
                        lastLoggedPercent = progress
                        EcosystemLogger.d(HaronConstants.TAG, "$SUB: $progress%, ${speed / 1024}KB/s, seeds=$seeds, peers=$peers")
                    }

                    val currentState = _state.value
                    if (currentState is TorrentStreamState.Buffering || currentState is TorrentStreamState.FetchingMetadata) {
                        // Check if enough buffered to start playback
                        val ti = handle.torrentFile() ?: return
                        val fileSize = ti.files().fileSize(streamFileIndex)
                        val downloaded = (fileSize * status.progress()).toLong()

                        if (progress >= BUFFER_PERCENT || downloaded >= MIN_BUFFER_BYTES) {
                            val filePath = File(cacheDir, ti.files().filePath(streamFileIndex)).absolutePath
                            val fileName = ti.files().fileName(streamFileIndex)
                            EcosystemLogger.i(HaronConstants.TAG, "$SUB: READY — $fileName, buffered ${downloaded / 1_000_000}MB")
                            _state.value = TorrentStreamState.Ready(filePath, fileName)
                        } else {
                            _state.value = TorrentStreamState.Buffering(progress, speed)
                        }
                    } else if (currentState is TorrentStreamState.Ready || currentState is TorrentStreamState.Streaming) {
                        val ti = handle.torrentFile() ?: return
                        val filePath = File(cacheDir, ti.files().filePath(streamFileIndex)).absolutePath
                        val fileName = ti.files().fileName(streamFileIndex)
                        _state.value = TorrentStreamState.Streaming(filePath, fileName, progress, speed, seeds, peers)
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
