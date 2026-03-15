package com.vamp.haron.presentation.transfer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.R
import com.vamp.haron.data.datastore.HaronPreferences
import com.vamp.haron.data.transfer.HotspotManager
import com.vamp.haron.data.transfer.HttpFileServer
import com.vamp.haron.data.transfer.WifiConnector
import com.vamp.haron.domain.model.DiscoveredDevice
import com.vamp.haron.domain.model.TransferHolder
import com.vamp.haron.domain.model.TransferProgressInfo
import com.vamp.haron.domain.model.TransferProtocol
import com.vamp.haron.domain.model.TransferState
import com.vamp.haron.domain.repository.TransferRepository
import com.vamp.haron.domain.usecase.DiscoverDevicesUseCase
import com.vamp.haron.domain.usecase.SendFilesUseCase
import com.vamp.haron.service.TransferService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class TransferUiState(
    val files: List<File> = emptyList(),
    val devices: List<DiscoveredDevice> = emptyList(),
    val transferState: TransferState = TransferState.IDLE,
    val progress: TransferProgressInfo = TransferProgressInfo(),
    val selectedProtocol: TransferProtocol? = null,
    val isScanning: Boolean = false,
    val serverUrl: String? = null,
    val showQrDialog: Boolean = false,
    val showReceiveDialog: Boolean = false,
    val incomingDeviceName: String = "",
    val incomingFileCount: Int = 0,
    val incomingTotalSize: Long = 0L,
    val isReceiving: Boolean = false,
    val isReceiveTransfer: Boolean = false,
    val batteryWarning: Boolean = false,
    val errorMessage: String? = null,
    val todayReceivedCount: Int = 0,
    val hotspotSsid: String? = null,
    val hotspotPassword: String? = null,
    val isHotspotMode: Boolean = false,
    val hotspotUrl: String? = null,
    val showWifiOffDialog: Boolean = false
)

