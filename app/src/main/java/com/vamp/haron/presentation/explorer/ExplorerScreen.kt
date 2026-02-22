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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.vamp.haron.presentation.explorer.components.QuickPreviewDialog
import com.vamp.haron.presentation.explorer.components.SelectionActionBar
import com.vamp.haron.presentation.explorer.components.ShelfPanel
import com.vamp.haron.presentation.explorer.components.TrashDialog
import com.vamp.haron.domain.model.NavigationEvent
import com.vamp.haron.presentation.explorer.state.DragState
import com.vamp.haron.presentation.explorer.state.DialogState

@Composable
fun ExplorerScreen(
    viewModel: ExplorerViewModel = hiltViewModel(),
    onOpenMediaPlayer: (startIndex: Int) -> Unit = { },
    onOpenTextEditor: (filePath: String, fileName: String) -> Unit = { _, _ -> },
    onOpenGallery: (startIndex: Int) -> Unit = { },
    onOpenPdfReader: (filePath: String, fileName: String) -> Unit = { _, _ -> },
    onOpenArchiveViewer: (filePath: String, fileName: String) -> Unit = { _, _ -> },
    onOpenStorageAnalysis: () -> Unit = { },
    onOpenDuplicateDetector: () -> Unit = { }
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
            }
        }
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
    val showDrawerOrShelf = state.showDrawer || state.showShelf

    // Back: drawer/shelf → rename → selection → history back → navigate up
    val canGoBack = viewModel.canNavigateBack(activePanel)
    BackHandler(enabled = state.showDrawer || state.showShelf || hasRenaming || hasSelection || canGoBack || viewModel.canNavigateUp(activePanel)) {
        when {
            state.showDrawer -> viewModel.dismissDrawer()
            state.showShelf -> viewModel.dismissShelf()
            hasRenaming -> viewModel.cancelInlineRename()
            hasSelection -> viewModel.clearSelection(activePanel)
            canGoBack -> viewModel.navigateBack(activePanel)
            else -> viewModel.navigateUp(activePanel, pushHistory = false)
        }
    }

    var totalHeightPx by remember { mutableFloatStateOf(0f) }

    // Panel Y positions for drag target detection
    var topPanelTopY by remember { mutableFloatStateOf(0f) }
    var topPanelBottomY by remember { mutableFloatStateOf(0f) }
    var bottomPanelTopY by remember { mutableFloatStateOf(0f) }
    var bottomPanelBottomY by remember { mutableFloatStateOf(0f) }

    val dragState = state.dragState
    val isDragging = dragState is DragState.Dragging

    // Determine which panel is drag target
    val dragTargetPanel: PanelId? = if (dragState is DragState.Dragging) {
        val y = dragState.dragOffset.y
        when {
            y in topPanelTopY..topPanelBottomY && dragState.sourcePanelId != PanelId.TOP -> PanelId.TOP
            y in bottomPanelTopY..bottomPanelBottomY && dragState.sourcePanelId != PanelId.BOTTOM -> PanelId.BOTTOM
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
            .onSizeChanged { totalHeightPx = it.height.toFloat() }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top panel
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
                onBreadcrumbClick = { viewModel.navigateTo(PanelId.TOP, it) },
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
                onDragEnded = { viewModel.endDrag(dragTargetPanel) },
                isDragTarget = dragTargetPanel == PanelId.TOP,
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
                onScrollPositionChanged = { viewModel.onScrollPositionChanged(PanelId.TOP, it) },
                initialScrollIndex = viewModel.getScrollIndex(PanelId.TOP),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(state.panelRatio)
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInRoot()
                        topPanelTopY = pos.y
                        topPanelBottomY = pos.y + coords.size.height
                    }
            )

            // Divider
            PanelDivider(
                totalHeight = totalHeightPx,
                onDrag = { delta ->
                    viewModel.updatePanelRatio(state.panelRatio + delta)
                },
                onDragEnd = { viewModel.savePanelRatio() },
                onDoubleTap = { viewModel.resetPanelRatio() }
            )

            // Bottom panel
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
                onBreadcrumbClick = { viewModel.navigateTo(PanelId.BOTTOM, it) },
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
                onDragEnded = { viewModel.endDrag(dragTargetPanel) },
                isDragTarget = dragTargetPanel == PanelId.BOTTOM,
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
                onScrollPositionChanged = { viewModel.onScrollPositionChanged(PanelId.BOTTOM, it) },
                initialScrollIndex = viewModel.getScrollIndex(PanelId.BOTTOM),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f - state.panelRatio)
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInRoot()
                        bottomPanelTopY = pos.y
                        bottomPanelBottomY = pos.y + coords.size.height
                    }
            )
        }

        // Selection action bar — overlay, no layout shift
        if (hasSelection && !isDragging) {
            SelectionActionBar(
                dirCount = selectedDirs,
                fileCount = selectedFiles,
                totalSize = viewModel.getSelectedTotalSize(),
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
                    viewModel.requestRename()
                },
                onZip = {
                    selectionPanelId?.let { viewModel.setActivePanel(it) }
                    viewModel.requestCreateArchive()
                },
                onAddToShelf = {
                    selectionPanelId?.let { viewModel.setActivePanel(it) }
                    viewModel.addToShelf()
                },
                onInfo = {
                    selectionPanelId?.let { viewModel.setActivePanel(it) }
                    viewModel.showSelectedFileProperties()
                },
                onOpenWith = {
                    selectionPanelId?.let { viewModel.setActivePanel(it) }
                    viewModel.openSelectedWithExternalApp()
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
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
                        val progressText = when {
                            p.current == 0 && p.currentFileName.isEmpty() ->
                                "${p.type.label}: ${p.total} файл(ов)…"
                            p.currentFileName.isNotEmpty() ->
                                "${p.type.label}: ${p.current}/${p.total} — ${p.currentFileName}"
                            else ->
                                "${p.type.label}: ${p.current}/${p.total}"
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
                            contentDescription = "Отмена",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Left edge swipe zone
        if (!showDrawerOrShelf && !isDragging) {
            // Swipe detection zone on left edge
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(24.dp)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val startY = down.position.y
                            val threshold = 60f // px
                            var totalDragX = 0f
                            var consumed = false
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                totalDragX += change.position.x - (change.previousPosition.x)
                                if (!consumed && totalDragX > threshold) {
                                    consumed = true
                                    val height = size.height.toFloat()
                                    if (startY < height / 2) {
                                        viewModel.toggleShelf()
                                    } else {
                                        viewModel.toggleDrawer()
                                    }
                                    change.consume()
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
            )
        }

        // Drag overlay — ghost icon following finger
        if (dragState is DragState.Dragging) {
            DragOverlay(
                previewName = dragState.previewName,
                fileCount = dragState.fileCount,
                offset = dragState.dragOffset
            )
        }
    }

    // Dialogs
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
                onDismiss = viewModel::dismissDialog
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
                    val idx = viewModel.buildPlaylistFromPreview(dialog.entry, dialog.adjacentFiles, dialog.currentFileIndex)
                    onOpenMediaPlayer(idx)
                },
                onEdit = {
                    viewModel.dismissDialog()
                    onOpenTextEditor(dialog.entry.path, dialog.entry.name)
                },
                onOpenGallery = {
                    viewModel.dismissDialog()
                    val idx = viewModel.buildGalleryFromPreview(dialog.entry, dialog.adjacentFiles, dialog.currentFileIndex)
                    onOpenGallery(idx)
                },
                onOpenPdf = {
                    viewModel.dismissDialog()
                    onOpenPdfReader(dialog.entry.path, dialog.entry.name)
                },
                onOpenArchive = {
                    viewModel.dismissDialog()
                    onOpenArchiveViewer(dialog.entry.path, dialog.entry.name)
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
                onConfirm = { archiveName ->
                    viewModel.confirmCreateArchive(dialog.selectedPaths, archiveName)
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
                    Toast.makeText(context, "Хеш скопирован", Toast.LENGTH_SHORT).show()
                },
                onRemoveExif = { viewModel.removeExif() },
                onDismiss = viewModel::dismissDialog,
                isContentUri = dialog.entry.isContentUri
            )
        }
        DialogState.None -> { /* no dialog */ }
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
                    onCycleTheme = { viewModel.cycleTheme() },
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
