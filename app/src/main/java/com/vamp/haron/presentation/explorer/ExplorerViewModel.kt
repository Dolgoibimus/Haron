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
    private val receiveFileManager: ReceiveFileManager
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
                gestureMappings = preferences.getGestureMappings()
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

        // Badge: track active background operations count
        viewModelScope.launch {
            FileOperationService.activeOperations.collect { count ->
                _uiState.update { it.copy(activeOperationsCount = count) }
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

        // Voice commands — subscribe to results
        // Navigation actions (OPEN_SETTINGS, OPEN_TERMINAL, etc.) are handled by
        // HaronNavigation's global voice dispatcher. Only local actions handled here.
        viewModelScope.launch {
            voiceCommandManager.lastResult.collect { action ->
                if (action != null && !action.isScreenNavigation) {
                    executeGestureAction(action)
                    voiceCommandManager.consumeResult()
                }
            }
        }

        // Quick Send — auto-refresh panels when files received
        viewModelScope.launch {
            receiveFileManager.quickReceiveCompleted.collect { dirPath ->
                // Refresh any panel showing the receive directory
                if (_uiState.value.topPanel.currentPath == dirPath) refreshPanel(PanelId.TOP)
                if (_uiState.value.bottomPanel.currentPath == dirPath) refreshPanel(PanelId.BOTTOM)
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

    fun startQuickSend(filePath: String, fileName: String, offset: Offset) {
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
                    haronDevices = haronDevices
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
        // Find nearest device circle using same arc layout as QuickSendOverlay
        val devices = current.haronDevices
        val count = devices.size
        val dm = appContext.resources.displayMetrics
        val density = dm.density
        val screenWidthPx = dm.widthPixels.toFloat()
        val screenHeightPx = dm.heightPixels.toFloat()
        val circleRadiusPx = 30f * density // 30.dp
        val circleDiameterPx = circleRadiusPx * 2
        val nearThresholdPx = 60f * density // 60.dp

        // Arc radius — same formula as QuickSendOverlay
        val arcAngle = Math.PI * 2 / 3 // 120 degrees
        val minArcRadius = if (count > 1) {
            (count * circleDiameterPx * 1.3f / arcAngle).toFloat()
        } else {
            circleDiameterPx * 2f
        }
        val arcRadiusPx = minArcRadius.coerceIn(circleDiameterPx * 2f, screenWidthPx * 0.4f)

        // Above anchor by default, below if no space above — same as QuickSendOverlay
        val spaceNeededAbove = arcRadiusPx + circleDiameterPx
        val placeAbove = current.anchorOffset.y > spaceNeededAbove
        // Shift anchor 50dp up or down for better visibility
        val extraOffsetPx = 50f * density
        val shiftedAnchorY = if (placeAbove) current.anchorOffset.y - extraOffsetPx else current.anchorOffset.y + extraOffsetPx

        val startAngle = if (placeAbove) (Math.PI + Math.PI / 6) else (Math.PI / 6)
        val endAngle = if (placeAbove) (2 * Math.PI - Math.PI / 6) else (Math.PI - Math.PI / 6)
        val step = if (count > 1) (endAngle - startAngle) / (count - 1) else 0.0

        var nearest: NetworkDevice? = null
        var nearestDist = Float.MAX_VALUE

        devices.forEachIndexed { index, device ->
            val angle = if (count > 1) startAngle + step * index else (startAngle + endAngle) / 2
            val cx = (current.anchorOffset.x + (arcRadiusPx * kotlin.math.cos(angle)).toFloat())
                .coerceIn(circleRadiusPx, screenWidthPx - circleRadiusPx)
            val cy = (shiftedAnchorY + (arcRadiusPx * kotlin.math.sin(angle)).toFloat())
                .coerceIn(circleRadiusPx, screenHeightPx - circleRadiusPx)
            val dx = finalOffset.x - cx
            val dy = finalOffset.y - cy
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
            if (dist < nearestDist) {
                nearestDist = dist
                nearest = device
            }
        }

        if (nearest != null && nearestDist < nearThresholdPx) {
            performQuickSend(current.filePath, nearest!!.displayName, nearest!!.address, nearest!!.port)
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
                    output.writeUTF(
                        com.vamp.haron.data.transfer.TransferProtocolNegotiator.buildFileHeader(
                            file.name, file.length(), 0
                        )
                    )
                    output.flush()

                    file.inputStream().use { input ->
                        val buffer = ByteArray(HaronConstants.TRANSFER_BUFFER_SIZE)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
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
        viewModelScope.launch {
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

            getFilesUseCase(
                path = path,
                sortOrder = panel.sortOrder,
                showHidden = panel.showHidden,
                showProtected = _uiState.value.isShieldUnlocked
            ).onSuccess { files ->
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
                        showProtected = path == HaronConstants.VIRTUAL_SECURE_PATH,
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
                if (path != HaronConstants.VIRTUAL_SECURE_PATH && !secureFolderRepository.isFileProtected(path)) {
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
        viewModelScope.launch {
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
        val currentPath = getPanel(panelId).currentPath
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
        // Protected directory — use history back
        if (secureFolderRepository.isFileProtected(currentPath)) {
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
        } else if (entry.isProtected && !entry.isDirectory) {
            onProtectedFileClick(entry)
            return
        } else if (entry.isDirectory) {
            navigateTo(panelId, entry.path)
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
                "document" -> {
                    preferences.lastDocumentFile = entry.path
                    _navigationEvent.tryEmit(
                        NavigationEvent.OpenPdfReader(entry.path, entry.name)
                    )
                }
                "archive" -> {
                    _navigationEvent.tryEmit(
                        NavigationEvent.OpenArchiveViewer(entry.path, entry.name)
                    )
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
        val path = getPanel(panelId).currentPath
        if (path == HaronConstants.VIRTUAL_SECURE_PATH) return true
        if (secureFolderRepository.isFileProtected(path)) return canNavigateBack(panelId)
        return fileRepository.getParentPath(path) != null
    }

    fun navigateBack(panelId: PanelId) {
        val panel = getPanel(panelId)
        if (panel.historyIndex <= 0) return
        val newIndex = panel.historyIndex - 1
        val path = panel.navigationHistory[newIndex]
        updatePanel(panelId) { it.copy(historyIndex = newIndex) }
        navigateTo(panelId, path, pushHistory = false)
    }

    fun navigateForward(panelId: PanelId) {
        val panel = getPanel(panelId)
        if (panel.historyIndex >= panel.navigationHistory.lastIndex) return
        val newIndex = panel.historyIndex + 1
        val path = panel.navigationHistory[newIndex]
        updatePanel(panelId) { it.copy(historyIndex = newIndex) }
        navigateTo(panelId, path, pushHistory = false)
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

        val roots = removable.map { vol ->
            val matchingUri = persisted.find { uri ->
                try {
                    val treeDocId = android.provider.DocumentsContract.getTreeDocumentId(uri)
                    val volumeId = treeDocId.split(":").firstOrNull()
                    volumeId != null && volumeId != "primary" &&
                        (vol.uuid != null && volumeId.equals(vol.uuid, ignoreCase = true))
                } catch (_: Exception) { false }
            }
            vol.label to (matchingUri?.toString() ?: "")
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
        updatePanel(panelId) { it.copy(showHidden = newValue) }
        refreshPanel(panelId)
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

    private fun indexFolderAndSearch(panelId: PanelId, folderPath: String, query: String) {
        folderIndexJobs[panelId]?.cancel()
        folderIndexJobs[panelId] = viewModelScope.launch {
            updatePanel(panelId) { it.copy(isContentIndexing = true, contentIndexProgress = null) }
            try {
                searchRepository.indexFolderContent(folderPath, force = true) { processed, total ->
                    updatePanel(panelId) {
                        it.copy(contentIndexProgress = "$processed / $total")
                    }
                }
            } finally {
                updatePanel(panelId) { it.copy(isContentIndexing = false, contentIndexProgress = null) }
            }
            // After indexing, perform content search if query is present
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
        if (paths.isEmpty()) return
        _uiState.update { it.copy(dialogState = DialogState.CreateArchive(paths)) }
    }

    fun confirmCreateArchive(selectedPaths: List<String>, archiveName: String) {
        dismissDialog()
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)
        val name = if (archiveName.endsWith(".zip")) archiveName else "$archiveName.zip"
        val outputPath = if (panel.currentPath.startsWith("content://")) {
            File(appContext.cacheDir, name).absolutePath
        } else {
            "${panel.currentPath}/$name"
        }

        viewModelScope.launch {
            clearSelection(activeId)
            _uiState.update {
                it.copy(operationProgress = OperationProgress(
                    type = OperationType.COPY,
                    current = 0,
                    total = selectedPaths.size,
                    currentFileName = appContext.getString(R.string.creating_zip),
                    isComplete = false
                ))
            }
            try {
                createZipUseCase(selectedPaths, outputPath)
                hapticManager.success()
                _toastMessage.tryEmit(appContext.getString(R.string.archive_created, name))
                refreshBothIfSamePath(activeId)
            } catch (e: Exception) {
                hapticManager.error()
                _toastMessage.tryEmit(appContext.getString(R.string.archive_create_error, e.message ?: ""))
                EcosystemLogger.e(HaronConstants.TAG, "Ошибка ZIP: ${e.message}")
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
            trashRepository.deleteFromTrash(ids)
                .onSuccess {
                    updateTrashSizeInfo()
                    showTrash()
                }
                .onFailure { e ->
                    EcosystemLogger.e(HaronConstants.TAG, "Ошибка удаления из корзины: ${e.message}")
                }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            emptyTrashUseCase()
                .onSuccess { count ->
                    updateTrashSizeInfo()
                    showStatusMessage(_uiState.value.activePanel, appContext.getString(R.string.trash_cleared_count, count))
                    dismissDialog()
                }
                .onFailure { e ->
                    EcosystemLogger.e(HaronConstants.TAG, "Ошибка очистки корзины: ${e.message}")
                }
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
        val path = getPanel(panelId).currentPath
        if (path.isNotEmpty()) {
            navigateTo(panelId, path, pushHistory = false)
        }
    }

    private fun refreshBothIfSamePath(panelId: PanelId) {
        refreshPanel(panelId)
        val otherId = if (panelId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
        if (getPanel(panelId).currentPath == getPanel(otherId).currentPath) {
            refreshPanel(otherId)
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
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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

    fun onSendSelected(paths: List<String>) {
        val files = paths.mapNotNull { path ->
            java.io.File(path).takeIf { it.exists() }
        }
        if (files.isEmpty()) return
        com.vamp.haron.domain.model.TransferHolder.selectedFiles = files
        clearSelection(uiState.value.activePanel)
        _navigationEvent.tryEmit(NavigationEvent.OpenTransfer)
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
            com.vamp.haron.domain.model.ComparisonHolder.leftPath = selected[0].path
            com.vamp.haron.domain.model.ComparisonHolder.rightPath = selected[1].path
            clearSelection(activeId)
            _navigationEvent.tryEmit(NavigationEvent.OpenComparison)
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

    // --- Gesture system ---

    fun executeGestureAction(action: GestureAction) {
        val panelId = _uiState.value.activePanel
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
        _uiState.update { it.copy(gestureMappings = preferences.getGestureMappings()) }
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
            // Need authentication first
            if (authManager.isPinSet() || (authManager.hasBiometricHardware() && authManager.isBiometricEnrolled())) {
                _uiState.update { it.copy(showShieldAuth = true) }
            } else {
                // No auth configured — just enable
                onShieldAuthenticated()
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

    fun verifyShieldPin(pin: String): Boolean = authManager.verifyPin(pin)

    fun getShieldLockMethod(): com.vamp.haron.domain.model.AppLockMethod = authManager.getAppLockMethod()

    fun hasShieldBiometric(): Boolean = authManager.hasBiometricHardware() && authManager.isBiometricEnrolled()

    fun getShieldPinLength(): Int = authManager.getPinLength()

    fun showAllProtectedFiles() {
        val activePanel = _uiState.value.activePanel
        if (!_uiState.value.isShieldUnlocked) {
            // Need auth first, then show all
            if (authManager.isPinSet() || (authManager.hasBiometricHardware() && authManager.isBiometricEnrolled())) {
                _uiState.update { it.copy(showShieldAuth = true, showAllProtectedAfterAuth = true) }
            } else {
                // No auth — unlock and show
                _uiState.update { it.copy(isShieldUnlocked = true, showAllProtectedAfterAuth = false) }
                showAllProtectedFiles()
            }
            return
        }
        navigateTo(activePanel, HaronConstants.VIRTUAL_SECURE_PATH)
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
                        _navigationEvent.tryEmit(
                            NavigationEvent.OpenArchiveViewer(tempFile.absolutePath, entry.name)
                        )
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
