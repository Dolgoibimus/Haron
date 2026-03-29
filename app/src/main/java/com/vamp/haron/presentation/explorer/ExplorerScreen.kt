package com.vamp.haron.presentation.explorer

import android.widget.Toast
import com.vamp.haron.domain.model.PreviewData
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamp.haron.R
import com.vamp.haron.domain.model.PanelId
import com.vamp.haron.presentation.explorer.components.ConflictComparisonCard
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.ui.text.input.KeyboardType
import android.content.Context
import com.vamp.haron.presentation.explorer.components.CreateArchiveDialog
import com.vamp.haron.presentation.explorer.components.CreateFromTemplateDialog
import com.vamp.haron.presentation.explorer.components.DeleteConfirmDialog
import com.vamp.haron.presentation.explorer.components.DrawerMenu
import com.vamp.haron.presentation.explorer.components.FilePropertiesDialog
import com.vamp.haron.presentation.explorer.components.DragOverlay
import com.vamp.haron.presentation.explorer.components.FilePanel
import com.vamp.haron.presentation.explorer.components.PanelDivider
import com.vamp.haron.presentation.explorer.components.ApkInstallDialog
import com.vamp.haron.presentation.explorer.components.BookmarkPopup
import com.vamp.haron.presentation.explorer.components.ToolsPopup
import com.vamp.haron.presentation.explorer.components.EmptyFolderCleanupDialog
import com.vamp.haron.presentation.explorer.components.ExtractOptionsDialog
import com.vamp.haron.presentation.explorer.components.BatchRenameDialog
import com.vamp.haron.presentation.explorer.components.CustomNavbar
import com.vamp.haron.presentation.explorer.components.ForceDeleteConfirmDialog
import com.vamp.haron.presentation.explorer.components.QuickPreviewDialog
import com.vamp.haron.presentation.explorer.components.QuickReceiveOverlay
import com.vamp.haron.presentation.explorer.components.QuickSendOverlay
import com.vamp.haron.presentation.explorer.state.QuickSendState
import com.vamp.haron.presentation.explorer.components.SelectionActionBar
import com.vamp.haron.presentation.explorer.components.ShelfPanel
import com.vamp.haron.presentation.explorer.components.TagAssignDialog
import com.vamp.haron.presentation.explorer.components.TagManageDialog
import com.vamp.haron.presentation.explorer.components.TrashDialog
import com.vamp.haron.domain.model.GestureType
import com.vamp.haron.domain.model.NavigationEvent
import com.vamp.haron.common.util.toFileSize
import com.vamp.haron.presentation.common.ProgressInfoRow
import com.vamp.haron.presentation.explorer.state.DragOperation
import com.vamp.haron.presentation.explorer.state.DragState
import com.vamp.haron.presentation.explorer.state.DialogState
import com.vamp.haron.presentation.applock.LockScreen
import com.vamp.haron.presentation.applock.PinSetupDialog
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

