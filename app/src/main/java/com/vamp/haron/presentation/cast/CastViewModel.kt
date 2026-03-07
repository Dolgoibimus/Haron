package com.vamp.haron.presentation.cast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.data.cast.DlnaManager
import com.vamp.haron.data.cast.GoogleCastManager
import com.vamp.haron.data.cast.MiracastManager
import com.vamp.haron.data.transfer.HttpFileServer
import com.vamp.haron.domain.model.CastDevice
import com.vamp.haron.domain.model.CastMode
import com.vamp.haron.domain.model.CastType
import com.vamp.haron.domain.model.PresentationState
import com.vamp.haron.domain.model.RemoteInputEvent
import com.vamp.haron.domain.model.SlideshowConfig
import com.vamp.haron.domain.usecase.TranscodeProgress
import com.vamp.haron.domain.usecase.TranscodeVideoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class CastViewModel @Inject constructor(
    val castManager: GoogleCastManager,
    private val miracastManager: MiracastManager,
    private val dlnaManager: DlnaManager,
    private val httpFileServer: HttpFileServer,
    private val transcodeVideoUseCase: TranscodeVideoUseCase
) : ViewModel() {

    val isAvailable = castManager.isAvailable

    val isConnected = combine(
        castManager.isConnected,
        dlnaManager.isConnected
    ) { cast, dlna -> cast || dlna }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isChromecastConnected = castManager.isConnected

    val connectedDeviceName = combine(
        castManager.connectedDeviceName,
        dlnaManager.connectedDeviceName
    ) { cast, dlna -> cast ?: dlna }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val mediaIsPlaying = combine(
        castManager.mediaIsPlaying,
        dlnaManager.mediaIsPlaying
    ) { cast, dlna -> cast || dlna }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val mediaPositionMs = combine(
        castManager.mediaPositionMs,
        dlnaManager.mediaPositionMs
    ) { cast, dlna -> if (cast > 0) cast else dlna }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val mediaDurationMs = combine(
        castManager.mediaDurationMs,
        dlnaManager.mediaDurationMs
    ) { cast, dlna -> if (cast > 0) cast else dlna }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    private val _showDeviceSheet = MutableStateFlow(false)
    val showDeviceSheet: StateFlow<Boolean> = _showDeviceSheet.asStateFlow()

    private val _devices = MutableStateFlow<List<CastDevice>>(emptyList())
    val devices: StateFlow<List<CastDevice>> = _devices.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _castMode = MutableStateFlow(CastMode.SINGLE_MEDIA)
    val castMode: StateFlow<CastMode> = _castMode.asStateFlow()

    private val _presentationState = MutableStateFlow(PresentationState())
    val presentationState: StateFlow<PresentationState> = _presentationState.asStateFlow()

    private val _transcodeProgress = MutableStateFlow<TranscodeProgress?>(null)
    val transcodeProgress: StateFlow<TranscodeProgress?> = _transcodeProgress.asStateFlow()

    val isTranscoding: StateFlow<Boolean> = _transcodeProgress
        .map { it != null && !it.isComplete }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // DEBUG: track castMedia flow
    private val _debugCastInfo = MutableStateFlow("")
    val debugCastInfo: StateFlow<String> = _debugCastInfo.asStateFlow()

    private var discoveryJob: Job? = null
    private var transcodeJob: Job? = null
    private var pendingCastPath: String? = null
    private var pendingCastTitle: String? = null
    private var pendingCastIsTranscoded: Boolean = false
    private var lastCastDeviceId: String? = null
    private var pdfPageCount: Int = 0

    init {
        // Auto-cast when Chromecast session connects if there's a pending request
        viewModelScope.launch {
            castManager.isConnected.collect { connected ->
                if (connected) {
                    val path = pendingCastPath
                    if (path != null) {
                        if (pendingCastIsTranscoded) {
                            // Reconnected after transcode — cast the ready file
                            val file = File(path)
                            if (file.exists() && file.length() > 0) {
                                castTranscodedFile(file, pendingCastTitle ?: "", false)
                                EcosystemLogger.d(HaronConstants.TAG, "Reconnected, casting transcoded file")
                            }
                            pendingCastIsTranscoded = false
                        } else {
                            castMedia(path, pendingCastTitle ?: "")
                        }
                        pendingCastPath = null
                        pendingCastTitle = null
                    }
                }
            }
        }

        // Poll media position every second when Chromecast connected
        viewModelScope.launch {
            while (isActive) {
                if (castManager.isConnected.value) {
                    castManager.updateMediaPosition()
                }
                delay(1000)
            }
        }
    }

    fun showSheet() {
        _showDeviceSheet.value = true
        startDiscovery()
    }

    fun hideSheet() {
        _showDeviceSheet.value = false
        stopDiscovery()
    }

    private fun startDiscovery() {
        _isSearching.value = true
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch {
            combine(
                castManager.discoverCastDevices(),
                miracastManager.discoverDisplays(),
                dlnaManager.discoverDevices()
            ) { cast, miracast, dlna -> cast + miracast + dlna }
                .collect { allDevices ->
                    _devices.value = allDevices
                    _isSearching.value = false
                }
        }
    }

    private fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        _isSearching.value = false
    }

    fun selectDevice(device: CastDevice) {
        when (device.type) {
            CastType.CHROMECAST -> {
                lastCastDeviceId = device.id
                castManager.selectCastDevice(device.id)
            }
            CastType.MIRACAST -> miracastManager.selectRoute(device.id)
            CastType.DLNA -> { /* DLNA connects on castMedia, no separate select */ }
        }
        hideSheet()
    }

    fun selectDeviceAndCast(device: CastDevice, filePath: String, title: String = "") {
        when (device.type) {
            CastType.CHROMECAST -> {
                pendingCastPath = filePath
                pendingCastTitle = title
                selectDevice(device)
            }
            CastType.DLNA -> {
                hideSheet()
                castMediaToDlna(device, filePath, title)
            }
            CastType.MIRACAST -> {
                selectDevice(device)
            }
        }
    }

    private fun castMediaToDlna(device: CastDevice, filePath: String, title: String) {
        viewModelScope.launch {
            val file = File(filePath)
            if (!file.exists()) return@launch

            httpFileServer.start(listOf(file))
            val streamUrl = httpFileServer.getStreamUrl(0) ?: return@launch
            val mimeType = guessMimeType(file.extension)
            dlnaManager.castMedia(device.id, streamUrl, mimeType, title.ifEmpty { file.name })
        }
    }

    fun castMedia(filePath: String, title: String = "") {
        viewModelScope.launch {
            val file = File(filePath)
            if (!file.exists()) {
                _debugCastInfo.value = "FILE NOT FOUND"
                EcosystemLogger.e(HaronConstants.TAG, "castMedia: file not found: $filePath")
                return@launch
            }

            val dlnaDeviceId = dlnaManager.connectedDeviceId
            val isChromecast = dlnaDeviceId == null
            val ext = file.extension.lowercase()
            val needsT = transcodeVideoUseCase.needsTranscode(filePath)
            _debugCastInfo.value = "ext=$ext cc=$isChromecast dlna=$dlnaDeviceId need=$needsT"
            EcosystemLogger.d(HaronConstants.TAG, "castMedia: ext=$ext, isChromecast=$isChromecast, needsTranscode=${isChromecast && needsT}")

            // DLNA — cast as-is (most Smart TVs support more formats)
            // Chromecast — transcode if needed
            if (isChromecast && needsT) {
                _debugCastInfo.value = "TRANSCODING ext=$ext"
                startTranscodedCast(filePath, title)
            } else {
                httpFileServer.start(listOf(file))
                val streamUrl = httpFileServer.getStreamUrl(0) ?: return@launch
                val mimeType = guessMimeType(file.extension)
                if (dlnaDeviceId != null) {
                    dlnaManager.castMedia(dlnaDeviceId, streamUrl, mimeType, title.ifEmpty { file.name })
                } else {
                    castManager.castMedia(streamUrl, mimeType, title)
                }
            }
        }
    }

    private fun startTranscodedCast(filePath: String, title: String) {
        transcodeJob?.cancel()
        _transcodeProgress.value = TranscodeProgress(percent = 0)
        EcosystemLogger.d(HaronConstants.TAG, "startTranscodedCast: $filePath")

        transcodeJob = viewModelScope.launch {
            transcodeVideoUseCase(filePath).collect { progress ->
                _transcodeProgress.value = progress

                if (progress.isComplete) {
                    _transcodeProgress.value = null

                    if (progress.error != null) {
                        EcosystemLogger.e(HaronConstants.TAG, "Transcode failed: ${progress.error}")
                    } else if (progress.outputPath != null) {
                        val transcodedFile = File(progress.outputPath)
                        if (transcodedFile.exists() && transcodedFile.length() > 0) {
                            if (!castManager.isConnected.value) {
                                // Chromecast disconnected during transcode — wait for reconnect
                                EcosystemLogger.w(HaronConstants.TAG, "Chromecast disconnected during transcode, waiting for reconnect...")
                                pendingCastPath = progress.outputPath
                                pendingCastTitle = title
                                pendingCastIsTranscoded = true
                                // Re-select last device to trigger reconnect
                                lastCastDeviceId?.let { castManager.selectCastDevice(it) }
                            } else {
                                castTranscodedFile(transcodedFile, title, progress.audioStripped)
                            }
                        } else {
                            EcosystemLogger.e(HaronConstants.TAG, "Transcode done but file missing")
                        }
                    }
                }
            }
        }
    }

    private fun castTranscodedFile(file: File, title: String, audioStripped: Boolean) {
        viewModelScope.launch {
            httpFileServer.start(listOf(file))
            val streamUrl = httpFileServer.getStreamUrl(0)
            if (streamUrl != null) {
                castManager.castMedia(streamUrl, "video/mp4", title)
                EcosystemLogger.d(HaronConstants.TAG, "Cast started: $streamUrl (${file.length() / 1024}KB)")
                if (audioStripped) {
                    EcosystemLogger.w(HaronConstants.TAG, "Cast WITHOUT audio (codec not supported)")
                }
            }
        }
    }

    fun cancelTranscode() {
        transcodeVideoUseCase.cancelTranscode()
        transcodeJob?.cancel()
        transcodeJob = null
        _transcodeProgress.value = null
        transcodeVideoUseCase.cleanupTempFiles()
    }

    fun sendRemoteInput(event: RemoteInputEvent) {
        if (dlnaManager.isConnected.value) {
            dlnaManager.sendRemoteInput(event)
        } else {
            castManager.sendRemoteInput(event)
        }
    }

    // --- Extended Cast modes ---

    fun castSlideshow(files: List<File>, config: SlideshowConfig) {
        _castMode.value = CastMode.SLIDESHOW
        viewModelScope.launch {
            val imageFiles = if (config.shuffle) files.shuffled() else files
            httpFileServer.start(emptyList())
            httpFileServer.setupSlideshow(imageFiles, config.intervalSec)
            val url = httpFileServer.getSlideshowUrl() ?: return@launch
            if (dlnaManager.isConnected.value) {
                dlnaManager.castMedia(dlnaManager.connectedDeviceId ?: "", url, "text/html", "Slideshow")
            } else {
                castManager.castMedia(url, "text/html", "Slideshow")
            }
        }
    }

    fun castPdfPresentation(pdfPath: String) {
        _castMode.value = CastMode.PDF_PRESENTATION
        viewModelScope.launch {
            val file = File(pdfPath)
            if (!file.exists()) return@launch

            val pageCount = try {
                val fd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = android.graphics.pdf.PdfRenderer(fd)
                val count = renderer.pageCount
                renderer.close()
                fd.close()
                count
            } catch (_: Exception) { 0 }

            pdfPageCount = pageCount
            _presentationState.value = PresentationState(0, pageCount, pdfPath)

            httpFileServer.start(emptyList())
            httpFileServer.setupPdf(file)

            val url = httpFileServer.getPresentationUrl(0) ?: return@launch
            if (dlnaManager.isConnected.value) {
                dlnaManager.castMedia(dlnaManager.connectedDeviceId ?: "", url, "image/png", file.nameWithoutExtension)
            } else {
                castManager.castMedia(url, "image/png", file.nameWithoutExtension)
            }
        }
    }

    fun presentationPrevPage() {
        val current = _presentationState.value
        if (current.currentPage > 0) {
            val newPage = current.currentPage - 1
            _presentationState.value = current.copy(currentPage = newPage)
            castPresentationPage(newPage)
        }
    }

    fun presentationNextPage() {
        val current = _presentationState.value
        if (current.currentPage < current.totalPages - 1) {
            val newPage = current.currentPage + 1
            _presentationState.value = current.copy(currentPage = newPage)
            castPresentationPage(newPage)
        }
    }

    private fun castPresentationPage(page: Int) {
        viewModelScope.launch {
            val url = httpFileServer.getPresentationUrl(page) ?: return@launch
            if (dlnaManager.isConnected.value) {
                dlnaManager.castMedia(dlnaManager.connectedDeviceId ?: "", url, "image/png", "Page ${page + 1}")
            } else {
                castManager.castMedia(url, "image/png", "Page ${page + 1}")
            }
        }
    }

    fun castMirrorUrl(url: String) {
        _castMode.value = CastMode.SCREEN_MIRROR
        viewModelScope.launch {
            if (dlnaManager.isConnected.value) {
                dlnaManager.castMedia(dlnaManager.connectedDeviceId ?: "", url, "text/html", "Screen Mirror")
            } else if (castManager.isConnected.value) {
                castManager.castMedia(url, "text/html", "Screen Mirror")
            } else {
                EcosystemLogger.e(HaronConstants.TAG, "Screen mirror: no device connected")
            }
        }
        EcosystemLogger.d(HaronConstants.TAG, "Screen mirror casting: $url")
    }

    fun castFileInfo(name: String, path: String, size: String, modified: String, mimeType: String) {
        _castMode.value = CastMode.FILE_INFO
        viewModelScope.launch {
            httpFileServer.start(emptyList())
            httpFileServer.setupFileInfo(name, path, size, modified, mimeType)
            val url = httpFileServer.getFileInfoUrl() ?: return@launch
            if (dlnaManager.isConnected.value) {
                dlnaManager.castMedia(dlnaManager.connectedDeviceId ?: "", url, "text/html", name)
            } else {
                castManager.castMedia(url, "text/html", name)
            }
        }
    }

    fun setCastMode(mode: CastMode) {
        _castMode.value = mode
    }

    fun disconnect() {
        cancelTranscode()
        _castMode.value = CastMode.SINGLE_MEDIA
        _presentationState.value = PresentationState()
        castManager.disconnect()
        dlnaManager.disconnect()
        httpFileServer.stop()
        transcodeVideoUseCase.cleanupTempFiles()
    }

    private fun guessMimeType(ext: String): String {
        return when (ext.lowercase()) {
            "mp4", "m4v" -> "video/mp4"
            "avi" -> "video/x-msvideo"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
    }

    override fun onCleared() {
        super.onCleared()
        discoveryJob?.cancel()
        transcodeJob?.cancel()
        transcodeVideoUseCase.cancelTranscode()
        transcodeVideoUseCase.cleanupTempFiles()
        httpFileServer.stop()
    }
}
