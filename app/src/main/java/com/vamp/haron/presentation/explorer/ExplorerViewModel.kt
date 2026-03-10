package com.vamp.haron.presentation.explorer

import android.content.Context
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamp.core.db.EcosystemPreferences
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.R
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.data.datastore.HaronPreferences
import com.vamp.haron.data.model.SortOrder
import com.vamp.haron.data.saf.SafUriManager
import com.vamp.haron.data.saf.StorageVolumeHelper
import com.vamp.haron.domain.model.GalleryHolder
import com.vamp.haron.domain.model.NavigationEvent
import com.vamp.haron.domain.model.PlaylistHolder
import com.vamp.haron.domain.model.SearchNavigationHolder
import com.vamp.haron.domain.model.TransferHolder
import com.vamp.haron.domain.model.ShelfItem
import com.vamp.haron.domain.model.ConflictFileInfo
import com.vamp.haron.domain.model.ConflictPair
import com.vamp.haron.domain.model.ConflictResolution
import com.vamp.haron.domain.model.FileEntry
import com.vamp.haron.domain.model.FileTag
import com.vamp.haron.domain.model.GestureAction
import com.vamp.haron.domain.model.GestureType
import com.vamp.haron.domain.model.OperationProgress
import com.vamp.haron.domain.model.OperationType
import com.vamp.haron.domain.model.PanelId
import com.vamp.haron.domain.repository.FileRepository
import com.vamp.haron.domain.repository.TrashRepository
import com.vamp.haron.domain.usecase.CleanExpiredTrashUseCase
import com.vamp.haron.domain.usecase.CopyFilesUseCase
import com.vamp.haron.domain.usecase.CreateZipUseCase
import com.vamp.haron.domain.usecase.CreateDirectoryUseCase
import com.vamp.haron.domain.usecase.CreateFileUseCase
import com.vamp.haron.domain.usecase.DeleteFilesUseCase
import com.vamp.haron.domain.usecase.EmptyTrashUseCase
import com.vamp.haron.domain.usecase.GetFilesUseCase
import com.vamp.haron.domain.usecase.LoadPreviewUseCase
import com.vamp.haron.domain.usecase.MoveFilesUseCase
import com.vamp.haron.domain.usecase.MoveToTrashUseCase
import com.vamp.haron.domain.usecase.RenameFileUseCase
import com.vamp.haron.domain.usecase.RestoreFromTrashUseCase
import com.vamp.haron.domain.usecase.GetFilePropertiesUseCase
import com.vamp.haron.domain.usecase.CalculateHashUseCase
import com.vamp.haron.domain.usecase.BrowseArchiveUseCase
import com.vamp.haron.domain.usecase.ExtractArchiveUseCase
import com.vamp.haron.domain.usecase.FindEmptyFoldersUseCase
import com.vamp.haron.domain.usecase.LoadApkInstallInfoUseCase
import com.vamp.haron.common.util.HapticManager
import com.vamp.haron.data.network.NetworkDevice
import com.vamp.haron.data.network.NetworkDeviceScanner
import com.vamp.haron.data.network.NetworkDeviceType
import com.vamp.haron.data.transfer.ReceiveFileManager
import com.vamp.haron.domain.model.DiscoveredDevice
import com.vamp.haron.domain.model.TransferProgressInfo
import com.vamp.haron.domain.model.TransferProtocol
import com.vamp.haron.domain.repository.TransferRepository
import com.vamp.haron.presentation.explorer.state.QuickSendState
import com.vamp.haron.data.voice.VoiceCommandManager
import com.vamp.haron.data.voice.VoiceState
import com.vamp.haron.data.security.AuthManager
import com.vamp.haron.data.usb.UsbStorageManager
import com.vamp.haron.domain.repository.SecureFolderRepository
import com.vamp.haron.domain.usecase.BatchRenameUseCase
import com.vamp.haron.domain.usecase.ForceDeleteUseCase
import com.vamp.haron.presentation.explorer.state.DragState
import com.vamp.haron.presentation.explorer.state.DialogState
import com.vamp.haron.presentation.explorer.state.ExplorerUiState
import com.vamp.haron.presentation.explorer.state.FileTemplate
import com.vamp.haron.presentation.explorer.state.PanelUiState
import com.vamp.haron.presentation.explorer.state.SafRootInfo
import com.vamp.haron.service.FileOperationService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Intent
import com.vamp.haron.common.util.iconRes
import com.vamp.haron.common.util.mimeType
import com.vamp.haron.common.util.toFileSize
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ExplorerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val getFilesUseCase: GetFilesUseCase,
    private val fileRepository: FileRepository,
    private val preferences: HaronPreferences,
    private val copyFilesUseCase: CopyFilesUseCase,
    private val moveFilesUseCase: MoveFilesUseCase,
    private val deleteFilesUseCase: DeleteFilesUseCase,
    private val renameFileUseCase: RenameFileUseCase,
    private val createDirectoryUseCase: CreateDirectoryUseCase,
    private val createFileUseCase: CreateFileUseCase,
    private val loadPreviewUseCase: LoadPreviewUseCase,
    private val createZipUseCase: CreateZipUseCase,
    private val moveToTrashUseCase: MoveToTrashUseCase,
    private val restoreFromTrashUseCase: RestoreFromTrashUseCase,
    private val emptyTrashUseCase: EmptyTrashUseCase,
    private val cleanExpiredTrashUseCase: CleanExpiredTrashUseCase,
    private val trashRepository: TrashRepository,
    private val safUriManager: SafUriManager,
    private val storageVolumeHelper: StorageVolumeHelper,
    private val getFilePropertiesUseCase: GetFilePropertiesUseCase,
    private val calculateHashUseCase: CalculateHashUseCase,
    private val loadApkInstallInfoUseCase: LoadApkInstallInfoUseCase,
    private val hapticManager: HapticManager,
    private val forceDeleteUseCase: ForceDeleteUseCase,
    private val findEmptyFoldersUseCase: FindEmptyFoldersUseCase,
    private val batchRenameUseCase: BatchRenameUseCase,
    private val secureFolderRepository: SecureFolderRepository,
    private val authManager: AuthManager,
    private val searchRepository: com.vamp.haron.domain.repository.SearchRepository,
    private val usbStorageManager: UsbStorageManager,
    private val networkDeviceScanner: NetworkDeviceScanner,
    val voiceCommandManager: VoiceCommandManager,
    private val transferRepository: TransferRepository,
    private val receiveFileManager: ReceiveFileManager,
    private val browseArchiveUseCase: BrowseArchiveUseCase,
    private val extractArchiveUseCase: ExtractArchiveUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExplorerUiState())

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessage = _toastMessage.asSharedFlow()
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>(extraBufferCapacity = 1)
    val navigationEvent = _navigationEvent.asSharedFlow()

    /** Selection snapshot at the start of a drag gesture (for non-contiguous multi-range). */
    private var dragBaseSelection: Set<String> = emptySet()

    /** Job for inline (non-service) file operations, used for cancellation. */
    private var inlineOperationJob: kotlinx.coroutines.Job? = null

    /** Scroll position cache: path → firstVisibleItemIndex */
    private val scrollCache = mutableMapOf<String, Int>()

    /** Current scroll positions reported by panels */
    private val panelScrollIndex = mutableMapOf<PanelId, Int>()

    /** Monotonic trigger counter for scroll events */
    private var scrollTrigger = 0L

    /** Folder size calculation jobs */
    private val folderSizeJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    /** Content search debounce jobs per panel */
    private val contentSearchJobs = mutableMapOf<PanelId, kotlinx.coroutines.Job>()
    private val folderIndexJobs = mutableMapOf<PanelId, kotlinx.coroutines.Job>()

    /** Navigation jobs per panel — cancel previous before starting new */
    private val navigationJobs = mutableMapOf<PanelId, kotlinx.coroutines.Job>()

    fun onScrollPositionChanged(panelId: PanelId, index: Int) {
        panelScrollIndex[panelId] = index
    }

    fun getScrollIndex(panelId: PanelId): Int = panelScrollIndex[panelId] ?: 0

    init {
        val savedSort = preferences.getSortOrder()
        val showHidden = preferences.showHidden
        val panelRatio = preferences.panelRatio
        val gridColumns = preferences.gridColumns
        // Load shelf and filter out non-existing files
        val shelfItems = preferences.getShelfItems().filter { item ->
            if (item.path.startsWith("content://")) true
            else File(item.path).exists()
        }
        if (shelfItems.size != preferences.getShelfItems().size) {
            preferences.saveShelfItems(shelfItems)
        }

        _uiState.update {
            it.copy(
                topPanel = it.topPanel.copy(sortOrder = savedSort, showHidden = showHidden, gridColumns = gridColumns),
                bottomPanel = it.bottomPanel.copy(sortOrder = savedSort, showHidden = showHidden, gridColumns = gridColumns),
                panelRatio = panelRatio,
                favorites = preferences.getFavorites(),
                recentPaths = preferences.getRecentPaths(),
                shelfItems = shelfItems,
                themeMode = EcosystemPreferences.theme,
                originalFolders = preferences.getOriginalFolders(),
                bookmarks = preferences.getBookmarks(),
                tagDefinitions = preferences.getTagDefinitions(),
                fileTags = preferences.getFileTagMappings(),
                gestureMappings = preferences.getGestureMappings(),
                marqueeEnabled = preferences.marqueeEnabled
            )
        }
        val topPath = preferences.topPanelPath.let { p ->
            if (p.startsWith("content://") || File(p).isDirectory) p else HaronConstants.ROOT_PATH
        }
        val bottomPath = preferences.bottomPanelPath.let { p ->
            if (p.startsWith("content://") || File(p).isDirectory) p else HaronConstants.ROOT_PATH
        }
        navigateTo(PanelId.TOP, topPath)
        navigateTo(PanelId.BOTTOM, bottomPath)

        // Загрузить SAF roots
        refreshSafRoots()

        // Автоочистка просроченных записей корзины + обновить инфо корзины
        viewModelScope.launch {
            cleanExpiredTrashUseCase()
            updateTrashSizeInfo()
        }

        // Подписка на прогресс файловых операций из foreground service
        viewModelScope.launch {
            FileOperationService.progress.collect { progress ->
                _uiState.update { it.copy(operationProgress = progress) }
                // Когда операция завершена — haptic + log + обновить обе панели
                if (progress?.isComplete == true) {
                    if (progress.error == null) {
                        hapticManager.completion()
                    } else {
                        hapticManager.error()
                    }
                    delay(500)
                    refreshPanel(PanelId.TOP)
                    refreshPanel(PanelId.BOTTOM)
                    // Очистить прогресс через 3 секунды
                    delay(3000)
                    _uiState.update { it.copy(operationProgress = null) }
                }
            }
        }

        // Network discovery — find Haron instances and SMB shares
        networkDeviceScanner.startDiscovery()
        viewModelScope.launch {
            networkDeviceScanner.devices.collect { devices ->
                val enriched = devices.map { d ->
                    d.copy(
                        alias = preferences.getDeviceAlias(d.name),
                        isTrusted = preferences.isDeviceTrusted(d.name)
                    )
                }
                _uiState.update { it.copy(networkDevices = enriched) }
            }
        }

        // Listener is started in MainActivity (lifecycle-level) to stay alive across all screens
        _uiState.update { it.copy(isListeningForTransfer = true) }

        // USB OTG — register receiver and subscribe to volume changes
        usbStorageManager.register()
        viewModelScope.launch {
            var previousPaths = emptySet<String>()
            usbStorageManager.usbVolumes.collect { volumes ->
                _uiState.update { it.copy(usbVolumes = volumes) }
                val currentPaths = volumes.map { it.path }.toSet()
                // Detect new USB connections
                val added = currentPaths - previousPaths
                for (path in added) {
                    val vol = volumes.firstOrNull { it.path == path }
                    if (vol != null) {
                        _toastMessage.tryEmit(
                            appContext.getString(R.string.usb_connected, vol.label)
                        )
                    }
                }
                // Detect USB disconnections
                val removed = previousPaths - currentPaths
                for (path in removed) {
                    // Navigate away from USB path if panel is on it
                    navigateAwayFromUsb(path)
                }
                previousPaths = currentPaths
            }
        }

        // Voice commands are handled globally by HaronNavigation's dispatcher.
        // Local actions are passed via TransferHolder.pendingVoiceAction → ExplorerScreen.

        // Quick Send — auto-refresh panels when files received
        viewModelScope.launch {
            receiveFileManager.quickReceiveCompleted.collect { dirPath ->
                // Refresh any panel showing the receive directory
                if (_uiState.value.topPanel.currentPath == dirPath) refreshPanel(PanelId.TOP)
                if (_uiState.value.bottomPanel.currentPath == dirPath) refreshPanel(PanelId.BOTTOM)
            }
        }

        // Quick Receive — progress tracking
        viewModelScope.launch {
            receiveFileManager.quickReceiveProgress.collect { progress ->
                _uiState.update {
                    it.copy(
                        quickReceiveProgress = progress,
                        quickReceiveDeviceName = if (progress != null) (it.quickReceiveDeviceName ?: "") else null
                    )
                }
            }
        }

    }

    override fun onCleared() {
        super.onCleared()
        usbStorageManager.unregister()
        networkDeviceScanner.stopDiscovery()
        // Don't call receiveFileManager.stopListening() — the global listener
        // lives in MainActivity and must survive ViewModel lifecycle
    }

    /** Threshold: use foreground service for >10 files or >50MB total */
    private companion object {
        const val SERVICE_FILE_THRESHOLD = 10
        const val SERVICE_SIZE_THRESHOLD = 50L * 1024 * 1024 // 50 MB
        const val MAX_HISTORY_SIZE = 50
    }

    // --- USB OTG ---

    private fun navigateAwayFromUsb(usbPath: String) {
        val topPath = _uiState.value.topPanel.currentPath
        val bottomPath = _uiState.value.bottomPanel.currentPath
        if (topPath.startsWith(usbPath)) {
            navigateTo(PanelId.TOP, HaronConstants.ROOT_PATH)
        }
        if (bottomPath.startsWith(usbPath)) {
            navigateTo(PanelId.BOTTOM, HaronConstants.ROOT_PATH)
        }
    }

    fun ejectUsb(usbPath: String) {
        val volume = usbStorageManager.getVolumeForPath(usbPath)
        val label = volume?.label ?: usbPath.substringAfterLast('/')
        // Navigate away first
        navigateAwayFromUsb(usbPath)
        viewModelScope.launch {
            val unmounted = withContext(Dispatchers.IO) {
                usbStorageManager.safeEject(usbPath)
            }
            if (unmounted) {
                _toastMessage.tryEmit(
                    appContext.getString(R.string.usb_ejected_safe, label)
                )
            } else {
                _toastMessage.tryEmit(
                    appContext.getString(R.string.usb_safe_to_remove, label)
                )
            }
        }
    }

    // --- Network ---

    fun onNetworkDeviceTap(device: com.vamp.haron.data.network.NetworkDevice) {
        when (device.type) {
            NetworkDeviceType.HARON -> {
                // Open transfer screen to send files to this Haron device
                _toastMessage.tryEmit(
                    appContext.getString(R.string.network_haron_device, device.address)
                )
                openTransfer()
            }
            NetworkDeviceType.SMB -> {
                _toastMessage.tryEmit(
                    appContext.getString(R.string.network_smb_info, device.address, device.port)
                )
            }
        }
    }

    fun refreshNetwork() {
        networkDeviceScanner.refreshDevices()
    }

    // --- Quick Send ---

    fun startQuickSend(filePath: String, fileName: String, offset: Offset, fromTopPanel: Boolean) {
        val haronDevices = _uiState.value.networkDevices.filter { it.type == NetworkDeviceType.HARON }
        if (haronDevices.isEmpty()) {
            _toastMessage.tryEmit(appContext.getString(R.string.quick_send_no_devices))
            return
        }
        _uiState.update {
            it.copy(
                quickSendState = QuickSendState.DraggingToDevice(
                    filePath = filePath,
                    fileName = fileName,
                    anchorOffset = offset,
                    dragOffset = offset,
                    haronDevices = haronDevices,
                    fromTopPanel = fromTopPanel
                )
            )
        }
    }

    fun updateQuickSendDrag(offset: Offset) {
        val current = _uiState.value.quickSendState
        if (current is QuickSendState.DraggingToDevice) {
            _uiState.update {
                it.copy(quickSendState = current.copy(dragOffset = offset))
            }
        }
    }

    fun endQuickSendAtPosition(finalOffset: Offset) {
        val current = _uiState.value.quickSendState
        if (current !is QuickSendState.DraggingToDevice) {
            cancelQuickSend()
            return
        }
        // Hit-test: vertical list of rows, mirrors QuickSendOverlay Column layout
        val devices = current.haronDevices
        val dm = appContext.resources.displayMetrics
        val density = dm.density
        val screenHeightPx = dm.heightPixels.toFloat()
        val rowHeightPx = 48f * density
        val rowSpacingPx = 4f * density
        val paddingPx = 8f * density // vertical padding
        val horizontalPaddingPx = 12f * density
        val statusBarPx = 40f * density // approximate status bar

        val showAtBottom = current.fromTopPanel
        val count = devices.size
        val totalContentHeight = count * rowHeightPx + (count - 1) * rowSpacingPx

        var matched: NetworkDevice? = null

        devices.forEachIndexed { index, device ->
            val rowTop: Float
            if (showAtBottom) {
                // Column aligned to bottom: items top-to-bottom within bottom-aligned Column
                val columnTop = screenHeightPx - paddingPx - totalContentHeight
                rowTop = columnTop + index * (rowHeightPx + rowSpacingPx)
            } else {
                // Column aligned to top with status bar inset
                rowTop = statusBarPx + paddingPx + index * (rowHeightPx + rowSpacingPx)
            }
            val rowBottom = rowTop + rowHeightPx

            if (finalOffset.y in rowTop..rowBottom &&
                finalOffset.x >= horizontalPaddingPx &&
                finalOffset.x <= dm.widthPixels - horizontalPaddingPx
            ) {
                matched = device
            }
        }

        if (matched != null) {
            performQuickSend(current.filePath, matched!!.displayName, matched!!.address, matched!!.port)
        } else {
            cancelQuickSend()
        }
    }

    fun cancelQuickSend() {
        _uiState.update {
            it.copy(
                topPanel = it.topPanel.copy(selectedPaths = emptySet(), isSelectionMode = false),
                bottomPanel = it.bottomPanel.copy(selectedPaths = emptySet(), isSelectionMode = false),
                quickSendState = QuickSendState.Idle
            )
        }
    }

    /** Find WiFi Network for socket binding (avoids mobile data routing) */
    private fun findWifiNetwork(): android.net.Network? {
        val cm = appContext.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                as? android.net.ConnectivityManager ?: return null
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) {
                return network
            }
        }
        return null
    }

    /** Try discovered port first, then scan 8080-8090 on ECONNREFUSED */
    private fun connectWithPortScan(address: String, preferredPort: Int): java.net.Socket {
        val wifiNetwork = findWifiNetwork()
        val portsToTry = buildList {
            add(preferredPort)
            for (p in HaronConstants.TRANSFER_PORT_START..HaronConstants.TRANSFER_PORT_END) {
                if (p != preferredPort) add(p)
            }
        }
        var lastError: Exception? = null
        for (tryPort in portsToTry) {
            try {
                val sock = java.net.Socket()
                // Bind to WiFi to avoid routing through mobile data
                wifiNetwork?.bindSocket(sock)
                sock.connect(java.net.InetSocketAddress(address, tryPort), 3_000)
                if (tryPort != preferredPort) {
                    EcosystemLogger.d(HaronConstants.TAG, "Port scan: connected on $tryPort (NSD reported $preferredPort)")
                }
                return sock
            } catch (e: java.net.ConnectException) {
                lastError = e
            } catch (e: java.net.SocketTimeoutException) {
                lastError = e
            }
        }
        throw lastError ?: java.io.IOException("Cannot connect to $address")
    }

    private fun performQuickSend(filePath: String, deviceName: String, address: String, port: Int) {
        _uiState.update { it.copy(quickSendState = QuickSendState.Sending(deviceName)) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    withContext(Dispatchers.Main) {
                        _toastMessage.tryEmit(appContext.getString(R.string.quick_send_failed, "File not found"))
                        _uiState.update { it.copy(quickSendState = QuickSendState.Idle) }
                    }
                    return@launch
                }

                connectWithPortScan(address, port).use { sock ->
                    val output = java.io.DataOutputStream(sock.getOutputStream())
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(sock.getInputStream()))

                    // Send QUICK_SEND with sender name for trust verification
                    val androidId = android.provider.Settings.Secure.getString(appContext.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: ""
                    val senderName = "Haron-${android.os.Build.MODEL}-${androidId.takeLast(8)}"
                    val request = com.vamp.haron.data.transfer.TransferProtocolNegotiator.buildQuickSend(listOf(file), senderName)
                    output.writeUTF(request)
                    output.flush()

                    // Wait for ACCEPT
                    val response = reader.readLine() ?: throw java.io.IOException("No response from receiver")
                    val type = com.vamp.haron.data.transfer.TransferProtocolNegotiator.parseType(response)
                    if (type == com.vamp.haron.data.transfer.TransferProtocolNegotiator.TYPE_DECLINE) {
                        withContext(Dispatchers.Main) {
                            _toastMessage.tryEmit(appContext.getString(R.string.quick_send_declined))
                            _uiState.update {
                                it.copy(
                                    topPanel = it.topPanel.copy(selectedPaths = emptySet(), isSelectionMode = false),
                                    bottomPanel = it.bottomPanel.copy(selectedPaths = emptySet(), isSelectionMode = false),
                                    quickSendState = QuickSendState.Idle
                                )
                            }
                        }
                        return@launch
                    }

                    // Send file header + data
                    val totalBytes = file.length()
                    output.writeUTF(
                        com.vamp.haron.data.transfer.TransferProtocolNegotiator.buildFileHeader(
                            file.name, totalBytes, 0
                        )
                    )
                    output.flush()

                    var transferred = 0L
                    val startTime = System.currentTimeMillis()
                    var lastUpdateTime = 0L
                    file.inputStream().use { input ->
                        val buffer = ByteArray(HaronConstants.TRANSFER_BUFFER_SIZE)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            transferred += read
                            val now = System.currentTimeMillis()
                            if (now - lastUpdateTime >= 100 || transferred == totalBytes) {
                                lastUpdateTime = now
                                val elapsed = now - startTime
                                val speed = if (elapsed > 0) (transferred * 1000 / elapsed) else 0
                                val eta = if (speed > 0) ((totalBytes - transferred) / speed) else 0
                                val progress = com.vamp.haron.domain.model.TransferProgressInfo(
                                    bytesTransferred = transferred,
                                    totalBytes = totalBytes,
                                    currentFileIndex = 0,
                                    totalFiles = 1,
                                    currentFileName = file.name,
                                    speedBytesPerSec = speed,
                                    etaSeconds = eta
                                )
                                _uiState.update {
                                    it.copy(quickSendState = QuickSendState.Sending(deviceName, progress))
                                }
                            }
                        }
                    }
                    output.flush()

                    // Send COMPLETE
                    output.writeUTF(com.vamp.haron.data.transfer.TransferProtocolNegotiator.buildComplete())
                    output.flush()
                }

                withContext(Dispatchers.Main) {
                    _toastMessage.tryEmit(appContext.getString(R.string.quick_send_done, deviceName))
                }
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "Quick send error: ${e.message}")
                withContext(Dispatchers.Main) {
                    _toastMessage.tryEmit(appContext.getString(R.string.quick_send_failed, e.message ?: ""))
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            topPanel = it.topPanel.copy(selectedPaths = emptySet(), isSelectionMode = false),
                            bottomPanel = it.bottomPanel.copy(selectedPaths = emptySet(), isSelectionMode = false),
                            quickSendState = QuickSendState.Idle
                        )
                    }
                }
            }
        }
    }

    // --- Navigation ---

    fun navigateTo(panelId: PanelId, path: String, pushHistory: Boolean = true) {
        navigationJobs[panelId]?.cancel()
        navigationJobs[panelId] = viewModelScope.launch {
            // Save current scroll position before navigating away (only when actually changing folder)
            val currentPath = getPanel(panelId).currentPath
            val isNewFolder = currentPath.isNotEmpty() && currentPath != path
            if (isNewFolder) {
                scrollCache[currentPath] = panelScrollIndex[panelId] ?: 0
            }

            // Clear search when navigating to a different folder
            if (isNewFolder) {
                contentSearchJobs[panelId]?.cancel()
                folderIndexJobs[panelId]?.cancel()
                updatePanel(panelId) { it.copy(isLoading = true, error = null, searchQuery = "", isSearchActive = false, searchInContent = false, contentSearchSnippets = null, isContentIndexing = false, contentIndexProgress = null) }
            } else {
                updatePanel(panelId) { it.copy(isLoading = true, error = null) }
            }

            val panel = getPanel(panelId)
            val displayPath = when {
                path == HaronConstants.VIRTUAL_SECURE_PATH -> appContext.getString(R.string.all_secure_files)
                path.startsWith("content://") -> buildSafDisplayPath(path)
                else -> path.removePrefix(HaronConstants.ROOT_PATH).ifEmpty { "/" }
            }

            val t0 = System.nanoTime()
            getFilesUseCase(
                path = path,
                sortOrder = panel.sortOrder,
                showHidden = panel.showHidden,
                showProtected = _uiState.value.isShieldUnlocked
            ).onSuccess { files ->
                val loadMs = (System.nanoTime() - t0) / 1_000_000
                if (loadMs > 50) {
                    EcosystemLogger.d("Perf", "navigateTo($path): ${loadMs}ms, ${files.size} files")
                }
                val isNewPath = currentPath != path
                val savedScroll = if (isNewPath) scrollCache[path] ?: 0 else -1
                if (isNewPath) scrollTrigger++
                updatePanel(panelId) {
                    var history = it.navigationHistory
                    var index = it.historyIndex
                    if (pushHistory) {
                        // Обрезаем forward-стек и добавляем новый путь
                        history = history.take(index + 1) + path
                        if (history.size > MAX_HISTORY_SIZE) {
                            history = history.takeLast(MAX_HISTORY_SIZE)
                        }
                        index = history.lastIndex
                    }
                    val base = it.copy(
                        currentPath = path,
                        displayPath = displayPath,
                        files = files,
                        isLoading = false,
                        error = null,
                        navigationHistory = history,
                        historyIndex = index,
                        isSafPath = path.startsWith("content://"),
                        showProtected = path == HaronConstants.VIRTUAL_SECURE_PATH ||
                            secureFolderRepository.isFileProtected(path) ||
                            (!java.io.File(path).exists() && secureFolderRepository.hasProtectedDescendants(path)),
                        // Ensure search is cleared when entering a new folder
                        // (guards against BasicTextField race re-emitting old query)
                        searchQuery = if (isNewPath) "" else it.searchQuery,
                        isSearchActive = if (isNewPath) false else it.isSearchActive,
                        searchInContent = if (isNewPath) false else it.searchInContent,
                        contentSearchSnippets = if (isNewPath) null else it.contentSearchSnippets,
                        isContentIndexing = if (isNewPath) false else it.isContentIndexing,
                        contentIndexProgress = if (isNewPath) null else it.contentIndexProgress
                    )
                    if (savedScroll >= 0) {
                        base.copy(
                            scrollToIndex = savedScroll,
                            scrollToTrigger = scrollTrigger
                        )
                    } else base
                }
                // Save panel path & track recent (skip virtual paths)
                if (path != HaronConstants.VIRTUAL_SECURE_PATH && !secureFolderRepository.isFileProtected(path) &&
                    !((!java.io.File(path).exists()) && secureFolderRepository.hasProtectedDescendants(path))) {
                    when (panelId) {
                        PanelId.TOP -> preferences.topPanelPath = path
                        PanelId.BOTTOM -> preferences.bottomPanelPath = path
                    }
                    if (!path.startsWith("content://")) {
                        preferences.addRecentPath(path)
                        _uiState.update { it.copy(recentPaths = preferences.getRecentPaths()) }
                    }
                }
                EcosystemLogger.d(HaronConstants.TAG, "[$panelId] Открыта папка: $path (${files.size} файлов)")
                // Calculate current folder size for breadcrumb
                if (!path.startsWith("content://") && path != HaronConstants.VIRTUAL_SECURE_PATH &&
                    _uiState.value.folderSizeCache[path] == null && !folderSizeJobs.containsKey(path)) {
                    calculateFolderSize(path)
                }
                // Calculate sizes for subdirectories (for display in file list)
                if (!path.startsWith("content://") && path != HaronConstants.VIRTUAL_SECURE_PATH) {
                    files.filter { it.isDirectory }.forEach { dir ->
                        if (_uiState.value.folderSizeCache[dir.path] == null && !folderSizeJobs.containsKey(dir.path)) {
                            calculateFolderSize(dir.path)
                        }
                    }
                }
            }.onFailure { error ->
                updatePanel(panelId) {
                    it.copy(
                        isLoading = false,
                        error = error.message ?: appContext.getString(R.string.unknown_error)
                    )
                }
                EcosystemLogger.e(HaronConstants.TAG, "[$panelId] Ошибка навигации: $path — ${error.message}")
            }
        }
    }

    /**
     * Navigate to a folder and scroll to a specific file by its full path.
     * Used when returning from global search to show the file in context.
     */
    fun navigateToFileLocation(panelId: PanelId, folderPath: String, filePath: String) {
        navigationJobs[panelId]?.cancel()
        navigationJobs[panelId] = viewModelScope.launch {
            val currentPath = getPanel(panelId).currentPath
            val isNewFolder = currentPath.isNotEmpty() && currentPath != folderPath
            if (isNewFolder) {
                scrollCache[currentPath] = panelScrollIndex[panelId] ?: 0
            }

            // Clear search when navigating to a different folder
            if (isNewFolder) {
                contentSearchJobs[panelId]?.cancel()
                folderIndexJobs[panelId]?.cancel()
                updatePanel(panelId) { it.copy(isLoading = true, error = null, searchQuery = "", isSearchActive = false, searchInContent = false, contentSearchSnippets = null, isContentIndexing = false, contentIndexProgress = null) }
            } else {
                updatePanel(panelId) { it.copy(isLoading = true, error = null) }
            }

            val panel = getPanel(panelId)
            val displayPath = when {
                folderPath == HaronConstants.VIRTUAL_SECURE_PATH -> appContext.getString(R.string.all_secure_files)
                folderPath.startsWith("content://") -> buildSafDisplayPath(folderPath)
                else -> folderPath.removePrefix(HaronConstants.ROOT_PATH).ifEmpty { "/" }
            }

            getFilesUseCase(
                path = folderPath,
                sortOrder = panel.sortOrder,
                showHidden = panel.showHidden,
                showProtected = _uiState.value.isShieldUnlocked
            ).onSuccess { files ->
                scrollTrigger++
                val fileIndex = files.indexOfFirst { it.path == filePath }.coerceAtLeast(0)
                updatePanel(panelId) {
                    var history = it.navigationHistory
                    var index = it.historyIndex
                    history = history.take(index + 1) + folderPath
                    if (history.size > MAX_HISTORY_SIZE) {
                        history = history.takeLast(MAX_HISTORY_SIZE)
                    }
                    index = history.lastIndex
                    it.copy(
                        currentPath = folderPath,
                        displayPath = displayPath,
                        files = files,
                        isLoading = false,
                        error = null,
                        navigationHistory = history,
                        historyIndex = index,
                        isSafPath = folderPath.startsWith("content://"),
                        scrollToIndex = fileIndex,
                        scrollToTrigger = scrollTrigger,
                        // Clear search when entering a new folder
                        searchQuery = if (isNewFolder) "" else it.searchQuery,
                        isSearchActive = if (isNewFolder) false else it.isSearchActive,
                        searchInContent = if (isNewFolder) false else it.searchInContent,
                        contentSearchSnippets = if (isNewFolder) null else it.contentSearchSnippets,
                        isContentIndexing = if (isNewFolder) false else it.isContentIndexing,
                        contentIndexProgress = if (isNewFolder) null else it.contentIndexProgress
                    )
                }
                when (panelId) {
                    PanelId.TOP -> preferences.topPanelPath = folderPath
                    PanelId.BOTTOM -> preferences.bottomPanelPath = folderPath
                }
            }.onFailure { error ->
                updatePanel(panelId) {
                    it.copy(
                        isLoading = false,
                        error = error.message ?: appContext.getString(R.string.unknown_error)
                    )
                }
            }
        }
    }

    fun navigateUp(panelId: PanelId, pushHistory: Boolean = true): Boolean {
        val panel = getPanel(panelId)
        // Archive mode — navigate up inside archive or exit archive
        if (panel.isArchiveMode) {
            if (panel.archiveVirtualPath.isNotEmpty()) {
                // Go up one level inside the archive
                val parentVirtual = panel.archiveVirtualPath.trimEnd('/').substringBeforeLast('/', "")
                navigateIntoArchive(panelId, panel.archivePath!!, parentVirtual, panel.archivePassword)
            } else {
                // Exit archive — return to the folder containing the archive file
                val archiveParent = File(panel.archivePath!!).parent ?: HaronConstants.ROOT_PATH
                // Force navigateTo to treat this as a "new folder" so scroll is restored
                updatePanel(panelId) { it.copy(archivePath = null, archiveVirtualPath = "", archivePassword = null, archiveExtractProgress = null, currentPath = "") }
                navigateTo(panelId, archiveParent, pushHistory = pushHistory)
            }
            return true
        }
        val currentPath = panel.currentPath
        // Virtual secure path — exit virtual view, turn off shield
        if (currentPath == HaronConstants.VIRTUAL_SECURE_PATH) {
            _uiState.update { it.copy(isShieldUnlocked = false) }
            if (canNavigateBack(panelId)) {
                navigateBack(panelId)
            } else {
                navigateTo(panelId, HaronConstants.ROOT_PATH, pushHistory = false)
            }
            return true
        }
        // Protected directory (real or virtual subdirectory) — use history back
        if (secureFolderRepository.isFileProtected(currentPath) ||
            (!java.io.File(currentPath).exists() && secureFolderRepository.hasProtectedDescendants(currentPath))) {
            if (canNavigateBack(panelId)) {
                navigateBack(panelId)
                return true
            }
            return false
        }
        val parentPath = fileRepository.getParentPath(currentPath) ?: return false
        navigateTo(panelId, parentPath, pushHistory = pushHistory)
        return true
    }

    fun onFileClick(panelId: PanelId, entry: FileEntry) {
        setActivePanel(panelId)
        val panel = getPanel(panelId)
        if (panel.isSelectionMode) {
            toggleSelection(panelId, entry.path)
        } else if (panel.isArchiveMode && entry.isDirectory) {
            // Navigate deeper inside the archive
            val virtualPath = entry.path.substringAfter("!/", "")
            navigateIntoArchive(panelId, panel.archivePath!!, virtualPath, panel.archivePassword)
            return
        } else if (entry.isProtected && !entry.isDirectory) {
            onProtectedFileClick(entry)
            return
        } else if (entry.isDirectory) {
            navigateTo(panelId, entry.path)
        } else if (entry.name.lowercase().endsWith(".fb2.zip")) {
            preferences.lastDocumentFile = entry.path
            _navigationEvent.tryEmit(NavigationEvent.OpenDocumentViewer(entry.path, entry.name))
        } else {
            // Set highlight query if in content search mode
            SearchNavigationHolder.highlightQuery = if (panel.searchInContent && panel.searchQuery.isNotBlank()) panel.searchQuery else null
            val type = entry.iconRes()
            when (type) {
                "video", "audio" -> {
                    // Build playlist from all media files in folder
                    val mediaFiles = panel.files.filter { f ->
                        !f.isDirectory && f.iconRes() in listOf("video", "audio")
                    }
                    PlaylistHolder.items = mediaFiles.map { f ->
                        PlaylistHolder.PlaylistItem(
                            filePath = f.path,
                            fileName = f.name,
                            fileType = f.iconRes()
                        )
                    }
                    val startIndex = mediaFiles.indexOfFirst { it.path == entry.path }.coerceAtLeast(0)
                    PlaylistHolder.startIndex = startIndex
                    preferences.lastMediaFile = entry.path
                    _navigationEvent.tryEmit(
                        NavigationEvent.OpenMediaPlayer(startIndex)
                    )
                }
                "text", "code" -> {
                    _navigationEvent.tryEmit(
                        NavigationEvent.OpenTextEditor(entry.path, entry.name)
                    )
                }
                "image" -> {
                    val imageFiles = panel.files.filter { f ->
                        !f.isDirectory && f.iconRes() == "image"
                    }
                    GalleryHolder.items = imageFiles.map { f ->
                        GalleryHolder.GalleryItem(
                            filePath = f.path,
                            fileName = f.name,
                            fileSize = f.size
                        )
                    }
                    val startIndex = imageFiles.indexOfFirst { it.path == entry.path }.coerceAtLeast(0)
                    GalleryHolder.startIndex = startIndex
                    _navigationEvent.tryEmit(NavigationEvent.OpenGallery(startIndex))
                }
                "pdf" -> {
                    preferences.lastDocumentFile = entry.path
                    _navigationEvent.tryEmit(
                        NavigationEvent.OpenPdfReader(entry.path, entry.name)
                    )
                }
                "document", "spreadsheet" -> {
                    preferences.lastDocumentFile = entry.path
                    _navigationEvent.tryEmit(
                        NavigationEvent.OpenDocumentViewer(entry.path, entry.name)
                    )
                }
                "archive" -> {
                    navigateIntoArchive(panelId, entry.path, "", null)
                }
                "apk" -> {
                    showApkInstallDialog(entry)
                }
                else -> {
                    // No built-in handler — open with external app
                    openWithExternalApp(entry)
                }
            }
        }
    }

    /** Build playlist from preview dialog context (for fullscreen play from QuickPreview).
     *  Returns the startIndex in the filtered media list. */
    fun buildPlaylistFromPreview(entry: FileEntry, adjacentFiles: List<FileEntry>, currentIndex: Int): Int {
        val mediaFiles = adjacentFiles.filter { f ->
            !f.isDirectory && f.iconRes() in listOf("video", "audio")
        }
        PlaylistHolder.items = mediaFiles.map { f ->
            PlaylistHolder.PlaylistItem(
                filePath = f.path,
                fileName = f.name,
                fileType = f.iconRes()
            )
        }
        // Find the index of the current entry in filtered media list
        val idx = mediaFiles.indexOfFirst { it.path == entry.path }.coerceAtLeast(0)
        PlaylistHolder.startIndex = idx
        return idx
    }

    /** Build gallery from preview dialog context. Returns startIndex in filtered image list. */
    fun buildGalleryFromPreview(entry: FileEntry, adjacentFiles: List<FileEntry>, currentIndex: Int): Int {
        val imageFiles = adjacentFiles.filter { f ->
            !f.isDirectory && f.iconRes() == "image"
        }
        GalleryHolder.items = imageFiles.map { f ->
            GalleryHolder.GalleryItem(
                filePath = f.path,
                fileName = f.name,
                fileSize = f.size
            )
        }
        val idx = imageFiles.indexOfFirst { it.path == entry.path }.coerceAtLeast(0)
        GalleryHolder.startIndex = idx
        return idx
    }

    fun onFileLongClick(panelId: PanelId, entry: FileEntry) {
        setActivePanel(panelId)
        val panel = getPanel(panelId)
        // Save current selection as base for potential drag
        dragBaseSelection = panel.selectedPaths
        updatePanel(panelId) {
            it.copy(
                isSelectionMode = true,
                selectedPaths = dragBaseSelection + entry.path
            )
        }
    }

    fun onIconClick(panelId: PanelId, entry: FileEntry) {
        setActivePanel(panelId)
        val panel = getPanel(panelId)
        when {
            panel.isSelectionMode -> toggleSelection(panelId, entry.path)
            entry.isDirectory -> navigateTo(panelId, entry.path)
            entry.iconRes() == "apk" && !entry.isProtected -> showApkInstallDialog(entry)
            else -> {
                val allFiles = panel.files.filter { !it.isDirectory }
                val fileIndex = allFiles.indexOfFirst { it.path == entry.path }
                val idx = if (fileIndex >= 0) fileIndex else 0

                _uiState.update {
                    it.copy(dialogState = DialogState.QuickPreview(
                        entry = entry,
                        adjacentFiles = allFiles,
                        currentFileIndex = idx
                    ))
                }
                viewModelScope.launch {
                    val previewEntry = resolvePreviewEntry(entry)
                    loadPreviewUseCase(previewEntry)
                        .onSuccess { data ->
                            val current = _uiState.value.dialogState
                            if (current is DialogState.QuickPreview && current.entry.path == entry.path) {
                                _uiState.update {
                                    it.copy(dialogState = current.copy(
                                        previewData = data,
                                        isLoading = false,
                                        previewCache = current.previewCache + (idx to data)
                                    ))
                                }
                                // Preload neighbors
                                preloadPreview(idx - 1, allFiles)
                                preloadPreview(idx + 1, allFiles)
                            }
                        }
                        .onFailure { e ->
                            val current = _uiState.value.dialogState
                            if (current is DialogState.QuickPreview && current.entry.path == entry.path) {
                                _uiState.update {
                                    it.copy(dialogState = current.copy(
                                        isLoading = false,
                                        error = e.message ?: appContext.getString(R.string.error_loading_preview)
                                    ))
                                }
                            }
                        }
                }
            }
        }
    }

    fun canNavigateUp(panelId: PanelId): Boolean {
        val panel = getPanel(panelId)
        if (panel.isArchiveMode) return true
        val path = panel.currentPath
        if (path == HaronConstants.VIRTUAL_SECURE_PATH) return false
        if (secureFolderRepository.isFileProtected(path) ||
            (!java.io.File(path).exists() && secureFolderRepository.hasProtectedDescendants(path))) return canNavigateBack(panelId)
        return fileRepository.getParentPath(path) != null
    }

    fun navigateBack(panelId: PanelId) {
        val panel = getPanel(panelId)
        if (panel.historyIndex <= 0) {
            // No history — if in archive mode, exit archive
            if (panel.isArchiveMode) {
                val archiveParent = File(panel.archivePath!!).parent ?: HaronConstants.ROOT_PATH
                updatePanel(panelId) { it.copy(archivePath = null, archiveVirtualPath = "", archivePassword = null, archiveExtractProgress = null) }
                navigateTo(panelId, archiveParent, pushHistory = false)
            }
            return
        }
        val newIndex = panel.historyIndex - 1
        val path = panel.navigationHistory[newIndex]
        updatePanel(panelId) { it.copy(historyIndex = newIndex) }
        if (path.contains("!/")) {
            val archivePath = path.substringBefore("!/")
            val virtualPath = path.substringAfter("!/", "")
            navigateIntoArchive(panelId, archivePath, virtualPath, panel.archivePassword, pushHistory = false)
        } else {
            // Exiting archive mode — clear archive state, reset currentPath to trigger scroll restore
            if (panel.isArchiveMode) {
                updatePanel(panelId) { it.copy(archivePath = null, archiveVirtualPath = "", archivePassword = null, archiveExtractProgress = null, currentPath = "") }
            }
            navigateTo(panelId, path, pushHistory = false)
        }
    }

    fun navigateForward(panelId: PanelId) {
        val panel = getPanel(panelId)
        if (panel.historyIndex >= panel.navigationHistory.lastIndex) return
        val newIndex = panel.historyIndex + 1
        val path = panel.navigationHistory[newIndex]
        updatePanel(panelId) { it.copy(historyIndex = newIndex) }
        if (path.contains("!/")) {
            val archivePath = path.substringBefore("!/")
            val virtualPath = path.substringAfter("!/", "")
            navigateIntoArchive(panelId, archivePath, virtualPath, panel.archivePassword, pushHistory = false)
        } else {
            if (panel.isArchiveMode) {
                updatePanel(panelId) { it.copy(archivePath = null, archiveVirtualPath = "", archivePassword = null, archiveExtractProgress = null) }
            }
            navigateTo(panelId, path, pushHistory = false)
        }
    }

    fun canNavigateBack(panelId: PanelId): Boolean {
        val panel = getPanel(panelId)
        // Virtual secure root — no back (exit via shield button only)
        if (panel.currentPath == HaronConstants.VIRTUAL_SECURE_PATH) return false
        return panel.historyIndex > 0
    }

    fun canNavigateForward(panelId: PanelId): Boolean {
        val panel = getPanel(panelId)
        return panel.historyIndex < panel.navigationHistory.lastIndex
    }

    fun openInOtherPanel(panelId: PanelId) {
        val path = getPanel(panelId).currentPath
        val otherId = if (panelId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
        navigateTo(otherId, path)
    }

    fun toggleOriginalFolder(panelId: PanelId) {
        val path = getPanel(panelId).currentPath
        _uiState.update { st ->
            val newFolders = st.originalFolders.toMutableSet()
            if (path in newFolders) newFolders.remove(path) else newFolders.add(path)
            st.copy(originalFolders = newFolders)
        }
        preferences.saveOriginalFolders(_uiState.value.originalFolders)
        val isNow = path in _uiState.value.originalFolders
        _toastMessage.tryEmit(if (isNow) appContext.getString(R.string.folder_marked_original) else appContext.getString(R.string.marker_removed))
    }

    fun cycleTheme() {
        val current = EcosystemPreferences.theme
        val next = when (current) {
            "system" -> "light"
            "light" -> "dark"
            else -> "system"
        }
        setTheme(next)
    }

    fun setTheme(theme: String) {
        EcosystemPreferences.theme = theme
        _uiState.update { it.copy(themeMode = theme) }
    }

    // --- SAF ---

    fun onSafUriGranted(uri: Uri) {
        safUriManager.persistUri(uri)
        refreshSafRoots()
        navigateTo(_uiState.value.activePanel, uri.toString())
    }

    fun hasRemovableStorage(): Boolean {
        return storageVolumeHelper.getRemovableVolumes().isNotEmpty() ||
            safUriManager.getPersistedUris().isNotEmpty()
    }

    fun getSdCardLabel(): String {
        val volumes = storageVolumeHelper.getRemovableVolumes()
        return volumes.firstOrNull()?.label ?: appContext.getString(R.string.sd_card)
    }

    fun navigateToSdCard() {
        val persisted = safUriManager.getPersistedUris()
        if (persisted.isNotEmpty()) {
            navigateTo(_uiState.value.activePanel, persisted.first().toString())
        }
        // If no persisted URI — UI should launch SAF picker
    }

    fun hasSafPermission(): Boolean {
        return safUriManager.getPersistedUris().isNotEmpty()
    }

    /**
     * Build list of storage items for DrawerMenu:
     * - Each removable volume from StorageVolumeHelper
     * - Matched against persisted SAF URIs to know if access is granted
     * Pair: (label, safUri?) — null uri means no access yet
     */
    fun refreshSafRoots() {
        val removable = storageVolumeHelper.getRemovableVolumes()
        val persisted = safUriManager.getPersistedUris()

        // Only show SD cards in SAF roots section — USB drives are shown separately
        // "SD" must be a standalone word: "SD-карта", "SD card" — but NOT "SanDisk"
        val sdPattern = Regex("(^|\\s)SD(\\s|[^a-zA-Z]|$)", RegexOption.IGNORE_CASE)
        val sdCards = removable.filter { vol -> sdPattern.containsMatchIn(vol.label) }
        val roots = sdCards.map { vol ->
            val matchingUri = persisted.find { uri ->
                try {
                    val treeDocId = android.provider.DocumentsContract.getTreeDocumentId(uri)
                    val volumeId = treeDocId.split(":").firstOrNull()
                    volumeId != null && volumeId != "primary" &&
                        (vol.uuid != null && volumeId.equals(vol.uuid, ignoreCase = true))
                } catch (_: Exception) { false }
            }
            val dir = vol.path?.let { java.io.File(it) }
            SafRootInfo(
                label = vol.label,
                safUri = matchingUri?.toString() ?: "",
                totalSpace = dir?.totalSpace ?: 0L,
                freeSpace = dir?.freeSpace ?: 0L
            )
        }
        _uiState.update { it.copy(safRoots = roots) }
    }

    fun removeSafRoot(uri: String) {
        safUriManager.releaseUri(Uri.parse(uri))
        refreshSafRoots()
    }

    // --- Active panel ---

    fun setActivePanel(panelId: PanelId) {
        _uiState.update { it.copy(activePanel = panelId) }
    }

    // --- Panel ratio ---

    fun updatePanelRatio(ratio: Float) {
        val clamped = ratio.coerceIn(0.2f, 0.8f)
        _uiState.update { it.copy(panelRatio = clamped) }
    }

    fun savePanelRatio() {
        preferences.panelRatio = _uiState.value.panelRatio
    }

    fun resetPanelRatio() {
        _uiState.update { it.copy(panelRatio = 0.5f) }
        preferences.panelRatio = 0.5f
    }

    // --- Grid columns ---

    fun setGridColumns(columns: Int) {
        val clamped = columns.coerceIn(1, 6)
        _uiState.update { state ->
            state.copy(
                topPanel = state.topPanel.copy(gridColumns = clamped),
                bottomPanel = state.bottomPanel.copy(gridColumns = clamped)
            )
        }
        preferences.gridColumns = clamped
    }

    // --- Sort & hidden ---

    fun setSortOrder(panelId: PanelId, order: SortOrder) {
        preferences.saveSortOrder(order)
        updatePanel(panelId) { it.copy(sortOrder = order) }
        refreshPanel(panelId)
    }

    fun toggleShowHidden(panelId: PanelId) {
        val panel = getPanel(panelId)
        val newValue = !panel.showHidden
        preferences.showHidden = newValue
        // Update both panels (global setting) and refresh both
        _uiState.update {
            it.copy(
                topPanel = it.topPanel.copy(showHidden = newValue),
                bottomPanel = it.bottomPanel.copy(showHidden = newValue)
            )
        }
        refreshPanel(PanelId.TOP)
        refreshPanel(PanelId.BOTTOM)
        _toastMessage.tryEmit(
            appContext.getString(if (newValue) R.string.hidden_files_shown else R.string.hidden_files_hidden)
        )
    }

    // --- Search ---

    fun setSearchQuery(panelId: PanelId, query: String) {
        updatePanel(panelId) { it.copy(searchQuery = query) }
        val panel = getPanel(panelId)
        if (panel.searchInContent) {
            performContentSearch(panelId, query)
        }
    }

    fun openSearch(panelId: PanelId) {
        updatePanel(panelId) { it.copy(isSearchActive = true) }
    }

    fun closeSearch(panelId: PanelId) {
        contentSearchJobs[panelId]?.cancel()
        folderIndexJobs[panelId]?.cancel()
        updatePanel(panelId) { it.copy(isSearchActive = false, searchQuery = "", searchInContent = false, contentSearchSnippets = null, isContentIndexing = false, contentIndexProgress = null) }
        // Reload files to guarantee they are visible after search close
        refreshPanel(panelId)
    }

    fun clearSearch(panelId: PanelId) {
        folderIndexJobs[panelId]?.cancel()
        updatePanel(panelId) { it.copy(searchQuery = "", isSearchActive = false, searchInContent = false, contentSearchSnippets = null, isContentIndexing = false, contentIndexProgress = null) }
    }

    fun toggleSearchInContent(panelId: PanelId) {
        val panel = getPanel(panelId)
        val newInContent = !panel.searchInContent
        updatePanel(panelId) { it.copy(searchInContent = newInContent, contentSearchSnippets = null) }
        if (newInContent) {
            // Auto-index current folder content when enabling content search
            indexFolderAndSearch(panelId, panel.currentPath, panel.searchQuery)
        } else {
            folderIndexJobs[panelId]?.cancel()
            updatePanel(panelId) { it.copy(isContentIndexing = false, contentIndexProgress = null) }
        }
    }

    /** Active indexing jobs by folder path — shared between panels */
    private val activeFolderIndexJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    private fun indexFolderAndSearch(panelId: PanelId, folderPath: String, query: String) {
        folderIndexJobs[panelId]?.cancel()
        folderIndexJobs[panelId] = viewModelScope.launch {
            if (folderPath !in preferences.getContentIndexedFolders()) {
                // Reuse existing indexing job if another panel is already indexing this folder
                val existingJob = activeFolderIndexJobs[folderPath]
                if (existingJob != null && existingJob.isActive) {
                    updatePanel(panelId) { it.copy(isContentIndexing = true, contentIndexProgress = null) }
                    try {
                        existingJob.join()
                    } finally {
                        updatePanel(panelId) { it.copy(isContentIndexing = false, contentIndexProgress = null) }
                    }
                } else {
                    updatePanel(panelId) { it.copy(isContentIndexing = true, contentIndexProgress = null) }
                    try {
                        val indexJob = viewModelScope.launch {
                            searchRepository.indexFolderContent(folderPath, force = false) { processed, total ->
                                val progress = "$processed / $total"
                                for (pid in PanelId.entries) {
                                    val p = getPanel(pid)
                                    if (p.currentPath == folderPath && p.searchInContent) {
                                        updatePanel(pid) { it.copy(contentIndexProgress = progress) }
                                    }
                                }
                            }
                        }
                        activeFolderIndexJobs[folderPath] = indexJob
                        indexJob.join()
                        activeFolderIndexJobs.remove(folderPath)
                        preferences.addContentIndexedFolder(folderPath)
                    } finally {
                        updatePanel(panelId) { it.copy(isContentIndexing = false, contentIndexProgress = null) }
                        val otherPanelId = if (panelId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
                        val otherPanel = getPanel(otherPanelId)
                        if (otherPanel.currentPath == folderPath && otherPanel.isContentIndexing) {
                            updatePanel(otherPanelId) { it.copy(isContentIndexing = false, contentIndexProgress = null) }
                        }
                    }
                }
            }
            // After indexing (or skip), perform content search if query is present
            val currentPanel = getPanel(panelId)
            if (currentPanel.searchInContent && currentPanel.searchQuery.isNotBlank()) {
                val paths = searchRepository.searchContentInFolder(currentPanel.currentPath, currentPanel.searchQuery)
                updatePanel(panelId) { it.copy(contentSearchSnippets = paths) }
            }
        }
    }

    private fun performContentSearch(panelId: PanelId, query: String) {
        contentSearchJobs[panelId]?.cancel()
        if (query.isBlank()) {
            updatePanel(panelId) { it.copy(contentSearchSnippets = null) }
            return
        }
        contentSearchJobs[panelId] = viewModelScope.launch {
            delay(300) // debounce
            val panel = getPanel(panelId)
            val paths = searchRepository.searchContentInFolder(panel.currentPath, query)
            updatePanel(panelId) { it.copy(contentSearchSnippets = paths) }
        }
    }

    // --- Selection ---

    fun toggleSelection(panelId: PanelId, path: String) {
        updatePanel(panelId) { panel ->
            val newSet = panel.selectedPaths.toMutableSet()
            if (path in newSet) newSet.remove(path) else newSet.add(path)
            val stillSelecting = newSet.isNotEmpty()
            panel.copy(
                selectedPaths = newSet,
                isSelectionMode = stillSelecting
            )
        }
    }

    fun selectAll(panelId: PanelId) {
        updatePanel(panelId) { panel ->
            val allPaths = panel.files.map { it.path }.toSet()
            if (panel.selectedPaths == allPaths) {
                // Toggle: deselect all but stay in selection mode
                panel.copy(selectedPaths = emptySet(), isSelectionMode = true)
            } else {
                panel.copy(selectedPaths = allPaths, isSelectionMode = true)
            }
        }
    }

    fun selectByExtension(panelId: PanelId, extension: String) {
        updatePanel(panelId) { panel ->
            val matching = panel.files
                .filter { !it.isDirectory && it.name.substringAfterLast('.').lowercase() == extension }
                .map { it.path }
                .toSet()
            panel.copy(selectedPaths = matching, isSelectionMode = true)
        }
    }

    fun clearSelection(panelId: PanelId) {
        updatePanel(panelId) { it.copy(selectedPaths = emptySet(), isSelectionMode = false) }
    }

    fun selectRange(panelId: PanelId, fromIndex: Int, toIndex: Int) {
        updatePanel(panelId) { panel ->
            val files = if (panel.searchQuery.isBlank()) {
                panel.files
            } else {
                panel.files.filter { it.name.contains(panel.searchQuery, ignoreCase = true) }
            }
            val minIdx = minOf(fromIndex, toIndex).coerceAtLeast(0)
            val maxIdx = maxOf(fromIndex, toIndex).coerceAtMost(files.lastIndex)
            val rangePaths = files.subList(minIdx, maxIdx + 1).map { it.path }.toSet()
            // Merge with base selection for non-contiguous multi-range
            panel.copy(
                selectedPaths = dragBaseSelection + rangePaths,
                isSelectionMode = true
            )
        }
    }

    // --- File operations ---

    fun copySelectedToOtherPanel() {
        val state = _uiState.value
        val activeId = state.activePanel
        val targetId = if (activeId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
        val sourcePanel = getPanel(activeId)
        val targetPanel = getPanel(targetId)
        val paths = sourcePanel.selectedPaths.toList()
        if (paths.isEmpty()) return

        // Block copy TO virtual secure view (only when target panel is in protected context)
        if (targetPanel.currentPath == HaronConstants.VIRTUAL_SECURE_PATH ||
            (targetPanel.showProtected && secureFolderRepository.isFileProtected(targetPanel.currentPath))) {
            _toastMessage.tryEmit(appContext.getString(R.string.cannot_copy_to_virtual))
            return
        }

        // Protected source files — check via FileEntry.isProtected, not path index lookup
        val selectedEntries = sourcePanel.files.filter { it.path in sourcePanel.selectedPaths }
        val hasProtected = selectedEntries.any { it.isProtected }
        if (hasProtected) {
            copyProtectedFiles(paths, targetPanel.currentPath)
            return
        }

        val conflictPairs = buildConflictPairs(paths, targetPanel)
        if (conflictPairs.isNotEmpty()) {
            _uiState.update {
                it.copy(dialogState = DialogState.ConfirmConflict(
                    conflictPairs = conflictPairs,
                    allPaths = paths,
                    destinationDir = targetPanel.currentPath,
                    operationType = OperationType.COPY
                ))
            }
            return
        }

        executeCopy(paths, targetPanel.currentPath, ConflictResolution.RENAME)
    }

    private fun executeCopy(
        paths: List<String>,
        destinationDir: String,
        resolution: ConflictResolution
    ) {
        val state = _uiState.value
        val activeId = state.activePanel
        val targetId = if (activeId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
        val sourcePanel = getPanel(activeId)

        val selected = sourcePanel.files.filter { it.path in sourcePanel.selectedPaths }
        val totalSize = selected.sumOf { it.size }
        val dirs = selected.count { it.isDirectory }
        val files = selected.size - dirs
        clearSelection(activeId)

        if (paths.size > SERVICE_FILE_THRESHOLD || totalSize > SERVICE_SIZE_THRESHOLD) {
            FileOperationService.start(appContext, paths, destinationDir, isMove = false, conflictResolution = resolution)
        } else {
            val fileSizes = selected.associate { it.path to it.size }
            inlineOperationJob = viewModelScope.launch {
                runInlineOperation(paths, OperationType.COPY, fileSizes) { path ->
                    copyFilesUseCase(listOf(path), destinationDir, resolution)
                }
                copyTagsToDestination(paths, destinationDir)
                hapticManager.success()
                showStatusMessage(targetId, appContext.getString(R.string.copied_format, formatFileCount(dirs, files)))
                refreshPanel(targetId)
            }
        }
    }

    private fun executeCopyWithDecisions(
        paths: List<String>,
        destinationDir: String,
        decisions: Map<String, ConflictResolution>
    ) {
        val state = _uiState.value
        val activeId = state.activePanel
        val targetId = if (activeId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
        val sourcePanel = getPanel(activeId)

        val selected = sourcePanel.files.filter { it.path in paths.toSet() }
        val dirs = selected.count { it.isDirectory }
        val files = selected.size - dirs
        clearSelection(activeId)

        inlineOperationJob = viewModelScope.launch {
            val total = paths.size
            var completed = 0
            _uiState.update {
                it.copy(operationProgress = OperationProgress(0, total, "", OperationType.COPY))
            }
            for ((index, path) in paths.withIndex()) {
                val fileName = extractFileName(path)
                _uiState.update {
                    it.copy(operationProgress = OperationProgress(index, total, fileName, OperationType.COPY))
                }
                val resolution = decisions[path] ?: ConflictResolution.RENAME
                fileRepository.copyFilesWithResolutions(
                    listOf(path), destinationDir, mapOf(path to resolution)
                ).onSuccess { c -> completed += c }
            }
            _uiState.update {
                it.copy(operationProgress = OperationProgress(completed, total, "", OperationType.COPY, isComplete = true))
            }
            val skipped = total - completed
            val msg = when {
                completed == 0 -> appContext.getString(R.string.skipped_count, total)
                skipped > 0 -> appContext.getString(R.string.copied_with_skip, completed, skipped)
                else -> appContext.getString(R.string.copied_format, formatFileCount(dirs, files))
            }
            showStatusMessage(targetId, msg)
            if (completed > 0) {
                hapticManager.success()
                copyTagsToDestination(paths, destinationDir)
            } else hapticManager.error()
            refreshPanel(targetId)
            viewModelScope.launch {
                delay(2000)
                _uiState.update {
                    if (it.operationProgress?.isComplete == true) it.copy(operationProgress = null) else it
                }
            }
        }
    }

    fun moveSelectedToOtherPanel() {
        val state = _uiState.value
        val activeId = state.activePanel
        val targetId = if (activeId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
        val sourcePanel = getPanel(activeId)
        val targetPanel = getPanel(targetId)
        val paths = sourcePanel.selectedPaths.toList()
        if (paths.isEmpty()) return

        // Block move TO virtual secure view (only when target panel is in protected context)
        if (targetPanel.currentPath == HaronConstants.VIRTUAL_SECURE_PATH ||
            (targetPanel.showProtected && secureFolderRepository.isFileProtected(targetPanel.currentPath))) {
            _toastMessage.tryEmit(appContext.getString(R.string.cannot_move_to_virtual))
            return
        }

        // Protected source files — check via FileEntry.isProtected, not path index lookup
        val selectedEntries = sourcePanel.files.filter { it.path in sourcePanel.selectedPaths }
        val hasProtected = selectedEntries.any { it.isProtected }
        if (hasProtected) {
            moveProtectedFiles(paths, targetPanel.currentPath)
            return
        }

        val conflictPairs = buildConflictPairs(paths, targetPanel)
        if (conflictPairs.isNotEmpty()) {
            _uiState.update {
                it.copy(dialogState = DialogState.ConfirmConflict(
                    conflictPairs = conflictPairs,
                    allPaths = paths,
                    destinationDir = targetPanel.currentPath,
                    operationType = OperationType.MOVE
                ))
            }
            return
        }

        executeMove(paths, targetPanel.currentPath, ConflictResolution.RENAME)
    }

    private fun executeMove(
        paths: List<String>,
        destinationDir: String,
        resolution: ConflictResolution
    ) {
        val state = _uiState.value
        val activeId = state.activePanel
        val targetId = if (activeId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
        val sourcePanel = getPanel(activeId)

        val selected = sourcePanel.files.filter { it.path in sourcePanel.selectedPaths }
        val totalSize = selected.sumOf { it.size }
        val dirs = selected.count { it.isDirectory }
        val files = selected.size - dirs
        clearSelection(activeId)

        if (paths.size > SERVICE_FILE_THRESHOLD || totalSize > SERVICE_SIZE_THRESHOLD) {
            FileOperationService.start(appContext, paths, destinationDir, isMove = true, conflictResolution = resolution)
        } else {
            val fileSizes = selected.associate { it.path to it.size }
            inlineOperationJob = viewModelScope.launch {
                runInlineOperation(paths, OperationType.MOVE, fileSizes) { path ->
                    moveFilesUseCase(listOf(path), destinationDir, resolution)
                }
                migrateTagsToDestination(paths, destinationDir)
                hapticManager.success()
                showStatusMessage(targetId, appContext.getString(R.string.moved_format, formatFileCount(dirs, files)))
                refreshPanel(activeId)
                refreshPanel(targetId)
            }
        }
    }

    private fun executeMoveWithDecisions(
        paths: List<String>,
        destinationDir: String,
        decisions: Map<String, ConflictResolution>
    ) {
        val state = _uiState.value
        val activeId = state.activePanel
        val targetId = if (activeId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
        val sourcePanel = getPanel(activeId)

        val selected = sourcePanel.files.filter { it.path in paths.toSet() }
        val dirs = selected.count { it.isDirectory }
        val files = selected.size - dirs
        clearSelection(activeId)

        inlineOperationJob = viewModelScope.launch {
            val total = paths.size
            var completed = 0
            _uiState.update {
                it.copy(operationProgress = OperationProgress(0, total, "", OperationType.MOVE))
            }
            for ((index, path) in paths.withIndex()) {
                val fileName = extractFileName(path)
                _uiState.update {
                    it.copy(operationProgress = OperationProgress(index, total, fileName, OperationType.MOVE))
                }
                val resolution = decisions[path] ?: ConflictResolution.RENAME
                fileRepository.moveFilesWithResolutions(
                    listOf(path), destinationDir, mapOf(path to resolution)
                ).onSuccess { c -> completed += c }
            }
            _uiState.update {
                it.copy(operationProgress = OperationProgress(completed, total, "", OperationType.MOVE, isComplete = true))
            }
            val skipped = total - completed
            val msg = when {
                completed == 0 -> appContext.getString(R.string.skipped_count, total)
                skipped > 0 -> appContext.getString(R.string.moved_with_skip, completed, skipped)
                else -> appContext.getString(R.string.moved_format, formatFileCount(dirs, files))
            }
            showStatusMessage(targetId, msg)
            if (completed > 0) {
                hapticManager.success()
                migrateTagsToDestination(paths, destinationDir)
            } else hapticManager.error()
            refreshPanel(activeId)
            refreshPanel(targetId)
            viewModelScope.launch {
                delay(2000)
                _uiState.update {
                    if (it.operationProgress?.isComplete == true) it.copy(operationProgress = null) else it
                }
            }
        }
    }

    fun requestDeleteSelected() {
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)
        val paths = panel.selectedPaths.toList()
        if (paths.isEmpty()) return
        hapticManager.warning()
        _uiState.update { it.copy(dialogState = DialogState.ConfirmDelete(paths)) }
    }

    fun confirmDelete(paths: List<String>) {
        dismissDialog()
        val activeId = _uiState.value.activePanel
        clearSelection(activeId)

        // Protected files — delete permanently from secure storage (only for entries marked isProtected)
        val activePanel = getPanel(activeId)
        val protectedPaths = paths.filter { p -> activePanel.files.any { it.path == p && it.isProtected } }
        if (protectedPaths.isNotEmpty()) {
            deleteProtectedPermanently(protectedPaths)
            return
        }

        viewModelScope.launch {
            val total = paths.size
            _uiState.update {
                it.copy(operationProgress = OperationProgress(0, total, "", OperationType.DELETE))
            }

            // Pre-calculate eviction: free space in trash before the loop
            val nonSafPaths = paths.filter { !it.startsWith("content://") }
            var totalEvicted = 0
            if (nonSafPaths.isNotEmpty()) {
                val maxMb = preferences.trashMaxSizeMb
                if (maxMb > 0) {
                    val incomingSize = nonSafPaths.sumOf { p ->
                        val f = File(p)
                        if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                        else f.length()
                    }
                    val currentTrashSize = trashRepository.getTrashSize()
                    val maxBytes = maxMb.toLong() * 1024 * 1024
                    val needed = (currentTrashSize + incomingSize) - maxBytes
                    if (needed > 0) {
                        totalEvicted = trashRepository.evictToFitSize(maxBytes - incomingSize)
                    }
                }
            }

            var completed = 0
            var lastError: String? = null
            for ((index, path) in paths.withIndex()) {
                val isSaf = path.startsWith("content://")
                val fileName = if (isSaf) {
                    Uri.parse(path).lastPathSegment?.substringAfterLast('/') ?: "file"
                } else {
                    File(path).name
                }
                _uiState.update {
                    it.copy(operationProgress = OperationProgress(index, total, fileName, OperationType.DELETE))
                }
                try {
                    if (isSaf) {
                        fileRepository.deleteFiles(listOf(path)).onSuccess { completed++ }
                    } else {
                        trashRepository.moveToTrash(listOf(path))
                            .onSuccess { count -> completed += count }
                            .onFailure { e -> lastError = e.message }
                    }
                } catch (e: Exception) {
                    lastError = e.message
                }
            }

            if (totalEvicted > 0) {
                _toastMessage.tryEmit(appContext.getString(R.string.auto_evicted_format, totalEvicted))
            }
            updateTrashSizeInfo()
            // Remove tags for deleted files
            if (completed > 0) {
                removeTagsForPaths(paths)
            }
            if (lastError != null && completed == 0) {
                _uiState.update {
                    it.copy(operationProgress = OperationProgress(0, total, "", OperationType.DELETE, isComplete = true, error = lastError))
                }
                hapticManager.error()
                _toastMessage.tryEmit(appContext.getString(R.string.delete_error_format, lastError ?: ""))
            } else {
                _uiState.update {
                    it.copy(operationProgress = OperationProgress(completed, total, "", OperationType.DELETE, isComplete = true))
                }
                hapticManager.success()
                showStatusMessage(activeId, appContext.getString(R.string.moved_to_trash_count, completed))
            }

            viewModelScope.launch {
                delay(2000)
                _uiState.update {
                    if (it.operationProgress?.isComplete == true) it.copy(operationProgress = null) else it
                }
            }
            refreshBothIfSamePath(activeId)
        }
    }

    /** Processes files one by one with progress updates in UI. */
    private suspend fun runInlineOperation(
        paths: List<String>,
        type: OperationType,
        fileSizes: Map<String, Long> = emptyMap(),
        action: suspend (String) -> Result<Int>
    ) {
        val total = paths.size
        _uiState.update {
            it.copy(operationProgress = OperationProgress(0, total, "", type))
        }
        var completed = 0
        for ((index, path) in paths.withIndex()) {
            val fileName = if (path.startsWith("content://")) {
                Uri.parse(path).lastPathSegment?.substringAfterLast('/') ?: "file"
            } else {
                path.substringAfterLast('/')
            }
            val size = fileSizes[path] ?: 0L
            val displayName = if (size > 1024 * 1024) {
                "$fileName (${size.toFileSize(appContext)})"
            } else {
                fileName
            }
            _uiState.update {
                it.copy(operationProgress = OperationProgress(index, total, displayName, type))
            }
            action(path).onSuccess { completed++ }
        }
        _uiState.update {
            it.copy(operationProgress = OperationProgress(completed, total, "", type, isComplete = true))
        }
        // Cleanup progress after delay — non-blocking so callers proceed immediately
        viewModelScope.launch {
            delay(2000)
            _uiState.update {
                if (it.operationProgress?.isComplete == true) it.copy(operationProgress = null) else it
            }
        }
    }

    // --- Cancel file operation ---

    fun cancelFileOperation() {
        // Cancel foreground service
        val intent = android.content.Intent(appContext, FileOperationService::class.java).apply {
            action = FileOperationService.ACTION_CANCEL
        }
        appContext.startService(intent)
        // Cancel inline operation
        inlineOperationJob?.cancel()
        inlineOperationJob = null
        _uiState.update { state ->
            val p = state.operationProgress
            if (p != null && !p.isComplete) {
                state.copy(operationProgress = p.copy(isComplete = true, error = appContext.getString(R.string.operation_cancelled_label)))
            } else state
        }
        viewModelScope.launch {
            delay(2000)
            _uiState.update { it.copy(operationProgress = null) }
        }
    }

    // --- Inline rename ---

    fun requestRename() {
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)
        val selected = panel.selectedPaths.firstOrNull() ?: return
        updatePanel(activeId) {
            it.copy(
                selectedPaths = emptySet(),
                isSelectionMode = false,
                renamingPath = selected
            )
        }
    }

    fun confirmInlineRename(newName: String) {
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)
        val path = panel.renamingPath ?: return
        updatePanel(activeId) { it.copy(renamingPath = null) }

        viewModelScope.launch {
            renameFileUseCase(path, newName)
                .onSuccess {
                    hapticManager.success()
                    val parentDir = path.substringBeforeLast('/')
                    val newPath = "$parentDir/$newName"
                    preferences.migrateFileTags(path, newPath)
                    refreshTags()
                    refreshBothIfSamePath(activeId)
                }
                .onFailure { e ->
                    hapticManager.error()
                    _toastMessage.tryEmit(e.message ?: appContext.getString(R.string.error_rename))
                    EcosystemLogger.e(HaronConstants.TAG, "Ошибка переименования: ${e.message}")
                }
        }
    }

    fun cancelInlineRename() {
        _uiState.update { state ->
            state.copy(
                topPanel = state.topPanel.copy(renamingPath = null),
                bottomPanel = state.bottomPanel.copy(renamingPath = null)
            )
        }
    }

    // --- Batch rename ---

    fun requestBatchRename() {
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)
        val paths = panel.selectedPaths.toList()
        if (paths.size < 2) return
        val selected = panel.selectedPaths
        val matchedEntries = panel.files.filter { e -> e.path in selected }
        _uiState.update { it.copy(dialogState = DialogState.BatchRename(paths, matchedEntries)) }
    }

    fun confirmBatchRename(renames: List<Pair<String, String>>) {
        dismissDialog()
        val activeId = _uiState.value.activePanel
        viewModelScope.launch {
            clearSelection(activeId)
            batchRenameUseCase(renames)
                .onSuccess { count ->
                    // Migrate tags for each renamed file
                    renames.forEach { (oldPath, newName) ->
                        val parent = File(oldPath).parent ?: return@forEach
                        val newPath = "$parent/$newName"
                        preferences.migrateFileTags(oldPath, newPath)
                    }
                    refreshTags()
                    hapticManager.success()
                    _toastMessage.tryEmit(appContext.getString(R.string.batch_rename_success, count))
                    refreshBothIfSamePath(activeId)
                }
                .onFailure { e ->
                    hapticManager.error()
                    _toastMessage.tryEmit(e.message ?: appContext.getString(R.string.error_rename))
                    EcosystemLogger.e(HaronConstants.TAG, "Batch rename error: ${e.message}")
                    refreshBothIfSamePath(activeId)
                }
        }
    }

    fun saveBatchRenamePattern(pattern: String) {
        preferences.addRenamePattern(pattern)
    }

    fun getRenamePatterns(): List<String> = preferences.getRenamePatterns()

    // --- Tags ---

    fun requestTagAssign() {
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)
        val paths = panel.selectedPaths.toList()
        if (paths.isEmpty()) return
        _uiState.update { it.copy(dialogState = DialogState.TagAssign(paths)) }
    }

    fun confirmTagAssign(paths: List<String>, tagNames: List<String>) {
        dismissDialog()
        paths.forEach { path -> preferences.setFileTags(path, tagNames) }
        refreshTags()
        clearSelection(_uiState.value.activePanel)
    }

    fun showTagManager() {
        _uiState.update { it.copy(dialogState = DialogState.TagManage) }
    }

    fun addTag(name: String, colorIndex: Int) {
        preferences.addTagDefinition(FileTag(name, colorIndex))
        refreshTags()
    }

    fun editTag(oldName: String, newName: String, colorIndex: Int) {
        preferences.updateTagDefinition(oldName, FileTag(newName, colorIndex))
        refreshTags()
    }

    fun deleteTag(name: String) {
        preferences.removeTagDefinition(name)
        // Clear active filter if it was this tag
        if (_uiState.value.activeTagFilter == name) {
            _uiState.update { it.copy(activeTagFilter = null) }
        }
        refreshTags()
    }

    fun setTagFilter(tagName: String?) {
        _uiState.update { it.copy(activeTagFilter = tagName) }
    }

    private fun refreshTags() {
        _uiState.update {
            it.copy(
                tagDefinitions = preferences.getTagDefinitions(),
                fileTags = preferences.getFileTagMappings()
            )
        }
    }

    /** Copy tags from source paths to destination directory (for copy operations). */
    private fun copyTagsToDestination(sourcePaths: List<String>, destinationDir: String) {
        val mappings = preferences.getFileTagMappings()
        for (path in sourcePaths) {
            if (path.startsWith("content://")) continue
            val tags = mappings[path] ?: continue
            if (tags.isEmpty()) continue
            val fileName = File(path).name
            val newPath = "$destinationDir/$fileName"
            preferences.setFileTags(newPath, tags)
        }
        refreshTags()
    }

    /** Migrate tags from source paths to destination directory (for move operations). */
    private fun migrateTagsToDestination(sourcePaths: List<String>, destinationDir: String) {
        for (path in sourcePaths) {
            if (path.startsWith("content://")) continue
            val fileName = File(path).name
            val newPath = "$destinationDir/$fileName"
            preferences.migrateFileTags(path, newPath)
        }
        refreshTags()
    }

    /** Remove tags for deleted paths. */
    private fun removeTagsForPaths(paths: List<String>) {
        for (path in paths) {
            if (path.startsWith("content://")) continue
            preferences.removeFileTags(path)
        }
        refreshTags()
    }

    // --- Templates ---

    fun requestCreateFromTemplate() {
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)
        if (panel.currentPath == HaronConstants.VIRTUAL_SECURE_PATH) {
            _toastMessage.tryEmit(appContext.getString(R.string.cannot_create_in_virtual))
            return
        }
        val inProtected = secureFolderRepository.isFileProtected(panel.currentPath)
        val templates = if (inProtected) {
            listOf(FileTemplate.FOLDER, FileTemplate.TXT)
        } else {
            FileTemplate.entries.toList()
        }
        _uiState.update { it.copy(dialogState = DialogState.CreateFromTemplate(templates)) }
    }

    fun confirmCreateFromTemplate(template: FileTemplate, name: String) {
        dismissDialog()
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)

        if (secureFolderRepository.isFileProtected(panel.currentPath)) {
            // Create in protected context — create file, encrypt, remove original
            createInProtectedDir(template, name, panel.currentPath, activeId)
            return
        }

        viewModelScope.launch {
            val result = when (template) {
                FileTemplate.FOLDER -> {
                    createDirectoryUseCase(panel.currentPath, name)
                }
                FileTemplate.TXT -> {
                    val fileName = if (name.endsWith(".txt")) name else "$name.txt"
                    createFileUseCase(panel.currentPath, fileName, "")
                }
                FileTemplate.MARKDOWN -> {
                    val fileName = if (name.endsWith(".md")) name else "$name.md"
                    val content = "# $name\n\n"
                    createFileUseCase(panel.currentPath, fileName, content)
                }
                FileTemplate.DATED_FOLDER -> {
                    val dateName = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    createDirectoryUseCase(panel.currentPath, dateName)
                }
            }
            result
                .onSuccess { refreshBothIfSamePath(activeId) }
                .onFailure { e ->
                    EcosystemLogger.e(HaronConstants.TAG, "Ошибка создания: ${e.message}")
                }
        }
    }

    private fun createInProtectedDir(template: FileTemplate, name: String, parentPath: String, panelId: PanelId) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Recreate the protected directory temporarily
                    val parentDir = File(parentPath)
                    parentDir.mkdirs()

                    when (template) {
                        FileTemplate.FOLDER -> {
                            val dir = File(parentDir, name)
                            dir.mkdirs()
                            secureFolderRepository.protectFiles(listOf(dir.absolutePath)) { _, _ -> }
                        }
                        FileTemplate.TXT -> {
                            val fileName = if (name.endsWith(".txt")) name else "$name.txt"
                            val file = File(parentDir, fileName)
                            file.writeText("")
                            secureFolderRepository.protectFiles(listOf(file.absolutePath)) { _, _ -> }
                        }
                        else -> { /* Only FOLDER and TXT allowed in protected view */ }
                    }

                    // Clean up: remove parent dir if it's now empty (was recreated temporarily)
                    if (parentDir.exists() && parentDir.isDirectory && (parentDir.listFiles()?.isEmpty() == true)) {
                        parentDir.delete()
                    }
                }
                refreshPanel(panelId)
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "createInProtectedDir error: ${e.message}")
                _toastMessage.tryEmit(appContext.getString(R.string.protect_error, e.message ?: ""))
            }
        }
    }

    // --- Archive creation ---

    fun requestCreateArchive() {
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)
        val paths = panel.selectedPaths.toList()
        EcosystemLogger.d(HaronConstants.TAG, "requestCreateArchive: panel=$activeId, selected=${paths.size}, " +
                "isArchive=${panel.isArchiveMode}, currentPath=${panel.currentPath}")
        if (paths.isEmpty()) {
            EcosystemLogger.d(HaronConstants.TAG, "requestCreateArchive: no files selected, skipping")
            _toastMessage.tryEmit(appContext.getString(R.string.no_files_selected))
            return
        }
        _uiState.update { it.copy(dialogState = DialogState.CreateArchive(paths)) }
    }

    fun confirmCreateArchive(
        selectedPaths: List<String>,
        archiveName: String,
        password: String? = null,
        splitSizeMb: Int = 0
    ) {
        dismissDialog()
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)
        val name = if (archiveName.endsWith(".zip")) archiveName else "$archiveName.zip"

        EcosystemLogger.d(HaronConstants.TAG, "confirmCreateArchive: name=$name, sources=${selectedPaths.size}, " +
                "currentPath=${panel.currentPath}, isArchive=${panel.isArchiveMode}, split=${splitSizeMb}MB, hasPwd=${!password.isNullOrEmpty()}")

        // Don't allow archive creation from within archive view mode
        if (panel.isArchiveMode) {
            _toastMessage.tryEmit(appContext.getString(R.string.archive_create_error, "Cannot create archive from archive view"))
            EcosystemLogger.e(HaronConstants.TAG, "confirmCreateArchive: blocked — panel is in archive mode")
            return
        }

        val outputPath = if (panel.currentPath.startsWith("content://")) {
            File(appContext.cacheDir, name).absolutePath
        } else {
            "${panel.currentPath}/$name"
        }

        // Validate output parent dir
        val outputFile = File(outputPath)
        val parentDir = outputFile.parentFile
        if (parentDir == null || (!parentDir.exists() && !parentDir.mkdirs())) {
            _toastMessage.tryEmit(appContext.getString(R.string.archive_create_error, "Output directory not accessible"))
            EcosystemLogger.e(HaronConstants.TAG, "confirmCreateArchive: parent dir doesn't exist: ${parentDir?.absolutePath}")
            return
        }

        // Validate source files exist
        val missingFiles = selectedPaths.filter { !File(it).exists() }
        if (missingFiles.isNotEmpty()) {
            EcosystemLogger.e(HaronConstants.TAG, "confirmCreateArchive: ${missingFiles.size} source files missing: ${missingFiles.take(3)}")
            _toastMessage.tryEmit(appContext.getString(R.string.archive_create_error, "Source files not found (${missingFiles.size})"))
            return
        }

        EcosystemLogger.d(HaronConstants.TAG, "confirmCreateArchive: outputPath=$outputPath, all sources valid")

        // Check if file already exists — show conflict dialog
        if (File(outputPath).exists()) {
            EcosystemLogger.d(HaronConstants.TAG, "confirmCreateArchive: file exists, showing conflict dialog")
            _uiState.update {
                it.copy(dialogState = DialogState.ArchiveCreateConflict(
                    selectedPaths = selectedPaths,
                    outputPath = outputPath,
                    archiveName = name,
                    password = password,
                    splitSizeMb = splitSizeMb
                ))
            }
            return
        }

        doCreateArchive(selectedPaths, outputPath, name, password, splitSizeMb)
    }

    fun confirmArchiveCreateReplace(dialog: DialogState.ArchiveCreateConflict) {
        dismissDialog()
        val file = File(dialog.outputPath)
        if (file.exists()) file.delete()
        EcosystemLogger.d(HaronConstants.TAG, "confirmArchiveCreateReplace: deleted existing ${dialog.archiveName}")
        doCreateArchive(dialog.selectedPaths, dialog.outputPath, dialog.archiveName, dialog.password, dialog.splitSizeMb)
    }

    fun confirmArchiveCreateRename(dialog: DialogState.ArchiveCreateConflict) {
        dismissDialog()
        val renamedPath = CreateZipUseCase.findUniqueZipPath(dialog.outputPath)
        val renamedName = File(renamedPath).name
        EcosystemLogger.d(HaronConstants.TAG, "confirmArchiveCreateRename: renamed to $renamedName")
        doCreateArchive(dialog.selectedPaths, renamedPath, renamedName, dialog.password, dialog.splitSizeMb)
    }

    private fun doCreateArchive(
        selectedPaths: List<String>,
        outputPath: String,
        archiveName: String,
        password: String?,
        splitSizeMb: Int
    ) {
        val activeId = _uiState.value.activePanel
        viewModelScope.launch {
            clearSelection(activeId)
            var actualName = archiveName
            try {
                createZipUseCase(selectedPaths, outputPath, password, splitSizeMb)
                    .collect { progress ->
                        if (progress.actualArchiveName != null) {
                            actualName = progress.actualArchiveName
                        }
                        _uiState.update {
                            it.copy(operationProgress = OperationProgress(
                                current = progress.current,
                                total = progress.total,
                                currentFileName = progress.fileName,
                                type = OperationType.ARCHIVE
                            ))
                        }
                    }
                hapticManager.success()
                _toastMessage.tryEmit(appContext.getString(R.string.archive_created, actualName))
                refreshBothIfSamePath(activeId)
            } catch (e: Exception) {
                hapticManager.error()
                _toastMessage.tryEmit(appContext.getString(R.string.archive_create_error, e.message ?: ""))
                EcosystemLogger.e(HaronConstants.TAG, "confirmCreateArchive: ZIP failed — ${e.javaClass.simpleName}: ${e.message}")
            }
            _uiState.update { it.copy(operationProgress = null) }
        }
    }

    fun onPreviewFileChanged(newIndex: Int) {
        val dialog = _uiState.value.dialogState
        if (dialog !is DialogState.QuickPreview) return
        val files = dialog.adjacentFiles
        if (newIndex !in files.indices) return
        val newEntry = files[newIndex]

        // Check cache — if already loaded, show instantly without flash
        val cached = dialog.previewCache[newIndex]
        if (cached != null) {
            _uiState.update {
                it.copy(dialogState = dialog.copy(
                    entry = newEntry,
                    previewData = cached,
                    isLoading = false,
                    error = null,
                    currentFileIndex = newIndex
                ))
            }
            preloadPreview(newIndex - 1, files)
            preloadPreview(newIndex + 1, files)
            return
        }

        // Not cached — load with spinner
        _uiState.update {
            it.copy(dialogState = dialog.copy(
                entry = newEntry,
                previewData = null,
                isLoading = true,
                error = null,
                currentFileIndex = newIndex
            ))
        }

        viewModelScope.launch {
            val resolved = resolvePreviewEntry(newEntry)
            loadPreviewUseCase(resolved)
                .onSuccess { data ->
                    val current = _uiState.value.dialogState
                    if (current is DialogState.QuickPreview && current.entry.path == newEntry.path) {
                        _uiState.update {
                            it.copy(dialogState = current.copy(
                                previewData = data,
                                isLoading = false,
                                previewCache = current.previewCache + (newIndex to data)
                            ))
                        }
                        preloadPreview(newIndex - 1, files)
                        preloadPreview(newIndex + 1, files)
                    }
                }
                .onFailure { e ->
                    val current = _uiState.value.dialogState
                    if (current is DialogState.QuickPreview && current.entry.path == newEntry.path) {
                        _uiState.update {
                            it.copy(dialogState = current.copy(
                                isLoading = false,
                                error = e.message ?: appContext.getString(R.string.unknown_error)
                            ))
                        }
                    }
                }
        }
    }

    /** Resolve a FileEntry for preview: if protected, decrypt to cache and return entry with temp path. */
    private suspend fun resolvePreviewEntry(entry: FileEntry): FileEntry {
        if (!entry.isProtected) return entry
        val allEntries = secureFolderRepository.getAllProtectedEntries()
        val secEntry = allEntries.find { it.originalPath == entry.path } ?: return entry
        return secureFolderRepository.decryptToCache(secEntry.id).getOrNull()?.let { tempFile ->
            entry.copy(path = tempFile.absolutePath)
        } ?: entry
    }

    private fun preloadPreview(index: Int, files: List<FileEntry>) {
        if (index !in files.indices) return
        val current = _uiState.value.dialogState
        if (current !is DialogState.QuickPreview) return
        if (index in current.previewCache) return

        viewModelScope.launch {
            val resolved = resolvePreviewEntry(files[index])
            loadPreviewUseCase(resolved)
                .onSuccess { data ->
                    val dialog = _uiState.value.dialogState
                    if (dialog is DialogState.QuickPreview) {
                        _uiState.update {
                            it.copy(dialogState = dialog.copy(
                                previewCache = dialog.previewCache + (index to data)
                            ))
                        }
                    }
                }
            // Silently ignore preload errors
        }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(dialogState = DialogState.None) }
    }

    // --- Favorites ---

    fun toggleFavorite(panelId: PanelId) {
        val path = getPanel(panelId).currentPath
        if (path in _uiState.value.favorites) {
            preferences.removeFavorite(path)
        } else {
            preferences.addFavorite(path)
        }
        _uiState.update { it.copy(favorites = preferences.getFavorites()) }
        updateWidget()
    }

    fun removeFavorite(path: String) {
        preferences.removeFavorite(path)
        _uiState.update { it.copy(favorites = preferences.getFavorites()) }
    }

    // --- Drawer ---

    fun toggleDrawer() {
        val opening = !_uiState.value.showDrawer
        _uiState.update {
            it.copy(
                showDrawer = !it.showDrawer,
                showShelf = false // close shelf when opening drawer
            )
        }
        if (opening) updateTrashSizeInfo()
    }

    fun dismissDrawer() {
        _uiState.update { it.copy(showDrawer = false) }
    }

    fun navigateFromDrawer(path: String) {
        val activeId = _uiState.value.activePanel
        dismissDrawer()
        navigateTo(activeId, path)
    }

    // --- Shelf ---

    fun addToShelf() {
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)
        val selected = panel.files.filter { it.path in panel.selectedPaths }
        if (selected.isEmpty()) return

        val newItems = selected.map {
            ShelfItem(name = it.name, path = it.path, isDirectory = it.isDirectory, size = it.size)
        }
        preferences.addShelfItems(newItems)
        val updatedShelf = preferences.getShelfItems()
        _uiState.update { it.copy(shelfItems = updatedShelf) }
        clearSelection(activeId)
        _toastMessage.tryEmit(appContext.getString(R.string.added_to_shelf_count, selected.size))
    }

    fun removeFromShelf(path: String) {
        preferences.removeShelfItem(path)
        _uiState.update { it.copy(shelfItems = preferences.getShelfItems()) }
    }

    fun clearShelf() {
        preferences.clearShelf()
        _uiState.update { it.copy(shelfItems = emptyList()) }
    }

    fun toggleShelf() {
        _uiState.update {
            it.copy(
                showShelf = !it.showShelf,
                showDrawer = false // close drawer when opening shelf
            )
        }
    }

    fun dismissShelf() {
        _uiState.update { it.copy(showShelf = false) }
    }

    fun pasteFromShelf(isMove: Boolean) {
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)
        val destinationDir = panel.currentPath
        val items = _uiState.value.shelfItems
        if (items.isEmpty()) return

        val paths = items.map { it.path }
        dismissShelf()

        if (isMove) {
            val fileSizes = items.associate { it.path to it.size }
            inlineOperationJob = viewModelScope.launch {
                runInlineOperation(paths, OperationType.MOVE, fileSizes) { path ->
                    moveFilesUseCase(listOf(path), destinationDir, ConflictResolution.RENAME)
                }
                migrateTagsToDestination(paths, destinationDir)
                clearShelf()
                refreshPanel(PanelId.TOP)
                refreshPanel(PanelId.BOTTOM)
                _toastMessage.tryEmit(appContext.getString(R.string.moved_from_shelf, paths.size))
            }
        } else {
            val fileSizes = items.associate { it.path to it.size }
            inlineOperationJob = viewModelScope.launch {
                runInlineOperation(paths, OperationType.COPY, fileSizes) { path ->
                    copyFilesUseCase(listOf(path), destinationDir, ConflictResolution.RENAME)
                }
                copyTagsToDestination(paths, destinationDir)
                refreshPanel(activeId)
                _toastMessage.tryEmit(appContext.getString(R.string.copied_from_shelf, paths.size))
            }
        }
    }

    // --- Duplicate Detector ---

    fun openDuplicateDetector() {
        _navigationEvent.tryEmit(NavigationEvent.OpenDuplicateDetector)
    }

    // --- Trash ---

    fun showTrash() {
        viewModelScope.launch {
            trashRepository.getTrashEntries()
                .onSuccess { entries ->
                    val totalSize = trashRepository.getTrashSize()
                    _uiState.update {
                        it.copy(dialogState = DialogState.ShowTrash(entries, totalSize, preferences.trashMaxSizeMb))
                    }
                }
                .onFailure { e ->
                    EcosystemLogger.e(HaronConstants.TAG, "Ошибка чтения корзины: ${e.message}")
                }
        }
    }

    fun restoreFromTrash(ids: List<String>) {
        viewModelScope.launch {
            restoreFromTrashUseCase(ids)
                .onSuccess { count ->
                    updateTrashSizeInfo()
                    showStatusMessage(_uiState.value.activePanel, appContext.getString(R.string.restored_count, count))
                    refreshPanel(PanelId.TOP)
                    refreshPanel(PanelId.BOTTOM)
                    // Обновить диалог корзины
                    showTrash()
                }
                .onFailure { e ->
                    EcosystemLogger.e(HaronConstants.TAG, "Ошибка восстановления: ${e.message}")
                }
        }
    }

    fun deleteFromTrashPermanently(ids: List<String>) {
        viewModelScope.launch {
            _uiState.update { state ->
                val dialog = state.dialogState as? DialogState.ShowTrash ?: return@update state
                state.copy(dialogState = dialog.copy(
                    deleteProgress = 0f,
                    deleteCurrentName = ""
                ))
            }
            trashRepository.deleteFromTrashWithProgress(ids) { deleted, total, name ->
                _uiState.update { state ->
                    val dialog = state.dialogState as? DialogState.ShowTrash ?: return@update state
                    state.copy(dialogState = dialog.copy(
                        deleteProgress = deleted.toFloat() / total,
                        deleteCurrentName = name
                    ))
                }
            }
            updateTrashSizeInfo()
            refreshPanel(PanelId.TOP)
            refreshPanel(PanelId.BOTTOM)
            showTrash()
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            val entries = trashRepository.getTrashEntries().getOrNull() ?: return@launch
            if (entries.isEmpty()) {
                dismissDialog()
                return@launch
            }
            val allIds = entries.map { it.id }
            _uiState.update { state ->
                val dialog = state.dialogState as? DialogState.ShowTrash ?: return@update state
                state.copy(dialogState = dialog.copy(
                    deleteProgress = 0f,
                    deleteCurrentName = ""
                ))
            }
            trashRepository.deleteFromTrashWithProgress(allIds) { deleted, total, name ->
                _uiState.update { state ->
                    val dialog = state.dialogState as? DialogState.ShowTrash ?: return@update state
                    state.copy(dialogState = dialog.copy(
                        deleteProgress = deleted.toFloat() / total,
                        deleteCurrentName = name
                    ))
                }
            }
            updateTrashSizeInfo()
            refreshPanel(PanelId.TOP)
            refreshPanel(PanelId.BOTTOM)
            showStatusMessage(_uiState.value.activePanel, appContext.getString(R.string.trash_cleared_count, entries.size))
            dismissDialog()
        }
    }

    // --- Drag-and-Drop ---

    fun startDrag(panelId: PanelId, paths: List<String>, offset: Offset) {
        if (paths.isEmpty()) return
        val panel = getPanel(panelId)
        val firstEntry = panel.files.find { it.path == paths.first() }
        _uiState.update {
            it.copy(
                dragState = DragState.Dragging(
                    sourcePanelId = panelId,
                    draggedPaths = paths,
                    dragOffset = offset,
                    fileCount = paths.size,
                    previewName = firstEntry?.name ?: paths.first().substringAfterLast('/')
                )
            )
        }
    }

    fun updateDragPosition(offset: Offset) {
        val current = _uiState.value.dragState
        if (current is DragState.Dragging) {
            _uiState.update {
                it.copy(dragState = current.copy(dragOffset = offset))
            }
        }
    }

    fun setDragHoveredFolder(folderPath: String?) {
        val current = _uiState.value.dragState
        if (current is DragState.Dragging && current.hoveredFolderPath != folderPath) {
            _uiState.update {
                it.copy(dragState = current.copy(hoveredFolderPath = folderPath))
            }
        }
    }

    fun endDrag(targetPanelId: PanelId?) {
        val current = _uiState.value.dragState
        if (current !is DragState.Dragging) return

        val hoveredFolder = current.hoveredFolderPath
        _uiState.update { it.copy(dragState = DragState.Idle) }

        // Drop into a specific folder (same or other panel)
        if (hoveredFolder != null) {
            val paths = current.draggedPaths
            // Don't move a folder into itself
            if (paths.any { hoveredFolder.startsWith(it) }) return
            // Don't move files into their own parent directory (no-op)
            val sourceDir = getPanel(current.sourcePanelId).currentPath
            if (hoveredFolder == sourceDir) return
            clearSelection(current.sourcePanelId)
            val destPanelId = targetPanelId ?: current.sourcePanelId
            executeDragMove(paths, hoveredFolder, current.sourcePanelId, destPanelId, current.fileCount, ConflictResolution.RENAME)
            return
        }

        if (targetPanelId == null || targetPanelId == current.sourcePanelId) return

        val sourcePanel = getPanel(current.sourcePanelId)
        val targetPanel = getPanel(targetPanelId)
        if (sourcePanel.currentPath == targetPanel.currentPath) return

        val paths = current.draggedPaths
        clearSelection(current.sourcePanelId)

        val conflictPairs = buildConflictPairs(paths, targetPanel)
        if (conflictPairs.isNotEmpty()) {
            _uiState.update {
                it.copy(dialogState = DialogState.ConfirmConflict(
                    conflictPairs = conflictPairs,
                    allPaths = paths,
                    destinationDir = targetPanel.currentPath,
                    operationType = OperationType.MOVE
                ))
            }
            return
        }

        executeDragMove(paths, targetPanel.currentPath, current.sourcePanelId, targetPanelId, current.fileCount, ConflictResolution.RENAME)
    }

    private fun executeDragMove(
        paths: List<String>,
        destinationDir: String,
        sourcePanelId: PanelId,
        targetPanelId: PanelId,
        fileCount: Int,
        resolution: ConflictResolution
    ) {
        val total = paths.size
        val dirs = paths.count { p ->
            if (p.startsWith("content://")) false else File(p).isDirectory
        }
        val files = total - dirs
        viewModelScope.launch {
            _uiState.update {
                it.copy(operationProgress = OperationProgress(0, total, "", OperationType.MOVE))
            }

            moveFilesUseCase(paths, destinationDir, resolution)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            operationProgress = OperationProgress(
                                total, total, "", OperationType.MOVE, isComplete = true
                            )
                        )
                    }
                    showStatusMessage(targetPanelId, appContext.getString(R.string.moved_format, formatFileCount(dirs, files)))
                    refreshPanel(sourcePanelId)
                    refreshPanel(targetPanelId)
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            operationProgress = OperationProgress(
                                0, total, "", OperationType.MOVE, isComplete = true, error = e.message
                            )
                        )
                    }
                    EcosystemLogger.e(HaronConstants.TAG, "Ошибка DnD перемещения: ${e.message}")
                }

            delay(2000)
            _uiState.update { it.copy(operationProgress = null) }
        }
    }

    fun cancelDrag() {
        _uiState.update { it.copy(dragState = DragState.Idle) }
    }

    // --- Conflict resolution (per-file card) ---

    private fun buildConflictPairs(paths: List<String>, targetPanel: PanelUiState): List<ConflictPair> {
        val destFiles = targetPanel.files.associateBy { it.name }
        val sourcePanel = getPanel(_uiState.value.activePanel)
        val sourceFiles = sourcePanel.files.associateBy { it.path }
        val pairs = mutableListOf<ConflictPair>()

        for (path in paths) {
            val srcEntry = sourceFiles[path]
            val name = srcEntry?.name ?: if (path.startsWith("content://")) {
                Uri.parse(path).lastPathSegment?.substringAfterLast('/') ?: continue
            } else {
                File(path).name
            }
            val destEntry = destFiles[name] ?: continue

            val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
            val srcExt = srcEntry?.extension ?: name.substringAfterLast('.', "").lowercase()
            val dstExt = destEntry.extension

            pairs.add(
                ConflictPair(
                    source = ConflictFileInfo(
                        name = name,
                        path = path,
                        size = srcEntry?.size ?: 0L,
                        lastModified = srcEntry?.lastModified ?: 0L,
                        isImage = srcExt in imageExtensions
                    ),
                    destination = ConflictFileInfo(
                        name = destEntry.name,
                        path = destEntry.path,
                        size = destEntry.size,
                        lastModified = destEntry.lastModified,
                        isImage = dstExt in imageExtensions
                    )
                )
            )
        }
        return pairs
    }

    fun resolveCurrentConflict(resolution: ConflictResolution, applyToAll: Boolean) {
        val dialog = _uiState.value.dialogState
        if (dialog !is DialogState.ConfirmConflict) return

        val currentPair = dialog.conflictPairs[dialog.currentIndex]
        val newDecisions = dialog.decisions.toMutableMap()
        newDecisions[currentPair.source.path] = resolution

        if (applyToAll) {
            // Apply same resolution to all remaining conflicts
            for (i in dialog.currentIndex + 1..dialog.conflictPairs.lastIndex) {
                newDecisions[dialog.conflictPairs[i].source.path] = resolution
            }
            dismissDialog()
            // Add default RENAME for non-conflict files
            val allDecisions = dialog.allPaths.associateWith { path ->
                newDecisions[path] ?: ConflictResolution.RENAME
            }
            executeWithDecisions(dialog, allDecisions)
        } else {
            val nextIndex = dialog.currentIndex + 1
            if (nextIndex >= dialog.conflictPairs.size) {
                // All conflicts resolved
                dismissDialog()
                val allDecisions = dialog.allPaths.associateWith { path ->
                    newDecisions[path] ?: ConflictResolution.RENAME
                }
                executeWithDecisions(dialog, allDecisions)
            } else {
                // Show next conflict card
                _uiState.update {
                    it.copy(dialogState = dialog.copy(
                        currentIndex = nextIndex,
                        decisions = newDecisions
                    ))
                }
            }
        }
    }

    private fun executeWithDecisions(
        dialog: DialogState.ConfirmConflict,
        decisions: Map<String, ConflictResolution>
    ) {
        when (dialog.operationType) {
            OperationType.COPY -> executeCopyWithDecisions(dialog.allPaths, dialog.destinationDir, decisions)
            OperationType.MOVE -> {
                val state = _uiState.value
                val activeId = state.activePanel
                val sourcePanel = getPanel(activeId)
                if (sourcePanel.selectedPaths.isNotEmpty()) {
                    executeMoveWithDecisions(dialog.allPaths, dialog.destinationDir, decisions)
                } else {
                    // DnD case
                    val targetId = if (activeId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
                    executeDragMoveWithDecisions(dialog.allPaths, dialog.destinationDir, activeId, targetId, decisions)
                }
            }
            else -> {}
        }
    }

    private fun executeDragMoveWithDecisions(
        paths: List<String>,
        destinationDir: String,
        sourcePanelId: PanelId,
        targetPanelId: PanelId,
        decisions: Map<String, ConflictResolution>
    ) {
        val total = paths.size
        viewModelScope.launch {
            var completed = 0
            _uiState.update {
                it.copy(operationProgress = OperationProgress(0, total, "", OperationType.MOVE))
            }
            for ((index, path) in paths.withIndex()) {
                val fileName = extractFileName(path)
                _uiState.update {
                    it.copy(operationProgress = OperationProgress(index, total, fileName, OperationType.MOVE))
                }
                val resolution = decisions[path] ?: ConflictResolution.RENAME
                fileRepository.moveFilesWithResolutions(
                    listOf(path), destinationDir, mapOf(path to resolution)
                ).onSuccess { c -> completed += c }
            }
            _uiState.update {
                it.copy(operationProgress = OperationProgress(completed, total, "", OperationType.MOVE, isComplete = true))
            }
            refreshPanel(sourcePanelId)
            refreshPanel(targetPanelId)
            if (completed > 0) hapticManager.success() else hapticManager.error()
            viewModelScope.launch {
                delay(2000)
                _uiState.update {
                    if (it.operationProgress?.isComplete == true) it.copy(operationProgress = null) else it
                }
            }
        }
    }

    /** Legacy single-resolution conflict handler (kept for backward compatibility with old dialog code paths) */
    fun confirmConflict(resolution: ConflictResolution) {
        val dialog = _uiState.value.dialogState
        if (dialog !is DialogState.ConfirmConflict) return
        // Apply same resolution to all
        resolveCurrentConflict(resolution, applyToAll = true)
    }

    // --- SAF display helpers ---

    private fun buildSafDisplayPath(path: String): String {
        val uri = Uri.parse(path)
        return try {
            val docId = android.provider.DocumentsContract.getDocumentId(uri)
            val treeDocId = android.provider.DocumentsContract.getTreeDocumentId(uri)
            val parts = docId.split(":")
            val treeParts = treeDocId.split(":")
            val relativePath = if (parts.size >= 2) parts[1] else ""
            val treeBasePath = if (treeParts.size >= 2) treeParts[1] else ""
            val suffix = if (relativePath.length > treeBasePath.length) {
                relativePath.removePrefix(treeBasePath).trimStart('/')
            } else {
                ""
            }
            if (suffix.isEmpty()) "/" else "/$suffix"
        } catch (_: Exception) {
            "/"
        }
    }

    // --- Helpers ---

    private fun updateTrashSizeInfo() {
        viewModelScope.launch {
            val trashSize = trashRepository.getTrashSize()
            val maxMb = preferences.trashMaxSizeMb
            val info = if (maxMb > 0) {
                val maxBytes = maxMb.toLong() * 1024 * 1024
                "${trashSize.toFileSize(appContext)} / ${maxBytes.toFileSize(appContext)}"
            } else {
                trashSize.toFileSize(appContext)
            }
            _uiState.update { it.copy(trashSizeInfo = info) }
        }
    }

    fun getSelectedTotalSize(): Long {
        val state = _uiState.value
        val activeId = state.activePanel
        val panel = getPanel(activeId)
        return panel.files
            .filter { it.path in panel.selectedPaths }
            .sumOf { it.size }
    }

    fun refreshPanel(panelId: PanelId) {
        val panel = getPanel(panelId)
        // Invalidate cached folder size so BreadcrumbBar shows fresh value
        val cachedPath = panel.currentPath
        if (cachedPath.isNotEmpty()) {
            folderSizeJobs[cachedPath]?.cancel()
            folderSizeJobs.remove(cachedPath)
            _uiState.update { state ->
                state.copy(folderSizeCache = state.folderSizeCache - cachedPath)
            }
        }
        if (panel.isArchiveMode) {
            navigateIntoArchive(panelId, panel.archivePath!!, panel.archiveVirtualPath, panel.archivePassword, pushHistory = false)
            return
        }
        if (cachedPath.isNotEmpty()) {
            navigateTo(panelId, cachedPath, pushHistory = false)
        }
    }

    private fun refreshBothIfSamePath(panelId: PanelId) {
        // Invalidate content index cache — files changed in this folder
        val path = getPanel(panelId).currentPath
        if (path.isNotEmpty()) preferences.removeContentIndexedFolder(path)
        refreshPanel(panelId)
        val otherId = if (panelId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
        val otherPath = getPanel(otherId).currentPath
        if (otherPath.isNotEmpty()) preferences.removeContentIndexedFolder(otherPath)
        if (path == otherPath) {
            refreshPanel(otherId)
        }
    }

    // --- Inline Archive Browsing ---

    fun navigateIntoArchive(panelId: PanelId, archivePath: String, virtualPath: String, password: String?, pushHistory: Boolean = true) {
        // Save scroll position when entering archive from regular folder
        val panel = getPanel(panelId)
        if (!panel.isArchiveMode && panel.currentPath.isNotEmpty()) {
            scrollCache[panel.currentPath] = panelScrollIndex[panelId] ?: 0
        }
        navigationJobs[panelId]?.cancel()
        navigationJobs[panelId] = viewModelScope.launch {
            updatePanel(panelId) { it.copy(isLoading = true, error = null) }
            browseArchiveUseCase(archivePath, virtualPath, password).onSuccess { entries ->
                val archiveName = File(archivePath).name
                val displayPath = if (virtualPath.isEmpty()) archiveName else "$archiveName/$virtualPath"
                val fileEntries = entries.map { ae ->
                    FileEntry(
                        name = ae.name,
                        path = "$archivePath!/${ae.fullPath}",
                        isDirectory = ae.isDirectory,
                        size = ae.size,
                        lastModified = ae.lastModified,
                        extension = ae.name.substringAfterLast('.', ""),
                        isHidden = false,
                        childCount = ae.childCount
                    )
                }
                updatePanel(panelId) { panel ->
                    var history = panel.navigationHistory
                    var index = panel.historyIndex
                    if (pushHistory) {
                        val historyKey = "$archivePath!/$virtualPath"
                        history = history.take(index + 1) + historyKey
                        if (history.size > MAX_HISTORY_SIZE) {
                            history = history.takeLast(MAX_HISTORY_SIZE)
                        }
                        index = history.lastIndex
                    }
                    panel.copy(
                        files = fileEntries,
                        isLoading = false,
                        error = null,
                        archivePath = archivePath,
                        archiveVirtualPath = virtualPath,
                        archivePassword = password,
                        displayPath = displayPath,
                        currentPath = panel.currentPath, // keep real path for extraction target
                        selectedPaths = emptySet(),
                        isSelectionMode = false,
                        navigationHistory = history,
                        historyIndex = index,
                        searchQuery = "",
                        isSearchActive = false
                    )
                }
            }.onFailure { error ->
                val message = error.message ?: ""
                if (message == "encrypted" || message.contains("password", ignoreCase = true)) {
                    // Show password dialog
                    updatePanel(panelId) { it.copy(isLoading = false) }
                    val errorMsg = if (password != null) appContext.getString(R.string.wrong_password) else null
                    _uiState.update {
                        it.copy(dialogState = DialogState.ArchivePassword(
                            panelId = panelId,
                            archivePath = archivePath,
                            errorMessage = errorMsg
                        ))
                    }
                } else {
                    updatePanel(panelId) {
                        it.copy(
                            isLoading = false,
                            error = message.ifEmpty { appContext.getString(R.string.archive_read_error) }
                        )
                    }
                }
            }
        }
    }

    fun onArchivePasswordSubmit(panelId: PanelId, archivePath: String, password: String) {
        _uiState.update { it.copy(dialogState = DialogState.None) }
        navigateIntoArchive(panelId, archivePath, "", password)
    }

    fun onBreadcrumbClick(panelId: PanelId, segmentPath: String) {
        val panel = getPanel(panelId)
        if (panel.isArchiveMode) {
            // If user tapped on root "Storage" — exit archive mode
            if (segmentPath == HaronConstants.ROOT_PATH) {
                EcosystemLogger.d(HaronConstants.TAG, "onBreadcrumbClick: tapped root in archive mode, exiting archive")
                val archiveParent = File(panel.archivePath!!).parent ?: HaronConstants.ROOT_PATH
                updatePanel(panelId) { it.copy(archivePath = null, archiveVirtualPath = "", archivePassword = null, archiveExtractProgress = null, currentPath = "") }
                navigateTo(panelId, archiveParent)
                return
            }
            // segmentPath is built by BreadcrumbBar as ROOT_PATH + /segments
            // In archive mode, displayPath = "archiveName/subfolder/..."
            // Segments: [archiveName, folder, subfolder, ...]
            val afterRoot = segmentPath.removePrefix(HaronConstants.ROOT_PATH).trimStart('/')
            val parts = afterRoot.split('/').filter { it.isNotEmpty() }
            // First part = archive name → skip it, rest = virtual path
            val virtualPath = if (parts.size <= 1) "" else parts.drop(1).joinToString("/")
            navigateIntoArchive(panelId, panel.archivePath!!, virtualPath, panel.archivePassword)
        } else {
            navigateTo(panelId, segmentPath)
        }
    }

    fun extractFromArchive(archivePanelId: PanelId, selectedOnly: Boolean = true) {
        val archivePanel = getPanel(archivePanelId)
        if (!archivePanel.isArchiveMode) return

        val activeId = _uiState.value.activePanel
        val destinationDir = if (activeId == archivePanelId) {
            File(archivePanel.archivePath!!).parent ?: HaronConstants.ROOT_PATH
        } else {
            getPanel(activeId).currentPath
        }

        val archiveName = File(archivePanel.archivePath!!).nameWithoutExtension

        // Check if archive has a single root folder (only at root level)
        val rootFiles = archivePanel.files
        val atArchiveRoot = archivePanel.archiveVirtualPath.isEmpty()
        val hasSingleRootFolder = atArchiveRoot && rootFiles.size == 1 && rootFiles[0].isDirectory
        EcosystemLogger.d(HaronConstants.TAG, "extractFromArchive: archiveName=$archiveName, " +
                "virtualPath='${archivePanel.archiveVirtualPath}', rootFiles=${rootFiles.size}, " +
                "atRoot=$atArchiveRoot, singleRoot=$hasSingleRootFolder, selectedOnly=$selectedOnly, dest=$destinationDir")

        if (hasSingleRootFolder) {
            // Single root folder — extract directly, no dialog needed
            EcosystemLogger.d(HaronConstants.TAG, "extractFromArchive: single root folder detected, extracting directly")
            _toastMessage.tryEmit(appContext.getString(R.string.extract_single_root_hint))
            checkExtractConflictsAndProceed(archivePanelId, destinationDir, selectedOnly)
            return
        }

        EcosystemLogger.d(HaronConstants.TAG, "extractFromArchive: showing extract options dialog")
        _uiState.update {
            it.copy(dialogState = DialogState.ArchiveExtractOptions(
                archivePanelId = archivePanelId,
                destinationDir = destinationDir,
                selectedOnly = selectedOnly,
                archiveName = archiveName,
                hasSingleRootFolder = false
            ))
        }
    }

    fun confirmExtractHere(dialog: DialogState.ArchiveExtractOptions) {
        dismissDialog()
        if (dialog.isFromNormalFolder) {
            doExtractSelectedArchiveFiles(dialog.archivePanelId, dialog.archivePaths, dialog.destinationDir)
        } else {
            checkExtractConflictsAndProceed(dialog.archivePanelId, dialog.destinationDir, dialog.selectedOnly)
        }
    }

    fun confirmExtractToFolder(dialog: DialogState.ArchiveExtractOptions) {
        dismissDialog()
        val subFolder = File(dialog.destinationDir, dialog.archiveName)
        if (!subFolder.exists()) subFolder.mkdirs()
        EcosystemLogger.d(HaronConstants.TAG, "confirmExtractToFolder: extracting to subfolder ${subFolder.absolutePath}")
        if (dialog.isFromNormalFolder) {
            doExtractSelectedArchiveFiles(dialog.archivePanelId, dialog.archivePaths, subFolder.absolutePath)
        } else {
            checkExtractConflictsAndProceed(dialog.archivePanelId, subFolder.absolutePath, dialog.selectedOnly)
        }
    }

    private fun checkExtractConflictsAndProceed(archivePanelId: PanelId, destinationDir: String, selectedOnly: Boolean) {
        val archivePanel = getPanel(archivePanelId)

        val archiveFileNames = if (selectedOnly && archivePanel.selectedPaths.isNotEmpty()) {
            archivePanel.files.filter { it.path in archivePanel.selectedPaths }.map { it.name }
        } else {
            archivePanel.files.filter { !it.isDirectory }.map { it.name }
        }
        val destDir = File(destinationDir)
        val conflictNames = archiveFileNames.filter { File(destDir, it).exists() }

        if (conflictNames.isNotEmpty()) {
            _uiState.update {
                it.copy(dialogState = DialogState.ArchiveExtractConflict(
                    archivePanelId = archivePanelId,
                    destinationDir = destinationDir,
                    conflictNames = conflictNames,
                    selectedOnly = selectedOnly
                ))
            }
            return
        }

        doExtractFromArchive(archivePanelId, destinationDir, selectedOnly)
    }

    fun confirmArchiveExtract(archivePanelId: PanelId, destinationDir: String, selectedOnly: Boolean) {
        _uiState.update { it.copy(dialogState = DialogState.None) }
        doExtractFromArchive(archivePanelId, destinationDir, selectedOnly)
    }

    private fun doExtractFromArchive(archivePanelId: PanelId, destinationDir: String, selectedOnly: Boolean) {
        val archivePanel = getPanel(archivePanelId)
        if (!archivePanel.isArchiveMode) return
        val activeId = _uiState.value.activePanel
        val virtualPath = archivePanel.archiveVirtualPath

        val selectedEntries = if (selectedOnly && archivePanel.selectedPaths.isNotEmpty()) {
            archivePanel.selectedPaths.map { path ->
                path.substringAfter("!/", "")
            }.toSet()
        } else {
            // Extract all visible files in current virtual path
            archivePanel.files.map { fe ->
                fe.path.substringAfter("!/", "")
            }.toSet()
        }

        viewModelScope.launch {
            extractArchiveUseCase(
                archivePath = archivePanel.archivePath!!,
                destinationDir = destinationDir,
                selectedEntries = selectedEntries,
                password = archivePanel.archivePassword,
                basePrefix = virtualPath
            ).collect { progress ->
                updatePanel(archivePanelId) { it.copy(archiveExtractProgress = progress) }
                if (progress.isComplete) {
                    delay(300)
                    val targetPanelId = if (activeId == archivePanelId) archivePanelId else activeId
                    if (!getPanel(targetPanelId).isArchiveMode) {
                        refreshPanel(targetPanelId)
                    }
                    val otherId = if (targetPanelId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
                    if (!getPanel(otherId).isArchiveMode && getPanel(otherId).currentPath == destinationDir) {
                        refreshPanel(otherId)
                    }
                    updatePanel(archivePanelId) {
                        it.copy(
                            selectedPaths = emptySet(),
                            isSelectionMode = false,
                            archiveExtractProgress = null
                        )
                    }
                    if (progress.error != null) {
                        _toastMessage.tryEmit(progress.error)
                    } else {
                        _toastMessage.tryEmit(appContext.getString(R.string.extracted_to_format, destinationDir))
                    }
                }
            }
        }
    }

    /** Extract selected archive file(s) from normal folder view into other panel. */
    private fun extractSelectedArchiveFiles(panelId: PanelId) {
        val panel = getPanel(panelId)
        val archiveExts = com.vamp.haron.common.util.ContentExtractor.ARCHIVE_EXTENSIONS
        val selectedArchives = panel.files
            .filter { it.path in panel.selectedPaths && it.name.substringAfterLast('.').lowercase() in archiveExts }
        if (selectedArchives.isEmpty()) {
            _toastMessage.tryEmit(appContext.getString(R.string.not_in_archive))
            return
        }
        val otherId = if (panelId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
        val destDir = getPanel(otherId).currentPath.ifEmpty { panel.currentPath }

        // For single archive — check single root folder and show dialog
        if (selectedArchives.size == 1) {
            val archive = selectedArchives[0]
            val archiveName = File(archive.path).nameWithoutExtension
            viewModelScope.launch {
                val hasSingleRoot = checkSingleRootFolder(archive.path)
                if (hasSingleRoot) {
                    EcosystemLogger.d(HaronConstants.TAG, "extractSelectedArchiveFiles: single root folder, extracting directly")
                    _toastMessage.tryEmit(appContext.getString(R.string.extract_single_root_hint))
                    doExtractSelectedArchiveFiles(panelId, listOf(archive.path), destDir)
                } else {
                    _uiState.update {
                        it.copy(dialogState = DialogState.ArchiveExtractOptions(
                            archivePanelId = panelId,
                            destinationDir = destDir,
                            selectedOnly = false,
                            archiveName = archiveName,
                            hasSingleRootFolder = false,
                            isFromNormalFolder = true,
                            archivePaths = listOf(archive.path)
                        ))
                    }
                }
            }
        } else {
            // Multiple archives — extract all directly without dialog
            doExtractSelectedArchiveFiles(panelId, selectedArchives.map { it.path }, destDir)
        }
    }

    private suspend fun checkSingleRootFolder(archivePath: String): Boolean {
        val result = browseArchiveUseCase(archivePath, "")
        return result.getOrNull()?.let { entries ->
            entries.size == 1 && entries[0].isDirectory
        } ?: false
    }

    private fun doExtractSelectedArchiveFiles(panelId: PanelId, archivePaths: List<String>, destDir: String) {
        val otherId = if (panelId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
        viewModelScope.launch {
            for (archivePath in archivePaths) {
                extractArchiveUseCase(
                    archivePath = archivePath,
                    destinationDir = destDir
                ).collect { progress ->
                    updatePanel(panelId) { it.copy(archiveExtractProgress = progress) }
                    if (progress.isComplete) {
                        delay(300)
                        refreshPanel(otherId)
                        if (getPanel(panelId).currentPath == destDir) refreshPanel(panelId)
                        updatePanel(panelId) {
                            it.copy(
                                selectedPaths = emptySet(),
                                isSelectionMode = false,
                                archiveExtractProgress = null
                            )
                        }
                        if (progress.error != null) {
                            _toastMessage.tryEmit(progress.error)
                        } else {
                            _toastMessage.tryEmit(appContext.getString(R.string.extracted_to_format, destDir))
                        }
                    }
                }
            }
        }
    }

    private fun showStatusMessage(panelId: PanelId, message: String) {
        updatePanel(panelId) { it.copy(statusMessage = message) }
        viewModelScope.launch {
            delay(5000)
            updatePanel(panelId) { it.copy(statusMessage = null) }
        }
    }

    private fun extractFileName(path: String): String {
        return if (path.startsWith("content://")) {
            Uri.parse(path).lastPathSegment?.substringAfterLast('/') ?: "file"
        } else {
            path.substringAfterLast('/')
        }
    }

    private fun formatFileCount(dirs: Int, files: Int): String {
        val parts = buildList {
            if (dirs > 0) add("$dirs ${pluralDirs(dirs)}")
            if (files > 0) add("$files ${pluralFiles(files)}")
        }
        return parts.joinToString(appContext.getString(R.string.and_conjunction)).ifEmpty { appContext.getString(R.string.zero_files) }
    }

    private fun pluralDirs(count: Int): String {
        val mod100 = count % 100
        val mod10 = count % 10
        return when {
            mod100 in 11..14 -> appContext.getString(R.string.plural_folders_genitive)
            mod10 == 1 -> appContext.getString(R.string.plural_folders_nom)
            mod10 in 2..4 -> appContext.getString(R.string.plural_folders_gen_few)
            else -> appContext.getString(R.string.plural_folders_genitive)
        }
    }

    private fun pluralFiles(count: Int): String {
        val mod100 = count % 100
        val mod10 = count % 10
        return when {
            mod100 in 11..14 -> appContext.getString(R.string.plural_files_genitive)
            mod10 == 1 -> appContext.getString(R.string.plural_files_nom)
            mod10 in 2..4 -> appContext.getString(R.string.plural_files_gen_few)
            else -> appContext.getString(R.string.plural_files_genitive)
        }
    }

    private fun getPanel(panelId: PanelId): PanelUiState {
        return when (panelId) {
            PanelId.TOP -> _uiState.value.topPanel
            PanelId.BOTTOM -> _uiState.value.bottomPanel
        }
    }

    private fun updatePanel(panelId: PanelId, transform: (PanelUiState) -> PanelUiState) {
        _uiState.update { state ->
            when (panelId) {
                PanelId.TOP -> state.copy(topPanel = transform(state.topPanel))
                PanelId.BOTTOM -> state.copy(bottomPanel = transform(state.bottomPanel))
            }
        }
    }

    // --- File Properties ---

    fun showFileProperties(entry: FileEntry) {
        _uiState.update { it.copy(dialogState = DialogState.FilePropertiesState(entry = entry)) }
        viewModelScope.launch {
            getFilePropertiesUseCase(entry).collect { props ->
                _uiState.update { state ->
                    val dialog = state.dialogState
                    if (dialog is DialogState.FilePropertiesState && dialog.entry.path == entry.path) {
                        state.copy(dialogState = dialog.copy(properties = props))
                    } else state
                }
            }
        }
    }

    fun showSelectedFileProperties() {
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)
        val selected = panel.files.filter { it.path in panel.selectedPaths }
        if (selected.size == 1) {
            showFileProperties(selected.first())
        }
    }

    fun calculateHash() {
        val dialog = _uiState.value.dialogState
        if (dialog !is DialogState.FilePropertiesState) return
        val entry = dialog.entry

        _uiState.update { state ->
            val d = state.dialogState
            if (d is DialogState.FilePropertiesState) {
                state.copy(dialogState = d.copy(isHashCalculating = true))
            } else state
        }

        viewModelScope.launch {
            calculateHashUseCase(entry.path).collect { hash ->
                _uiState.update { state ->
                    val d = state.dialogState
                    if (d is DialogState.FilePropertiesState && d.entry.path == entry.path) {
                        state.copy(dialogState = d.copy(hashResult = hash))
                    } else state
                }
            }
            _uiState.update { state ->
                val d = state.dialogState
                if (d is DialogState.FilePropertiesState) {
                    state.copy(dialogState = d.copy(isHashCalculating = false))
                } else state
            }
        }
    }

    fun removeExif() {
        val dialog = _uiState.value.dialogState
        if (dialog !is DialogState.FilePropertiesState) return
        val entry = dialog.entry

        viewModelScope.launch {
            val success = getFilePropertiesUseCase.removeExif(entry.path)
            if (success) {
                _toastMessage.tryEmit(appContext.getString(R.string.exif_data_removed))
                // Reload properties to reflect removal
                showFileProperties(entry)
            } else {
                _toastMessage.tryEmit(appContext.getString(R.string.exif_remove_error))
            }
        }
    }

    // --- APK Install ---

    fun showApkInstallDialog(entry: FileEntry) {
        _uiState.update {
            it.copy(dialogState = DialogState.ApkInstallDialog(entry = entry))
        }
        viewModelScope.launch {
            loadApkInstallInfoUseCase(entry)
                .onSuccess { info ->
                    val current = _uiState.value.dialogState
                    if (current is DialogState.ApkInstallDialog && current.entry.path == entry.path) {
                        _uiState.update {
                            it.copy(dialogState = current.copy(apkInfo = info, isLoading = false))
                        }
                    }
                }
                .onFailure { e ->
                    val current = _uiState.value.dialogState
                    if (current is DialogState.ApkInstallDialog && current.entry.path == entry.path) {
                        _uiState.update {
                            it.copy(dialogState = current.copy(
                                isLoading = false,
                                error = e.message ?: appContext.getString(R.string.apk_analysis_error)
                            ))
                        }
                    }
                }
        }
    }

    fun installApk(entry: FileEntry) {
        dismissDialog()
        try {
            val uri = if (entry.isContentUri) {
                Uri.parse(entry.path)
            } else {
                androidx.core.content.FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.fileprovider",
                    File(entry.path)
                )
            }
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
            }
            appContext.startActivity(intent)
        } catch (_: Exception) {
            _toastMessage.tryEmit(appContext.getString(R.string.installer_open_failed))
        }
    }

    // --- App Manager ---

    fun openAppManager() {
        _navigationEvent.tryEmit(NavigationEvent.OpenAppManager)
    }

    // --- Storage Analysis ---

    fun openStorageAnalysis() {
        _navigationEvent.tryEmit(NavigationEvent.OpenStorageAnalysis)
    }

    fun openGlobalSearch() {
        _navigationEvent.tryEmit(NavigationEvent.OpenGlobalSearch)
    }

    // --- File Transfer ---

    fun openTransfer() {
        _navigationEvent.tryEmit(NavigationEvent.OpenTransfer)
    }

    fun onSendToTransfer(paths: List<String>) {
        val files = paths.mapNotNull { path ->
            java.io.File(path).takeIf { it.exists() }
        }
        if (files.isEmpty()) return
        TransferHolder.selectedFiles = files
        _navigationEvent.tryEmit(NavigationEvent.OpenTransfer)
    }

    fun onSendSelected(paths: List<String>) {
        val files = paths.mapNotNull { path ->
            java.io.File(path).takeIf { it.exists() }
        }
        if (files.isEmpty()) return

        try {
            val uris = files.map { file ->
                androidx.core.content.FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.fileprovider",
                    file
                )
            }

            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = files[0].let { f ->
                        android.webkit.MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(f.extension.lowercase())
                            ?: "*/*"
                    }
                    putExtra(Intent.EXTRA_STREAM, uris[0])
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

            val chooser = Intent.createChooser(intent, null).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(chooser)
        } catch (_: Exception) {
            _toastMessage.tryEmit(appContext.getString(R.string.no_app_for_file))
        }

        clearSelection(uiState.value.activePanel)
    }

    // --- Open with external app ---

    fun openWithExternalApp(entry: FileEntry) {
        if (entry.isDirectory) {
            _toastMessage.tryEmit(appContext.getString(R.string.cannot_open_folders_external))
            return
        }
        try {
            val uri = if (entry.isContentUri) {
                Uri.parse(entry.path)
            } else {
                androidx.core.content.FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.fileprovider",
                    File(entry.path)
                )
            }
            val mime = entry.mimeType()
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, appContext.getString(R.string.open_in_chooser)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(chooser)
        } catch (_: Exception) {
            _toastMessage.tryEmit(appContext.getString(R.string.no_app_for_file))
        }
    }

    fun openSelectedWithExternalApp() {
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)
        val selected = panel.files.filter { it.path in panel.selectedPaths }
        if (selected.size == 1 && !selected.first().isDirectory) {
            openWithExternalApp(selected.first())
        }
    }

    fun openTerminal() {
        _navigationEvent.tryEmit(NavigationEvent.OpenTerminal)
    }

    fun compareSelected() {
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)
        val selected = panel.files.filter { it.path in panel.selectedPaths }

        if (selected.size == 2) {
            // Two items in the same panel
            com.vamp.haron.domain.model.ComparisonHolder.leftPath = selected[0].path
            com.vamp.haron.domain.model.ComparisonHolder.rightPath = selected[1].path
            clearSelection(activeId)
            _navigationEvent.tryEmit(NavigationEvent.OpenComparison)
        } else if (selected.size == 1) {
            // One item in active panel — check other panel for one selected item
            val otherId = if (activeId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
            val otherPanel = getPanel(otherId)
            val otherSelected = otherPanel.files.filter { it.path in otherPanel.selectedPaths }
            if (otherSelected.size == 1) {
                com.vamp.haron.domain.model.ComparisonHolder.leftPath = selected[0].path
                com.vamp.haron.domain.model.ComparisonHolder.rightPath = otherSelected[0].path
                clearSelection(activeId)
                clearSelection(otherId)
                _navigationEvent.tryEmit(NavigationEvent.OpenComparison)
            }
        }
    }

    fun hideSelectedInFile() {
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)
        val selected = panel.files.filter { it.path in panel.selectedPaths }
        if (selected.size == 1 && !selected.first().isDirectory) {
            com.vamp.haron.domain.model.StegoHolder.payloadPath = selected.first().path
            com.vamp.haron.domain.model.StegoHolder.carrierPath = ""
            clearSelection(activeId)
            _navigationEvent.tryEmit(NavigationEvent.OpenSteganography)
        }
    }

    fun openSteganography() {
        _navigationEvent.tryEmit(NavigationEvent.OpenSteganography)
    }

    fun castSelected() {
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)
        val selected = panel.files.filter { it.path in panel.selectedPaths }
        if (selected.isEmpty()) return

        val modes = mutableListOf<com.vamp.haron.domain.model.CastMode>()

        // Determine available modes based on selection
        val hasImages = selected.any { !it.isDirectory && it.path.lowercase().let { p ->
            p.endsWith(".jpg") || p.endsWith(".jpeg") || p.endsWith(".png") ||
            p.endsWith(".gif") || p.endsWith(".webp") || p.endsWith(".bmp")
        }}
        val hasMedia = selected.any { !it.isDirectory && it.path.lowercase().let { p ->
            p.endsWith(".mp4") || p.endsWith(".mkv") || p.endsWith(".avi") ||
            p.endsWith(".webm") || p.endsWith(".mp3") || p.endsWith(".flac") ||
            p.endsWith(".ogg") || p.endsWith(".wav")
        }}
        val hasPdf = selected.any { !it.isDirectory && it.path.lowercase().endsWith(".pdf") }

        if (hasMedia || hasImages) modes.add(com.vamp.haron.domain.model.CastMode.SINGLE_MEDIA)
        if (hasImages && selected.size > 1) modes.add(com.vamp.haron.domain.model.CastMode.SLIDESHOW)
        if (hasPdf && selected.size == 1) modes.add(com.vamp.haron.domain.model.CastMode.PDF_PRESENTATION)
        modes.add(com.vamp.haron.domain.model.CastMode.FILE_INFO)
        modes.add(com.vamp.haron.domain.model.CastMode.SCREEN_MIRROR)

        val paths = selected.map { it.path }
        _uiState.update { it.copy(dialogState = DialogState.CastModeSelect(paths, modes)) }
    }

    fun openSettings() {
        _navigationEvent.tryEmit(NavigationEvent.OpenSettings)
    }

    fun openFeatures() {
        _navigationEvent.tryEmit(NavigationEvent.OpenFeatures)
    }

    fun openSupport() {
        _navigationEvent.tryEmit(NavigationEvent.OpenSupport)
    }

    // --- Gesture system ---

    fun executeGestureAction(action: GestureAction) {
        if (action == GestureAction.NONE) return
        // Dismiss any open dialog/drawer/shelf before executing voice command
        // (except when the command itself opens drawer/shelf)
        val st = _uiState.value
        if (st.dialogState != DialogState.None) dismissDialog()
        if (action != GestureAction.OPEN_DRAWER && st.showDrawer) dismissDrawer()
        if (action != GestureAction.OPEN_SHELF && st.showShelf) dismissShelf()
        // Use panel override if set (e.g. "назад вверху"), otherwise active panel
        val panelId = voiceCommandManager.consumePanelOverride() ?: _uiState.value.activePanel
        when (action) {
            GestureAction.NONE -> { /* do nothing */ }
            GestureAction.OPEN_DRAWER -> toggleDrawer()
            GestureAction.OPEN_SHELF -> toggleShelf()
            GestureAction.TOGGLE_HIDDEN -> toggleShowHidden(panelId)
            GestureAction.CREATE_NEW -> requestCreateFromTemplate()
            GestureAction.GLOBAL_SEARCH -> openGlobalSearch()
            GestureAction.OPEN_TERMINAL -> openTerminal()
            GestureAction.SELECT_ALL -> selectAll(panelId)
            GestureAction.REFRESH -> {
                refreshPanel(panelId)
                _toastMessage.tryEmit(appContext.getString(R.string.panel_refreshed))
            }
            GestureAction.GO_HOME -> navigateTo(panelId, HaronConstants.ROOT_PATH)
            GestureAction.SORT_CYCLE -> cycleSortOrder(panelId)
            GestureAction.OPEN_SETTINGS -> openSettings()
            GestureAction.OPEN_TRANSFER -> openTransfer()
            GestureAction.OPEN_TRASH -> showTrash()
            GestureAction.OPEN_STORAGE -> _navigationEvent.tryEmit(NavigationEvent.OpenStorageAnalysis)
            GestureAction.OPEN_DUPLICATES -> _navigationEvent.tryEmit(NavigationEvent.OpenDuplicateDetector)
            GestureAction.OPEN_APPS -> _navigationEvent.tryEmit(NavigationEvent.OpenAppManager)
            GestureAction.OPEN_SCANNER -> _navigationEvent.tryEmit(NavigationEvent.OpenScanner)
            GestureAction.SORT_SPECIFIC -> applySortFromVoice(panelId)
            // --- Voice Level 1 + Level 2 ---
            GestureAction.NAVIGATE_BACK -> {
                if (canNavigateBack(panelId)) navigateBack(panelId)
                else _toastMessage.tryEmit(appContext.getString(R.string.no_history_back))
            }
            GestureAction.NAVIGATE_FORWARD -> {
                val panel = getPanel(panelId)
                if (panel.historyIndex < panel.navigationHistory.lastIndex) navigateForward(panelId)
                else _toastMessage.tryEmit(appContext.getString(R.string.no_history_forward))
            }
            GestureAction.NAVIGATE_UP -> { navigateUp(panelId) }
            GestureAction.DELETE_SELECTED -> requestDeleteSelected()
            GestureAction.COPY_SELECTED -> copySelectedToOtherPanel()
            GestureAction.MOVE_SELECTED -> moveSelectedToOtherPanel()
            GestureAction.RENAME -> executeVoiceRename(panelId)
            GestureAction.CREATE_ARCHIVE -> requestCreateArchive()
            GestureAction.EXTRACT_ARCHIVE -> {
                val panel = getPanel(panelId)
                if (panel.isArchiveMode) {
                    extractFromArchive(panelId)
                } else {
                    // Try to extract selected archive file(s) into other panel
                    extractSelectedArchiveFiles(panelId)
                }
            }
            GestureAction.FILE_PROPERTIES -> showSelectedFileProperties()
            GestureAction.DESELECT_ALL -> clearSelection(panelId)
            GestureAction.NAVIGATE_TO_FOLDER -> navigateToFolderFromVoice(panelId)
            GestureAction.REFRESH_FOLDER_CACHE -> {
                val map = rebuildFolderCache()
                _toastMessage.tryEmit(appContext.getString(R.string.folder_cache_refreshed, map.size))
            }
            GestureAction.OPEN_SECURE_FOLDER -> showAllProtectedFiles()
            // Handled at NavHost level, not here
            GestureAction.OPEN_LOGS, GestureAction.LOGS_PAUSE, GestureAction.LOGS_RESUME -> {}
        }
    }

    private fun applySortFromVoice(panelId: PanelId) {
        val field = voiceCommandManager.pendingSortField
        val explicitDirection = voiceCommandManager.pendingSortDirection
        voiceCommandManager.consumeSortParams()
        if (field == null) return cycleSortOrder(panelId)
        val current = getPanel(panelId).sortOrder
        // If same field and no explicit direction → toggle direction
        val direction = explicitDirection ?: if (current.field == field) {
            if (current.direction == com.vamp.haron.data.model.SortDirection.ASCENDING)
                com.vamp.haron.data.model.SortDirection.DESCENDING
            else com.vamp.haron.data.model.SortDirection.ASCENDING
        } else {
            current.direction
        }
        val newOrder = com.vamp.haron.data.model.SortOrder(field, direction)
        setSortOrder(panelId, newOrder)
        val sortName = when (field) {
            com.vamp.haron.data.model.SortField.NAME -> appContext.getString(R.string.sort_by_name)
            com.vamp.haron.data.model.SortField.DATE -> appContext.getString(R.string.sort_by_date)
            com.vamp.haron.data.model.SortField.SIZE -> appContext.getString(R.string.sort_by_size)
            com.vamp.haron.data.model.SortField.EXTENSION -> appContext.getString(R.string.sort_by_type)
        }
        val dirName = if (direction == com.vamp.haron.data.model.SortDirection.ASCENDING) "↑" else "↓"
        _toastMessage.tryEmit(appContext.getString(R.string.sort_changed_to, "$sortName $dirName"))
    }

    private fun executeVoiceRename(panelId: PanelId) {
        val name = voiceCommandManager.consumeRenameName()
        if (name == null) {
            // No name specified — just open inline rename UI
            requestRename()
            return
        }
        val panel = getPanel(panelId)
        val selected = panel.selectedPaths.firstOrNull()
        if (selected == null) {
            _toastMessage.tryEmit(appContext.getString(R.string.select_files_first))
            return
        }
        viewModelScope.launch {
            // Preserve extension
            val file = java.io.File(selected)
            val ext = file.extension
            val newName = if (ext.isNotEmpty()) "$name.$ext" else name
            renameFileUseCase(selected, newName).fold(
                onSuccess = { newPath ->
                    _toastMessage.tryEmit(appContext.getString(R.string.renamed_to, java.io.File(newPath).name))
                    clearSelection(panelId)
                    refreshPanel(panelId)
                },
                onFailure = { e ->
                    _toastMessage.tryEmit(appContext.getString(R.string.error_rename) + ": ${e.message}")
                }
            )
        }
    }

    /**
     * Stem-based aliases: Russian word stem → English folder name.
     * Ordered longest-first so "фотограф" matches before "фото".
     * Handles all case forms: камера/камеру/камере/камерой → Camera.
     */
    private val folderStemAliases = listOf(
        // Download
        "загрузк" to "Download", "загрузо" to "Download",
        // Documents
        "документ" to "Documents",
        // Pictures
        "изображен" to "Pictures", "картин" to "Pictures",
        // DCIM
        "фотограф" to "DCIM",
        // Camera
        "камер" to "Camera",
        // Movies
        "фильм" to "Movies",
        // Music
        "музык" to "Music",
        // Telegram
        "телеграм" to "Telegram",
        // WhatsApp
        "ватсап" to "WhatsApp", "вотсап" to "WhatsApp", "вацап" to "WhatsApp",
        // Screenshots
        "скриншот" to "Screenshots",
        // Bluetooth
        "блютуз" to "Bluetooth", "блютус" to "Bluetooth",
        // Notifications
        "уведомлен" to "Notifications",
        // Ringtones
        "рингтон" to "Ringtones",
        // Podcasts
        "подкаст" to "Podcasts",
        // Short stems last (to avoid false prefix matches)
        "фото" to "DCIM", "видео" to "Movies",
    )

    /** Resolve Russian query to English folder name via stem matching. */
    private fun resolveAlias(query: String): String? {
        val lower = query.lowercase()
        return folderStemAliases.firstOrNull { (stem, _) -> lower.startsWith(stem) }?.second
    }

    /** Cached folder name → path map. Permanent until manual refresh via voice command. */
    private var folderScanCache: Map<String, String> = emptyMap()

    private fun getFolderNameMap(): Map<String, String> {
        if (folderScanCache.isNotEmpty()) return folderScanCache
        return rebuildFolderCache()
    }

    /** Rebuild folder cache from storage scan. */
    private fun rebuildFolderCache(): Map<String, String> {
        val candidates = mutableListOf<String>()
        val root = java.io.File(com.vamp.haron.common.constants.HaronConstants.ROOT_PATH)
        scanFolders(root, maxDepth = 3, currentDepth = 0, candidates)

        val state = _uiState.value
        candidates.addAll(state.favorites.filter { java.io.File(it).isDirectory })
        candidates.addAll(state.recentPaths.filter { java.io.File(it).isDirectory })
        candidates.addAll(state.bookmarks.values.filter { java.io.File(it).isDirectory })

        // Sort by depth (shallowest first) so root-level folders win over nested ones
        // e.g. /storage/.../Music wins over /storage/.../TwinApps/Music
        val map = mutableMapOf<String, String>()
        for (path in candidates.distinct().sortedBy { it.count { c -> c == '/' || c == '\\' } }) {
            val name = java.io.File(path).name
            map.putIfAbsent(name, path)
        }
        folderScanCache = map
        return map
    }

    private fun navigateToFolderFromVoice(panelId: PanelId) {
        val query = voiceCommandManager.consumeFolderQuery() ?: return

        // Check Russian alias first (stem-based: камеру/камере/камерой → Camera)
        val resolvedAlias = resolveAlias(query)
        val searchName = resolvedAlias ?: query

        // Get cached folder map + add current directory subfolders
        val nameToPathMap = getFolderNameMap().toMutableMap()
        val panel = getPanel(panelId)
        for (f in panel.files) {
            if (f.isDirectory) nameToPathMap.putIfAbsent(java.io.File(f.path).name, f.path)
        }

        // Direct lookup by alias/name (case-insensitive)
        val directMatch = nameToPathMap.entries.firstOrNull {
            it.key.equals(searchName, ignoreCase = true)
        }
        if (directMatch != null) {
            navigateTo(panelId, directMatch.value)
            _toastMessage.tryEmit(appContext.getString(R.string.navigated_to_format, directMatch.key))
            return
        }

        // Fuzzy match
        val match = com.vamp.haron.common.util.FuzzyMatch.findBestMatch(
            searchName, nameToPathMap.keys.toList(), threshold = 0.4f
        )
        if (match != null) {
            val path = nameToPathMap[match]!!
            navigateTo(panelId, path)
            _toastMessage.tryEmit(appContext.getString(R.string.navigated_to_format, match))
        } else {
            _toastMessage.tryEmit(appContext.getString(R.string.folder_not_found_format, query))
        }
    }

    /** Recursively collect directory paths up to maxDepth. Skips hidden and Android/data. */
    private fun scanFolders(dir: java.io.File, maxDepth: Int, currentDepth: Int, out: MutableList<String>) {
        if (currentDepth > maxDepth) return
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (!child.isDirectory) continue
            val name = child.name
            // Skip hidden dirs and heavy system dirs
            if (name.startsWith(".") || name == "Android" && currentDepth == 0) continue
            out.add(child.absolutePath)
            scanFolders(child, maxDepth, currentDepth + 1, out)
        }
    }

    private fun cycleSortOrder(panelId: PanelId) {
        val current = getPanel(panelId).sortOrder
        val fields = com.vamp.haron.data.model.SortField.entries
        val nextIndex = (fields.indexOf(current.field) + 1) % fields.size
        val newOrder = current.copy(field = fields[nextIndex])
        setSortOrder(panelId, newOrder)
        val sortName = when (newOrder.field) {
            com.vamp.haron.data.model.SortField.NAME -> appContext.getString(R.string.sort_by_name)
            com.vamp.haron.data.model.SortField.DATE -> appContext.getString(R.string.sort_by_date)
            com.vamp.haron.data.model.SortField.SIZE -> appContext.getString(R.string.sort_by_size)
            com.vamp.haron.data.model.SortField.EXTENSION -> appContext.getString(R.string.sort_by_type)
        }
        _toastMessage.tryEmit(appContext.getString(R.string.sort_changed_to, sortName))
    }

    fun reloadGestureMappings() {
        _uiState.update {
            it.copy(
                gestureMappings = preferences.getGestureMappings(),
                marqueeEnabled = preferences.marqueeEnabled
            )
        }
    }

    // --- Force Delete ---

    fun requestForceDelete() {
        val state = _uiState.value
        val activeId = state.activePanel
        val panel = getPanel(activeId)
        val selected = panel.selectedPaths.toList()
        if (selected.isEmpty()) {
            _toastMessage.tryEmit(appContext.getString(R.string.select_files_first))
            return
        }
        val names = panel.files
            .filter { it.path in panel.selectedPaths }
            .map { it.name }
        _uiState.update {
            it.copy(dialogState = DialogState.ForceDeleteConfirm(selected, names))
        }
    }

    fun confirmForceDelete(paths: List<String>) {
        dismissDialog()
        val activeId = _uiState.value.activePanel
        clearSelection(activeId)
        val total = paths.size

        viewModelScope.launch {
            _uiState.update {
                it.copy(operationProgress = OperationProgress(0, total, "", OperationType.DELETE))
            }
            forceDeleteUseCase(paths) { current, fileName ->
                _uiState.update {
                    it.copy(operationProgress = OperationProgress(current, total, fileName, OperationType.DELETE))
                }
            }
                .onSuccess { count ->
                    hapticManager.success()
                    _toastMessage.tryEmit(appContext.getString(R.string.force_deleted_count, count))
                }
                .onFailure { e ->
                    hapticManager.error()
                    _toastMessage.tryEmit(appContext.getString(R.string.error_format, e.message ?: ""))
                }
            _uiState.update {
                it.copy(operationProgress = OperationProgress(total, total, "", OperationType.DELETE, isComplete = true))
            }
            viewModelScope.launch {
                delay(2000)
                _uiState.update {
                    if (it.operationProgress?.isComplete == true) it.copy(operationProgress = null) else it
                }
            }
            refreshPanel(PanelId.TOP)
            refreshPanel(PanelId.BOTTOM)
        }
    }

    // --- Empty Folders ---

    fun findEmptyFolders() {
        val activeId = _uiState.value.activePanel
        val path = getPanel(activeId).currentPath
        if (path.startsWith("content://")) {
            _toastMessage.tryEmit(appContext.getString(R.string.saf_unavailable))
            return
        }
        _uiState.update {
            it.copy(dialogState = DialogState.EmptyFolderCleanup(isLoading = true, isRecursive = true))
        }
        viewModelScope.launch {
            findEmptyFoldersUseCase(path, recursive = true).collect { folders ->
                _uiState.update {
                    it.copy(dialogState = DialogState.EmptyFolderCleanup(
                        folders = folders,
                        isRecursive = true,
                        selectedPaths = folders.toSet(),
                        isLoading = false
                    ))
                }
            }
        }
    }

    fun toggleEmptyFoldersRecursive(recursive: Boolean) {
        val dialog = _uiState.value.dialogState
        if (dialog !is DialogState.EmptyFolderCleanup) return
        val activeId = _uiState.value.activePanel
        val path = getPanel(activeId).currentPath
        _uiState.update {
            it.copy(dialogState = dialog.copy(isLoading = true, isRecursive = recursive))
        }
        viewModelScope.launch {
            findEmptyFoldersUseCase(path, recursive = recursive).collect { folders ->
                _uiState.update {
                    it.copy(dialogState = DialogState.EmptyFolderCleanup(
                        folders = folders,
                        isRecursive = recursive,
                        selectedPaths = folders.toSet(),
                        isLoading = false
                    ))
                }
            }
        }
    }

    fun toggleEmptyFolderSelected(path: String) {
        val dialog = _uiState.value.dialogState
        if (dialog !is DialogState.EmptyFolderCleanup) return
        val newSelected = dialog.selectedPaths.toMutableSet()
        if (path in newSelected) newSelected.remove(path) else newSelected.add(path)
        _uiState.update { it.copy(dialogState = dialog.copy(selectedPaths = newSelected)) }
    }

    fun selectAllEmptyFolders() {
        val dialog = _uiState.value.dialogState
        if (dialog !is DialogState.EmptyFolderCleanup) return
        val newSelected = if (dialog.selectedPaths.size == dialog.folders.size) {
            emptySet()
        } else {
            dialog.folders.toSet()
        }
        _uiState.update { it.copy(dialogState = dialog.copy(selectedPaths = newSelected)) }
    }

    fun deleteEmptyFolders() {
        val dialog = _uiState.value.dialogState
        if (dialog !is DialogState.EmptyFolderCleanup) return
        val paths = dialog.selectedPaths.toList()
        if (paths.isEmpty()) return
        dismissDialog()

        viewModelScope.launch {
            moveToTrashUseCase(paths)
                .onSuccess { result ->
                    hapticManager.success()
                    _toastMessage.tryEmit(appContext.getString(R.string.empty_folders_deleted, result.movedCount))
                    updateTrashSizeInfo()
                }
                .onFailure { e ->
                    hapticManager.error()
                    _toastMessage.tryEmit(appContext.getString(R.string.error_format, e.message ?: ""))
                }
            refreshPanel(PanelId.TOP)
            refreshPanel(PanelId.BOTTOM)
        }
    }

    // --- Folder Size Calculation ---

    fun getSelectedTotalSizeWithFolders(): Pair<Long, Boolean> {
        val state = _uiState.value
        val activeId = state.activePanel
        val panel = getPanel(activeId)
        val selected = panel.files.filter { it.path in panel.selectedPaths }
        var total = 0L
        var calculating = false
        for (entry in selected) {
            if (entry.isDirectory) {
                val cached = state.folderSizeCache[entry.path]
                if (cached != null) {
                    total += cached
                } else {
                    calculating = true
                    // Launch calculation if not already running
                    if (!folderSizeJobs.containsKey(entry.path)) {
                        calculateFolderSize(entry.path)
                    }
                }
            } else {
                total += entry.size
            }
        }
        return total to calculating
    }

    private fun calculateFolderSize(folderPath: String) {
        val job = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val size = File(folderPath).walkTopDown().filter { it.isFile }.sumOf { it.length() }
                _uiState.update { state ->
                    state.copy(folderSizeCache = state.folderSizeCache + (folderPath to size))
                }
            }
        }
        folderSizeJobs[folderPath] = job
        job.invokeOnCompletion { folderSizeJobs.remove(folderPath) }
    }

    fun clearFolderSizeCache() {
        folderSizeJobs.values.forEach { it.cancel() }
        folderSizeJobs.clear()
        _uiState.update { it.copy(folderSizeCache = emptyMap()) }
    }

    // --- Bookmarks ---

    fun showBookmarkPopup() {
        _uiState.update {
            it.copy(
                showBookmarkPopup = true,
                bookmarks = preferences.getBookmarks()
            )
        }
    }

    fun dismissBookmarkPopup() {
        _uiState.update { it.copy(showBookmarkPopup = false) }
    }

    fun navigateToBookmark(slot: Int) {
        val path = _uiState.value.bookmarks[slot] ?: return
        dismissBookmarkPopup()
        val activeId = _uiState.value.activePanel
        navigateTo(activeId, path)
        hapticManager.tick()
    }

    fun saveBookmark(slot: Int) {
        val activeId = _uiState.value.activePanel
        val path = getPanel(activeId).currentPath
        preferences.setBookmark(slot, path)
        _uiState.update { it.copy(bookmarks = preferences.getBookmarks()) }
        hapticManager.success()
        _toastMessage.tryEmit(appContext.getString(R.string.bookmark_saved, slot))
    }

    // --- Tools popup ---

    fun showToolsPopup() {
        _uiState.update { it.copy(showToolsPopup = true) }
    }

    fun dismissToolsPopup() {
        _uiState.update { it.copy(showToolsPopup = false) }
    }

    fun onToolSelected(index: Int) {
        dismissToolsPopup()
        when (index) {
            0 -> showTrash()
            1 -> openStorageAnalysis()
            2 -> openDuplicateDetector()
            3 -> openAppManager()
            4 -> openLastMedia()
            5 -> openLastDocument()
        }
    }

    private fun openLastMedia() {
        val path = preferences.lastMediaFile
        if (path == null || !File(path).exists()) {
            _toastMessage.tryEmit(appContext.getString(R.string.no_recent_files))
            return
        }
        val file = File(path)
        PlaylistHolder.items = listOf(
            PlaylistHolder.PlaylistItem(
                filePath = path,
                fileName = file.name,
                fileType = file.name.substringAfterLast('.', "").lowercase().let {
                    when (it) {
                        "mp4", "mkv", "avi", "webm", "mov", "3gp" -> "video"
                        else -> "audio"
                    }
                }
            )
        )
        PlaylistHolder.startIndex = 0
        _navigationEvent.tryEmit(NavigationEvent.OpenMediaPlayer(0))
    }

    private fun openLastDocument() {
        val path = preferences.lastDocumentFile
        if (path == null || !File(path).exists()) {
            _toastMessage.tryEmit(appContext.getString(R.string.no_recent_files))
            return
        }
        val file = File(path)
        _navigationEvent.tryEmit(NavigationEvent.OpenPdfReader(path, file.name))
    }

    // --- Widget update ---

    fun updateWidget() {
        // Widget reads from SharedPreferences directly, just trigger update
        try {
            val intent = Intent("android.appwidget.action.APPWIDGET_UPDATE")
            intent.setPackage(appContext.packageName)
            appContext.sendBroadcast(intent)
        } catch (_: Exception) { }
    }

    // --- Protected file operations (virtual view) ---

    private fun copyProtectedFiles(paths: List<String>, destinationDir: String) {
        val activeId = _uiState.value.activePanel
        val targetId = if (activeId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
        clearSelection(activeId)

        viewModelScope.launch {
            val allEntries = secureFolderRepository.getAllProtectedEntries()
            // Expand directory entries → include children
            val expandedPaths = mutableListOf<String>()
            for (path in paths) {
                expandedPaths.add(path)
                val entry = allEntries.find { it.originalPath == path }
                if (entry != null && entry.isDirectory) {
                    allEntries.filter { it.originalPath.startsWith("$path/") && !it.isDirectory }
                        .forEach { expandedPaths.add(it.originalPath) }
                }
            }

            val fileEntries = expandedPaths.mapNotNull { p -> allEntries.find { it.originalPath == p } }
                .filter { !it.isDirectory }
            val total = fileEntries.size
            if (total == 0) {
                _toastMessage.tryEmit(appContext.getString(R.string.folder_empty))
                return@launch
            }

            _uiState.update {
                it.copy(operationProgress = OperationProgress(0, total, appContext.getString(R.string.copying_secure_files), OperationType.COPY))
            }

            var completed = 0
            for ((index, entry) in fileEntries.withIndex()) {
                _uiState.update {
                    it.copy(operationProgress = OperationProgress(index, total, entry.originalName, OperationType.COPY))
                }
                secureFolderRepository.decryptToCache(entry.id).onSuccess { tempFile ->
                    try {
                        // Preserve relative path inside directory
                        val relativePath = paths.firstOrNull { entry.originalPath.startsWith("$it/") }
                            ?.let { entry.originalPath.removePrefix("$it/").substringBeforeLast('/') }
                        val destDir = if (relativePath != null && relativePath.isNotEmpty()) {
                            File(destinationDir, relativePath).also { it.mkdirs() }
                        } else {
                            File(destinationDir)
                        }
                        val destFile = File(destDir, entry.originalName)
                        if (destFile.exists()) {
                            // Auto-rename
                            val baseName = entry.originalName.substringBeforeLast('.')
                            val ext = entry.originalName.substringAfterLast('.', "")
                            var counter = 1
                            var renamed: File
                            do {
                                val newName = if (ext.isNotEmpty()) "${baseName}_($counter).$ext" else "${baseName}_($counter)"
                                renamed = File(destDir, newName)
                                counter++
                            } while (renamed.exists())
                            tempFile.copyTo(renamed)
                        } else {
                            tempFile.copyTo(destFile)
                        }
                        tempFile.delete()
                        completed++
                    } catch (e: Exception) {
                        tempFile.delete()
                        EcosystemLogger.e(HaronConstants.TAG, "copyProtectedFiles error: ${e.message}")
                    }
                }
            }

            _uiState.update {
                it.copy(operationProgress = OperationProgress(completed, total, "", OperationType.COPY, isComplete = true))
            }
            if (completed > 0) {
                hapticManager.success()
                showStatusMessage(targetId, appContext.getString(R.string.copied_format, formatFileCount(0, completed)))
            } else {
                hapticManager.error()
            }
            refreshPanel(targetId)
            delay(2000)
            _uiState.update { if (it.operationProgress?.isComplete == true) it.copy(operationProgress = null) else it }
        }
    }

    private fun moveProtectedFiles(paths: List<String>, destinationDir: String) {
        val activeId = _uiState.value.activePanel
        val targetId = if (activeId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
        clearSelection(activeId)

        viewModelScope.launch {
            val allEntries = secureFolderRepository.getAllProtectedEntries()
            // Expand directory entries → include children
            val expandedPaths = mutableListOf<String>()
            for (path in paths) {
                expandedPaths.add(path)
                val entry = allEntries.find { it.originalPath == path }
                if (entry != null && entry.isDirectory) {
                    allEntries.filter { it.originalPath.startsWith("$path/") && !it.isDirectory }
                        .forEach { expandedPaths.add(it.originalPath) }
                }
            }

            val fileEntries = expandedPaths.mapNotNull { p -> allEntries.find { it.originalPath == p } }
                .filter { !it.isDirectory }
            val dirEntries = paths.mapNotNull { p -> allEntries.find { it.originalPath == p && it.isDirectory } }
            val total = fileEntries.size
            if (total == 0) {
                _toastMessage.tryEmit(appContext.getString(R.string.folder_empty))
                return@launch
            }

            _uiState.update {
                it.copy(operationProgress = OperationProgress(0, total, appContext.getString(R.string.moving_secure_files), OperationType.MOVE))
            }

            var completed = 0
            val idsToRemove = mutableListOf<String>()

            for ((index, entry) in fileEntries.withIndex()) {
                _uiState.update {
                    it.copy(operationProgress = OperationProgress(index, total, entry.originalName, OperationType.MOVE))
                }
                secureFolderRepository.decryptToCache(entry.id).onSuccess { tempFile ->
                    try {
                        val relativePath = paths.firstOrNull { entry.originalPath.startsWith("$it/") }
                            ?.let { entry.originalPath.removePrefix("$it/").substringBeforeLast('/') }
                        val destDir = if (relativePath != null && relativePath.isNotEmpty()) {
                            File(destinationDir, relativePath).also { it.mkdirs() }
                        } else {
                            File(destinationDir)
                        }
                        val destFile = File(destDir, entry.originalName)
                        if (destFile.exists()) {
                            val baseName = entry.originalName.substringBeforeLast('.')
                            val ext = entry.originalName.substringAfterLast('.', "")
                            var counter = 1
                            var renamed: File
                            do {
                                val newName = if (ext.isNotEmpty()) "${baseName}_($counter).$ext" else "${baseName}_($counter)"
                                renamed = File(destDir, newName)
                                counter++
                            } while (renamed.exists())
                            tempFile.copyTo(renamed)
                        } else {
                            tempFile.copyTo(destFile)
                        }
                        tempFile.delete()
                        idsToRemove.add(entry.id)
                        completed++
                    } catch (e: Exception) {
                        tempFile.delete()
                        EcosystemLogger.e(HaronConstants.TAG, "moveProtectedFiles error: ${e.message}")
                    }
                }
            }

            // Remove from secure storage (files + parent dirs)
            val allIdsToRemove = idsToRemove + dirEntries.map { it.id }
            if (allIdsToRemove.isNotEmpty()) {
                secureFolderRepository.deleteFromSecureStorage(allIdsToRemove) { _, _ -> }
            }

            _uiState.update {
                it.copy(operationProgress = OperationProgress(completed, total, "", OperationType.MOVE, isComplete = true))
            }
            if (completed > 0) {
                hapticManager.success()
                showStatusMessage(targetId, appContext.getString(R.string.moved_format, formatFileCount(0, completed)))
            } else {
                hapticManager.error()
            }
            refreshPanel(activeId)
            refreshPanel(targetId)
            delay(2000)
            _uiState.update { if (it.operationProgress?.isComplete == true) it.copy(operationProgress = null) else it }
        }
    }

    private fun deleteProtectedPermanently(paths: List<String>) {
        viewModelScope.launch {
            val allEntries = secureFolderRepository.getAllProtectedEntries()
            // Collect IDs: exact match + cascade for directories
            val directIds = allEntries.filter { it.originalPath in paths }.map { it.id }
            val dirPaths = allEntries.filter { it.isDirectory && it.originalPath in paths }.map { it.originalPath }
            val cascadeIds = if (dirPaths.isNotEmpty()) {
                allEntries.filter { entry ->
                    dirPaths.any { dir -> entry.originalPath.startsWith("$dir/") }
                }.map { it.id }
            } else emptyList()

            val ids = (directIds + cascadeIds).distinct()
            if (ids.isEmpty()) return@launch

            _uiState.update {
                it.copy(operationProgress = OperationProgress(0, ids.size, "", OperationType.DELETE))
            }

            secureFolderRepository.deleteFromSecureStorage(ids) { current, name ->
                _uiState.update {
                    it.copy(operationProgress = OperationProgress(current, ids.size, name, OperationType.DELETE))
                }
            }.onSuccess { count ->
                _uiState.update {
                    it.copy(operationProgress = OperationProgress(count, ids.size, "", OperationType.DELETE, isComplete = true))
                }
                hapticManager.success()
                _toastMessage.tryEmit(appContext.getString(R.string.secure_deleted_count, count))
                refreshPanel(PanelId.TOP)
                refreshPanel(PanelId.BOTTOM)
            }.onFailure { e ->
                _uiState.update { it.copy(operationProgress = null) }
                hapticManager.error()
                _toastMessage.tryEmit(appContext.getString(R.string.protect_error, e.message ?: ""))
            }

            delay(2000)
            _uiState.update { if (it.operationProgress?.isComplete == true) it.copy(operationProgress = null) else it }
        }
    }

    // --- Secure Folder / Shield ---

    fun toggleShield() {
        val currentState = _uiState.value
        if (currentState.isShieldUnlocked) {
            // Turn off shield
            _uiState.update { it.copy(isShieldUnlocked = false, showShieldAuth = false) }
            // If any panel is in virtual secure path, navigate back
            for (panelId in PanelId.entries) {
                if (getPanel(panelId).currentPath == HaronConstants.VIRTUAL_SECURE_PATH) {
                    if (canNavigateBack(panelId)) {
                        navigateBack(panelId)
                    } else {
                        navigateTo(panelId, HaronConstants.ROOT_PATH, pushHistory = false)
                    }
                } else {
                    refreshPanel(panelId)
                }
            }
            _toastMessage.tryEmit(appContext.getString(R.string.shield_off))
        } else {
            val method = authManager.getAppLockMethod()
            val hasAuth = method != com.vamp.haron.domain.model.AppLockMethod.NONE &&
                (authManager.isPinSet() || (authManager.hasBiometricHardware() && authManager.isBiometricEnrolled()))
            if (hasAuth) {
                _uiState.update { it.copy(showShieldAuth = true) }
            } else {
                // No protection configured — show PIN setup dialog
                _uiState.update { it.copy(showShieldPinSetup = true) }
            }
        }
    }

    fun onShieldAuthenticated() {
        val showAll = _uiState.value.showAllProtectedAfterAuth
        _uiState.update { it.copy(isShieldUnlocked = true, showShieldAuth = false, showAllProtectedAfterAuth = false) }
        if (showAll) {
            showAllProtectedFiles()
        } else {
            refreshPanel(PanelId.TOP)
            refreshPanel(PanelId.BOTTOM)
            _toastMessage.tryEmit(appContext.getString(R.string.shield_on))
        }
    }

    fun dismissShieldAuth() {
        _uiState.update { it.copy(showShieldAuth = false, showAllProtectedAfterAuth = false) }
    }

    fun dismissShieldPinSetup() {
        _uiState.update { it.copy(showShieldPinSetup = false) }
    }

    fun onShieldPinSetupConfirm(currentPin: String?, newPin: String, question: String?, answer: String?): Boolean {
        if (currentPin != null && !authManager.verifyPin(currentPin)) return false
        authManager.setPin(newPin)
        if (question != null && answer != null) {
            authManager.setSecurityQuestion(question, answer)
        }
        if (authManager.getAppLockMethod() == com.vamp.haron.domain.model.AppLockMethod.NONE) {
            authManager.setAppLockMethod(com.vamp.haron.domain.model.AppLockMethod.PIN_ONLY)
        }
        _uiState.update { it.copy(showShieldPinSetup = false) }
        _toastMessage.tryEmit(appContext.getString(R.string.pin_set_success))
        return true
    }

    fun verifyShieldPin(pin: String): Boolean = authManager.verifyPin(pin)

    fun getShieldLockMethod(): com.vamp.haron.domain.model.AppLockMethod = authManager.getAppLockMethod()

    fun hasShieldBiometric(): Boolean = authManager.hasBiometricHardware() && authManager.isBiometricEnrolled()

    fun getShieldPinLength(): Int = authManager.getPinLength()

    fun showAllProtectedFiles() {
        val activePanel = _uiState.value.activePanel
        if (!_uiState.value.isShieldUnlocked) {
            val method = authManager.getAppLockMethod()
            val hasAuth = method != com.vamp.haron.domain.model.AppLockMethod.NONE &&
                (authManager.isPinSet() || (authManager.hasBiometricHardware() && authManager.isBiometricEnrolled()))
            if (hasAuth) {
                _uiState.update { it.copy(showShieldAuth = true, showAllProtectedAfterAuth = true) }
            } else {
                // No protection configured — show PIN setup dialog
                _uiState.update { it.copy(showShieldPinSetup = true) }
            }
            return
        }
        navigateTo(activePanel, HaronConstants.VIRTUAL_SECURE_PATH)
    }

    /** Exit protected mode completely — find the last real folder in history and go there */
    fun exitProtectedMode(panelId: PanelId) {
        _uiState.update { it.copy(isShieldUnlocked = false, showShieldAuth = false) }
        val panel = getPanel(panelId)
        // Find last non-protected path in history
        val history = panel.navigationHistory
        val realPath = history.lastOrNull { path ->
            path != HaronConstants.VIRTUAL_SECURE_PATH &&
                !secureFolderRepository.isFileProtected(path) &&
                java.io.File(path).exists()
        }
        navigateTo(panelId, realPath ?: HaronConstants.ROOT_PATH, pushHistory = false)
    }

    fun protectSelectedFiles(explicitPaths: List<String>? = null) {
        val panelId = _uiState.value.activePanel
        val panel = getPanel(panelId)
        val paths = explicitPaths ?: panel.selectedPaths.toList()
        if (paths.isEmpty()) return

        // Collect protected directory paths to check other panel after operation
        val protectedDirs = paths.filter { File(it).isDirectory }.map { File(it).absolutePath }.toSet()

        viewModelScope.launch {
            _uiState.update { it.copy(isProtecting = true) }
            clearSelection(panelId)

            secureFolderRepository.protectFiles(paths) { current, name ->
                _uiState.update {
                    it.copy(protectProgress = appContext.getString(R.string.protecting_files, current, paths.size, name))
                }
            }.onSuccess { count ->
                _uiState.update { it.copy(isProtecting = false, protectProgress = null) }
                _toastMessage.tryEmit(appContext.getString(R.string.protect_success, count))

                // If other panel is inside a protected (now deleted) directory, navigate up
                for (panelId in PanelId.entries) {
                    val panelPath = getPanel(panelId).currentPath
                    val needsUp = protectedDirs.any { dir ->
                        panelPath == dir || panelPath.startsWith("$dir/")
                    }
                    if (needsUp) {
                        // Find the nearest existing parent
                        val parent = protectedDirs
                            .filter { panelPath == it || panelPath.startsWith("$it/") }
                            .maxByOrNull { it.length }
                            ?.let { File(it).parent }
                            ?: HaronConstants.ROOT_PATH
                        navigateTo(panelId, parent, pushHistory = false)
                    } else {
                        refreshPanel(panelId)
                    }
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isProtecting = false, protectProgress = null) }
                _toastMessage.tryEmit(appContext.getString(R.string.protect_error, e.message ?: ""))
            }
        }
    }

    fun unprotectSelectedFiles(explicitPaths: List<String>? = null) {
        val panel = getPanel(_uiState.value.activePanel)
        val paths = explicitPaths ?: panel.selectedPaths.toList()
        if (paths.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isProtecting = true) }
            val allEntries = secureFolderRepository.getAllProtectedEntries()

            // Collect IDs: exact match + cascade for directories (all children)
            val directIds = allEntries.filter { it.originalPath in paths }.map { it.id }
            val dirPaths = allEntries.filter { it.isDirectory && it.originalPath in paths }.map { it.originalPath }
            val cascadeIds = if (dirPaths.isNotEmpty()) {
                allEntries.filter { entry ->
                    !entry.isDirectory && dirPaths.any { dir -> entry.originalPath.startsWith("$dir/") }
                }.map { it.id }
            } else emptyList()

            // Merge, directories first (so folder is created before files are written)
            val dirEntryIds = directIds.filter { id -> allEntries.find { it.id == id }?.isDirectory == true }
            val fileEntryIds = (directIds + cascadeIds).distinct().filter { id -> allEntries.find { it.id == id }?.isDirectory != true }
            val ids = dirEntryIds + fileEntryIds

            clearSelection(_uiState.value.activePanel)

            secureFolderRepository.unprotectFiles(ids) { current, name ->
                _uiState.update {
                    it.copy(protectProgress = appContext.getString(R.string.unprotecting_files, current, ids.size, name))
                }
            }.onSuccess { count ->
                _uiState.update { it.copy(isProtecting = false, protectProgress = null) }
                _toastMessage.tryEmit(appContext.getString(R.string.unprotect_success, count))
                refreshPanel(PanelId.TOP)
                refreshPanel(PanelId.BOTTOM)
            }.onFailure { e ->
                _uiState.update { it.copy(isProtecting = false, protectProgress = null) }
                _toastMessage.tryEmit(appContext.getString(R.string.protect_error, e.message ?: ""))
            }
        }
    }

    fun onProtectedFileClick(entry: FileEntry) {
        if (!entry.isProtected) return
        viewModelScope.launch {
            _toastMessage.tryEmit(appContext.getString(R.string.decrypting_file))
            val allEntries = secureFolderRepository.getAllProtectedEntries()
            val secureEntry = allEntries.find { it.originalPath == entry.path } ?: return@launch
            secureFolderRepository.decryptToCache(secureEntry.id).onSuccess { tempFile ->
                val type = entry.iconRes()
                when (type) {
                    "video", "audio" -> {
                        PlaylistHolder.items = listOf(
                            PlaylistHolder.PlaylistItem(
                                filePath = tempFile.absolutePath,
                                fileName = entry.name,
                                fileType = type
                            )
                        )
                        PlaylistHolder.startIndex = 0
                        _navigationEvent.tryEmit(NavigationEvent.OpenMediaPlayer(0))
                    }
                    "image" -> {
                        GalleryHolder.items = listOf(
                            GalleryHolder.GalleryItem(
                                filePath = tempFile.absolutePath,
                                fileName = entry.name,
                                fileSize = entry.size
                            )
                        )
                        GalleryHolder.startIndex = 0
                        _navigationEvent.tryEmit(NavigationEvent.OpenGallery(0))
                    }
                    "text", "code" -> {
                        _navigationEvent.tryEmit(
                            NavigationEvent.OpenTextEditor(tempFile.absolutePath, entry.name)
                        )
                    }
                    "pdf", "document" -> {
                        _navigationEvent.tryEmit(
                            NavigationEvent.OpenPdfReader(tempFile.absolutePath, entry.name)
                        )
                    }
                    "archive" -> {
                        val panelId = _uiState.value.activePanel
                        navigateIntoArchive(panelId, tempFile.absolutePath, "", null)
                    }
                    else -> {
                        openFileWithIntent(tempFile.absolutePath, entry.name)
                    }
                }
            }.onFailure { e ->
                _toastMessage.tryEmit(appContext.getString(R.string.protect_error, e.message ?: ""))
            }
        }
    }

    private fun openFileWithIntent(path: String, name: String) {
        try {
            val file = File(path)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                appContext, "${appContext.packageName}.fileprovider", file
            )
            val ext = name.substringAfterLast('.', "").lowercase()
            val mimeType = android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(ext) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(Intent.createChooser(intent, name))
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "openFileWithIntent error: ${e.message}")
        }
    }

    fun getSecureFolderInfo(): Pair<Int, Long> {
        val entries = try {
            kotlinx.coroutines.runBlocking { secureFolderRepository.getAllProtectedEntries() }
        } catch (_: Exception) { emptyList() }
        return entries.size to entries.sumOf { it.originalSize }
    }

    fun getProtectedCountForDir(dirPath: String): Int {
        return try {
            kotlinx.coroutines.runBlocking { secureFolderRepository.getProtectedEntriesForDir(dirPath).size }
        } catch (_: Exception) { 0 }
    }

    fun hasAnyProtectedEntry(paths: Set<String>): Boolean {
        return paths.any { secureFolderRepository.isFileProtected(it) }
    }
}
