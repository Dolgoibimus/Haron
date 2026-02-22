package com.vamp.haron.presentation.explorer.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vamp.haron.data.model.SortOrder
import com.vamp.haron.domain.model.FileEntry
import com.vamp.haron.presentation.explorer.state.PanelUiState
import com.vamp.haron.service.PlaybackService
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun FilePanel(
    state: PanelUiState,
    isActive: Boolean,
    canNavigateUp: Boolean = false,
    isFavorite: Boolean,
    onFileClick: (FileEntry) -> Unit,
    onIconClick: (FileEntry) -> Unit,
    onNavigateUp: () -> Unit = {},
    onSortChanged: (SortOrder) -> Unit,
    onToggleHidden: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onPanelTap: () -> Unit,
    onSearchChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShowDrawer: () -> Unit,
    onSelectRange: (fromIndex: Int, toIndex: Int) -> Unit,
    onLongPressItem: (FileEntry) -> Unit,
    onRenameConfirm: (String) -> Unit,
    onRenameCancel: () -> Unit,
    onCreateNew: () -> Unit,
    onShowTrash: () -> Unit = {},
    onBreadcrumbClick: (String) -> Unit = {},
    onNavigateBack: () -> Unit = {},
    onNavigateForward: () -> Unit = {},
    canNavigateBack: Boolean = false,
    canNavigateForward: Boolean = false,
    onOpenInOtherPanel: () -> Unit = {},
    isOriginalFolder: Boolean = false,
    onToggleOriginalFolder: () -> Unit = {},
    onCycleTheme: () -> Unit = {},
    themeMode: String = "system",
    trashSizeInfo: String = "",
    onGridColumnsChanged: (Int) -> Unit = {},
    onDragStarted: ((List<String>, Offset) -> Unit)? = null,
    onDragMoved: ((Offset) -> Unit)? = null,
    onDragEnded: (() -> Unit)? = null,
    isDragTarget: Boolean = false,
    hasRemovableStorage: Boolean = false,
    sdCardLabel: String = "SD-карта",
    hasSafPermission: Boolean = false,
    onSdCardClick: () -> Unit = {},
    onRequestSafAccess: () -> Unit = {},
    safVolumeLabel: String = "",
    onOpenStorageAnalysis: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onCloseSearch: () -> Unit = {},
    onScrollPositionChanged: (Int) -> Unit = {},
    initialScrollIndex: Int = 0,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        isDragTarget -> MaterialTheme.colorScheme.tertiary
        isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    }
    val borderWidth = if (isDragTarget) 3.dp else if (isActive) 2.dp else 1.dp

    var showOverflow by remember { mutableStateOf(false) }
    val showSearch = state.isSearchActive

    // Playback service state polling
    var serviceRunning by remember { mutableStateOf(false) }
    var isServicePlaying by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (isActive) {
            val adapter = PlaybackService.instance?.getAdapter()
            serviceRunning = adapter != null
            isServicePlaying = adapter?.isCurrentlyPlaying() == true
            delay(500)
        }
    }

    // Search focus
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(showSearch) {
        if (showSearch) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    // Filter files by search query
    val filteredFiles = if (state.searchQuery.isBlank()) {
        state.files
    } else {
        state.files.filter {
            it.name.contains(state.searchQuery, ignoreCase = true)
        }
    }

    val containerColor = if (isActive) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    val isGridMode = state.gridColumns >= 2

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .border(
                width = borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(4.dp)
            )
    ) {
        // Compact header (36dp)
        Surface(
            color = containerColor,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .padding(start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Navigation icons
                when {
                    showSearch -> {
                        IconButton(
                            onClick = { onCloseSearch() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Закрыть поиск",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    state.isSelectionMode -> {
                        IconButton(
                            onClick = {
                                onPanelTap()
                                onClearSelection()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Отменить выделение",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    else -> {
                        // Кнопка назад по истории
                        IconButton(
                            onClick = {
                                onPanelTap()
                                onNavigateBack()
                            },
                            enabled = canNavigateBack,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Назад",
                                modifier = Modifier.size(18.dp),
                                tint = if (canNavigateBack) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                }
                            )
                        }
                        // Кнопка вперёд по истории
                        IconButton(
                            onClick = {
                                onPanelTap()
                                onNavigateForward()
                            },
                            enabled = canNavigateForward,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Вперёд",
                                modifier = Modifier.size(18.dp),
                                tint = if (canNavigateForward) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.width(4.dp))

                // Title / search field
                when {
                    showSearch -> {
                        val searchTextStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        BasicTextField(
                            value = state.searchQuery,
                            onValueChange = onSearchChanged,
                            textStyle = searchTextStyle,
                            singleLine = true,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (state.searchQuery.isEmpty()) {
                                        Text(
                                            "Поиск...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(searchFocusRequester)
                        )
                    }
                    state.statusMessage != null -> {
                        Text(
                            text = state.statusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onPanelTap() }
                        )
                    }
                    state.isSelectionMode -> {
                        val selected = state.files.filter { it.path in state.selectedPaths }
                        val dirs = selected.count { it.isDirectory }
                        val files = selected.size - dirs
                        Text(
                            text = "Выбрано: ${formatFileCount(dirs, files)}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onPanelTap() }
                        )
                    }
                    else -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onPanelTap() }
                        ) {
                            if (state.isSafPath) {
                                Icon(
                                    Icons.Filled.SdCard,
                                    contentDescription = "SAF",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(Modifier.width(3.dp))
                            }
                            Text(
                                text = state.displayPath.substringAfterLast('/').ifEmpty { "Хранилище" },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Actions — hide when status message is shown to give it full width
                when {
                    state.statusMessage != null -> {
                        // No action icons — status message uses entire row
                    }
                    showSearch -> {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = onClearSearch,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Очистить",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    state.isSelectionMode -> {
                        // Compact: SelectAll + collapsed chevron menu
                        IconButton(
                            onClick = onSelectAll,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.SelectAll,
                                contentDescription = "Выбрать/снять все",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        var showCollapsed by remember { mutableStateOf(false) }
                        Box {
                            IconButton(
                                onClick = { showCollapsed = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Filled.ExpandMore,
                                    contentDescription = "Ещё",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showCollapsed,
                                onDismissRequest = { showCollapsed = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Поиск") },
                                    onClick = {
                                        onOpenSearch()
                                        showCollapsed = false
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Меню") },
                                    onClick = {
                                        onShowDrawer()
                                        showCollapsed = false
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Menu, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Создать") },
                                    onClick = {
                                        onCreateNew()
                                        showCollapsed = false
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Text(if (state.showHidden) "Скрыть скрытые" else "Показать скрытые")
                                    },
                                    onClick = {
                                        onToggleHidden()
                                        showCollapsed = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (state.showHidden) Icons.Filled.VisibilityOff
                                            else Icons.Filled.Visibility,
                                            contentDescription = null
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(if (isFavorite) "Убрать из избранного" else "В избранное")
                                    },
                                    onClick = {
                                        onToggleFavorite()
                                        showCollapsed = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                            contentDescription = null
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(if (isOriginalFolder) "Убрать пометку оригинала" else "Папка-оригинал")
                                    },
                                    onClick = {
                                        onToggleOriginalFolder()
                                        showCollapsed = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (isOriginalFolder) Icons.Filled.FolderSpecial else Icons.Filled.Folder,
                                            contentDescription = null
                                        )
                                    }
                                )
                            }
                        }
                    }
                    else -> {
                        if (serviceRunning) {
                            IconButton(
                                onClick = {
                                    val vlc = PlaybackService.instance?.getVlcPlayer()
                                    if (vlc != null) {
                                        if (isServicePlaying) vlc.pause() else vlc.play()
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    if (isServicePlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isServicePlaying) "Пауза" else "Воспроизвести",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                onOpenSearch()
                                onPanelTap()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = "Поиск",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                onPanelTap()
                                onShowDrawer()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.Menu,
                                contentDescription = "Меню",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                onPanelTap()
                                onCreateNew()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "Создать",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        SortMenu(
                            currentOrder = state.sortOrder,
                            onSortChanged = onSortChanged
                        )
                        Box {
                            IconButton(
                                onClick = { showOverflow = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = "Ещё",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showOverflow,
                                onDismissRequest = { showOverflow = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(if (state.showHidden) "Скрыть скрытые" else "Показать скрытые")
                                    },
                                    onClick = {
                                        onToggleHidden()
                                        showOverflow = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (state.showHidden) {
                                                Icons.Filled.VisibilityOff
                                            } else {
                                                Icons.Filled.Visibility
                                            },
                                            contentDescription = null
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(if (isFavorite) "Убрать из избранного" else "Добавить в избранное")
                                    },
                                    onClick = {
                                        onToggleFavorite()
                                        showOverflow = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                            contentDescription = null
                                        )
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Открыть в другой панели") },
                                    onClick = {
                                        onOpenInOtherPanel()
                                        showOverflow = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.OpenInNew, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(if (isOriginalFolder) "Убрать пометку оригинала" else "Папка-оригинал")
                                    },
                                    onClick = {
                                        onToggleOriginalFolder()
                                        showOverflow = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (isOriginalFolder) Icons.Filled.FolderSpecial else Icons.Filled.Folder,
                                            contentDescription = null
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!showSearch) {
            Box(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onPanelTap() }
            ) {
                BreadcrumbBar(
                    displayPath = state.displayPath,
                    currentPath = state.currentPath,
                    safVolumeLabel = safVolumeLabel,
                    folderSize = state.files.sumOf { it.size },
                    onSegmentClick = onBreadcrumbClick
                )
            }
        }

        HorizontalDivider()

        when {
            state.isLoading && state.files.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onPanelTap() },
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            state.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onPanelTap() }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            filteredFiles.isEmpty() && !state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onPanelTap() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (state.searchQuery.isNotBlank()) "Ничего не найдено" else "Папка пуста",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                val gridState = rememberLazyGridState()

                // Restore scroll when returning from another screen
                LaunchedEffect(Unit) {
                    if (initialScrollIndex > 0) {
                        gridState.scrollToItem(initialScrollIndex)
                    }
                }

                // Report scroll position to ViewModel
                LaunchedEffect(gridState) {
                    snapshotFlow { gridState.firstVisibleItemIndex }
                        .collect { index -> onScrollPositionChanged(index) }
                }

                // Restore scroll on folder navigation only (skip re-creation)
                val handledTrigger = remember { mutableStateOf(state.scrollToTrigger) }
                LaunchedEffect(state.scrollToTrigger) {
                    if (state.scrollToTrigger != handledTrigger.value) {
                        handledTrigger.value = state.scrollToTrigger
                        gridState.scrollToItem(state.scrollToIndex)
                    }
                }

                var dragStartIndex by remember { mutableIntStateOf(-1) }
                var isCrossPanelDrag by remember { mutableStateOf(false) }

                // Stable references for gesture handler
                val currentFilteredFiles by rememberUpdatedState(filteredFiles)
                val currentOnPanelTap by rememberUpdatedState(onPanelTap)
                val currentOnLongPressItem by rememberUpdatedState(onLongPressItem)
                val currentOnSelectRange by rememberUpdatedState(onSelectRange)
                val currentOnDragStarted by rememberUpdatedState(onDragStarted)
                val currentOnDragMoved by rememberUpdatedState(onDragMoved)
                val currentOnDragEnded by rememberUpdatedState(onDragEnded)
                val currentSelectedPaths by rememberUpdatedState(state.selectedPaths)
                val currentGridColumns by rememberUpdatedState(state.gridColumns)
                val currentOnGridColumnsChanged by rememberUpdatedState(onGridColumnsChanged)

                // Track list position in root for global offset calc
                var listRootOffset by remember { mutableStateOf(Offset.Zero) }

                // Pinch-to-zoom accumulated scale
                val haptic = LocalHapticFeedback.current
                var accumulatedScale by remember { mutableFloatStateOf(1f) }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(state.gridColumns),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onGloballyPositioned { coords ->
                            listRootOffset = coords.positionInRoot()
                        }
                        .pointerInput(Unit) {
                            // Custom 2-finger pinch detector.
                            // Does NOT consume single-finger events → scroll & drag work normally.
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                var prevSpan = 0f
                                do {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.size >= 2) {
                                        val dx = pressed[0].position.x - pressed[1].position.x
                                        val dy = pressed[0].position.y - pressed[1].position.y
                                        val span = kotlin.math.sqrt(dx * dx + dy * dy)
                                        if (prevSpan > 0f) {
                                            accumulatedScale *= span / prevSpan
                                            val cols = currentGridColumns
                                            when {
                                                accumulatedScale > 1.4f && cols > 1 -> {
                                                    currentOnGridColumnsChanged(cols - 1)
                                                    accumulatedScale = 1f
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                                accumulatedScale < 0.7f && cols < 6 -> {
                                                    currentOnGridColumnsChanged(cols + 1)
                                                    accumulatedScale = 1f
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                            }
                                        }
                                        prevSpan = span
                                        // Consume only multi-touch to prevent scroll during pinch
                                        event.changes.forEach { it.consume() }
                                    } else {
                                        prevSpan = 0f
                                    }
                                } while (event.changes.any { it.pressed })
                                accumulatedScale = 1f
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    currentOnPanelTap()
                                    val index = findItemIndexAtPosition(
                                        gridState.layoutInfo, offset, currentGridColumns
                                    )
                                    if (index >= 0 && index < currentFilteredFiles.size) {
                                        val entry = currentFilteredFiles[index]
                                        // If tapped on an already-selected file → cross-panel DnD
                                        if (entry.path in currentSelectedPaths && currentOnDragStarted != null) {
                                            isCrossPanelDrag = true
                                            dragStartIndex = -1
                                            val globalOffset = Offset(
                                                listRootOffset.x + offset.x,
                                                listRootOffset.y + offset.y
                                            )
                                            currentOnDragStarted?.invoke(
                                                currentSelectedPaths.toList(),
                                                globalOffset
                                            )
                                        } else {
                                            // Range selection
                                            isCrossPanelDrag = false
                                            dragStartIndex = index
                                            currentOnLongPressItem(entry)
                                        }
                                    }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    if (isCrossPanelDrag) {
                                        val globalOffset = Offset(
                                            listRootOffset.x + change.position.x,
                                            listRootOffset.y + change.position.y
                                        )
                                        currentOnDragMoved?.invoke(globalOffset)
                                    } else if (dragStartIndex >= 0) {
                                        val currentIndex = findItemIndexAtPosition(
                                            gridState.layoutInfo, change.position, currentGridColumns
                                        )
                                        if (currentIndex >= 0 && currentIndex < currentFilteredFiles.size) {
                                            currentOnSelectRange(dragStartIndex, currentIndex)
                                        }
                                    }
                                },
                                onDragEnd = {
                                    if (isCrossPanelDrag) {
                                        currentOnDragEnded?.invoke()
                                    }
                                    dragStartIndex = -1
                                    isCrossPanelDrag = false
                                },
                                onDragCancel = {
                                    if (isCrossPanelDrag) {
                                        currentOnDragEnded?.invoke()
                                    }
                                    dragStartIndex = -1
                                    isCrossPanelDrag = false
                                }
                            )
                        }
                ) {
                    itemsIndexed(
                        items = filteredFiles,
                        key = { _, entry -> entry.path },
                        span = { _, entry ->
                            if (state.renamingPath == entry.path) GridItemSpan(maxLineSpan)
                            else GridItemSpan(1)
                        }
                    ) { _, entry ->
                        FileListItem(
                            entry = entry,
                            isSelected = entry.path in state.selectedPaths,
                            isSelectionMode = state.isSelectionMode,
                            isRenaming = state.renamingPath == entry.path,
                            onClick = { onFileClick(entry) },
                            onLongClick = { onLongPressItem(entry) },
                            onIconClick = { onIconClick(entry) },
                            onRenameConfirm = onRenameConfirm,
                            onRenameCancel = onRenameCancel,
                            isGridMode = isGridMode
                        )
                    }
                }
            }
        }
    }
}

private fun findItemIndexAtPosition(
    layoutInfo: androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo,
    position: Offset,
    gridColumns: Int
): Int {
    // Try to find the item directly under the touch point
    for (item in layoutInfo.visibleItemsInfo) {
        val left = item.offset.x.toFloat()
        val top = item.offset.y.toFloat()
        val right = left + item.size.width.toFloat()
        val bottom = top + item.size.height.toFloat()
        if (position.x in left..right && position.y in top..bottom) {
            return item.index
        }
    }
    // Fallback: find closest item by Y then X
    if (layoutInfo.visibleItemsInfo.isEmpty()) return -1
    val first = layoutInfo.visibleItemsInfo.first()
    val last = layoutInfo.visibleItemsInfo.last()
    return when {
        position.y < first.offset.y -> first.index
        position.y > last.offset.y + last.size.height -> last.index
        else -> {
            // Find the row, then the column
            val rowItems = layoutInfo.visibleItemsInfo.filter { item ->
                val top = item.offset.y.toFloat()
                val bottom = top + item.size.height.toFloat()
                position.y in top..bottom
            }
            if (rowItems.isEmpty()) return last.index
            // Find closest column
            rowItems.minByOrNull {
                val centerX = it.offset.x + it.size.width / 2f
                kotlin.math.abs(position.x - centerX)
            }?.index ?: last.index
        }
    }
}

private fun formatFileCount(dirs: Int, files: Int): String {
    val parts = buildList {
        if (dirs > 0) add("$dirs ${pluralDirs(dirs)}")
        if (files > 0) add("$files ${pluralFiles(files)}")
    }
    return parts.joinToString(" и ").ifEmpty { "0 файлов" }
}

private fun pluralDirs(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> "папок"
        mod10 == 1 -> "папка"
        mod10 in 2..4 -> "папки"
        else -> "папок"
    }
}

private fun pluralFiles(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> "файлов"
        mod10 == 1 -> "файл"
        mod10 in 2..4 -> "файла"
        else -> "файлов"
    }
}