@Composable
fun ExplorerScreen(
    viewModel: ExplorerViewModel = hiltViewModel(),
    initialNavigatePath: String? = null,
    onOpenMediaPlayer: (startIndex: Int) -> Unit = { },
    onOpenTextEditor: (filePath: String, fileName: String) -> Unit = { _, _ -> },
    onOpenGallery: (startIndex: Int) -> Unit = { },
    onOpenPdfReader: (filePath: String, fileName: String) -> Unit = { _, _ -> },
    onOpenArchiveViewer: (filePath: String, fileName: String) -> Unit = { _, _ -> },
    onOpenStorageAnalysis: () -> Unit = { },
    onOpenDuplicateDetector: () -> Unit = { },
    onOpenAppManager: () -> Unit = { },
    onOpenSettings: () -> Unit = { },
    onOpenFeatures: () -> Unit = { },
    onOpenAbout: () -> Unit = { },
    onOpenSupport: () -> Unit = { },
    onOpenGlobalSearch: () -> Unit = { },
    onOpenTransfer: () -> Unit = { },
    onOpenTerminal: () -> Unit = { },
    onNavigateToLibrary: () -> Unit = { },
    onOpenComparison: () -> Unit = { },
    onOpenSteganography: () -> Unit = { },
    onOpenScanner: () -> Unit = { },
    onOpenDocumentViewer: (filePath: String, fileName: String) -> Unit = { _, _ -> },
    onOpenTextEditorCloud: (localCachePath: String, fileName: String, cloudUri: String, otherPanelPath: String) -> Unit = { _, _, _, _ -> },
    onCastModeSelected: (com.vamp.haron.domain.model.CastMode, List<String>) -> Unit = { _, _ -> }
) {
    val state by viewModel.uiState.collectAsState()
    val cloudAccounts by viewModel.cloudAccounts.collectAsState()
    val context = LocalContext.current

    // SAF launcher state for restricted Android/data and Android/obb paths
    var pendingSafPanelId by remember { mutableStateOf(state.activePanel) }
    var pendingSafPath by remember { mutableStateOf("") }
    var showCloudAuthDialog by remember { mutableStateOf(false) }

    // BT HID discoverable launcher
    val castVmOwner = context as? androidx.activity.ComponentActivity
    val castVmForBt: com.vamp.haron.presentation.cast.CastViewModel? = remember(castVmOwner) {
        castVmOwner?.let {
            androidx.lifecycle.ViewModelProvider(it)[com.vamp.haron.presentation.cast.CastViewModel::class.java]
        }
    }
    val btDiscoverableLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { /* result ignored — we observe connectionState instead */ }
    LaunchedEffect(castVmForBt) {
        castVmForBt?.requestDiscoverable?.collect { durationSec ->
            val intent = android.content.Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            intent.putExtra(android.bluetooth.BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, durationSec)
            btDiscoverableLauncher.launch(intent)
        }
    }

    val restrictedSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.onSafAccessGrantedForRestrictedPath(uri, pendingSafPanelId, pendingSafPath)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is NavigationEvent.OpenMediaPlayer -> {
                    onOpenMediaPlayer(event.startIndex)
                }
                is NavigationEvent.OpenTextEditor -> {
                    onOpenTextEditor(event.filePath, event.fileName)
                }
                is NavigationEvent.OpenTextEditorCloud -> {
                    onOpenTextEditorCloud(event.localCachePath, event.fileName, event.cloudUri, event.otherPanelPath)
                }
                is NavigationEvent.OpenGallery -> {
                    onOpenGallery(event.startIndex)
                }
                is NavigationEvent.OpenPdfReader -> {
                    onOpenPdfReader(event.filePath, event.fileName)
                }
                is NavigationEvent.OpenArchiveViewer -> {
                    onOpenArchiveViewer(event.filePath, event.fileName)
                }
                is NavigationEvent.OpenStorageAnalysis -> {
                    onOpenStorageAnalysis()
                }
                is NavigationEvent.OpenDuplicateDetector -> {
                    onOpenDuplicateDetector()
                }
                is NavigationEvent.OpenAppManager -> {
                    onOpenAppManager()
                }
                is NavigationEvent.OpenSettings -> {
                    onOpenSettings()
                }
                is NavigationEvent.OpenFeatures -> {
                    onOpenFeatures()
                }
                is NavigationEvent.OpenSupport -> {
                    onOpenSupport()
                }
                is NavigationEvent.OpenAbout -> {
                    onOpenAbout()
                }
                is NavigationEvent.OpenGlobalSearch -> {
                    onOpenGlobalSearch()
                }
                is NavigationEvent.OpenTransfer -> {
                    onOpenTransfer()
                }
                is NavigationEvent.OpenTerminal -> {
                    onOpenTerminal()
                }
                is NavigationEvent.OpenComparison -> {
                    onOpenComparison()
                }
                is NavigationEvent.OpenSteganography -> {
                    onOpenSteganography()
                }
                is NavigationEvent.OpenScanner -> {
                    onOpenScanner()
                }
                is NavigationEvent.OpenDocumentViewer -> {
                    onOpenDocumentViewer(event.filePath, event.fileName)
                }
                is NavigationEvent.HandleExternalFile -> {
                    // Handled at navigation level
                }
                is NavigationEvent.RequestSafAccess -> {
                    pendingSafPanelId = event.panelId
                    pendingSafPath = event.filePath
                    restrictedSafLauncher.launch(event.initialSafUri)
                }
                is NavigationEvent.OpenCloudAuth -> {
                    showCloudAuthDialog = true
                }
            }
        }
    }
    // Navigate to path from widget intent
    LaunchedEffect(initialNavigatePath) {
        if (!initialNavigatePath.isNullOrEmpty()) {
            viewModel.navigateTo(viewModel.uiState.value.activePanel, initialNavigatePath)
        }
    }
    // Navigate to path from global search result (scroll to specific file)
    LaunchedEffect(Unit) {
        val searchParent = com.vamp.haron.domain.model.SearchNavigationHolder.targetParentPath
        val searchFile = com.vamp.haron.domain.model.SearchNavigationHolder.targetFilePath
        if (!searchParent.isNullOrEmpty()) {
            val panel = viewModel.uiState.value.activePanel
            if (!searchFile.isNullOrEmpty()) {
                viewModel.navigateToFileLocation(panel, searchParent, searchFile)
            } else {
                viewModel.navigateTo(panel, searchParent)
            }
            com.vamp.haron.domain.model.SearchNavigationHolder.targetParentPath = null
            com.vamp.haron.domain.model.SearchNavigationHolder.targetFilePath = null
        }
    }

    // Navigate to received files folder (from Transfer screen or receive overlay)
    val transferPath by com.vamp.haron.domain.model.TransferHolder.pendingNavigationPath.collectAsState()
    LaunchedEffect(transferPath) {
        if (!transferPath.isNullOrEmpty()) {
            val panel = viewModel.uiState.value.activePanel
            viewModel.navigateTo(panel, transferPath!!)
            com.vamp.haron.domain.model.TransferHolder.pendingNavigationPath.value = null
        }
    }

    // Navbar config — reload on resume (after returning from NavbarSettings)
    val prefs = remember { com.vamp.haron.data.datastore.HaronPreferences(context) }
    var navbarConfig by remember { mutableStateOf(prefs.getNavbarConfig()) }
    val lifecycleOwner2 = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner2) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                navbarConfig = prefs.getNavbarConfig()
            }
        }
        lifecycleOwner2.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner2.lifecycle.removeObserver(observer) }
    }

    // Execute pending voice/gesture action after returning from another screen
    val pendingVoiceAction by com.vamp.haron.domain.model.TransferHolder.pendingVoiceAction.collectAsState()
    LaunchedEffect(pendingVoiceAction) {
        val action = pendingVoiceAction ?: return@LaunchedEffect
        com.vamp.haron.domain.model.TransferHolder.pendingVoiceAction.value = null
        viewModel.executeGestureAction(action)
    }

    LaunchedEffect(state.topPanel.currentPath) {
        viewModel.ensureStorageTotalCalculated(state.topPanel.currentPath)
    }
    LaunchedEffect(state.bottomPanel.currentPath) {
        viewModel.ensureStorageTotalCalculated(state.bottomPanel.currentPath)
    }

    // Reload gesture mappings when returning from Settings
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.reloadGestureMappings()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val activePanel = state.activePanel

    // SAF document tree picker (SD card access)
    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.onSafUriGranted(uri)
        }
    }

    // Show selection bar if ANY panel has selection (not just active)
    val selectionPanelId = when {
        state.topPanel.isSelectionMode && state.bottomPanel.isSelectionMode -> activePanel
        state.topPanel.isSelectionMode -> PanelId.TOP
        state.bottomPanel.isSelectionMode -> PanelId.BOTTOM
        else -> null
    }
    val hasSelection = selectionPanelId != null
    val selectionPanelState = when (selectionPanelId) {
        PanelId.TOP -> state.topPanel
        PanelId.BOTTOM -> state.bottomPanel
        else -> state.topPanel // fallback, won't be used
    }
    val selectedEntries = selectionPanelState.files.filter { it.path in selectionPanelState.selectedPaths }
    val selectedDirs = selectedEntries.count { it.isDirectory }
    val selectedFiles = selectedEntries.size - selectedDirs
    val archiveExtsForSelection = remember { setOf("zip", "7z", "rar", "tar", "gz", "bz2", "xz", "tgz", "tbz2", "txz", "gtar") }
    val allSelectedAreArchives = selectedEntries.isNotEmpty() && !selectionPanelState.isArchiveMode &&
        selectedEntries.all { !it.isDirectory && it.name.substringAfterLast('.').lowercase() in archiveExtsForSelection }
    // Cache selection-derived values — avoid repeated .filter() on every recomposition
    val topSelectedEntries = remember(state.topPanel.files, state.topPanel.selectedPaths) {
        state.topPanel.files.filter { it.path in state.topPanel.selectedPaths }
    }
    val topSelectionHasProtected = remember(topSelectedEntries) { topSelectedEntries.any { it.isProtected } }
    val topSelectionDirCount = remember(topSelectedEntries) { topSelectedEntries.count { it.isDirectory } }

    val bottomSelectedEntries = remember(state.bottomPanel.files, state.bottomPanel.selectedPaths) {
        state.bottomPanel.files.filter { it.path in state.bottomPanel.selectedPaths }
    }
    val bottomSelectionHasProtected = remember(bottomSelectedEntries) { bottomSelectedEntries.any { it.isProtected } }
    val bottomSelectionDirCount = remember(bottomSelectedEntries) { bottomSelectedEntries.count { it.isDirectory } }

    val hasRenaming = state.topPanel.renamingPath != null || state.bottomPanel.renamingPath != null
    val hasSearch = state.topPanel.isSearchActive || state.bottomPanel.isSearchActive
    val showDrawerOrShelf = state.showDrawer || state.showShelf

    // Back: drawer/shelf → search → rename → selection → archive up → history back → navigate up → double-back exit
    val canGoBack = viewModel.canNavigateBack(activePanel)
    val canGoUp = viewModel.canNavigateUp(activePanel)
    val activePanelState = if (activePanel == PanelId.TOP) state.topPanel else state.bottomPanel
    BackHandler(enabled = true) {
        when {
            state.showDrawer -> viewModel.dismissDrawer()
            state.showShelf -> viewModel.dismissShelf()
            hasSearch -> {
                // Close search in the panel that has it active
                if (state.topPanel.isSearchActive) viewModel.closeSearch(PanelId.TOP)
                if (state.bottomPanel.isSearchActive) viewModel.closeSearch(PanelId.BOTTOM)
            }
            hasRenaming -> viewModel.cancelInlineRename()
            hasSelection -> selectionPanelId?.let { viewModel.clearSelection(it) }
            // Archive mode: use navigateUp (goes up one level or exits archive instantly)
            activePanelState.isArchiveMode -> viewModel.navigateUp(activePanel)
            canGoBack -> viewModel.navigateBack(activePanel)
            canGoUp -> viewModel.navigateUp(activePanel, pushHistory = false)
            // At root — do nothing (exit via custom navbar)
        }
    }

    var totalSizePx by remember { mutableFloatStateOf(0f) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Panel positions for drag target detection (Y in portrait, X in landscape)
    var topPanelStart by remember { mutableFloatStateOf(0f) }
    var topPanelEnd by remember { mutableFloatStateOf(0f) }
    var bottomPanelStart by remember { mutableFloatStateOf(0f) }
    var bottomPanelEnd by remember { mutableFloatStateOf(0f) }

    // Divider position for DnD copy/move strip
    var dividerStart by remember { mutableFloatStateOf(0f) }
    var dividerEnd by remember { mutableFloatStateOf(0f) }
    var dividerCrossStart by remember { mutableFloatStateOf(0f) }
    var dividerCrossEnd by remember { mutableFloatStateOf(0f) }

    val dragState = state.dragState
    val isDragging = dragState is DragState.Dragging

    // Determine which panel is drag target
    val dragTargetPanel: PanelId? = if (dragState is DragState.Dragging) {
        val coord = if (isLandscape) dragState.dragOffset.x else dragState.dragOffset.y
        when {
            coord in topPanelStart..topPanelEnd && dragState.sourcePanelId != PanelId.TOP -> PanelId.TOP
            coord in bottomPanelStart..bottomPanelEnd && dragState.sourcePanelId != PanelId.BOTTOM -> PanelId.BOTTOM
            else -> null
        }
    } else null

    val haptic = LocalHapticFeedback.current

    // Detect divider zone crossing for copy/move selection
    if (dragState is DragState.Dragging) {
        val primaryCoord = if (isLandscape) dragState.dragOffset.x else dragState.dragOffset.y
        val crossCoord = if (isLandscape) dragState.dragOffset.y else dragState.dragOffset.x
        val isOverDivider = primaryCoord in dividerStart..dividerEnd
        if (isOverDivider && dividerCrossEnd > dividerCrossStart) {
            val midpoint = (dividerCrossStart + dividerCrossEnd) / 2f
            val newOp = if (crossCoord < midpoint) DragOperation.COPY else DragOperation.MOVE
            if (newOp != dragState.dragOperation) {
                viewModel.updateDragOperation(newOp)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    // Track previous drag target for haptic
    var prevDragTarget by remember { mutableStateOf<PanelId?>(null) }
    if (dragTargetPanel != prevDragTarget) {
        if (dragTargetPanel != null) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        prevDragTarget = dragTargetPanel
    }

    // SAF state
    val hasRemovable = viewModel.hasRemovableStorage()
    val sdLabel = viewModel.getSdCardLabel()
    val hasSafPerm = viewModel.hasSafPermission()

    // Box overlay: panels fill all space, SelectionActionBar overlays at bottom
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { totalSizePx = if (isLandscape) it.width.toFloat() else it.height.toFloat() }
            .pointerInput(showDrawerOrShelf, isDragging, state.gestureMappings) {
                if (showDrawerOrShelf || isDragging) return@pointerInput
                val edgePx = 24.dp.toPx()
                val swipeThreshold = 60f
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startX = down.position.x
                    val startY = down.position.y
                    val width = size.width.toFloat()
                    val height = size.height.toFloat()
                    val isLeftEdge = startX < edgePx
                    val isRightEdge = startX > width - edgePx
                    if (!isLeftEdge && !isRightEdge) return@awaitEachGesture
                    var totalDragX = 0f
                    var consumed = false
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        totalDragX += change.position.x - change.previousPosition.x
                        if (!consumed) {
                            val isTopHalf = startY < height / 2
                            if (isLeftEdge && totalDragX > swipeThreshold) {
                                consumed = true
                                val gestureType = if (isTopHalf) GestureType.LEFT_EDGE_TOP else GestureType.LEFT_EDGE_BOTTOM
                                val action = state.gestureMappings[gestureType] ?: gestureType.defaultAction
                                viewModel.executeGestureAction(action)
                                change.consume()
                            } else if (isRightEdge && totalDragX < -swipeThreshold) {
                                consumed = true
                                val gestureType = if (isTopHalf) GestureType.RIGHT_EDGE_TOP else GestureType.RIGHT_EDGE_BOTTOM
                                val action = state.gestureMappings[gestureType] ?: gestureType.defaultAction
                                viewModel.executeGestureAction(action)
                                change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        // Shared panel parameters — modifiers are applied per-orientation inside Row/Column scope
        @Composable
        fun TopPanel(modifier: Modifier) {
            FilePanel(
                state = state.topPanel,
                isActive = activePanel == PanelId.TOP,
                canNavigateUp = viewModel.canNavigateUp(PanelId.TOP),
                isFavorite = state.topPanel.currentPath in state.favorites,
                onFileClick = { viewModel.onFileClick(PanelId.TOP, it) },
                onIconClick = { viewModel.onIconClick(PanelId.TOP, it) },
                onNavigateUp = { viewModel.navigateUp(PanelId.TOP) },
                onSortChanged = { viewModel.setSortOrder(PanelId.TOP, it) },
                onToggleHidden = { viewModel.toggleShowHidden(PanelId.TOP) },
                onSelectAll = { viewModel.selectAll(PanelId.TOP) },
                onSelectByExtension = { ext -> viewModel.selectByExtension(PanelId.TOP, ext) },
                onClearSelection = { viewModel.clearSelection(PanelId.TOP) },
                onPanelTap = { viewModel.setActivePanel(PanelId.TOP) },
                onSearchChanged = { viewModel.setSearchQuery(PanelId.TOP, it) },
                onClearSearch = { viewModel.clearSearch(PanelId.TOP) },
                onToggleFavorite = { viewModel.toggleFavorite(PanelId.TOP) },
                onShowDrawer = { viewModel.toggleDrawer() },
                onSelectRange = { from, to -> viewModel.selectRange(PanelId.TOP, from, to) },
                onLongPressItem = { viewModel.onFileLongClick(PanelId.TOP, it) },
                onRenameConfirm = { viewModel.confirmInlineRename(it) },
                onRenameCancel = { viewModel.cancelInlineRename() },
                onCreateNew = { viewModel.requestCreateFromTemplate() },
                onShowTrash = { viewModel.showTrash() },
                onBreadcrumbClick = { viewModel.onBreadcrumbClick(PanelId.TOP, it) },
                onNavigateBack = { viewModel.navigateBack(PanelId.TOP) },
                onNavigateForward = { viewModel.navigateForward(PanelId.TOP) },
                onExitApp = { (context as? android.app.Activity)?.finishAffinity() },
                canNavigateBack = viewModel.canNavigateBack(PanelId.TOP),
                canNavigateForward = viewModel.canNavigateForward(PanelId.TOP),
                onOpenInOtherPanel = { viewModel.openInOtherPanel(PanelId.TOP) },
                isOriginalFolder = state.topPanel.currentPath in state.originalFolders,
                onToggleOriginalFolder = { viewModel.toggleOriginalFolder(PanelId.TOP) },
                onCycleTheme = { viewModel.cycleTheme() },
                themeMode = state.themeMode,
                trashSizeInfo = state.trashSizeInfo,
                onGridColumnsChanged = { viewModel.setGridColumns(it) },
                onDragStarted = { paths, offset ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.startDrag(PanelId.TOP, paths, offset)
                },
                onDragMoved = { offset -> viewModel.updateDragPosition(offset) },
                onDragEnded = {
                    viewModel.endDrag(dragTargetPanel)
                },
                isDragTarget = dragTargetPanel == PanelId.TOP,
                externalDragOffset = if (isDragging) (dragState as DragState.Dragging).dragOffset else null,
                hoveredFolderPath = if (isDragging) (dragState as DragState.Dragging).hoveredFolderPath else null,
                onDragHoverFolder = { viewModel.setDragHoveredFolder(it) },
                hasRemovableStorage = hasRemovable,
                sdCardLabel = sdLabel,
                hasSafPermission = hasSafPerm,
                onSdCardClick = {
                    viewModel.setActivePanel(PanelId.TOP)
                    viewModel.navigateToSdCard()
                },
                onRequestSafAccess = {
                    viewModel.setActivePanel(PanelId.TOP)
                    safLauncher.launch(null)
                },
                safVolumeLabel = sdLabel,
                onOpenStorageAnalysis = { viewModel.openStorageAnalysis() },
                onOpenSearch = { viewModel.openSearch(PanelId.TOP) },
                onOpenGlobalSearch = { viewModel.openGlobalSearch() },
                onCloseSearch = { viewModel.closeSearch(PanelId.TOP) },
                onToggleSearchInContent = { viewModel.toggleSearchInContent(PanelId.TOP) },
                onScrollPositionChanged = { viewModel.onScrollPositionChanged(PanelId.TOP, it) },
                initialScrollIndex = viewModel.getScrollIndex(PanelId.TOP),
                fileTags = state.fileTags,
                tagDefinitions = state.tagDefinitions,
                activeTagFilter = state.activeTagFilter,
                onTagFilterChanged = { viewModel.setTagFilter(it) },
                onLongPressShield = { viewModel.showAllProtectedFiles() },
                onExitProtected = { viewModel.exitProtectedMode(PanelId.TOP) },
                onQuickSendStart = { path, name, offset ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.startQuickSend(path, name, offset, fromTopPanel = true)
                },
                onQuickSendDrag = { offset -> viewModel.updateQuickSendDrag(offset) },
                onQuickSendEnd = {
                    val qs = state.quickSendState
                    if (qs is QuickSendState.DraggingToDevice) {
                        viewModel.endQuickSendAtPosition(qs.dragOffset)
                    } else {
                        viewModel.cancelQuickSend()
                    }
                },
                isQuickSendActive = state.quickSendState !is QuickSendState.Idle,
                onProtectSelection = {
                    viewModel.setActivePanel(PanelId.TOP)
                    val paths = topSelectedEntries.map { it.path }
                    if (topSelectionHasProtected) viewModel.unprotectSelectedFiles(paths) else viewModel.protectSelectedFiles(paths)
                },
                onCompareSelection = {
                    viewModel.setActivePanel(PanelId.TOP)
                    viewModel.compareSelected()
                },
                onHideInFileSelection = {
                    viewModel.setActivePanel(PanelId.TOP)
                    viewModel.hideSelectedInFile()
                },
                onInfoSelection = {
                    viewModel.setActivePanel(PanelId.TOP)
                    viewModel.showSelectedFileProperties()
                },
                onOpenWithSelection = {
                    viewModel.setActivePanel(PanelId.TOP)
                    viewModel.openSelectedWithExternalApp()
                },
                selectionHasProtected = topSelectionHasProtected,
                selectionTotalCount = state.topPanel.selectedPaths.size,
                otherPanelSelectionCount = state.bottomPanel.selectedPaths.size,
                selectionDirCount = topSelectionDirCount,
                currentFolderSize = state.folderSizeCache[state.topPanel.currentPath],
                storageTotalSize = viewModel.getStorageTotalFor(state.topPanel.currentPath),
                marqueeEnabled = state.marqueeEnabled,
                folderSizeCache = state.folderSizeCache,
                cloudAuthHeader = viewModel.getCloudAuthHeader(state.topPanel.currentPath),
                archiveThumbnailCache = viewModel.archiveThumbnailCache,
                onSizeClick = { viewModel.showStorageSizeInfo(PanelId.TOP) },
                modifier = modifier
            )
        }

        @Composable
        fun Divider() {
            val topSizeStr = if (state.topPanel.isSelectionMode) {
                val (size, calculating) = viewModel.getSelectedTotalSizeForPanel(PanelId.TOP)
                if (calculating && size == 0L) "…" else size.toFileSize()
            } else ""
            val bottomSizeStr = if (state.bottomPanel.isSelectionMode) {
                val (size, calculating) = viewModel.getSelectedTotalSizeForPanel(PanelId.BOTTOM)
                if (calculating && size == 0L) "…" else size.toFileSize()
            } else ""

            PanelDivider(
                totalSize = totalSizePx,
                topFileCount = state.topPanel.files.size,
                bottomFileCount = state.bottomPanel.files.size,
                isTopActive = activePanel == PanelId.TOP,
                isLandscape = isLandscape,
                isDragging = isDragging,
                dragOperation = if (dragState is DragState.Dragging) dragState.dragOperation else DragOperation.MOVE,
                topSelectedSize = topSizeStr,
                bottomSelectedSize = bottomSizeStr,
                onDrag = { delta ->
                    viewModel.updatePanelRatio(state.panelRatio + delta)
                },
                onDragEnd = { viewModel.savePanelRatio() },
                onDoubleTap = { viewModel.resetPanelRatio() },
                onBookmarkTap = { viewModel.showBookmarkPopup() },
                onRightZoneTap = { viewModel.showToolsPopup() },
                modifier = Modifier.onGloballyPositioned { coords ->
                    val pos = coords.positionInRoot()
                    if (isLandscape) {
                        dividerStart = pos.x
                        dividerEnd = pos.x + coords.size.width
                        dividerCrossStart = pos.y
                        dividerCrossEnd = pos.y + coords.size.height
                    } else {
                        dividerStart = pos.y
                        dividerEnd = pos.y + coords.size.height
                        dividerCrossStart = pos.x
                        dividerCrossEnd = pos.x + coords.size.width
                    }
                }
            )
        }

        @Composable
        fun BottomPanel(modifier: Modifier) {
            FilePanel(
                state = state.bottomPanel,
                isActive = activePanel == PanelId.BOTTOM,
                canNavigateUp = viewModel.canNavigateUp(PanelId.BOTTOM),
                isFavorite = state.bottomPanel.currentPath in state.favorites,
                onFileClick = { viewModel.onFileClick(PanelId.BOTTOM, it) },
                onIconClick = { viewModel.onIconClick(PanelId.BOTTOM, it) },
                onNavigateUp = { viewModel.navigateUp(PanelId.BOTTOM) },
                onSortChanged = { viewModel.setSortOrder(PanelId.BOTTOM, it) },
                onToggleHidden = { viewModel.toggleShowHidden(PanelId.BOTTOM) },
                onSelectAll = { viewModel.selectAll(PanelId.BOTTOM) },
                onSelectByExtension = { ext -> viewModel.selectByExtension(PanelId.BOTTOM, ext) },
                onClearSelection = { viewModel.clearSelection(PanelId.BOTTOM) },
                onPanelTap = { viewModel.setActivePanel(PanelId.BOTTOM) },
                onSearchChanged = { viewModel.setSearchQuery(PanelId.BOTTOM, it) },
                onClearSearch = { viewModel.clearSearch(PanelId.BOTTOM) },
                onToggleFavorite = { viewModel.toggleFavorite(PanelId.BOTTOM) },
                onShowDrawer = { viewModel.toggleDrawer() },
                onSelectRange = { from, to -> viewModel.selectRange(PanelId.BOTTOM, from, to) },
                onLongPressItem = { viewModel.onFileLongClick(PanelId.BOTTOM, it) },
                onRenameConfirm = { viewModel.confirmInlineRename(it) },
                onRenameCancel = { viewModel.cancelInlineRename() },
                onCreateNew = { viewModel.requestCreateFromTemplate() },
                onShowTrash = { viewModel.showTrash() },
                onBreadcrumbClick = { viewModel.onBreadcrumbClick(PanelId.BOTTOM, it) },
                onNavigateBack = { viewModel.navigateBack(PanelId.BOTTOM) },
                onNavigateForward = { viewModel.navigateForward(PanelId.BOTTOM) },
                onExitApp = { (context as? android.app.Activity)?.finishAffinity() },
                canNavigateBack = viewModel.canNavigateBack(PanelId.BOTTOM),
                canNavigateForward = viewModel.canNavigateForward(PanelId.BOTTOM),
                onOpenInOtherPanel = { viewModel.openInOtherPanel(PanelId.BOTTOM) },
                isOriginalFolder = state.bottomPanel.currentPath in state.originalFolders,
                onToggleOriginalFolder = { viewModel.toggleOriginalFolder(PanelId.BOTTOM) },
                onCycleTheme = { viewModel.cycleTheme() },
                themeMode = state.themeMode,
                trashSizeInfo = state.trashSizeInfo,
                onGridColumnsChanged = { viewModel.setGridColumns(it) },
                onDragStarted = { paths, offset ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.startDrag(PanelId.BOTTOM, paths, offset)
                },
                onDragMoved = { offset -> viewModel.updateDragPosition(offset) },
                onDragEnded = {
                    viewModel.endDrag(dragTargetPanel)
                },
                isDragTarget = dragTargetPanel == PanelId.BOTTOM,
                externalDragOffset = if (isDragging) (dragState as DragState.Dragging).dragOffset else null,
                hoveredFolderPath = if (isDragging) (dragState as DragState.Dragging).hoveredFolderPath else null,
                onDragHoverFolder = { viewModel.setDragHoveredFolder(it) },
                hasRemovableStorage = hasRemovable,
                sdCardLabel = sdLabel,
                hasSafPermission = hasSafPerm,
                onSdCardClick = {
                    viewModel.setActivePanel(PanelId.BOTTOM)
                    viewModel.navigateToSdCard()
                },
                onRequestSafAccess = {
                    viewModel.setActivePanel(PanelId.BOTTOM)
                    safLauncher.launch(null)
                },
                safVolumeLabel = sdLabel,
                onOpenStorageAnalysis = { viewModel.openStorageAnalysis() },
                onOpenSearch = { viewModel.openSearch(PanelId.BOTTOM) },
                onOpenGlobalSearch = { viewModel.openGlobalSearch() },
                onCloseSearch = { viewModel.closeSearch(PanelId.BOTTOM) },
                onToggleSearchInContent = { viewModel.toggleSearchInContent(PanelId.BOTTOM) },
                onScrollPositionChanged = { viewModel.onScrollPositionChanged(PanelId.BOTTOM, it) },
                initialScrollIndex = viewModel.getScrollIndex(PanelId.BOTTOM),
                fileTags = state.fileTags,
                tagDefinitions = state.tagDefinitions,
                activeTagFilter = state.activeTagFilter,
                onTagFilterChanged = { viewModel.setTagFilter(it) },
                onLongPressShield = { viewModel.showAllProtectedFiles() },
                onExitProtected = { viewModel.exitProtectedMode(PanelId.BOTTOM) },
                onQuickSendStart = { path, name, offset ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.startQuickSend(path, name, offset, fromTopPanel = false)
                },
                onQuickSendDrag = { offset -> viewModel.updateQuickSendDrag(offset) },
                onQuickSendEnd = {
                    val qs = state.quickSendState
                    if (qs is QuickSendState.DraggingToDevice) {
                        viewModel.endQuickSendAtPosition(qs.dragOffset)
                    } else {
                        viewModel.cancelQuickSend()
                    }
                },
                isQuickSendActive = state.quickSendState !is QuickSendState.Idle,
                onProtectSelection = {
                    viewModel.setActivePanel(PanelId.BOTTOM)
                    val paths = bottomSelectedEntries.map { it.path }
                    if (bottomSelectionHasProtected) viewModel.unprotectSelectedFiles(paths) else viewModel.protectSelectedFiles(paths)
                },
                onCompareSelection = {
                    viewModel.setActivePanel(PanelId.BOTTOM)
                    viewModel.compareSelected()
                },
                onHideInFileSelection = {
                    viewModel.setActivePanel(PanelId.BOTTOM)
                    viewModel.hideSelectedInFile()
                },
                onInfoSelection = {
                    viewModel.setActivePanel(PanelId.BOTTOM)
                    viewModel.showSelectedFileProperties()
                },
                onOpenWithSelection = {
                    viewModel.setActivePanel(PanelId.BOTTOM)
                    viewModel.openSelectedWithExternalApp()
                },
                selectionHasProtected = bottomSelectionHasProtected,
                selectionTotalCount = state.bottomPanel.selectedPaths.size,
                otherPanelSelectionCount = state.topPanel.selectedPaths.size,
                selectionDirCount = bottomSelectionDirCount,
                currentFolderSize = state.folderSizeCache[state.bottomPanel.currentPath],
                storageTotalSize = viewModel.getStorageTotalFor(state.bottomPanel.currentPath),
                marqueeEnabled = state.marqueeEnabled,
                folderSizeCache = state.folderSizeCache,
                hasSelectionBar = hasSelection && !isDragging,
                cloudAuthHeader = viewModel.getCloudAuthHeader(state.bottomPanel.currentPath),
                archiveThumbnailCache = viewModel.archiveThumbnailCache,
                onSizeClick = { viewModel.showStorageSizeInfo(PanelId.BOTTOM) },
                modifier = modifier
            )
        }

        if (isLandscape) {
            Row(modifier = Modifier.fillMaxSize().padding(bottom = 48.dp)) {
                TopPanel(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(state.panelRatio)
                        .onGloballyPositioned { coords ->
                            val pos = coords.positionInRoot()
                            topPanelStart = pos.x
                            topPanelEnd = pos.x + coords.size.width
                        }
                )
                Divider()
                BottomPanel(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f - state.panelRatio)
                        .onGloballyPositioned { coords ->
                            val pos = coords.positionInRoot()
                            bottomPanelStart = pos.x
                            bottomPanelEnd = pos.x + coords.size.width
                        }
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(bottom = 48.dp)) {
                TopPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(state.panelRatio)
                        .onGloballyPositioned { coords ->
                            val pos = coords.positionInRoot()
                            topPanelStart = pos.y
                            topPanelEnd = pos.y + coords.size.height
                        }
                )
                Divider()
                BottomPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f - state.panelRatio)
                        .onGloballyPositioned { coords ->
                            val pos = coords.positionInRoot()
                            bottomPanelStart = pos.y
                            bottomPanelEnd = pos.y + coords.size.height
                        }
                )
            }
        }

        // Selection action bar — overlay, no layout shift
        if (hasSelection && !isDragging) {
            val (totalSizeWithFolders, isSizeCalculating) = viewModel.getSelectedTotalSizeWithFolders()
            SelectionActionBar(
                dirCount = selectedDirs,
                fileCount = selectedFiles,
                totalSize = totalSizeWithFolders,
                isSizeCalculating = isSizeCalculating,
                onCopy = {
                    selectionPanelId?.let { viewModel.setActivePanel(it) }
                    viewModel.copySelectedToOtherPanel()
                },
                onMove = {
                    selectionPanelId?.let { viewModel.setActivePanel(it) }
                    viewModel.moveSelectedToOtherPanel()
                },
                onDelete = {
                    selectionPanelId?.let { viewModel.setActivePanel(it) }
                    viewModel.requestDeleteSelected()
                },
                onRename = {
                    selectionPanelId?.let { viewModel.setActivePanel(it) }
                    if (selectedDirs + selectedFiles == 1) {
                        viewModel.requestRename()
                    } else {
                        viewModel.requestBatchRename()
                    }
                },
                onZip = {
                    selectionPanelId?.let { viewModel.setActivePanel(it) }
                    viewModel.requestCreateArchive()
                },
                onAddToShelf = {
                    selectionPanelId?.let { viewModel.setActivePanel(it) }
                    viewModel.addToShelf()
                },
                onTag = {
                    selectionPanelId?.let { viewModel.setActivePanel(it) }
                    viewModel.requestTagAssign()
                },
                onSend = {
                    selectionPanelId?.let { viewModel.setActivePanel(it) }
                    viewModel.onSendSelected(selectedEntries.map { it.path })
                },
                onSendLongClick = {
                    selectionPanelId?.let { viewModel.setActivePanel(it) }
                    viewModel.onSendToTransfer(selectedEntries.map { it.path })
                },
                onCopyLongClick = {
                    selectionPanelId?.let { viewModel.setActivePanel(it) }
                    viewModel.showDuplicateDialog()
                },
                onCast = {
                    selectionPanelId?.let { viewModel.setActivePanel(it) }
                    viewModel.castSelected()
                },
                isArchiveMode = selectionPanelState.isArchiveMode,
                allSelectedAreArchives = allSelectedAreArchives,
                onExtract = {
                    selectionPanelId?.let { viewModel.extractFromArchive(it, selectedOnly = true) }
                },
                onExtractAll = {
                    selectionPanelId?.let { viewModel.extractFromArchive(it, selectedOnly = false) }
                },
                onExtractArchives = {
                    selectionPanelId?.let { viewModel.showExtractArchivesDialog(it) }
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)
            )
        }

        // Archive extract progress bar
        val archiveProgress = state.topPanel.archiveExtractProgress ?: state.bottomPanel.archiveExtractProgress
        if (archiveProgress != null && !archiveProgress.isComplete) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (hasSelection && !isDragging) 112.dp else 56.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.extract_progress_format, archiveProgress.current, archiveProgress.total, archiveProgress.fileName),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val archFraction = if (archiveProgress.total > 0) archiveProgress.current.toFloat() / archiveProgress.total else 0f
                    LinearProgressIndicator(
                        progress = { archFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                    ProgressInfoRow(
                        counter = if (archiveProgress.total > 0) "${archiveProgress.current}/${archiveProgress.total}" else "",
                        percent = if (archiveProgress.total > 0) "${(archFraction * 100).toInt()}%" else ""
                    )
                }
            }
        }

        // File operation progress bar (supports multiple concurrent operations)
        // Auto-collapsed by default, user can expand manually
        val progressList = state.operationProgressList.ifEmpty {
            listOfNotNull(state.operationProgress)
        }
        val primaryProgress = progressList.firstOrNull()
        val secondaryList = progressList.drop(1)
        var progressExpanded by remember { mutableStateOf(false) }
        // Auto-collapse when new operation starts
        LaunchedEffect(primaryProgress?.id) { progressExpanded = false }
        AnimatedVisibility(
            visible = primaryProgress != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (hasSelection && !isDragging) 112.dp else 56.dp)
        ) {
            primaryProgress?.let { p ->
                val hasFilePercent = p.filePercent in 0..100
                val isWaitingForBytes = p.filePercent == 0 && p.speedBytesPerSec == 0L && !p.isComplete
                val isIndeterminate = isWaitingForBytes || (p.current == 0 && p.total > 0 && !hasFilePercent)
                val barProgress = if (hasFilePercent) {
                    p.filePercent / 100f
                } else {
                    if (p.total > 0) p.current.toFloat() / p.total else 0f
                }
                val speedText = if (p.speedBytesPerSec > 0) "${p.speedBytesPerSec.toFileSize()}/s" else ""
                val counterText = if (p.total > 1) "${p.current}/${p.total}" else ""
                val percentText = if (!isIndeterminate && (hasFilePercent || p.total > 0)) "${(barProgress * 100).toInt()}%" else ""

                if (!progressExpanded) {
                    // --- Collapsed: thin line with progress info ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f))
                            .clickable { progressExpanded = true }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Thin progress indicator
                        if (isIndeterminate) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = { barProgress },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                            )
                        }
                        if (speedText.isNotEmpty()) {
                            Text(
                                text = speedText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        if (counterText.isNotEmpty()) {
                            Text(
                                text = counterText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                        if (percentText.isNotEmpty()) {
                            Text(
                                text = percentText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                } else {
                    // --- Expanded: full progress bar ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            // Secondary operations — compact lines above primary
                            secondaryList.forEach { s ->
                                val sTypeLabel = stringResource(s.type.labelRes)
                                val sName = if (s.currentFileName.isNotEmpty()) "$sTypeLabel: ${s.currentFileName}" else sTypeLabel
                                val sCounter = if (s.total > 1) "(${s.current}/${s.total})" else ""
                                val sPercent = if (s.filePercent in 0..100) "${s.filePercent}%" else ""
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = sName,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (sCounter.isNotEmpty()) {
                                        Text(
                                            text = sCounter,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 6.dp)
                                        )
                                    }
                                    if (sPercent.isNotEmpty()) {
                                        Text(
                                            text = sPercent,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(start = 6.dp)
                                        )
                                    }
                                }
                            }
                            // Primary operation — full details
                            val typeLabel = stringResource(p.type.labelRes)
                            val nameText = when {
                                p.currentFileName.isNotEmpty() -> "$typeLabel: ${p.currentFileName}"
                                p.current == 0 && p.currentFileName.isEmpty() ->
                                    stringResource(R.string.file_operation_progress_format, typeLabel, p.total)
                                else -> typeLabel
                            }
                            Text(
                                text = nameText,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            if (isIndeterminate) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                            } else {
                                LinearProgressIndicator(
                                    progress = { barProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                            }
                            ProgressInfoRow(
                                speed = speedText,
                                counter = counterText,
                                percent = percentText
                            )
                        }
                        Column {
                            // Collapse button
                            IconButton(
                                onClick = { progressExpanded = false },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Filled.ExpandMore,
                                    contentDescription = stringResource(R.string.collapse),
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // Cancel button
                            IconButton(
                                onClick = { viewModel.cancelFileOperation() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.cancel),
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Left edge swipe detection moved to parent Box modifier

        // Voice FAB moved to global overlay in MainActivity

        // Drag overlay — ghost icon following finger
        if (dragState is DragState.Dragging) {
            DragOverlay(
                previewName = dragState.previewName,
                fileCount = dragState.fileCount,
                offset = dragState.dragOffset,
                dragOperation = dragState.dragOperation
            )
        }

        // Quick Send overlay — device circles / drop target / sending indicator
        if (state.quickSendState !is QuickSendState.Idle) {
            QuickSendOverlay(state = state.quickSendState)
        }

        // Quick Receive progress overlay
        val receiveProgress = state.quickReceiveProgress
        if (receiveProgress != null) {
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                QuickReceiveOverlay(
                    progress = receiveProgress,
                    deviceName = state.quickReceiveDeviceName
                )
            }
        }

        // Custom navbar (replaces system navbar)
        val matrixPrefsForNavbar = remember { com.vamp.haron.data.datastore.HaronPreferences(context) }
        CustomNavbar(
            config = navbarConfig,
            shiftModeActive = activePanelState.shiftMode,
            transparentBackground = matrixPrefsForNavbar.matrixEnabled,
            onAction = { action ->
                val panelId = state.activePanel
                when (action) {
                    com.vamp.haron.domain.model.NavbarAction.BACK -> {
                        when {
                            state.showDrawer -> viewModel.dismissDrawer()
                            state.showShelf -> viewModel.dismissShelf()
                            hasSearch -> {
                                if (state.topPanel.isSearchActive) viewModel.closeSearch(PanelId.TOP)
                                if (state.bottomPanel.isSearchActive) viewModel.closeSearch(PanelId.BOTTOM)
                            }
                            hasRenaming -> viewModel.cancelInlineRename()
                            hasSelection -> selectionPanelId?.let { viewModel.clearSelection(it) }
                            activePanelState.isArchiveMode -> viewModel.navigateUp(panelId)
                            viewModel.canNavigateBack(panelId) -> viewModel.navigateBack(panelId)
                            viewModel.canNavigateUp(panelId) -> viewModel.navigateUp(panelId, pushHistory = false)
                        }
                    }
                    com.vamp.haron.domain.model.NavbarAction.EXIT -> (context as? android.app.Activity)?.finishAffinity()
                    com.vamp.haron.domain.model.NavbarAction.FORWARD -> viewModel.navigateForward(panelId)
                    com.vamp.haron.domain.model.NavbarAction.UP -> viewModel.navigateUp(panelId)
                    com.vamp.haron.domain.model.NavbarAction.HOME -> viewModel.navigateTo(panelId, com.vamp.haron.common.constants.HaronConstants.ROOT_PATH)
                    com.vamp.haron.domain.model.NavbarAction.REFRESH -> viewModel.refreshPanel(panelId)
                    com.vamp.haron.domain.model.NavbarAction.SEARCH -> viewModel.openGlobalSearch()
                    com.vamp.haron.domain.model.NavbarAction.SETTINGS -> viewModel.openSettings()
                    com.vamp.haron.domain.model.NavbarAction.TERMINAL -> viewModel.openTerminal()
                    com.vamp.haron.domain.model.NavbarAction.TRANSFER -> viewModel.openTransfer()
                    com.vamp.haron.domain.model.NavbarAction.TRASH -> viewModel.showTrash()
                    com.vamp.haron.domain.model.NavbarAction.STORAGE -> viewModel.openStorageAnalysis()
                    com.vamp.haron.domain.model.NavbarAction.APPS -> viewModel.openAppManager()
                    com.vamp.haron.domain.model.NavbarAction.DUPLICATES -> viewModel.openDuplicateDetector()
                    com.vamp.haron.domain.model.NavbarAction.SELECT_ALL -> viewModel.selectAll(panelId)
                    com.vamp.haron.domain.model.NavbarAction.TOGGLE_HIDDEN -> viewModel.toggleShowHidden(panelId)
                    com.vamp.haron.domain.model.NavbarAction.CREATE_NEW -> viewModel.requestCreateFolder()
                    com.vamp.haron.domain.model.NavbarAction.COPY -> viewModel.copySelectedToOtherPanel()
                    com.vamp.haron.domain.model.NavbarAction.MOVE -> viewModel.moveSelectedToOtherPanel()
                    com.vamp.haron.domain.model.NavbarAction.DELETE -> viewModel.requestDeleteSelected()
                    com.vamp.haron.domain.model.NavbarAction.RENAME -> viewModel.requestRename()
                    com.vamp.haron.domain.model.NavbarAction.LIBRARY -> onNavigateToLibrary()
                    com.vamp.haron.domain.model.NavbarAction.SCANNER -> {} // TODO: open scanner
                    com.vamp.haron.domain.model.NavbarAction.COPY_MOVE -> viewModel.copySelectedToOtherPanel() // default tap = copy
                    com.vamp.haron.domain.model.NavbarAction.DELETE_MENU -> viewModel.requestDeleteSelected() // default tap = trash
                    com.vamp.haron.domain.model.NavbarAction.CREATE_MENU -> viewModel.requestCreateFromTemplate() // default tap = create
                    com.vamp.haron.domain.model.NavbarAction.FORCE_DELETE -> viewModel.requestForceDelete()
                    com.vamp.haron.domain.model.NavbarAction.CREATE_FILE -> viewModel.requestCreateFromTemplate()
                    com.vamp.haron.domain.model.NavbarAction.ARROW_UP -> viewModel.moveFocusUp(panelId)
                    com.vamp.haron.domain.model.NavbarAction.ARROW_DOWN -> viewModel.moveFocusDown(panelId)
                    com.vamp.haron.domain.model.NavbarAction.ARROW_LEFT -> viewModel.navigateBack(panelId)
                    com.vamp.haron.domain.model.NavbarAction.ARROW_RIGHT -> viewModel.navigateForward(panelId)
                    com.vamp.haron.domain.model.NavbarAction.ENTER_FOLDER -> {
                        val focused = viewModel.getFocusedFile(panelId)
                        if (focused != null && focused.isDirectory) {
                            viewModel.navigateTo(panelId, focused.path)
                        }
                    }
                    com.vamp.haron.domain.model.NavbarAction.CURSOR_LEFT -> viewModel.moveFocusLeft(panelId)
                    com.vamp.haron.domain.model.NavbarAction.CURSOR_RIGHT -> viewModel.moveFocusRight(panelId)
                    com.vamp.haron.domain.model.NavbarAction.TOGGLE_SHIFT -> viewModel.toggleShiftMode(panelId)
                    com.vamp.haron.domain.model.NavbarAction.SWITCH_PANEL -> {
                        val other = if (panelId == com.vamp.haron.domain.model.PanelId.TOP)
                            com.vamp.haron.domain.model.PanelId.BOTTOM else com.vamp.haron.domain.model.PanelId.TOP
                        viewModel.setActivePanel(other)
                    }
                    else -> {}
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

    }

    ExplorerDialogs(
        state = state,
        viewModel = viewModel,
        context = context,
        onOpenMediaPlayer = onOpenMediaPlayer,
        onOpenTextEditor = onOpenTextEditor,
        onOpenGallery = onOpenGallery,
        onOpenPdfReader = onOpenPdfReader,
        onOpenDocumentViewer = onOpenDocumentViewer,
        onCastModeSelected = onCastModeSelected
    )

    // Rename overlay — floats above keyboard
    val renamingPath = state.topPanel.renamingPath ?: state.bottomPanel.renamingPath
    if (renamingPath != null) {
        val renamingName = java.io.File(renamingPath).name
        RenameOverlay(
            currentName = renamingName,
            onConfirm = { viewModel.confirmInlineRename(it) },
            onCancel = { viewModel.cancelInlineRename() }
        )
    }

    ExplorerOverlays(
        state = state,
        viewModel = viewModel,
        context = context,
        showDrawerOrShelf = showDrawerOrShelf,
        safLauncher = safLauncher,
        cloudAccounts = cloudAccounts,
        showCloudAuthDialog = showCloudAuthDialog,
        onShowCloudAuthDialogChange = { showCloudAuthDialog = it },
        onOpenTvRemote = { castVmForBt?.connectForRemote() },
        onOpenBtRemote = { castVmForBt?.connectForBluetoothRemote() },
        onNavigateToLibrary = onNavigateToLibrary
    )
}

@Composable
private fun RenameOverlay(
    currentName: String,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit
) {
    val dotIndex = currentName.lastIndexOf('.')
    val selectionEnd = if (dotIndex > 0) dotIndex else currentName.length
    var textFieldValue by remember(currentName) {
        mutableStateOf(
            androidx.compose.ui.text.input.TextFieldValue(
                text = currentName,
                selection = androidx.compose.ui.text.TextRange(0, selectionEnd)
            )
        )
    }
    val focusRequester = remember { FocusRequester() }
    var confirmed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCancel() },
        contentAlignment = Alignment.BottomCenter
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(bottom = 30.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* consume tap on surface */ },
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = false,
                    maxLines = 3,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            confirmed = true
                            val newName = textFieldValue.text.trim()
                            if (newName.isNotBlank() && newName != currentName) {
                                onConfirm(newName)
                            } else {
                                onCancel()
                            }
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .drawBehind {
                            drawLine(
                                color = androidx.compose.ui.graphics.Color(0xFF6200EE),
                                start = Offset(0f, size.height),
                                end = Offset(size.width, size.height),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                )
            }
        }
    }
}

@Composable
private fun ExplorerDialogs(
    state: com.vamp.haron.presentation.explorer.state.ExplorerUiState,
    viewModel: ExplorerViewModel,
    context: Context,
    onOpenMediaPlayer: (Int) -> Unit,
    onOpenTextEditor: (String, String) -> Unit,
    onOpenGallery: (Int) -> Unit,
    onOpenPdfReader: (String, String) -> Unit,
    onOpenDocumentViewer: (String, String) -> Unit,
    onCastModeSelected: (com.vamp.haron.domain.model.CastMode, List<String>) -> Unit
) {
    when (val dialog = state.dialogState) {
        is DialogState.ConfirmDelete -> {
            DeleteConfirmDialog(
                count = dialog.paths.size,
                onConfirm = { viewModel.confirmDelete(dialog.paths) },
                onDismiss = viewModel::dismissDialog
            )
        }
        is DialogState.CreateFromTemplate -> {
            CreateFromTemplateDialog(
                onConfirm = viewModel::confirmCreateFromTemplate,
                onDismiss = viewModel::dismissDialog,
                allowedTemplates = dialog.allowedTemplates
            )
        }
        is DialogState.ShowTrash -> {
            TrashDialog(
                entries = dialog.entries,
                totalSize = dialog.totalSize,
                maxSizeMb = dialog.maxSizeMb,
                deleteProgress = dialog.deleteProgress,
                deleteCurrentName = dialog.deleteCurrentName,
                onRestore = viewModel::restoreFromTrash,
                onDeletePermanently = viewModel::deleteFromTrashPermanently,
                onEmptyTrash = viewModel::emptyTrash,
                onDismiss = viewModel::dismissDialog
            )
        }
        is DialogState.ConfirmConflict -> {
            ConflictComparisonCard(
                pair = dialog.conflictPairs[dialog.currentIndex],
                currentIndex = dialog.currentIndex,
                totalCount = dialog.conflictPairs.size,
                onResolve = { resolution, applyToAll ->
                    viewModel.resolveCurrentConflict(resolution, applyToAll)
                },
                onDismiss = viewModel::dismissDialog
            )
        }
        is DialogState.QuickPreview -> {
            QuickPreviewDialog(
                entry = dialog.entry,
                previewData = dialog.previewData,
                isLoading = dialog.isLoading,
                error = dialog.error,
                onDismiss = viewModel::dismissDialog,
                onFullscreenPlay = { _ ->
                    viewModel.dismissDialog()
                    if (dialog.entry.isProtected) {
                        viewModel.onProtectedFileClick(dialog.entry)
                    } else if (viewModel.isCloudPath(dialog.entry.path)) {
                        viewModel.cloudStreamAndPlay(dialog.entry)
                    } else {
                        val idx = viewModel.buildPlaylistFromPreview(dialog.entry, dialog.adjacentFiles, dialog.currentFileIndex)
                        onOpenMediaPlayer(idx)
                    }
                },
                onEdit = {
                    viewModel.dismissDialog()
                    if (dialog.entry.isProtected) {
                        viewModel.onProtectedFileClick(dialog.entry)
                    } else if (viewModel.isCloudPath(dialog.entry.path)) {
                        viewModel.cloudDownloadAndOpenText(dialog.entry)
                    } else {
                        onOpenTextEditor(dialog.entry.path, dialog.entry.name)
                    }
                },
                onOpenGallery = if (dialog.entry.path.startsWith("ext4://")) null else ({
                    viewModel.dismissDialog()
                    if (dialog.entry.isProtected) {
                        viewModel.onProtectedFileClick(dialog.entry)
                    } else if (viewModel.isCloudPath(dialog.entry.path)) {
                        viewModel.openCloudGallery(dialog.entry, dialog.adjacentFiles, dialog.currentFileIndex)
                    } else {
                        val idx = viewModel.buildGalleryFromPreview(dialog.entry, dialog.adjacentFiles, dialog.currentFileIndex)
                        onOpenGallery(idx)
                    }
                }),
                onOpenPdf = {
                    viewModel.dismissDialog()
                    if (dialog.entry.isProtected) {
                        viewModel.onProtectedFileClick(dialog.entry)
                    } else {
                        val pdfPath = dialog.resolvedPath
                            ?: (dialog.previewData as? PreviewData.PdfPreview)?.filePath
                                ?.takeIf { it.isNotEmpty() }
                            ?: dialog.entry.path
                        onOpenPdfReader(pdfPath, dialog.entry.name)
                    }
                },
                onOpenDocument = {
                    viewModel.dismissDialog()
                    if (dialog.entry.isProtected) {
                        viewModel.onProtectedFileClick(dialog.entry)
                    } else {
                        val docPath = dialog.resolvedPath ?: dialog.entry.path
                        onOpenDocumentViewer(docPath, dialog.entry.name)
                    }
                },
                onOpenArchive = {
                    viewModel.dismissDialog()
                    if (dialog.entry.isProtected) {
                        viewModel.onProtectedFileClick(dialog.entry)
                    } else {
                        val archivePath = dialog.resolvedPath ?: dialog.entry.path
                        viewModel.navigateIntoArchive(state.activePanel, archivePath, "", null)
                    }
                },
                onInstallApk = {
                    viewModel.installApk(dialog.entry)
                },
                onDelete = {
                    viewModel.deleteFromPreview()
                },
                adjacentFiles = dialog.adjacentFiles,
                currentFileIndex = dialog.currentFileIndex,
                onFileChanged = { newIndex ->
                    viewModel.onPreviewFileChanged(newIndex)
                },
                previewCache = dialog.previewCache
            )
        }
        is DialogState.CreateArchive -> {
            CreateArchiveDialog(
                onConfirm = { archiveName, password, splitSizeMb ->
                    viewModel.confirmCreateArchive(dialog.selectedPaths, archiveName, password, splitSizeMb)
                },
                onDismiss = viewModel::dismissDialog,
                onOneToOne = if (dialog.selectedPaths.size >= 2) {
                    { viewModel.createArchiveOneToOne(dialog.selectedPaths) }
                } else null
            )
        }
        is DialogState.FilePropertiesState -> {
            FilePropertiesDialog(
                properties = dialog.properties,
                hashResult = dialog.hashResult,
                isHashCalculating = dialog.isHashCalculating,
                coverResult = dialog.coverResult,
                onCalculateHash = { viewModel.calculateHash() },
                onCopyHash = { hash ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("hash", hash))
                    Toast.makeText(context, context.getString(R.string.hash_copied), Toast.LENGTH_SHORT).show()
                },
                onRemoveExif = { viewModel.removeExif() },
                onFetchCover = { query -> viewModel.fetchAlbumCover(query) },
                onSaveAll = { tags -> viewModel.saveAllAudioData(tags) },
                onDismiss = viewModel::dismissDialog,
                isContentUri = dialog.entry.isContentUri
            )
        }
        is DialogState.ApkInstallDialog -> {
            ApkInstallDialog(
                apkInfo = dialog.apkInfo,
                isLoading = dialog.isLoading,
                error = dialog.error,
                onInstall = { viewModel.installApk(dialog.entry) },
                onDismiss = viewModel::dismissDialog
            )
        }
        is DialogState.ApkDowngradeConfirm -> {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
                title = { Text(stringResource(R.string.apk_downgrade_title)) },
                text = {
                    Text(stringResource(
                        R.string.apk_downgrade_message,
                        dialog.installedVersionName,
                        dialog.installedVersionCode,
                        dialog.apkVersionName,
                        dialog.apkVersionCode
                    ))
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.uninstallAndInstall(dialog) }) {
                        Text(stringResource(R.string.apk_downgrade_uninstall_and_install))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissDialog) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
        is DialogState.EmptyFolderCleanup -> {
            EmptyFolderCleanupDialog(
                folders = dialog.folders,
                isRecursive = dialog.isRecursive,
                selectedPaths = dialog.selectedPaths,
                onToggleRecursive = { viewModel.toggleEmptyFoldersRecursive(it) },
                onToggleSelected = { viewModel.toggleEmptyFolderSelected(it) },
                onSelectAll = { viewModel.selectAllEmptyFolders() },
                onDelete = { viewModel.deleteEmptyFolders() },
                onDismiss = viewModel::dismissDialog
            )
        }
        is DialogState.ForceDeleteConfirm -> {
            ForceDeleteConfirmDialog(
                names = dialog.names,
                onConfirm = { viewModel.confirmForceDelete(dialog.paths) },
                onDismiss = viewModel::dismissDialog
            )
        }
        is DialogState.BatchRename -> {
            BatchRenameDialog(
                entries = dialog.entries,
                recentPatterns = viewModel.getRenamePatterns(),
                onConfirm = { renames -> viewModel.confirmBatchRename(renames) },
                onSavePattern = { viewModel.saveBatchRenamePattern(it) },
                onDismiss = viewModel::dismissDialog
            )
        }
        is DialogState.TagAssign -> {
            TagAssignDialog(
                paths = dialog.paths,
                tagDefinitions = state.tagDefinitions,
                fileTags = state.fileTags,
                onConfirm = { paths, tagNames -> viewModel.confirmTagAssign(paths, tagNames) },
                onManageTags = { viewModel.showTagManager() },
                onDismiss = viewModel::dismissDialog
            )
        }
        is DialogState.TagManage -> {
            TagManageDialog(
                tagDefinitions = state.tagDefinitions,
                onAddTag = { name, colorIndex -> viewModel.addTag(name, colorIndex) },
                onEditTag = { oldName, newName, colorIndex -> viewModel.editTag(oldName, newName, colorIndex) },
                onDeleteTag = { viewModel.deleteTag(it) },
                onDismiss = viewModel::dismissTagManager
            )
        }
        is DialogState.CastModeSelect -> {
            com.vamp.haron.presentation.cast.components.CastModeSheet(
                availableModes = dialog.availableModes,
                onModeSelected = { mode ->
                    viewModel.dismissDialog()
                    onCastModeSelected(mode, dialog.filePaths)
                },
                onDismiss = viewModel::dismissDialog
            )
        }
        is DialogState.ArchivePassword -> {
            com.vamp.haron.presentation.common.PasswordDialog(
                fileName = java.io.File(dialog.archivePath).name,
                errorMessage = dialog.errorMessage,
                onConfirm = { password ->
                    viewModel.onArchivePasswordSubmit(dialog.panelId, dialog.archivePath, password)
                },
                onDismiss = viewModel::dismissDialog
            )
        }
        is DialogState.ArchiveExtractConflict -> {
            AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
                title = { Text(stringResource(R.string.extract_conflict_title)) },
                text = {
                    Text(stringResource(R.string.extract_conflict_message, dialog.conflictNames.size))
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.confirmArchiveExtract(
                            dialog.archivePanelId,
                            dialog.destinationDir,
                            dialog.selectedOnly
                        )
                    }) {
                        Text(stringResource(R.string.replace_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissDialog) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
        is DialogState.ArchiveCreateConflict -> {
            AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
                title = { Text(stringResource(R.string.archive_file_exists_title)) },
                text = {
                    Text(stringResource(R.string.archive_file_exists_message, dialog.archiveName))
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmArchiveCreateReplace(dialog) }) {
                        Text(stringResource(R.string.archive_replace))
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = viewModel::dismissDialog) {
                            Text(stringResource(R.string.cancel))
                        }
                        TextButton(onClick = { viewModel.confirmArchiveCreateRename(dialog) }) {
                            Text(stringResource(R.string.archive_rename))
                        }
                    }
                }
            )
        }
        is DialogState.ArchiveExtractOptions -> {
            ExtractOptionsDialog(
                archiveName = dialog.archiveName,
                hasSingleRootFolder = dialog.hasSingleRootFolder,
                onExtractHere = { viewModel.confirmExtractHere(dialog) },
                onExtractToFolder = { viewModel.confirmExtractToFolder(dialog) },
                onDismiss = viewModel::dismissDialog
            )
        }
        is DialogState.TrashOverflow -> {
            AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
                title = { Text(stringResource(R.string.trash_overflow_title)) },
                text = {
                    Text(stringResource(
                        R.string.trash_overflow_message,
                        dialog.incomingSize.toFileSize(),
                        dialog.maxSize.toFileSize()
                    ))
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmTrashOverflowDelete(dialog.paths) }) {
                        Text(stringResource(R.string.delete_permanently))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissDialog) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
        is DialogState.ShizukuNotInstalled -> {
            com.vamp.haron.presentation.explorer.components.ShizukuNotInstalledDialog(
                onDismiss = viewModel::dismissShizukuDialog,
                onOpenPlayStore = {
                    viewModel.dismissShizukuDialog()
                    com.vamp.haron.presentation.explorer.components.openShizukuPlayStore(context)
                },
                onOpenGitHub = {
                    viewModel.dismissShizukuDialog()
                    com.vamp.haron.presentation.explorer.components.openShizukuGitHub(context)
                }
            )
        }
        is DialogState.ShizukuNotRunning -> {
            com.vamp.haron.presentation.explorer.components.ShizukuNotRunningDialog(
                onDismiss = viewModel::dismissShizukuDialog,
                onOpenApp = {
                    viewModel.dismissShizukuDialog()
                    com.vamp.haron.presentation.explorer.components.openShizukuApp(context)
                },
                onRetry = {
                    viewModel.onShizukuReady(dialog.panelId, dialog.path)
                }
            )
        }
        is DialogState.CloudTransfer -> {
            if (dialog.transfers.isNotEmpty()) {
                com.vamp.haron.presentation.cloud.CloudTransferDialog(
                    transfers = dialog.transfers.map {
                        com.vamp.haron.presentation.cloud.CloudTransferItem(it.id, it.fileName, it.percent, it.isUpload, it.bytesTransferred, it.totalBytes, it.speedBytesPerSec)
                    },
                    onCancel = { transferId -> viewModel.cancelSingleCloudTransfer(transferId) },
                    onCancelAll = { viewModel.cancelCloudTransfer() }
                )
            }
        }
        is DialogState.CloudCreateFolder -> {
            com.vamp.haron.presentation.cloud.CloudCreateFolderDialog(
                onConfirm = { name -> viewModel.cloudCreateFolder(name) },
                onDismiss = { viewModel.dismissDialog() }
            )
        }
        is DialogState.DuplicateDialog -> {
            DuplicateDialog(
                paths = dialog.paths,
                onDismiss = viewModel::dismissDialog,
                onConfirm = { count, destination ->
                    viewModel.executeDuplicate(count, destination)
                }
            )
        }
        is DialogState.ExtractArchivesDialog -> {
            ExtractArchivesDialog(
                archiveCount = dialog.archivePaths.size,
                onDismiss = viewModel::dismissDialog,
                onConfirm = { destination ->
                    viewModel.executeExtractArchives(destination)
                }
            )
        }
        DialogState.None -> { /* no dialog */ }
    }
}

@Composable
private fun ExtractArchivesDialog(
    archiveCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (com.vamp.haron.presentation.explorer.state.ExtractDestination) -> Unit
) {
    var destination by remember { mutableStateOf(com.vamp.haron.presentation.explorer.state.ExtractDestination.NEXT_TO_ARCHIVE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.extract_destination_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                com.vamp.haron.presentation.explorer.state.ExtractDestination.entries.forEach { dest ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { destination = dest }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = destination == dest,
                            onClick = { destination = dest }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = when (dest) {
                                com.vamp.haron.presentation.explorer.state.ExtractDestination.NEXT_TO_ARCHIVE ->
                                    stringResource(R.string.extract_next_to_archive)
                                com.vamp.haron.presentation.explorer.state.ExtractDestination.SAME_PANEL ->
                                    stringResource(R.string.extract_to_current_panel)
                                com.vamp.haron.presentation.explorer.state.ExtractDestination.OTHER_PANEL ->
                                    stringResource(R.string.extract_to_other_panel)
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(destination) }) {
                Text(stringResource(R.string.extract_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun DuplicateDialog(
    paths: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (count: Int, destination: com.vamp.haron.presentation.explorer.state.DuplicateDestination) -> Unit
) {
    var countText by remember { mutableStateOf("1") }
    var destination by remember { mutableStateOf(com.vamp.haron.presentation.explorer.state.DuplicateDestination.SAME_SUBFOLDER) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.duplicate_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = countText,
                    onValueChange = { newVal -> countText = newVal.filter { it.isDigit() }.take(4) },
                    label = { Text(stringResource(R.string.duplicate_count)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                com.vamp.haron.presentation.explorer.state.DuplicateDestination.entries.forEach { dest ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { destination = dest }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = destination == dest,
                            onClick = { destination = dest }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = when (dest) {
                                com.vamp.haron.presentation.explorer.state.DuplicateDestination.SAME_SUBFOLDER ->
                                    stringResource(R.string.duplicate_to_subfolder_here)
                                com.vamp.haron.presentation.explorer.state.DuplicateDestination.OTHER_PANEL_SUBFOLDER ->
                                    stringResource(R.string.duplicate_to_other_subfolder)
                                com.vamp.haron.presentation.explorer.state.DuplicateDestination.OTHER_PANEL_DIRECT ->
                                    stringResource(R.string.duplicate_to_other_direct)
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val count = countText.toIntOrNull() ?: 1
                    if (count > 0) onConfirm(count, destination)
                }
            ) {
                Text(stringResource(R.string.duplicate_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ExplorerOverlays(
    state: com.vamp.haron.presentation.explorer.state.ExplorerUiState,
    viewModel: ExplorerViewModel,
    context: Context,
    showDrawerOrShelf: Boolean,
    safLauncher: androidx.activity.result.ActivityResultLauncher<android.net.Uri?>,
    cloudAccounts: List<com.vamp.haron.domain.model.CloudAccount>,
    showCloudAuthDialog: Boolean,
    onShowCloudAuthDialogChange: (Boolean) -> Unit,
    onOpenTvRemote: () -> Unit = {},
    onOpenBtRemote: () -> Unit = {},
    onNavigateToLibrary: () -> Unit = {}
) {
    // Shield auth overlay
    if (state.showShieldAuth) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            LockScreen(
                lockMethod = viewModel.getShieldLockMethod(),
                onPinVerified = { pin ->
                    val ok = viewModel.verifyShieldPin(pin)
                    if (ok) viewModel.onShieldAuthenticated()
                    ok
                },
                onBiometricRequest = {
                    val activity = context as? FragmentActivity ?: return@LockScreen
                    val executor = ContextCompat.getMainExecutor(activity)
                    val callback = object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            viewModel.onShieldAuthenticated()
                        }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { /* stay */ }
                        override fun onAuthenticationFailed() { /* retry */ }
                    }
                    val prompt = BiometricPrompt(activity, executor, callback)
                    val shieldMethod = viewModel.getShieldLockMethod()
                    val negText = if (shieldMethod == com.vamp.haron.domain.model.AppLockMethod.BIOMETRIC_ONLY) {
                        context.getString(com.vamp.haron.R.string.cancel)
                    } else {
                        context.getString(com.vamp.haron.R.string.biometric_use_pin)
                    }
                    val info = BiometricPrompt.PromptInfo.Builder()
                        .setTitle(context.getString(com.vamp.haron.R.string.biometric_prompt_title))
                        .setSubtitle(context.getString(com.vamp.haron.R.string.biometric_prompt_subtitle))
                        .setNegativeButtonText(negText)
                        .build()
                    prompt.authenticate(info)
                },
                hasBiometric = viewModel.hasShieldBiometric(),
                pinLength = viewModel.getShieldPinLength(),
                onDismiss = { viewModel.dismissShieldAuth() }
            )
        }
    }

    // Shield PIN setup dialog (first-time protection)
    if (state.showShieldPinSetup) {
        PinSetupDialog(
            isChange = false,
            onConfirm = { currentPin, newPin, question, answer ->
                viewModel.onShieldPinSetupConfirm(currentPin, newPin, question, answer)
            },
            onDismiss = { viewModel.dismissShieldPinSetup() }
        )
    }

    // Bookmark popup
    if (state.showBookmarkPopup) {
        BookmarkPopup(
            bookmarks = state.bookmarks,
            onNavigate = { viewModel.navigateToBookmark(it) },
            onSave = { viewModel.saveBookmark(it) },
            onDismiss = { viewModel.dismissBookmarkPopup() }
        )
    }

    // Tools popup
    if (state.showToolsPopup) {
        ToolsPopup(
            onToolSelected = { viewModel.onToolSelected(it) },
            onDismiss = { viewModel.dismissToolsPopup() }
        )
    }

    // Drawer / Shelf overlay
    if (showDrawerOrShelf) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        viewModel.dismissDrawer()
                        viewModel.dismissShelf()
                    }
            )

            // Drawer
            AnimatedVisibility(
                visible = state.showDrawer,
                enter = slideInHorizontally(initialOffsetX = { -it }),
                exit = slideOutHorizontally(targetOffsetX = { -it }),
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                DrawerMenu(
                    favorites = state.favorites,
                    recentPaths = state.recentPaths,
                    safRoots = state.safRoots,
                    themeMode = state.themeMode,
                    trashSizeInfo = state.trashSizeInfo,
                    onNavigate = viewModel::navigateFromDrawer,
                    onRemoveFavorite = viewModel::removeFavorite,
                    onNavigateToInternalStorage = {
                        viewModel.dismissDrawer()
                        viewModel.navigateTo(
                            state.activePanel,
                            com.vamp.haron.common.constants.HaronConstants.ROOT_PATH
                        )
                    },
                    onGrantVolumeAccess = {
                        viewModel.dismissDrawer()
                        safLauncher.launch(null)
                    },
                    onRevokeVolumeAccess = { uri ->
                        viewModel.removeSafRoot(uri)
                    },
                    onShowTrash = { viewModel.showTrash() },
                    onOpenStorageAnalysis = { viewModel.openStorageAnalysis() },
                    onOpenDuplicateDetector = { viewModel.openDuplicateDetector() },
                    onOpenAppManager = { viewModel.openAppManager() },
                    onFindEmptyFolders = { viewModel.findEmptyFolders() },
                    onForceDelete = { viewModel.requestForceDelete() },
                    onManageTags = { viewModel.showTagManager() },
                    onOpenTransfer = { viewModel.openTransfer() },
                    onOpenTerminal = { viewModel.openTerminal() },
                    onOpenTvRemote = onOpenTvRemote,
                    onOpenBtRemote = onOpenBtRemote,
                    cloudAccounts = cloudAccounts,
                    onOpenCloudAuth = { viewModel.openCloudAuth() },
                    onNavigateToCloud = { accountId ->
                        viewModel.dismissDrawer()
                        viewModel.navigateToCloud(accountId)
                    },
                    isListeningForTransfer = state.isListeningForTransfer,
                    usbVolumes = state.usbVolumes,
                    onNavigateUsb = { path ->
                        viewModel.dismissDrawer()
                        viewModel.navigateTo(state.activePanel, path)
                    },
                    onEjectUsb = { path -> viewModel.ejectUsb(path) },
                    networkDevices = state.networkDevices,
                    onNetworkDeviceTap = { device -> viewModel.onNetworkDeviceTap(device) },

                    onRefreshNetwork = { viewModel.refreshNetwork() },
                    onOpenLibrary = {
                        viewModel.dismissDrawer()
                        onNavigateToLibrary()
                    },
                    onOpenSettings = { viewModel.openSettings() },
                    onOpenFeatures = { viewModel.openFeatures() },
                    onOpenAbout = { viewModel.openAbout() },
                    onOpenSupport = { viewModel.openSupport() },
                    onSetTheme = { viewModel.setTheme(it) },
                    onDismiss = viewModel::dismissDrawer
                )
            }

            // Shelf
            AnimatedVisibility(
                visible = state.showShelf,
                enter = slideInHorizontally(initialOffsetX = { -it }),
                exit = slideOutHorizontally(targetOffsetX = { -it }),
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                ShelfPanel(
                    items = state.shelfItems,
                    onRemoveItem = viewModel::removeFromShelf,
                    onPasteCopy = { viewModel.pasteFromShelf(isMove = false) },
                    onPasteMove = { viewModel.pasteFromShelf(isMove = true) },
                    onClear = viewModel::clearShelf,
                    onDismiss = viewModel::dismissShelf
                )
            }
        }

        // Cloud Auth Dialog
        if (showCloudAuthDialog) {
            com.vamp.haron.presentation.cloud.CloudAuthDialog(
                connectedAccounts = cloudAccounts,
                onSignIn = { provider ->
                    val url = viewModel.getCloudAuthUrl(provider)
                    if (url != null) {
                        val intent = androidx.browser.customtabs.CustomTabsIntent.Builder().build()
                        intent.launchUrl(context, android.net.Uri.parse(url))
                    }
                },
                onSignOut = { accountId ->
                    viewModel.cloudSignOut(accountId)
                },
                onNavigateToCloud = { accountId ->
                    onShowCloudAuthDialogChange(false)
                    viewModel.navigateToCloud(accountId)
                },
                onDismiss = { onShowCloudAuthDialogChange(false) }
            )
        }
    }
}