@HiltViewModel
class TransferViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val discoverDevicesUseCase: DiscoverDevicesUseCase,
    private val sendFilesUseCase: SendFilesUseCase,
    private val httpFileServer: HttpFileServer,
    private val hotspotManager: HotspotManager,
    private val wifiConnector: WifiConnector,
    private val transferRepository: TransferRepository,
    private val preferences: HaronPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(TransferUiState())
    val state: StateFlow<TransferUiState> = _state.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()

    private var discoveryJob: Job? = null
    private var discoveryTimeoutJob: Job? = null
    private var transferJob: Job? = null
    private var receiveJob: Job? = null
    private var pendingQuickSend: com.vamp.haron.data.transfer.QuickSendPending? = null

    init {
        val files = TransferHolder.selectedFiles
        _state.update { it.copy(files = files) }

        if (TransferService.isPowerSaveMode(appContext)) {
            _state.update { it.copy(batteryWarning = true) }
        }

        // Count today's received files
        _state.update { it.copy(todayReceivedCount = countTodayReceivedFiles()) }

        // Subscribe to shared incoming requests (regular transfer)
        viewModelScope.launch {
            transferRepository.incomingRequests.collect { request ->
                pendingQuickSend = null
                _state.update {
                    it.copy(
                        showReceiveDialog = true,
                        incomingDeviceName = request.deviceName,
                        incomingFileCount = request.files.size,
                        incomingTotalSize = request.files.sumOf { f -> f.size }
                    )
                }
            }
        }

        // Quick Send from untrusted devices — socket stays on IO thread
        viewModelScope.launch {
            transferRepository.quickSendPending.collect { pending ->
                pendingQuickSend = pending
                _state.update {
                    it.copy(
                        showReceiveDialog = true,
                        incomingDeviceName = pending.senderName,
                        incomingFileCount = pending.fileCount,
                        incomingTotalSize = 0L
                    )
                }
            }
        }

        // Track completed receives to update today's count
        viewModelScope.launch {
            transferRepository.receiveCompleted.collect { fileCount ->
                _state.update { it.copy(todayReceivedCount = countTodayReceivedFiles()) }
            }
        }

        // Auto-start receiving when opening transfer screen
        startReceiving()
    }

    fun startDiscovery() {
        EcosystemLogger.d(HaronConstants.TAG, "TransferVM: starting device discovery")
        discoveryJob?.cancel()
        discoveryTimeoutJob?.cancel()
        _state.update { it.copy(isScanning = true) }

        discoveryJob = viewModelScope.launch {
            discoverDevicesUseCase()
                .catch { e ->
                    EcosystemLogger.e(HaronConstants.TAG, "TransferVM: discovery error: ${e.message}")
                    _state.update { it.copy(isScanning = false) }
                    _toastMessage.emit(e.message ?: "Discovery error")
                }
                .collect { devices ->
                    val enriched = devices.map { d ->
                        d.copy(
                            alias = preferences.getDeviceAlias(d.name),
                            isTrusted = preferences.isDeviceTrusted(d.name)
                        )
                    }
                    _state.update { it.copy(devices = enriched) }
                }
        }

        // Auto-stop scanning indicator after 15 seconds
        discoveryTimeoutJob = viewModelScope.launch {
            delay(DISCOVERY_TIMEOUT_MS)
            _state.update { it.copy(isScanning = false) }
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        discoveryTimeoutJob?.cancel()
        discoveryTimeoutJob = null
        discoverDevicesUseCase.stop()
        _state.update { it.copy(isScanning = false) }
    }

    fun sendToDevice(device: DiscoveredDevice) {
        val files = _state.value.files
        if (files.isEmpty()) return

        val protocol = chooseBestProtocol(device)
        EcosystemLogger.d(HaronConstants.TAG, "TransferVM: send ${files.size} files to ${device.name} via $protocol")
        _state.update {
            it.copy(
                transferState = TransferState.CONNECTING,
                selectedProtocol = protocol,
                isReceiveTransfer = false
            )
        }

        stopDiscovery()

        transferJob = viewModelScope.launch {
            _state.update { it.copy(transferState = TransferState.TRANSFERRING) }

            TransferService.startServer(appContext)

            sendFilesUseCase(files, device, protocol)
                .catch { e ->
                    EcosystemLogger.e(HaronConstants.TAG, "TransferVM: send failed: ${e.message}")
                    _state.update {
                        it.copy(
                            transferState = TransferState.FAILED,
                            errorMessage = e.message
                        )
                    }
                }
                .collect { progress ->
                    _state.update { it.copy(progress = progress) }
                    TransferService.updateProgress(progress)

                    if (progress.bytesTransferred >= progress.totalBytes && progress.totalBytes > 0) {
                        EcosystemLogger.d(HaronConstants.TAG, "TransferVM: send complete to ${device.name}")
                        _state.update { it.copy(transferState = TransferState.COMPLETED) }
                        TransferService.stopServer(appContext)
                    }
                }
        }
    }

    fun startHttpServer() {
        val files = _state.value.files
        if (files.isEmpty()) return

        EcosystemLogger.d(HaronConstants.TAG, "TransferVM.startHttpServer: ${files.size} files, names=${files.take(3).map { it.name }}")
        viewModelScope.launch {
            // Check if we have a usable local network (not CGNAT/VPN)
            val detectedIp = httpFileServer.getLocalIpAddress()
            val isUsableLocalIp = detectedIp != null && !isCgnatAddress(detectedIp)
            EcosystemLogger.d(HaronConstants.TAG, "TransferVM.startHttpServer: detectedIp=$detectedIp, isUsableLocal=$isUsableLocalIp")

            var serverIp: String?
            var hotspotSsid: String? = null
            var hotspotPassword: String? = null

            if (isUsableLocalIp) {
                // Good local Wi-Fi (home network etc.)
                serverIp = detectedIp
                EcosystemLogger.d(HaronConstants.TAG, "TransferVM.startHttpServer: using local Wi-Fi IP=$serverIp")
            } else {
                // No usable local network — try to start a local-only hotspot
                EcosystemLogger.d(HaronConstants.TAG, "TransferVM.startHttpServer: no usable local IP, trying hotspot...")
                val hotspotInfo = hotspotManager.start()
                EcosystemLogger.d(HaronConstants.TAG, "TransferVM.startHttpServer: hotspotInfo=$hotspotInfo")
                if (hotspotInfo == null || hotspotInfo.ip.isEmpty()) {
                    hotspotManager.stop()
                    EcosystemLogger.e(HaronConstants.TAG, "TransferVM.startHttpServer: hotspot failed, showing WiFi dialog")
                    // Hotspot API failed — ask user to enable system hotspot
                    _state.update { it.copy(showWifiOffDialog = true) }
                    return@launch
                } else {
                    serverIp = hotspotInfo.ip
                    hotspotSsid = hotspotInfo.ssid
                    hotspotPassword = hotspotInfo.password.ifEmpty { null }
                    EcosystemLogger.d(HaronConstants.TAG, "TransferVM.startHttpServer: hotspot IP=$serverIp, SSID=$hotspotSsid")
                }
            }

            TransferService.startServer(appContext)
            httpFileServer.start(files)
            // Build URL using known IP (don't rely on getLocalIpAddress() again — hotspot may not be detected)
            val url = "http://$serverIp:${httpFileServer.actualPort}"
            EcosystemLogger.d(HaronConstants.TAG, "TransferVM.startHttpServer: server URL=$url")
            val totalBytes = files.sumOf { it.length() }
            _state.update {
                it.copy(
                    serverUrl = url,
                    showQrDialog = true,
                    transferState = TransferState.TRANSFERRING,
                    hotspotSsid = hotspotSsid,
                    hotspotPassword = hotspotPassword,
                    progress = TransferProgressInfo(
                        totalBytes = totalBytes,
                        totalFiles = files.size
                    )
                )
            }
            // Track HTTP downloads
            collectHttpDownloads(files.size, totalBytes)
        }
    }

    private fun collectHttpDownloads(totalFiles: Int, totalBytes: Long) {
        viewModelScope.launch {
            var downloadedFiles = 0
            var downloadedBytes = 0L
            httpFileServer.downloadEvents.collect { event ->
                downloadedFiles++
                downloadedBytes += event.fileSize
                val progress = TransferProgressInfo(
                    bytesTransferred = downloadedBytes,
                    totalBytes = totalBytes,
                    currentFileIndex = downloadedFiles,
                    totalFiles = totalFiles,
                    currentFileName = event.fileName
                )
                _state.update { it.copy(progress = progress) }
                if (downloadedFiles >= totalFiles) {
                    _state.update { it.copy(transferState = TransferState.COMPLETED) }
                }
            }
        }
    }

    /** CGNAT range 100.64.0.0/10 — used by Tailscale, carrier NAT, corporate networks */
    private fun isCgnatAddress(ip: String): Boolean {
        val parts = ip.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return false
        return parts[0] == 100 && parts[1] in 64..127
    }

    fun toggleHotspotMode() {
        viewModelScope.launch {
            val current = _state.value
            if (current.isHotspotMode) {
                // Switch back to Wi-Fi mode
                EcosystemLogger.d(HaronConstants.TAG, "TransferVM.toggleHotspotMode: switching to Wi-Fi mode")
                hotspotManager.stop()
                val wifiIp = httpFileServer.getLocalIpAddress()
                val wifiUrl = if (wifiIp != null) "http://$wifiIp:${httpFileServer.actualPort}" else current.serverUrl
                _state.update {
                    it.copy(
                        isHotspotMode = false,
                        hotspotSsid = null,
                        hotspotPassword = null,
                        hotspotUrl = null,
                        serverUrl = wifiUrl
                    )
                }
            } else {
                // Switch to hotspot mode
                EcosystemLogger.d(HaronConstants.TAG, "TransferVM.toggleHotspotMode: switching to hotspot mode")
                val info = hotspotManager.start()
                if (info != null && info.ip.isNotEmpty()) {
                    val hotspotUrl = "http://${info.ip}:${httpFileServer.actualPort}"
                    EcosystemLogger.d(HaronConstants.TAG, "TransferVM.toggleHotspotMode: hotspot started, url=$hotspotUrl, ssid=${info.ssid}")
                    _state.update {
                        it.copy(
                            isHotspotMode = true,
                            hotspotSsid = info.ssid,
                            hotspotPassword = info.password.ifEmpty { null },
                            hotspotUrl = hotspotUrl
                        )
                    }
                } else {
                    EcosystemLogger.e(HaronConstants.TAG, "TransferVM.toggleHotspotMode: hotspot failed to start")
                    _toastMessage.emit(appContext.getString(R.string.transfer_hotspot_connect_failed))
                }
            }
        }
    }

    fun connectAndDownload(ssid: String, password: String?, url: String) {
        EcosystemLogger.d(HaronConstants.TAG, "TransferVM.connectAndDownload: ssid=$ssid, url=$url")
        _state.update {
            it.copy(
                transferState = TransferState.CONNECTING,
                isReceiveTransfer = true
            )
        }

        viewModelScope.launch {
            val connected = wifiConnector.connect(ssid, password)
            if (connected) {
                EcosystemLogger.d(HaronConstants.TAG, "TransferVM.connectAndDownload: connected to hotspot, starting download")
                downloadFromQr(url)
            } else {
                EcosystemLogger.e(HaronConstants.TAG, "TransferVM.connectAndDownload: failed to connect to hotspot")
                _state.update {
                    it.copy(
                        transferState = TransferState.FAILED,
                        errorMessage = appContext.getString(R.string.transfer_hotspot_connect_failed)
                    )
                }
            }
        }
    }

    fun stopHttpServer() {
        EcosystemLogger.d(HaronConstants.TAG, "TransferVM: stopping HTTP server")
        httpFileServer.stop()
        hotspotManager.stop()
        TransferService.stopServer(appContext)
        _state.update {
            it.copy(
                serverUrl = null,
                showQrDialog = false,
                transferState = TransferState.IDLE,
                hotspotSsid = null,
                hotspotPassword = null,
                isHotspotMode = false,
                hotspotUrl = null
            )
        }
    }

    fun dismissQrDialog() {
        // Stop hotspot when dialog is dismissed (server keeps running on Wi-Fi)
        if (_state.value.isHotspotMode) {
            hotspotManager.stop()
            _state.update {
                it.copy(
                    showQrDialog = false,
                    isHotspotMode = false,
                    hotspotSsid = null,
                    hotspotPassword = null,
                    hotspotUrl = null
                )
            }
        } else {
            _state.update { it.copy(showQrDialog = false) }
        }
    }

    fun dismissWifiOffDialog() {
        _state.update { it.copy(showWifiOffDialog = false) }
    }

    fun cancelTransfer() {
        EcosystemLogger.d(HaronConstants.TAG, "TransferVM: cancel transfer")
        transferJob?.cancel()
        sendFilesUseCase.cancel()
        TransferService.stopServer(appContext)
        _state.update {
            it.copy(
                transferState = TransferState.IDLE,
                progress = TransferProgressInfo()
            )
        }
    }

    fun dismissBatteryWarning() {
        _state.update { it.copy(batteryWarning = false) }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null, transferState = TransferState.IDLE) }
    }

    // --- Receiving ---

    fun startReceiving() {
        if (receiveJob?.isActive == true) return
        EcosystemLogger.d(HaronConstants.TAG, "TransferVM: start receiving")
        _state.update { it.copy(isReceiving = true) }

        receiveJob = viewModelScope.launch {
            transferRepository.startReceiving()
                .catch { e ->
                    EcosystemLogger.e(HaronConstants.TAG, "TransferVM: receive error: ${e.message}")
                    _state.update { it.copy(isReceiving = false) }
                }
                .collect { request ->
                    _state.update {
                        it.copy(
                            showReceiveDialog = true,
                            incomingDeviceName = request.deviceName,
                            incomingFileCount = request.files.size,
                            incomingTotalSize = request.files.sumOf { f -> f.size }
                        )
                    }
                }
        }
    }

    fun stopReceiving() {
        receiveJob?.cancel()
        receiveJob = null
        // Don't call transferRepository.stopReceiving() — the global listener
        // lives in MainActivity and must survive screen transitions
        _state.update { it.copy(isReceiving = false) }
    }

    fun acceptIncoming() {
        val qs = pendingQuickSend
        if (qs != null) {
            EcosystemLogger.d(HaronConstants.TAG, "TransferVM: accept quick send from ${qs.senderName}")
            // Quick Send: complete the deferred — file receive happens on IO thread
            qs.response.complete(true)
            pendingQuickSend = null
            _state.update { it.copy(showReceiveDialog = false) }
            return
        }

        EcosystemLogger.d(HaronConstants.TAG, "TransferVM: accept incoming transfer from ${_state.value.incomingDeviceName}")
        _state.update {
            it.copy(
                showReceiveDialog = false,
                transferState = TransferState.TRANSFERRING,
                isReceiveTransfer = true
            )
        }
        TransferService.startServer(appContext)

        transferJob = viewModelScope.launch {
            transferRepository.acceptTransfer()
                .catch { e ->
                    EcosystemLogger.e(HaronConstants.TAG, "TransferVM: accept transfer failed: ${e.message}")
                    _state.update {
                        it.copy(
                            transferState = TransferState.FAILED,
                            errorMessage = e.message
                        )
                    }
                    TransferService.stopServer(appContext)
                }
                .collect { progress ->
                    _state.update { it.copy(progress = progress) }
                    TransferService.updateProgress(progress)

                    if (progress.bytesTransferred >= progress.totalBytes && progress.totalBytes > 0) {
                        EcosystemLogger.d(HaronConstants.TAG, "TransferVM: receive complete")
                        _state.update { it.copy(transferState = TransferState.COMPLETED) }
                        TransferService.stopServer(appContext)
                    }
                }
        }
    }

    fun declineIncoming() {
        val qs = pendingQuickSend
        if (qs != null) {
            qs.response.complete(false)
            pendingQuickSend = null
            _state.update { it.copy(showReceiveDialog = false) }
            return
        }

        _state.update { it.copy(showReceiveDialog = false) }
        transferRepository.declineTransfer()
    }

    fun getReceiveFolder(): String {
        val dir = java.io.File(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
            "Haron"
        )
        if (!dir.exists()) dir.mkdirs()
        return dir.absolutePath
    }

    /**
     * Download files from another Haron's HTTP server (scanned via QR).
     */
    fun downloadFromQr(baseUrl: String) {
        EcosystemLogger.d(HaronConstants.TAG, "TransferVM.downloadFromQr: url=$baseUrl")
        _state.update {
            it.copy(
                transferState = TransferState.TRANSFERRING,
                isReceiveTransfer = true
            )
        }
        TransferService.startServer(appContext)

        transferJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Normalize URL
                val url = baseUrl.trimEnd('/')

                // Log own IP for diagnostics
                val ownIp = httpFileServer.getLocalIpAddress()
                EcosystemLogger.d(HaronConstants.TAG, "TransferVM.downloadFromQr: ownIp=$ownIp, targetUrl=$url")

                // Fetch file list from /api/files
                val apiUrl = java.net.URL("$url/api/files")
                EcosystemLogger.d(HaronConstants.TAG, "TransferVM.downloadFromQr: connecting to $apiUrl ...")
                val apiConn = apiUrl.openConnection() as java.net.HttpURLConnection
                apiConn.connectTimeout = 10_000
                apiConn.readTimeout = 10_000
                val responseCode = apiConn.responseCode
                EcosystemLogger.d(HaronConstants.TAG, "TransferVM.downloadFromQr: /api/files responseCode=$responseCode")
                if (responseCode != 200) {
                    val errorBody = try { apiConn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null }
                    EcosystemLogger.e(HaronConstants.TAG, "TransferVM.downloadFromQr: /api/files failed: HTTP $responseCode, error=$errorBody")
                    apiConn.disconnect()
                    _state.update {
                        it.copy(
                            transferState = TransferState.FAILED,
                            errorMessage = "Server returned HTTP $responseCode"
                        )
                    }
                    TransferService.stopServer(appContext)
                    return@launch
                }
                val jsonStr = apiConn.inputStream.bufferedReader().readText()
                apiConn.disconnect()
                EcosystemLogger.d(HaronConstants.TAG, "TransferVM.downloadFromQr: /api/files response=${jsonStr.take(200)}")

                // Simple JSON parsing: [{"index":0,"name":"file.txt","size":123},...]
                val fileEntries = parseFileList(jsonStr)
                EcosystemLogger.d(HaronConstants.TAG, "TransferVM.downloadFromQr: parsed ${fileEntries.size} files")
                if (fileEntries.isEmpty()) {
                    _state.update {
                        it.copy(
                            transferState = TransferState.FAILED,
                            errorMessage = "No files found"
                        )
                    }
                    TransferService.stopServer(appContext)
                    return@launch
                }

                val saveDir = java.io.File(
                    android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS
                    ), "Haron"
                )
                if (!saveDir.exists()) saveDir.mkdirs()

                val totalBytes = fileEntries.sumOf { it.size }
                var transferred = 0L
                val startTime = System.currentTimeMillis()

                fileEntries.forEachIndexed { index, entry ->
                    val streamUrl = java.net.URL("$url/stream/${entry.index}")
                    EcosystemLogger.d(HaronConstants.TAG, "TransferVM.downloadFromQr: downloading file ${index+1}/${fileEntries.size}: ${entry.name} (${entry.size} bytes) from $streamUrl")
                    val conn = streamUrl.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 15_000
                    conn.readTimeout = 30_000

                    val destFile = getUniqueFile(saveDir, entry.name)
                    conn.inputStream.use { input ->
                        destFile.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                transferred += read
                                val elapsed = System.currentTimeMillis() - startTime
                                val speed = if (elapsed > 0) (transferred * 1000 / elapsed) else 0
                                val eta = if (speed > 0) ((totalBytes - transferred) / speed) else 0
                                _state.update {
                                    it.copy(
                                        progress = TransferProgressInfo(
                                            bytesTransferred = transferred,
                                            totalBytes = totalBytes,
                                            currentFileIndex = index,
                                            totalFiles = fileEntries.size,
                                            currentFileName = entry.name,
                                            speedBytesPerSec = speed,
                                            etaSeconds = eta
                                        )
                                    )
                                }
                            }
                        }
                    }
                    conn.disconnect()
                }

                _state.update {
                    it.copy(
                        transferState = TransferState.COMPLETED,
                        progress = TransferProgressInfo(
                            bytesTransferred = totalBytes,
                            totalBytes = totalBytes,
                            currentFileIndex = fileEntries.size,
                            totalFiles = fileEntries.size,
                            currentFileName = "",
                            speedBytesPerSec = 0,
                            etaSeconds = 0
                        )
                    )
                }
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "TransferVM.downloadFromQr FAILED: ${e::class.simpleName}: ${e.message}")
                EcosystemLogger.e(HaronConstants.TAG, "TransferVM.downloadFromQr stacktrace: ${e.stackTraceToString().take(500)}")
                _state.update {
                    it.copy(
                        transferState = TransferState.FAILED,
                        errorMessage = e.message
                    )
                }
            }
            TransferService.stopServer(appContext)
        }
    }

    private data class QrFileEntry(val index: Int, val name: String, val size: Long)

    private fun parseFileList(json: String): List<QrFileEntry> {
        // Minimal JSON array parser for [{"index":0,"name":"...","size":123},...]
        val entries = mutableListOf<QrFileEntry>()
        val pattern = """\{"index":(\d+),"name":"([^"\\]*(?:\\.[^"\\]*)*)","size":(\d+)\}""".toRegex()
        for (match in pattern.findAll(json)) {
            val idx = match.groupValues[1].toIntOrNull() ?: continue
            val name = match.groupValues[2].replace("\\\"", "\"").replace("\\\\", "\\")
            val size = match.groupValues[3].toLongOrNull() ?: 0L
            entries.add(QrFileEntry(idx, name, size))
        }
        return entries
    }

    private fun getUniqueFile(dir: java.io.File, name: String): java.io.File {
        var file = java.io.File(dir, name)
        if (!file.exists()) return file
        val dotIndex = name.lastIndexOf('.')
        val baseName = if (dotIndex > 0) name.substring(0, dotIndex) else name
        val ext = if (dotIndex > 0) name.substring(dotIndex) else ""
        var counter = 1
        while (file.exists()) {
            file = java.io.File(dir, "${baseName}_($counter)$ext")
            counter++
        }
        return file
    }

    private fun chooseBestProtocol(device: DiscoveredDevice): TransferProtocol {
        return when {
            TransferProtocol.WIFI_DIRECT in device.supportedProtocols -> TransferProtocol.WIFI_DIRECT
            TransferProtocol.HTTP in device.supportedProtocols -> TransferProtocol.HTTP
            TransferProtocol.BLUETOOTH in device.supportedProtocols -> TransferProtocol.BLUETOOTH
            else -> TransferProtocol.HTTP
        }
    }

    private fun countTodayReceivedFiles(): Int {
        val dir = java.io.File(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
            "Haron"
        )
        if (!dir.exists()) return 0
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val todayStart = cal.timeInMillis
        return dir.listFiles()?.count { it.isFile && it.lastModified() >= todayStart } ?: 0
    }

    fun toggleDeviceTrust(device: DiscoveredDevice) {
        val newTrust = !device.isTrusted
        preferences.setDeviceTrusted(device.name, newTrust)
        _state.update { st ->
            st.copy(devices = st.devices.map { d ->
                if (d.id == device.id) d.copy(isTrusted = newTrust) else d
            })
        }
    }

    fun renameDevice(device: DiscoveredDevice, newAlias: String) {
        val trimmed = newAlias.trim()
        if (trimmed.isEmpty()) {
            preferences.removeDeviceAlias(device.name)
        } else {
            preferences.setDeviceAlias(device.name, trimmed)
        }
        _state.update { st ->
            st.copy(devices = st.devices.map { d ->
                if (d.id == device.id) d.copy(alias = trimmed.ifEmpty { null }) else d
            })
        }
    }

    override fun onCleared() {
        stopDiscovery()
        // Don't stop global receiving — it's managed by MainActivity
        receiveJob?.cancel()
        // Stop hotspot if still running
        hotspotManager.stop()
        super.onCleared()
    }

    companion object {
        private const val DISCOVERY_TIMEOUT_MS = 15_000L
    }
}
