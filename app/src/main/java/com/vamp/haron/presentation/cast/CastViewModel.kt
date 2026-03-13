package com.vamp.haron.presentation.cast

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import android.bluetooth.BluetoothDevice
import com.vamp.haron.data.cast.BluetoothHidManager
import com.vamp.haron.data.cast.DlnaManager
import com.vamp.haron.data.cast.GoogleCastManager
import com.vamp.haron.data.cast.MiracastManager
import com.vamp.haron.data.cast.RemoteInputChannel
import com.vamp.haron.data.transfer.HttpFileServer
import com.vamp.haron.domain.model.CastDevice
import com.vamp.haron.domain.model.CastImageInfo
import com.vamp.haron.domain.model.CastMode
import com.vamp.haron.domain.model.CastType
import com.vamp.haron.domain.model.HidConnectionState
import com.vamp.haron.domain.model.PresentationState
import com.vamp.haron.domain.model.RemoteInputEvent
import com.vamp.haron.domain.model.SlideshowConfig
import com.vamp.haron.domain.usecase.TranscodeProgress
import com.vamp.haron.domain.usecase.TranscodeVideoUseCase
import com.vamp.haron.service.CastMediaService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.vamp.haron.service.ScreenMirrorService
import java.io.File
import javax.inject.Inject

