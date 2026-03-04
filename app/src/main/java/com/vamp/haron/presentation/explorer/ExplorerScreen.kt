package com.vamp.haron.presentation.explorer

import android.widget.Toast
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.vamp.haron.presentation.explorer.components.BatchRenameDialog
import com.vamp.haron.presentation.explorer.components.ForceDeleteConfirmDialog
import com.vamp.haron.presentation.explorer.components.QuickPreviewDialog
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
import com.vamp.haron.presentation.explorer.state.DragState
import com.vamp.haron.presentation.explorer.state.DialogState
import com.vamp.haron.presentation.applock.LockScreen
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
    onOpenGlobalSearch: () -> Unit = { },
    onOpenTransfer: () -> Unit = { },
    onOpenTerminal: () -> Unit = { },
    onOpenComparison: () -> Unit = { },
    onOpenSteganography: () -> Unit = { },
    onOpenScanner: () -> Unit = { },
    onOpenDocumentViewer: (filePath: String, fileName: String) -> Unit = { _, _ -> },
    onCastModeSelected: (com.vamp.haron.domain.model.CastMode, List<String>) -> Unit = { _, _ -> }
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
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

    // Execute pending voice/gesture action after returning from another screen
    val pendingVoiceAction by com.vamp.haron.domain.model.TransferHolder.pendingVoiceAction.collectAsState()
    LaunchedEffect(pendingVoiceAction) {
        val action = pendingVoiceAction ?: return@LaunchedEffect
        com.vamp.haron.domain.model.TransferHolder.pendingVoiceAction.value = null
        viewModel.executeGestureAction(action)
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

    // SAF document tree picker
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
    val hasRenaming = state.topPanel.renamingPath != null || state.bottomPanel.renamingPath != null
    val hasSearch = state.topPanel.isSearchActive || state.bottomPanel.isSearchActive
    val showDrawerOrShelf = state.showDrawer || state.showShelf

    // Back: drawer/shelf → search → rename → selection → history back → navigate up → no-op at root
    val canGoBack = viewModel.canNavigateBack(activePanel)
    val canGoUp = viewModel.canNavigateUp(activePanel)
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
            canGoBack -> viewModel.navigateBack(activePanel)
            canGoUp -> viewModel.navigateUp(activePanel, pushHistory = false)
            // At root or virtual root — consume back press, do nothing
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
                    viewModel.startQuickSend(path, name, offset)
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
                    val paths = state.topPanel.files.filter { it.path in state.topPanel.selectedPaths }.map { it.path }
                    val hasProtected = state.topPanel.files.filter { it.path in state.topPanel.selectedPaths }.any { it.isProtected }
                    if (hasProtected) viewModel.unprotectSelectedFiles(paths) else viewModel.protectSelectedFiles(paths)
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
                selectionHasProtected = state.topPanel.files.filter { it.path in state.topPanel.selectedPaths }.any { it.isProtected },
                selectionTotalCount = state.topPanel.selectedPaths.size,
                otherPanelSelectionCount = state.bottomPanel.selectedPaths.size,
                selectionDirCount = state.topPanel.files.filter { it.path in state.topPanel.selectedPaths }.count { it.isDirectory },
                currentFolderSize = state.folderSizeCache[state.topPanel.currentPath],
                marqueeEnabled = state.marqueeEnabled,
                folderSizeCache = state.folderSizeCache,
                modifier = modifier
            )
        }

        @Composable
        fun Divider() {
            PanelDivider(
                totalSize = totalSizePx,
                topFileCount = state.topPanel.files.size,
                bottomFileCount = state.bottomPanel.files.size,
                isTopActive = activePanel == PanelId.TOP,
                isLandscape = isLandscape,
                onDrag = { delta ->
                    viewModel.updatePanelRatio(state.panelRatio + delta)
                },
                onDragEnd = { viewModel.savePanelRatio() },
                onDoubleTap = { viewModel.resetPanelRatio() },
                onBookmarkTap = { viewModel.showBookmarkPopup() },
                onRightZoneTap = { viewModel.showToolsPopup() }
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
                    viewModel.startQuickSend(path, name, offset)
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
                    val paths = state.bottomPanel.files.filter { it.path in state.bottomPanel.selectedPaths }.map { it.path }
                    val hasProtected = state.bottomPanel.files.filter { it.path in state.bottomPanel.selectedPaths }.any { it.isProtected }
                    if (hasProtected) viewModel.unprotectSelectedFiles(paths) else viewModel.protectSelectedFiles(paths)
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
                selectionHasProtected = state.bottomPanel.files.filter { it.path in state.bottomPanel.selectedPaths }.any { it.isProtected },
                selectionTotalCount = state.bottomPanel.selectedPaths.size,
                otherPanelSelectionCount = state.topPanel.selectedPaths.size,
                selectionDirCount = state.bottomPanel.files.filter { it.path in state.bottomPanel.selectedPaths }.count { it.isDirectory },
                currentFolderSize = state.folderSizeCache[state.bottomPanel.currentPath],
                marqueeEnabled = state.marqueeEnabled,
                folderSizeCache = state.folderSizeCache,
                modifier = modifier
            )
        }

        if (isLandscape) {
            Row(modifier = Modifier.fillMaxSize()) {
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
            Column(modifier = Modifier.fillMaxSize()) {
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
                onCast = {
                    selectionPanelId?.let { viewModel.setActivePanel(it) }
                    viewModel.castSelected()
                },
                isArchiveMode = selectionPanelState.isArchiveMode,
                onExtract = {
                    selectionPanelId?.let { viewModel.extractFromArchive(it, selectedOnly = true) }
                },
                onExtractAll = {
                    selectionPanelId?.let { viewModel.extractFromArchive(it, selectedOnly = false) }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // Archive extract progress bar
        val archiveProgress = state.topPanel.archiveExtractProgress ?: state.bottomPanel.archiveExtractProgress
        if (archiveProgress != null && !archiveProgress.isComplete) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (hasSelection && !isDragging) 64.dp else 8.dp)
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
                    LinearProgressIndicator(
                        progress = { if (archiveProgress.total > 0) archiveProgress.current.toFloat() / archiveProgress.total else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            }
        }

        // File operation progress bar
        val progress = state.operationProgress
        AnimatedVisibility(
            visible = progress != null && !progress.isComplete,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (hasSelection && !isDragging) 64.dp else 8.dp)
        ) {
            progress?.let { p ->
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
                        val typeLabel = stringResource(p.type.labelRes)
                        val progressText = when {
                            p.current == 0 && p.currentFileName.isEmpty() ->
                                stringResource(R.string.file_operation_progress_format, typeLabel, p.total)
                            p.currentFileName.isNotEmpty() ->
                                stringResource(R.string.file_operation_current_format, typeLabel, p.current, p.total, p.currentFileName)
                            else ->
                                stringResource(R.string.file_operation_count_format, typeLabel, p.current, p.total)
                        }
                        Text(
                            text = progressText,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val isIndeterminate = p.current == 0 && p.total > 0
                        if (isIndeterminate) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = { if (p.total > 0) p.current.toFloat() / p.total else 0f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { viewModel.cancelFileOperation() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cancel),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                offset = dragState.dragOffset
            )
        }

        // Quick Send overlay — device circles / drop target / sending indicator
        if (state.quickSendState !is QuickSendState.Idle) {
            QuickSendOverlay(state = state.quickSendState)
        }
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

    ExplorerOverlays(
        state = state,
        viewModel = viewModel,
        context = context,
        showDrawerOrShelf = showDrawerOrShelf,
        safLauncher = safLauncher
    )
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
                    } else {
                        val idx = viewModel.buildPlaylistFromPreview(dialog.entry, dialog.adjacentFiles, dialog.currentFileIndex)
                        onOpenMediaPlayer(idx)
                    }
                },
                onEdit = {
                    viewModel.dismissDialog()
                    if (dialog.entry.isProtected) {
                        viewModel.onProtectedFileClick(dialog.entry)
                    } else {
                        onOpenTextEditor(dialog.entry.path, dialog.entry.name)
                    }
                },
                onOpenGallery = {
                    viewModel.dismissDialog()
                    if (dialog.entry.isProtected) {
                        viewModel.onProtectedFileClick(dialog.entry)
                    } else {
                        val idx = viewModel.buildGalleryFromPreview(dialog.entry, dialog.adjacentFiles, dialog.currentFileIndex)
                        onOpenGallery(idx)
                    }
                },
                onOpenPdf = {
                    viewModel.dismissDialog()
                    if (dialog.entry.isProtected) {
                        viewModel.onProtectedFileClick(dialog.entry)
                    } else {
                        onOpenPdfReader(dialog.entry.path, dialog.entry.name)
                    }
                },
                onOpenDocument = {
                    viewModel.dismissDialog()
                    if (dialog.entry.isProtected) {
                        viewModel.onProtectedFileClick(dialog.entry)
                    } else {
                        onOpenDocumentViewer(dialog.entry.path, dialog.entry.name)
                    }
                },
                onOpenArchive = {
                    viewModel.dismissDialog()
                    if (dialog.entry.isProtected) {
                        viewModel.onProtectedFileClick(dialog.entry)
                    } else {
                        viewModel.navigateIntoArchive(state.activePanel, dialog.entry.path, "", null)
                    }
                },
                onInstallApk = {
                    viewModel.installApk(dialog.entry)
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
                onDismiss = viewModel::dismissDialog
            )
        }
        is DialogState.FilePropertiesState -> {
            FilePropertiesDialog(
                properties = dialog.properties,
                hashResult = dialog.hashResult,
                isHashCalculating = dialog.isHashCalculating,
                onCalculateHash = { viewModel.calculateHash() },
                onCopyHash = { hash ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("hash", hash))
                    Toast.makeText(context, context.getString(R.string.hash_copied), Toast.LENGTH_SHORT).show()
                },
                onRemoveExif = { viewModel.removeExif() },
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
                onDismiss = viewModel::dismissDialog
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
        DialogState.None -> { /* no dialog */ }
    }
}

@Composable
private fun ExplorerOverlays(
    state: com.vamp.haron.presentation.explorer.state.ExplorerUiState,
    viewModel: ExplorerViewModel,
    context: Context,
    showDrawerOrShelf: Boolean,
    safLauncher: androidx.activity.result.ActivityResultLauncher<android.net.Uri?>
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
                    val info = BiometricPrompt.PromptInfo.Builder()
                        .setTitle(context.getString(com.vamp.haron.R.string.biometric_prompt_title))
                        .setSubtitle(context.getString(com.vamp.haron.R.string.biometric_prompt_subtitle))
                        .setNegativeButtonText(context.getString(com.vamp.haron.R.string.biometric_use_pin))
                        .build()
                    prompt.authenticate(info)
                },
                hasBiometric = viewModel.hasShieldBiometric(),
                pinLength = viewModel.getShieldPinLength(),
                onDismiss = { viewModel.dismissShieldAuth() }
            )
        }
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
                    onOpenSettings = { viewModel.openSettings() },
                    onOpenFeatures = { viewModel.openFeatures() },
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
    }
}
