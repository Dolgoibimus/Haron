package com.vamp.haron.presentation.cast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamp.haron.data.cast.GoogleCastManager
import com.vamp.haron.data.cast.MiracastManager
import com.vamp.haron.data.transfer.HttpFileServer
import com.vamp.haron.domain.model.CastDevice
import com.vamp.haron.domain.model.CastMode
import com.vamp.haron.domain.model.CastType
import com.vamp.haron.domain.model.PresentationState
import com.vamp.haron.domain.model.RemoteInputEvent
import com.vamp.haron.domain.model.SlideshowConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class CastViewModel @Inject constructor(
    val castManager: GoogleCastManager,
    private val miracastManager: MiracastManager,
    private val httpFileServer: HttpFileServer
) : ViewModel() {

    val isAvailable = castManager.isAvailable
    val isConnected = castManager.isConnected
    val connectedDeviceName = castManager.connectedDeviceName

    // Media playback state from Cast session
    val mediaIsPlaying = castManager.mediaIsPlaying
    val mediaPositionMs = castManager.mediaPositionMs
    val mediaDurationMs = castManager.mediaDurationMs

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

    private var discoveryJob: Job? = null
    private var pendingCastPath: String? = null
    private var pendingCastTitle: String? = null
    private var pdfPageCount: Int = 0

    init {
        // Auto-cast when session connects if there's a pending request
        viewModelScope.launch {
            castManager.isConnected.collect { connected ->
                if (connected) {
                    val path = pendingCastPath
                    if (path != null) {
                        castMedia(path, pendingCastTitle ?: "")
                        pendingCastPath = null
                        pendingCastTitle = null
                    }
                }
            }
        }

        // Poll media position every second when connected
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
                miracastManager.discoverDisplays()
            ) { cast, miracast -> cast + miracast }
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
            CastType.CHROMECAST -> castManager.selectCastDevice(device.id)
            CastType.MIRACAST -> miracastManager.selectRoute(device.id)
        }
        hideSheet()
    }

    fun selectDeviceAndCast(device: CastDevice, filePath: String, title: String = "") {
        if (device.type == CastType.CHROMECAST) {
            pendingCastPath = filePath
            pendingCastTitle = title
        }
        selectDevice(device)
    }

    fun castMedia(filePath: String, title: String = "") {
        viewModelScope.launch {
            val file = File(filePath)
            if (!file.exists()) return@launch

            httpFileServer.start(listOf(file))
            val streamUrl = httpFileServer.getStreamUrl(0) ?: return@launch
            val mimeType = guessMimeType(file.extension)
            castManager.castMedia(streamUrl, mimeType, title)
        }
    }

    fun sendRemoteInput(event: RemoteInputEvent) {
        castManager.sendRemoteInput(event)
    }

    // --- Extended Cast modes ---

    fun castSlideshow(files: List<File>, config: SlideshowConfig) {
        _castMode.value = CastMode.SLIDESHOW
        viewModelScope.launch {
            val imageFiles = if (config.shuffle) files.shuffled() else files
            httpFileServer.start(emptyList())
            httpFileServer.setupSlideshow(imageFiles, config.intervalSec)
            val url = httpFileServer.getSlideshowUrl() ?: return@launch
            castManager.castMedia(url, "text/html", "Slideshow")
        }
    }

    fun castPdfPresentation(pdfPath: String) {
        _castMode.value = CastMode.PDF_PRESENTATION
        viewModelScope.launch {
            val file = File(pdfPath)
            if (!file.exists()) return@launch

            // Count pages
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

            // Cast first page
            val url = httpFileServer.getPresentationUrl(0) ?: return@launch
            castManager.castMedia(url, "image/png", file.nameWithoutExtension)
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
            castManager.castMedia(url, "image/png", "Page ${page + 1}")
        }
    }

    fun castFileInfo(name: String, path: String, size: String, modified: String, mimeType: String) {
        _castMode.value = CastMode.FILE_INFO
        viewModelScope.launch {
            httpFileServer.start(emptyList())
            httpFileServer.setupFileInfo(name, path, size, modified, mimeType)
            val url = httpFileServer.getFileInfoUrl() ?: return@launch
            castManager.castMedia(url, "text/html", name)
        }
    }

    fun setCastMode(mode: CastMode) {
        _castMode.value = mode
    }

    fun disconnect() {
        _castMode.value = CastMode.SINGLE_MEDIA
        _presentationState.value = PresentationState()
        castManager.disconnect()
        httpFileServer.stop()
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
        httpFileServer.stop()
    }
}