@HiltViewModel
class CastViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    val castManager: GoogleCastManager,
    private val miracastManager: MiracastManager,
    private val dlnaManager: DlnaManager,
    private val httpFileServer: HttpFileServer,
    private val transcodeVideoUseCase: TranscodeVideoUseCase,
    private val remoteInputChannel: RemoteInputChannel,
    private val bluetoothHidManager: BluetoothHidManager
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

    // TV Remote touchpad state
    private val _showTouchpad = MutableStateFlow(false)
    val showTouchpad: StateFlow<Boolean> = _showTouchpad.asStateFlow()

    private val _showKeyboard = MutableStateFlow(false)
    val showKeyboard: StateFlow<Boolean> = _showKeyboard.asStateFlow()

    // Image navigation during cast
    private var castImageFiles: List<String> = emptyList()
    private var castImageIndex: Int = 0
    private val _castImageInfo = MutableStateFlow<CastImageInfo?>(null)
    val castImageInfo: StateFlow<CastImageInfo?> = _castImageInfo.asStateFlow()

    // Bluetooth HID
    val hidConnectionState: StateFlow<HidConnectionState> = bluetoothHidManager.connectionState
    val btPairedDevices: StateFlow<List<BluetoothDevice>> = bluetoothHidManager.pairedDevices

    private val _showBtDevicePicker = MutableStateFlow(false)
    val showBtDevicePicker: StateFlow<Boolean> = _showBtDevicePicker.asStateFlow()

    private val _isBluetoothHidMode = MutableStateFlow(false)
    val isBluetoothHidMode: StateFlow<Boolean> = _isBluetoothHidMode.asStateFlow()

    /** One-shot event: UI should launch ACTION_REQUEST_DISCOVERABLE */
    private val _requestDiscoverable = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val requestDiscoverable: SharedFlow<Int> = _requestDiscoverable.asSharedFlow()

    /** Whether we're waiting for TV to connect to us */
    private val _btHidWaitingForTv = MutableStateFlow(false)
    val btHidWaitingForTv: StateFlow<Boolean> = _btHidWaitingForTv.asStateFlow()

    val isTranscoding: StateFlow<Boolean> = _transcodeProgress
        .map { it != null && !it.isComplete }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var discoveryJob: Job? = null
    private var transcodeJob: Job? = null
    private var pendingCastPath: String? = null
    private var pendingCastTitle: String? = null
    private var pendingCastIsTranscoded: Boolean = false
    private var lastCastDeviceId: String? = null

    /** Pending action to execute after device connection (for modes selected before connecting) */
    private var pendingAction: (() -> Unit)? = null
    private var pdfPageCount: Int = 0

    /** DLNA device selected from sheet before castMedia — DLNA has no separate "connect" step */
    private var pendingDlnaDeviceId: String? = null

    /** Browser URL for modes that can't be cast to Chromecast (mirror, file info) */
    private val _browserUrl = MutableStateFlow<String?>(null)
    val browserUrl: StateFlow<String?> = _browserUrl.asStateFlow()

    init {
        // Set callbacks for notification actions
        CastMediaService.onPlayPauseRequested = { sendRemoteInput(RemoteInputEvent.PlayPause) }
        CastMediaService.onDisconnectRequested = { disconnect() }

        // Auto-cast when Chromecast session connects if there's a pending request
        viewModelScope.launch {
            castManager.isConnected.collect { connected ->
                EcosystemLogger.d("CastFlow", "chromecast.isConnected=$connected, hasPendingAction=${pendingAction != null}")
                if (connected) {
                    executePendingAction()
                    val path = pendingCastPath
                    if (path != null) {
                        if (pendingCastIsTranscoded) {
                            val file = File(path)
                            if (file.isDirectory && File(file, "playlist.m3u8").exists()) {
                                castHls(path, pendingCastTitle ?: "")
                                EcosystemLogger.d(HaronConstants.TAG, "Reconnected, casting HLS dir")
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

        // Auto-action when DLNA connects with pending action
        viewModelScope.launch {
            dlnaManager.isConnected.collect { connected ->
                EcosystemLogger.d("CastFlow", "dlna.isConnected=$connected, hasPendingAction=${pendingAction != null}")
                if (connected) executePendingAction()
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

        // Observe BT HID connection state (single observer, not per-call)
        viewModelScope.launch {
            bluetoothHidManager.connectionState.collect { state ->
                when (state) {
                    is HidConnectionState.Connected -> {
                        _showBtDevicePicker.value = false
                        _btHidWaitingForTv.value = false
                        _isBluetoothHidMode.value = true
                        _showTouchpad.value = true
                        EcosystemLogger.d(HaronConstants.TAG, "BT HID: connected to ${state.deviceName}, showing touchpad")
                    }
                    is HidConnectionState.Error -> {
                        EcosystemLogger.e(HaronConstants.TAG, "BT HID: error: ${state.message}")
                    }
                    is HidConnectionState.Disconnected -> {
                        if (_isBluetoothHidMode.value) {
                            _isBluetoothHidMode.value = false
                            _showTouchpad.value = false
                            _showKeyboard.value = false
                            EcosystemLogger.d(HaronConstants.TAG, "BT HID: disconnected, hiding touchpad")
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun startCastService(mediaTitle: String) {
        val deviceName = connectedDeviceName.value ?: ""
        val intent = Intent(appContext, CastMediaService::class.java).apply {
            action = CastMediaService.ACTION_START
            putExtra(CastMediaService.EXTRA_TITLE, mediaTitle)
            putExtra(CastMediaService.EXTRA_DEVICE_NAME, deviceName)
        }
        ContextCompat.startForegroundService(appContext, intent)
    }

    private fun stopCastService() {
        if (!CastMediaService.isRunning) return
        val intent = Intent(appContext, CastMediaService::class.java).apply {
            action = CastMediaService.ACTION_STOP
        }
        appContext.startService(intent)
    }

    fun showSheet() {
        EcosystemLogger.d("CastFlow", "showSheet()")
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
        EcosystemLogger.d("CastFlow", "selectDevice: ${device.name} (${device.type}), hasPendingAction=${pendingAction != null}")
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
            startCastService(title.ifEmpty { file.name })
            val streamUrl = httpFileServer.getStreamUrl(0) ?: return@launch
            val mimeType = guessMimeType(file.extension)
            dlnaManager.castMedia(device.id, streamUrl, mimeType, title.ifEmpty { file.name })
        }
    }

    fun castMedia(filePath: String, title: String = "") {
        EcosystemLogger.d(HaronConstants.TAG, "castMedia ENTER: path=$filePath, title=$title")
        viewModelScope.launch {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    EcosystemLogger.e(HaronConstants.TAG, "castMedia: file not found: $filePath")
                    return@launch
                }

                // Check DLNA: either already connected or just selected from sheet
                val dlnaDeviceId = dlnaManager.connectedDeviceId ?: pendingDlnaDeviceId
                pendingDlnaDeviceId = null
                val isChromecast = dlnaDeviceId == null
                val ext = file.extension.lowercase()
                val needsT = transcodeVideoUseCase.needsTranscode(filePath)
                EcosystemLogger.d(HaronConstants.TAG, "castMedia: ext=$ext, isChromecast=$isChromecast, dlnaDevice=$dlnaDeviceId, needsTranscode=${isChromecast && needsT}")

                // Images — always cast directly (no transcoding)
                val isImage = ext in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

                // Collect adjacent images for navigation
                if (isImage) {
                    val parentDir = file.parentFile
                    if (parentDir != null && parentDir.isDirectory) {
                        val imageExts = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
                        val images = parentDir.listFiles()
                            ?.filter { it.isFile && it.extension.lowercase() in imageExts }
                            ?.sortedBy { it.name.lowercase() }
                            ?.map { it.absolutePath }
                            ?: emptyList()
                        castImageFiles = images
                        castImageIndex = images.indexOf(filePath).coerceAtLeast(0)
                        _castImageInfo.value = CastImageInfo(
                            currentIndex = castImageIndex,
                            totalCount = images.size,
                            fileName = file.name
                        )
                        EcosystemLogger.d(HaronConstants.TAG, "castMedia: image nav initialized, ${castImageIndex + 1}/${images.size}")
                    }
                } else {
                    // Not an image — clear image navigation
                    castImageFiles = emptyList()
                    castImageIndex = 0
                    _castImageInfo.value = null
                }

                // DLNA — cast as-is (most Smart TVs support more formats)
                // Chromecast — transcode video if needed, images always direct
                if (isChromecast && needsT && !isImage) {
                    EcosystemLogger.d(HaronConstants.TAG, "castMedia: calling startTranscodedCast")
                    startTranscodedCast(filePath, title)
                } else {
                    // For images: start server with ALL image files so each has a unique URL index
                    if (isImage && castImageFiles.isNotEmpty()) {
                        httpFileServer.start(castImageFiles.map { File(it) })
                    } else {
                        httpFileServer.start(listOf(file))
                    }
                    startCastService(title.ifEmpty { file.name })
                    val streamIndex = if (isImage) castImageIndex else 0
                    val streamUrl = httpFileServer.getStreamUrl(streamIndex) ?: return@launch
                    val mimeType = guessMimeType(file.extension)
                    if (dlnaDeviceId != null) {
                        dlnaManager.castMedia(dlnaDeviceId, streamUrl, mimeType, title.ifEmpty { file.name })
                    } else {
                        castManager.castMedia(streamUrl, mimeType, title)
                    }
                }
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "castMedia CRASH: ${e.javaClass.simpleName}: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun startTranscodedCast(filePath: String, title: String) {
        transcodeJob?.cancel()
        _transcodeProgress.value = TranscodeProgress(percent = 0)
        EcosystemLogger.d(HaronConstants.TAG, "startTranscodedCast ENTER: $filePath")

        transcodeJob = viewModelScope.launch {
            try {
                var castStarted = false
                var lastLoggedPercent = -1
                EcosystemLogger.d(HaronConstants.TAG, "startTranscodedCast: invoking transcodeVideoUseCase")
                transcodeVideoUseCase(filePath).collect { progress ->
                    // Log only on percent change or important events
                    if (progress.percent != lastLoggedPercent || progress.isComplete || progress.error != null) {
                        lastLoggedPercent = progress.percent
                        EcosystemLogger.d(HaronConstants.TAG, "transcode: ${progress.percent}% ready=${progress.readyToStream} complete=${progress.isComplete} err=${progress.error}")
                    }
                    // Always update progress (visible during playback too)
                    _transcodeProgress.value = progress

                    // Start HLS cast when enough segments are ready (~30 sec)
                    if (!castStarted && progress.readyToStream && progress.hlsDir != null) {
                        castStarted = true
                        castHls(progress.hlsDir, title)
                    }

                    if (progress.isComplete) {
                        _transcodeProgress.value = null
                        if (progress.error != null) {
                            EcosystemLogger.e(HaronConstants.TAG, "Transcode failed: ${progress.error}")
                        } else if (progress.hlsDir != null) {
                            if (!castStarted) {
                                // Cache hit or very short video — cast as VOD
                                castStarted = true
                                castHls(progress.hlsDir, title, live = false)
                            }
                        }
                    }
                }
                EcosystemLogger.d(HaronConstants.TAG, "startTranscodedCast: flow collection ended")
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "startTranscodedCast CRASH: ${e.javaClass.simpleName}: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun castHls(hlsDirPath: String, title: String, live: Boolean = true) {
        viewModelScope.launch {
            val dir = File(hlsDirPath)
            if (!dir.exists() || !dir.isDirectory) {
                EcosystemLogger.e(HaronConstants.TAG, "castHls: dir not found: $hlsDirPath")
                return@launch
            }
            httpFileServer.start(emptyList())
            httpFileServer.setupHls(dir)
            startCastService(title)
            val hlsUrl = httpFileServer.getHlsUrl()
            if (hlsUrl != null) {
                if (!castManager.isConnected.value) {
                    pendingCastPath = hlsDirPath
                    pendingCastTitle = title
                    pendingCastIsTranscoded = true
                    lastCastDeviceId?.let { castManager.selectCastDevice(it) }
                    EcosystemLogger.w(HaronConstants.TAG, "Chromecast disconnected, pending HLS cast")
                } else {
                    val st = if (live) com.google.android.gms.cast.MediaInfo.STREAM_TYPE_LIVE
                             else com.google.android.gms.cast.MediaInfo.STREAM_TYPE_BUFFERED
                    castManager.castMedia(hlsUrl, "application/x-mpegURL", title, streamType = st)
                    EcosystemLogger.d(HaronConstants.TAG, "HLS cast started: $hlsUrl (live=$live)")
                }
            }
        }
    }

    private fun reloadHlsAsVod(hlsDirPath: String, title: String) {
        viewModelScope.launch {
            val hlsUrl = httpFileServer.getHlsUrl()
            if (hlsUrl != null && castManager.isConnected.value) {
                castManager.castMedia(
                    hlsUrl, "application/x-mpegURL", title,
                    streamType = com.google.android.gms.cast.MediaInfo.STREAM_TYPE_BUFFERED
                )
                EcosystemLogger.d(HaronConstants.TAG, "HLS reloaded as VOD (seek enabled): $hlsUrl")
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

    fun castNextImage() {
        if (castImageFiles.isEmpty()) return
        val newIndex = (castImageIndex + 1).coerceAtMost(castImageFiles.size - 1)
        if (newIndex == castImageIndex) return
        castImageIndex = newIndex
        val path = castImageFiles[newIndex]
        val name = File(path).name
        _castImageInfo.value = CastImageInfo(newIndex, castImageFiles.size, name)
        EcosystemLogger.d(HaronConstants.TAG, "castNextImage: ${newIndex + 1}/${castImageFiles.size} $name")
        castCurrentImage()
    }

    fun castPrevImage() {
        if (castImageFiles.isEmpty()) return
        val newIndex = (castImageIndex - 1).coerceAtLeast(0)
        if (newIndex == castImageIndex) return
        castImageIndex = newIndex
        val path = castImageFiles[newIndex]
        val name = File(path).name
        _castImageInfo.value = CastImageInfo(newIndex, castImageFiles.size, name)
        EcosystemLogger.d(HaronConstants.TAG, "castPrevImage: ${newIndex + 1}/${castImageFiles.size} $name")
        castCurrentImage()
    }

    private fun castCurrentImage() {
        val path = castImageFiles.getOrNull(castImageIndex) ?: return
        val file = File(path)
        if (!file.exists()) return
        viewModelScope.launch {
            // Server already started with all images — just use the index for unique URL
            val streamUrl = httpFileServer.getStreamUrl(castImageIndex) ?: return@launch
            val mimeType = guessMimeType(file.extension)
            val dlnaDeviceId = dlnaManager.connectedDeviceId
            if (dlnaDeviceId != null) {
                dlnaManager.castMedia(dlnaDeviceId, streamUrl, mimeType, file.name)
            } else {
                castManager.castMedia(streamUrl, mimeType, file.name)
            }
        }
    }

    fun sendRemoteInput(event: RemoteInputEvent) {
        // Intercept Next/Prev for image navigation
        if (castImageFiles.isNotEmpty()) {
            when (event) {
                is RemoteInputEvent.Next -> { castNextImage(); return }
                is RemoteInputEvent.Prev -> { castPrevImage(); return }
                else -> { /* fall through */ }
            }
        }

        // Bluetooth HID mode — route input events through BT
        if (_isBluetoothHidMode.value) {
            bluetoothHidManager.sendRemoteInput(event)
            return
        }

        when (event) {
            is RemoteInputEvent.MouseMove,
            is RemoteInputEvent.MouseClick,
            is RemoteInputEvent.Scroll,
            is RemoteInputEvent.KeyPress,
            is RemoteInputEvent.TextInput -> {
                // TV remote events → WebSocket → browser JS on TV
                remoteInputChannel.send(event)
            }
            else -> {
                // Media events → Cast SDK / DLNA
                if (dlnaManager.isConnected.value) {
                    dlnaManager.sendRemoteInput(event)
                } else {
                    castManager.sendRemoteInput(event)
                }
            }
        }
    }

    fun toggleTouchpad() {
        _showTouchpad.value = !_showTouchpad.value
        _showKeyboard.value = false
    }

    fun showTouchpadPanel() {
        _showTouchpad.value = true
        _showKeyboard.value = false
    }

    fun showKeyboardPanel() {
        _showKeyboard.value = true
        _showTouchpad.value = false
    }

    fun hideRemoteInput() {
        _showTouchpad.value = false
        _showKeyboard.value = false
    }

    // --- Bluetooth HID Remote ---

    fun connectForBluetoothRemote() {
        if (!bluetoothHidManager.isSupported()) {
            _showBtDevicePicker.value = true
            return
        }
        bluetoothHidManager.init()
        _btHidWaitingForTv.value = false
        _showBtDevicePicker.value = true
        EcosystemLogger.d(HaronConstants.TAG, "BT HID: showing setup dialog")
    }

    /** Request discoverable mode — TV must initiate HID connection to us */
    fun requestBtDiscoverable() {
        EcosystemLogger.d(HaronConstants.TAG, "BT HID: requesting discoverable mode")
        _btHidWaitingForTv.value = true
        // Emit event for UI to launch ACTION_REQUEST_DISCOVERABLE
        _requestDiscoverable.tryEmit(300) // 300 seconds
    }

    fun dismissBtDevicePicker() {
        _showBtDevicePicker.value = false
        _btHidWaitingForTv.value = false
    }

    fun disconnectBtHid() {
        bluetoothHidManager.disconnect()
        _isBluetoothHidMode.value = false
        _btHidWaitingForTv.value = false
        _showTouchpad.value = false
        _showKeyboard.value = false
        EcosystemLogger.d(HaronConstants.TAG, "BT HID: disconnected by user")
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

    /** Open device sheet for remote-only connection (no file casting) */
    fun connectForRemote() {
        EcosystemLogger.d("CastFlow", "connectForRemote: opening device sheet")
        setPendingAction {
            _showTouchpad.value = true
            _castMode.value = CastMode.SINGLE_MEDIA // reuse, no media will play
        }
        showSheet()
    }

    /** Set a pending action to execute after device connection */
    fun setPendingAction(action: () -> Unit) {
        EcosystemLogger.d("CastFlow", "setPendingAction: action set")
        pendingAction = action
    }

    private fun executePendingAction() {
        val action = pendingAction ?: return
        EcosystemLogger.d("CastFlow", "executePendingAction: running")
        pendingAction = null
        action()
    }

    /** Select device and execute pending action (for non-media cast modes from Explorer) */
    fun selectDeviceWithPendingAction(device: CastDevice) {
        EcosystemLogger.d("CastFlow", "selectDeviceWithPendingAction: ${device.name} (${device.type})")
        if (device.type == CastType.DLNA) {
            // DLNA has no separate "connect" step — store device ID and execute action immediately
            pendingDlnaDeviceId = device.id
            hideSheet()
            executePendingAction()
        } else {
            selectDevice(device)
        }
    }

    fun castMirrorUrl(url: String) {
        EcosystemLogger.d("CastFlow", "castMirrorUrl (browser): $url")
        _castMode.value = CastMode.SCREEN_MIRROR
        _browserUrl.value = url
    }

    /** Close browser-based cast (mirror) */
    fun closeBrowserCast() {
        val mode = _castMode.value
        _browserUrl.value = null
        _castMode.value = CastMode.SINGLE_MEDIA
        if (mode == CastMode.SCREEN_MIRROR) {
            val intent = Intent(appContext, ScreenMirrorService::class.java).apply {
                action = ScreenMirrorService.ACTION_STOP
            }
            appContext.startService(intent)
        }
        EcosystemLogger.d("CastFlow", "closeBrowserCast: mode was $mode")
    }

    fun setCastMode(mode: CastMode) {
        _castMode.value = mode
    }

    fun disconnect() {
        transcodeVideoUseCase.cancelTranscode()
        transcodeJob?.cancel()
        transcodeJob = null
        _transcodeProgress.value = null
        _browserUrl.value = null
        _castMode.value = CastMode.SINGLE_MEDIA
        _presentationState.value = PresentationState()
        castImageFiles = emptyList()
        castImageIndex = 0
        _castImageInfo.value = null
        pendingDlnaDeviceId = null
        castManager.disconnect()
        dlnaManager.disconnect()
        httpFileServer.stop()
        stopCastService()
        // Don't cleanupTempFiles — keep transcode cache for reuse
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
        // Don't cleanupTempFiles — keep transcode cache for reuse
        httpFileServer.stop()
        stopCastService()
        CastMediaService.onPlayPauseRequested = null
        CastMediaService.onDisconnectRequested = null
    }
}
