package com.vamp.haron.presentation.explorer.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.ui.res.stringResource
import com.vamp.haron.R
import com.vamp.haron.data.model.SortOrder
import com.vamp.haron.domain.model.FileEntry
import com.vamp.haron.domain.model.FileTag
import com.vamp.haron.domain.model.TagColors
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
    sdCardLabel: String = stringResource(R.string.sd_card),
    hasSafPermission: Boolean = false,
    onSdCardClick: () -> Unit = {},
    onRequestSafAccess: () -> Unit = {},
    safVolumeLabel: String = "",
    onOpenStorageAnalysis: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenGlobalSearch: () -> Unit = {},
    onCloseSearch: () -> Unit = {},
    onScrollPositionChanged: (Int) -> Unit = {},
    initialScrollIndex: Int = 0,
    fileTags: Map<String, List<String>> = emptyMap(),
    tagDefinitions: List<FileTag> = emptyList(),
    activeTagFilter: String? = null,
    onTagFilterChanged: (String?) -> Unit = {},
    onLongPressShield: () -> Unit = {},
    onExitProtected: () -> Unit = {},
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

    // Filter files by search query and active tag filter
    val filteredFiles = state.files
        .let { files ->
            if (activeTagFilter != null) {
                files.filter { entry -> fileTags[entry.path]?.contains(activeTagFilter) == true }
            } else files
        }
        .let { files ->
            if (state.searchQuery.isBlank()) files
            else files.filter { it.name.contains(state.searchQuery, ignoreCase = true) }
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
                                contentDescription = stringResource(R.string.close_search),
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
                                contentDescription = stringResource(R.string.cancel_selection),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    else -> {
                        // Кнопка назад: история → navigateUp (fallback)
                        val canGoBackOrUp = canNavigateBack || canNavigateUp
                        IconButton(
                            onClick = {
                                onPanelTap()
                                if (canNavigateBack) onNavigateBack() else onNavigateUp()
                            },
                            enabled = canGoBackOrUp,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                modifier = Modifier.size(18.dp),
                                tint = if (canGoBackOrUp) {
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
                                contentDescription = stringResource(R.string.forward),
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
                                            stringResource(R.string.search_placeholder),
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
                            text = stringResource(R.string.selected_count, formatFileCount(dirs, files)),
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
                                text = state.displayPath.substringAfterLast('/').ifEmpty { stringResource(R.string.storage) },
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
                                    contentDescription = stringResource(R.string.clear),
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
                                contentDescription = stringResource(R.string.select_deselect_all),
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
                                    contentDescription = stringResource(R.string.more),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showCollapsed,
                                onDismissRequest = { showCollapsed = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.search)) },
                                    onClick = {
                                        onOpenSearch()
                                        showCollapsed = false
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu)) },
                                    onClick = {
                                        onShowDrawer()
                                        showCollapsed = false
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Menu, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.create)) },
                                    onClick = {
                                        onCreateNew()
                                        showCollapsed = false
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Text(if (state.showHidden) stringResource(R.string.hide_hidden) else stringResource(R.string.show_hidden))
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
                                        Text(if (isFavorite) stringResource(R.string.remove_from_favorites) else stringResource(R.string.to_favorites))
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
                                        Text(if (isOriginalFolder) stringResource(R.string.remove_original_marker) else stringResource(R.string.original_folder))
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
                                    contentDescription = if (isServicePlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        // Search: tap = local, long press = global search
                        @OptIn(ExperimentalFoundationApi::class)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .combinedClickable(
                                    onClick = {
                                        onOpenSearch()
                                        onPanelTap()
                                    },
                                    onLongClick = {
                                        onPanelTap()
                                        onOpenGlobalSearch()
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = stringResource(R.string.search),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        // Shield button — long press only → show all protected files
                        @OptIn(ExperimentalFoundationApi::class)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .combinedClickable(
                                    onClick = {
                                        if (state.showProtected) {
                                            onPanelTap()
                                            onExitProtected()
                                        }
                                    },
                                    onLongClick = {
                                        onPanelTap()
                                        onLongPressShield()
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Shield,
                                contentDescription = stringResource(R.string.shield_button),
                                modifier = Modifier.size(18.dp),
                                tint = if (state.showProtected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
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
                                contentDescription = stringResource(R.string.menu),
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
                                contentDescription = stringResource(R.string.create),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        SortMenu(
                            currentOrder = state.sortOrder,
                            onSortChanged = onSortChanged,
                            tagDefinitions = tagDefinitions,
                            activeTagFilter = activeTagFilter,
                            onTagFilterChanged = onTagFilterChanged
                        )
                        Box {
                            IconButton(
                                onClick = { showOverflow = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = stringResource(R.string.more),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showOverflow,
                                onDismissRequest = { showOverflow = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(if (state.showHidden) stringResource(R.string.hide_hidden) else stringResource(R.string.show_hidden))
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
                                        Text(if (isFavorite) stringResource(R.string.remove_from_favorites) else stringResource(R.string.add_to_favorites))
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
                                    text = { Text(stringResource(R.string.open_in_other_panel)) },
                                    onClick = {
                                        onOpenInOtherPanel()
                                        showOverflow = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(if (isOriginalFolder) stringResource(R.string.remove_original_marker) else stringResource(R.string.original_folder))
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
                        text = if (state.searchQuery.isNotBlank()) stringResource(R.string.nothing_found) else stringResource(R.string.folder_empty),
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
                        val entryTagColors = fileTags[entry.path]
                            ?.mapNotNull { name -> tagDefinitions.find { it.name == name } }
                            ?.map { TagColors.palette.getOrElse(it.colorIndex) { TagColors.palette[0] } }
                            ?: emptyList()
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
                            isGridMode = isGridMode,
                            tagColors = entryTagColors
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

@Composable
private fun formatFileCount(dirs: Int, files: Int): String {
    val folderLabel = pluralForm(
        dirs,
        stringResource(R.string.plural_folders_nom),
        stringResource(R.string.plural_folders_gen_few),
        stringResource(R.string.plural_folders_genitive)
    )
    val fileLabel = pluralForm(
        files,
        stringResource(R.string.plural_files_nom),
        stringResource(R.string.plural_files_gen_few),
        stringResource(R.string.plural_files_genitive)
    )
    val andConj = stringResource(R.string.and_conjunction)
    val zeroFiles = stringResource(R.string.zero_files)
    val parts = buildList {
        if (dirs > 0) add("$dirs $folderLabel")
        if (files > 0) add("$files $fileLabel")
    }
    return parts.joinToString(andConj).ifEmpty { zeroFiles }
}

private fun pluralForm(count: Int, nom: String, genFew: String, genitive: String): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> genitive
        mod10 == 1 -> nom
        mod10 in 2..4 -> genFew
        else -> genitive
    }
}
