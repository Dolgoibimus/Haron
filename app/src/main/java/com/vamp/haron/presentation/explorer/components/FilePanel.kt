package com.vamp.haron.presentation.explorer.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
    onSelectByExtension: (String) -> Unit = {},
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
    onExitApp: () -> Unit = {},
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
    externalDragOffset: Offset? = null,
    hoveredFolderPath: String? = null,
    onDragHoverFolder: ((String?) -> Unit)? = null,
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
    onToggleSearchInContent: () -> Unit = {},
    onScrollPositionChanged: (Int) -> Unit = {},
    initialScrollIndex: Int = 0,
    fileTags: Map<String, List<String>> = emptyMap(),
    tagDefinitions: List<FileTag> = emptyList(),
    activeTagFilter: String? = null,
    onTagFilterChanged: (String?) -> Unit = {},
    onLongPressShield: () -> Unit = {},
    onExitProtected: () -> Unit = {},
    onQuickSendStart: ((filePath: String, fileName: String, Offset) -> Unit)? = null,
    onQuickSendDrag: ((Offset) -> Unit)? = null,
    onQuickSendEnd: (() -> Unit)? = null,
    isQuickSendActive: Boolean = false,
    // Selection toolbar extra actions (moved from bottom bar)
    onProtectSelection: () -> Unit = {},
    onInfoSelection: () -> Unit = {},
    onOpenWithSelection: () -> Unit = {},
    onCompareSelection: () -> Unit = {},
    onHideInFileSelection: () -> Unit = {},
    selectionHasProtected: Boolean = false,
    selectionTotalCount: Int = 0,
    selectionDirCount: Int = 0,
    otherPanelSelectionCount: Int = 0,
    currentFolderSize: Long? = null,
    storageTotalSize: Long = 0L,
    marqueeEnabled: Boolean = true,
    folderSizeCache: Map<String, Long> = emptyMap(),
    hasSelectionBar: Boolean = false,
    cloudAuthHeader: String? = null,
    archiveThumbnailCache: com.vamp.haron.common.util.ArchiveThumbnailCache? = null,
    onLoadProtectedThumbnail: (suspend (String) -> android.graphics.Bitmap?)? = null,
    onSizeClick: () -> Unit = {},
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

    // Search focus + cursor preservation
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var searchFieldValue by remember {
        mutableStateOf(TextFieldValue(state.searchQuery, TextRange(state.searchQuery.length)))
    }
    // Sync from external (e.g. clearSearch)
    if (searchFieldValue.text != state.searchQuery) {
        searchFieldValue = TextFieldValue(state.searchQuery, TextRange(state.searchQuery.length))
    }

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
            if (state.searchQuery.isBlank() || !state.isSearchActive) files
            else if (state.searchInContent) {
                // Content search: filter files by FTS snippets, keep folders matching name
                val snippets = state.contentSearchSnippets
                when {
                    state.isContentIndexing -> files // still indexing — show all
                    snippets != null && snippets.isNotEmpty() -> {
                        val snippetKeys = snippets.keys
                        files.filter {
                            it.path in snippetKeys ||
                            (it.isDirectory && (
                                it.name.contains(state.searchQuery, ignoreCase = true) ||
                                snippetKeys.any { key -> key.startsWith(it.path + "/") }
                            ))
                        }
                    }
                    snippets == null -> files // not yet searched — show all
                    else -> emptyList() // empty results — nothing found
                }
            } else {
                files.filter { it.name.contains(state.searchQuery, ignoreCase = true) }
            }
        }
        .let { currentFolderResults ->
            // Append deep search results from subfolders (if any)
            if (state.isSearchActive && state.searchQuery.isNotBlank() && !state.deepSearchResults.isNullOrEmpty()) {
                currentFolderResults + state.deepSearchResults!!
            } else {
                currentFolderResults
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
                            value = searchFieldValue,
                            onValueChange = { newValue ->
                                searchFieldValue = newValue
                                onSearchChanged(newValue.text)
                            },
                            textStyle = searchTextStyle,
                            singleLine = true,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (searchFieldValue.text.isEmpty()) {
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
                        // Toggle: search by name (Тт)
                        IconButton(
                            onClick = {
                                if (state.searchInContent) onToggleSearchInContent()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.TextFields,
                                contentDescription = stringResource(R.string.search_by_name),
                                modifier = Modifier.size(18.dp),
                                tint = if (!state.searchInContent) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Toggle: search by content (FindInPage)
                        IconButton(
                            onClick = {
                                if (!state.searchInContent) onToggleSearchInContent()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            if (state.isContentIndexing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Filled.FindInPage,
                                    contentDescription = stringResource(R.string.search_by_content),
                                    modifier = Modifier.size(18.dp),
                                    tint = if (state.searchInContent) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (state.contentIndexProgress != null) {
                            Text(
                                text = state.contentIndexProgress,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )
                        }
                        // Deep search progress/status
                        if (state.isDeepSearching && state.deepSearchProgress != null) {
                            Text(
                                text = state.deepSearchProgress,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(horizontal = 2.dp),
                                maxLines = 1
                            )
                        } else if (state.isDeepSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp).padding(horizontal = 2.dp),
                                strokeWidth = 1.5.dp,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
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
                        // SelectAll (tap) + select by extension (long press)
                        Box {
                            var showExtMenu by remember { mutableStateOf(false) }
                            @OptIn(ExperimentalFoundationApi::class)
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .combinedClickable(
                                        onClick = onSelectAll,
                                        onLongClick = { showExtMenu = true }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.SelectAll,
                                    contentDescription = stringResource(R.string.select_deselect_all),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showExtMenu,
                                onDismissRequest = { showExtMenu = false }
                            ) {
                                val extensions = remember(state.files) {
                                    state.files
                                        .filter { !it.isDirectory && it.name.contains('.') }
                                        .map { it.name.substringAfterLast('.').lowercase() }
                                        .distinct()
                                        .sorted()
                                }
                                if (extensions.size > 10) {
                                    // Two narrow columns
                                    val half = (extensions.size + 1) / 2
                                    Row {
                                        Column(modifier = Modifier.width(60.dp)) {
                                            extensions.take(half).forEach { ext ->
                                                DropdownMenuItem(
                                                    text = { Text(".$ext", style = MaterialTheme.typography.bodySmall) },
                                                    onClick = {
                                                        showExtMenu = false
                                                        onSelectByExtension(ext)
                                                    },
                                                    modifier = Modifier.height(32.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                                )
                                            }
                                        }
                                        Column(modifier = Modifier.width(60.dp)) {
                                            extensions.drop(half).forEach { ext ->
                                                DropdownMenuItem(
                                                    text = { Text(".$ext", style = MaterialTheme.typography.bodySmall) },
                                                    onClick = {
                                                        showExtMenu = false
                                                        onSelectByExtension(ext)
                                                    },
                                                    modifier = Modifier.height(32.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    extensions.forEach { ext ->
                                        DropdownMenuItem(
                                            text = { Text(".$ext") },
                                            onClick = {
                                                showExtMenu = false
                                                onSelectByExtension(ext)
                                            },
                                            modifier = Modifier.height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                        )
                                    }
                                }
                            }
                        }
                        IconButton(
                            onClick = onCompareSelection,
                            enabled = selectionTotalCount == 2 || (selectionTotalCount == 1 && otherPanelSelectionCount == 1),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.Compare, contentDescription = stringResource(R.string.compare_action), modifier = Modifier.size(18.dp))
                        }
                        // Shield button moved outside when — always visible at same position
                        // Steganography hidden until ready for release
                        // IconButton(
                        //     onClick = onHideInFileSelection,
                        //     enabled = selectionTotalCount == 1 && selectionDirCount == 0,
                        //     modifier = Modifier.size(32.dp)
                        // ) {
                        //     Icon(Icons.Filled.VisibilityOff, contentDescription = stringResource(R.string.stego_hide_action), modifier = Modifier.size(18.dp))
                        // }
                        IconButton(
                            onClick = onInfoSelection,
                            enabled = selectionTotalCount == 1,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.properties_action), modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = onOpenWithSelection,
                            enabled = selectionTotalCount == 1 && selectionDirCount == 0,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.open_with_action), modifier = Modifier.size(18.dp))
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
                                    val adapter = PlaybackService.instance?.getAdapter()
                                    if (adapter != null) {
                                        if (isServicePlaying) adapter.togglePause() else adapter.togglePlay()
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
                        // Shield button moved outside when — always visible at same position
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

                // === UNIFIED SHIELD BUTTON — always at same position ===
                // Tap + selection + not protected → protect selected files
                // Tap + no selection + showProtected → exit protected mode
                // Long press + not showProtected → enter protected mode
                // Long press + showProtected + selection → unprotect selected files
                val currentOnLongPressShield by rememberUpdatedState(onLongPressShield)
                val currentOnExitProtected by rememberUpdatedState(onExitProtected)
                val currentOnProtectSelection by rememberUpdatedState(onProtectSelection)
                val currentOnPanelTap by rememberUpdatedState(onPanelTap)
                val currentShowProtected by rememberUpdatedState(state.showProtected)
                val currentIsSelectionMode by rememberUpdatedState(state.isSelectionMode)
                val currentHasProtected by rememberUpdatedState(selectionHasProtected)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .pointerInput(Unit) {
                            val longPressMs = viewConfiguration.longPressTimeoutMillis
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                var longFired = false
                                val result = withTimeoutOrNull(longPressMs) {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.all { !it.pressed }) break
                                    }
                                }
                                if (result == null && down.pressed) {
                                    // Long press
                                    longFired = true
                                    currentOnPanelTap()
                                    if (currentShowProtected && currentIsSelectionMode) {
                                        // In protected mode with selection → unprotect
                                        currentOnProtectSelection()
                                    } else {
                                        // Enter protected mode
                                        currentOnLongPressShield()
                                    }
                                    try {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            if (event.changes.all { !it.pressed }) break
                                        }
                                    } catch (_: Exception) { }
                                }
                                if (!longFired) {
                                    // Short tap
                                    currentOnPanelTap()
                                    if (currentIsSelectionMode && !currentHasProtected) {
                                        // Selection + files not protected → protect
                                        currentOnProtectSelection()
                                    } else if (currentShowProtected && !currentIsSelectionMode) {
                                        // In protected mode, no selection → exit
                                        currentOnExitProtected()
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Shield,
                        contentDescription = stringResource(R.string.shield_button),
                        modifier = Modifier.size(18.dp),
                        tint = when {
                            state.showProtected -> MaterialTheme.colorScheme.primary
                            state.isSelectionMode -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        }
                    )
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
                    folderSize = currentFolderSize ?: state.files.filter { !it.isDirectory }.sumOf { it.size },
                    storageTotalSize = storageTotalSize,
                    onSegmentClick = onBreadcrumbClick,
                    onSizeClick = onSizeClick,
                    cloudBreadcrumbs = state.cloudBreadcrumbs
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
                val gridState = rememberLazyGridState(
                    initialFirstVisibleItemIndex = initialScrollIndex
                )

                // Report scroll position to ViewModel (skip initial 0 when restoring)
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

                // Auto-scroll to renaming item so keyboard doesn't cover it
                LaunchedEffect(state.renamingPath) {
                    val rp = state.renamingPath ?: return@LaunchedEffect
                    val idx = filteredFiles.indexOfFirst { it.path == rp }
                    if (idx >= 0) gridState.animateScrollToItem(idx)
                }

                var dragStartIndex by remember { mutableIntStateOf(-1) }
                var isCrossPanelDrag by remember { mutableStateOf(false) }
                var isQuickSendDrag by remember { mutableStateOf(false) }
                var isSamePanelDrag by remember { mutableStateOf(false) }
                var samePanelDragActivated by remember { mutableStateOf(false) }
                var lastHoveredFolderPath by remember { mutableStateOf<String?>(null) }

                // Stable references for gesture handler
                val currentFilteredFiles by rememberUpdatedState(filteredFiles)
                val currentOnPanelTap by rememberUpdatedState(onPanelTap)
                val currentIsActive by rememberUpdatedState(isActive)
                val currentOnLongPressItem by rememberUpdatedState(onLongPressItem)
                val currentOnSelectRange by rememberUpdatedState(onSelectRange)
                val currentOnDragStarted by rememberUpdatedState(onDragStarted)
                val currentOnDragMoved by rememberUpdatedState(onDragMoved)
                val currentOnDragEnded by rememberUpdatedState(onDragEnded)
                val currentOnQuickSendStart by rememberUpdatedState(onQuickSendStart)
                val currentOnQuickSendDrag by rememberUpdatedState(onQuickSendDrag)
                val currentOnQuickSendEnd by rememberUpdatedState(onQuickSendEnd)
                val currentSelectedPaths by rememberUpdatedState(state.selectedPaths)
                val currentGridColumns by rememberUpdatedState(state.gridColumns)
                val currentOnGridColumnsChanged by rememberUpdatedState(onGridColumnsChanged)
                val currentOnFileClick by rememberUpdatedState(onFileClick)
                val currentOnIconClick by rememberUpdatedState(onIconClick)
                val currentOnDragHoverFolder by rememberUpdatedState(onDragHoverFolder)

                // Track list position in root for global offset calc
                var listRootOffset by remember { mutableStateOf(Offset.Zero) }

                // Detect folder hover during external drag
                LaunchedEffect(externalDragOffset, listRootOffset) {
                    if (externalDragOffset == null || onDragHoverFolder == null) {
                        return@LaunchedEffect
                    }
                    // Convert global offset to local
                    val localX = externalDragOffset.x - listRootOffset.x
                    val localY = externalDragOffset.y - listRootOffset.y
                    // Skip if cursor is outside this panel's grid
                    if (localX < 0 || localY < 0) return@LaunchedEffect
                    val localOffset = Offset(localX, localY)
                    // Exact hit-test only: check if cursor is directly over an item
                    var hitIndex = -1
                    for (item in gridState.layoutInfo.visibleItemsInfo) {
                        val left = item.offset.x.toFloat()
                        val top = item.offset.y.toFloat()
                        val right = left + item.size.width.toFloat()
                        val bottom = top + item.size.height.toFloat()
                        if (localOffset.x in left..right && localOffset.y in top..bottom) {
                            hitIndex = item.index
                            break
                        }
                    }
                    if (hitIndex >= 0 && hitIndex < filteredFiles.size) {
                        val entry = filteredFiles[hitIndex]
                        if (entry.isDirectory) {
                            onDragHoverFolder?.invoke(entry.path)
                        } else {
                            onDragHoverFolder?.invoke(null)
                        }
                    } else {
                        onDragHoverFolder?.invoke(null)
                    }
                }

                // Pinch-to-zoom accumulated scale
                val haptic = LocalHapticFeedback.current
                val currentHaptic = haptic
                var accumulatedScale by remember { mutableFloatStateOf(1f) }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(state.gridColumns),
                    state = gridState,
                    contentPadding = PaddingValues(bottom = if (hasSelectionBar) 64.dp else 0.dp),
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
                            // Tap handler for grid mode: icon tap = preview, name tap = open
                            // If panel is not active, first tap only activates it
                            detectTapGestures { offset ->
                                if (!currentIsActive) {
                                    currentOnPanelTap()
                                    return@detectTapGestures
                                }
                                val index = findItemIndexAtPosition(
                                    gridState.layoutInfo, offset, currentGridColumns
                                )
                                if (index >= 0 && index < currentFilteredFiles.size) {
                                    val entry = currentFilteredFiles[index]
                                    currentOnPanelTap()
                                    if (currentGridColumns >= 2) {
                                        val itemInfo = gridState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { it.index == index }
                                        val isOnIcon = if (itemInfo != null) {
                                            (offset.y - itemInfo.offset.y) <= itemInfo.size.width
                                        } else false
                                        if (isOnIcon) {
                                            currentOnIconClick(entry)
                                        } else {
                                            currentOnFileClick(entry)
                                        }
                                    } else {
                                        currentOnFileClick(entry)
                                    }
                                }
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

                                        // In grid mode (2+ columns): check if long press is on icon area (top square) vs name area
                                        val isOnIconArea = if (currentGridColumns >= 2) {
                                            val itemInfo = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                                            if (itemInfo != null) {
                                                val yInCell = offset.y - itemInfo.offset.y
                                                yInCell <= itemInfo.size.width // icon is 1:1 square = width
                                            } else false
                                        } else false

                                        if (isOnIconArea) {
                                            // Icon area in grid → DnD zone
                                            isSamePanelDrag = true
                                            isQuickSendDrag = false
                                            isCrossPanelDrag = false
                                            dragStartIndex = -1
                                            if (entry.path in currentSelectedPaths) {
                                                // Already selected → DnD all selected immediately
                                                samePanelDragActivated = true
                                                currentHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                val globalOffset = Offset(
                                                    listRootOffset.x + offset.x,
                                                    listRootOffset.y + offset.y
                                                )
                                                currentOnDragStarted?.invoke(
                                                    currentSelectedPaths.toList(),
                                                    globalOffset
                                                )
                                            } else {
                                                // Not selected → select + range selection on drag
                                                isSamePanelDrag = false
                                                dragStartIndex = index
                                                currentOnLongPressItem(entry)
                                            }
                                        } else if (currentGridColumns >= 2) {
                                            // Name area in grid → original behavior: QuickSend / cross-panel DnD / select
                                            if (!entry.isDirectory && entry.path !in currentSelectedPaths && currentOnQuickSendStart != null) {
                                                isQuickSendDrag = true
                                                isCrossPanelDrag = false
                                                isSamePanelDrag = false
                                                dragStartIndex = -1
                                                val globalOffset = Offset(
                                                    listRootOffset.x + offset.x,
                                                    listRootOffset.y + offset.y
                                                )
                                                currentOnQuickSendStart?.invoke(entry.path, entry.name, globalOffset)
                                            } else if (entry.path in currentSelectedPaths && currentOnDragStarted != null) {
                                                isQuickSendDrag = false
                                                isCrossPanelDrag = true
                                                isSamePanelDrag = false
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
                                                isQuickSendDrag = false
                                                isCrossPanelDrag = false
                                                isSamePanelDrag = false
                                                dragStartIndex = index
                                                currentOnLongPressItem(entry)
                                            }
                                        } else {
                                            // List mode (1 column) — 3 zones: left / middle / right
                                            val zoneWidth = size.width / 3f
                                            val zone = when {
                                                offset.x < zoneWidth -> 0       // left third
                                                offset.x < zoneWidth * 2f -> 1  // middle third
                                                else -> 2                        // right third
                                            }
                                            when (zone) {
                                                0 -> {
                                                    // LEFT zone: range selection
                                                    isQuickSendDrag = false
                                                    isCrossPanelDrag = false
                                                    isSamePanelDrag = false
                                                    dragStartIndex = index
                                                    currentOnLongPressItem(entry)
                                                }
                                                1 -> {
                                                    // MIDDLE zone: DnD zone
                                                    isSamePanelDrag = true
                                                    isQuickSendDrag = false
                                                    isCrossPanelDrag = false
                                                    dragStartIndex = -1
                                                    if (entry.path in currentSelectedPaths) {
                                                        // Already selected → DnD all selected immediately
                                                        samePanelDragActivated = true
                                                        currentHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        val globalOffset = Offset(
                                                            listRootOffset.x + offset.x,
                                                            listRootOffset.y + offset.y
                                                        )
                                                        currentOnDragStarted?.invoke(
                                                            currentSelectedPaths.toList(),
                                                            globalOffset
                                                        )
                                                    } else {
                                                        // Not selected → select, DnD activates on first movement
                                                        samePanelDragActivated = false
                                                        currentOnLongPressItem(entry)
                                                    }
                                                }
                                                else -> {
                                                    // RIGHT zone: QuickSend / cross-panel DnD / fallback select
                                                    if (!entry.isDirectory && entry.path !in currentSelectedPaths && currentOnQuickSendStart != null) {
                                                        isQuickSendDrag = true
                                                        isCrossPanelDrag = false
                                                        isSamePanelDrag = false
                                                        dragStartIndex = -1
                                                        val globalOffset = Offset(
                                                            listRootOffset.x + offset.x,
                                                            listRootOffset.y + offset.y
                                                        )
                                                        currentOnQuickSendStart?.invoke(entry.path, entry.name, globalOffset)
                                                    } else if (entry.path in currentSelectedPaths && currentOnDragStarted != null) {
                                                        isQuickSendDrag = false
                                                        isCrossPanelDrag = true
                                                        isSamePanelDrag = false
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
                                                        isQuickSendDrag = false
                                                        isCrossPanelDrag = false
                                                        isSamePanelDrag = false
                                                        dragStartIndex = index
                                                        currentOnLongPressItem(entry)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    if (isQuickSendDrag) {
                                        val globalOffset = Offset(
                                            listRootOffset.x + change.position.x,
                                            listRootOffset.y + change.position.y
                                        )
                                        currentOnQuickSendDrag?.invoke(globalOffset)
                                    } else if (isCrossPanelDrag) {
                                        val globalOffset = Offset(
                                            listRootOffset.x + change.position.x,
                                            listRootOffset.y + change.position.y
                                        )
                                        currentOnDragMoved?.invoke(globalOffset)
                                    } else if (isSamePanelDrag) {
                                        val globalOffset = Offset(
                                            listRootOffset.x + change.position.x,
                                            listRootOffset.y + change.position.y
                                        )
                                        if (!samePanelDragActivated) {
                                            // Single file: first movement activates DnD
                                            samePanelDragActivated = true
                                            currentHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            currentOnDragStarted?.invoke(
                                                if (currentSelectedPaths.isNotEmpty()) currentSelectedPaths.toList()
                                                else emptyList(),
                                                globalOffset
                                            )
                                        }
                                        // DnD active — update overlay position
                                        currentOnDragMoved?.invoke(globalOffset)
                                        // Local folder hover detection (same panel) with hysteresis
                                        val hitIndex = findItemIndexAtPosition(
                                            gridState.layoutInfo, change.position, currentGridColumns
                                        )
                                        if (hitIndex >= 0 && hitIndex < currentFilteredFiles.size) {
                                            val hoverEntry = currentFilteredFiles[hitIndex]
                                            if (hoverEntry.isDirectory && hoverEntry.path !in currentSelectedPaths) {
                                                lastHoveredFolderPath = hoverEntry.path
                                                currentOnDragHoverFolder?.invoke(hoverEntry.path)
                                            } else if (hoverEntry.path != lastHoveredFolderPath) {
                                                // Only clear when hovering over a different non-folder item
                                                lastHoveredFolderPath = null
                                                currentOnDragHoverFolder?.invoke(null)
                                            }
                                        }
                                        // Don't clear hover on gaps — keep last hovered folder
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
                                    if (isQuickSendDrag) {
                                        currentOnQuickSendEnd?.invoke()
                                    } else if (isCrossPanelDrag) {
                                        currentOnDragEnded?.invoke()
                                    } else if (isSamePanelDrag && samePanelDragActivated) {
                                        currentOnDragEnded?.invoke()
                                    }
                                    dragStartIndex = -1
                                    isCrossPanelDrag = false
                                    isQuickSendDrag = false
                                    isSamePanelDrag = false
                                    samePanelDragActivated = false
                                    lastHoveredFolderPath = null
                                },
                                onDragCancel = {
                                    if (isQuickSendDrag) {
                                        currentOnQuickSendEnd?.invoke()
                                    } else if (isCrossPanelDrag) {
                                        currentOnDragEnded?.invoke()
                                    } else if (isSamePanelDrag && samePanelDragActivated) {
                                        currentOnDragEnded?.invoke()
                                    }
                                    dragStartIndex = -1
                                    isCrossPanelDrag = false
                                    isQuickSendDrag = false
                                    isSamePanelDrag = false
                                    samePanelDragActivated = false
                                    lastHoveredFolderPath = null
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
                    ) { index, entry ->
                        val entryTagColors = fileTags[entry.path]
                            ?.mapNotNull { name -> tagDefinitions.find { it.name == name } }
                            ?.map { TagColors.palette.getOrElse(it.colorIndex) { TagColors.palette[0] } }
                            ?: emptyList()
                        FileListItem(
                            entry = entry,
                            isSelected = entry.path in state.selectedPaths,
                            isSelectionMode = state.isSelectionMode,
                            isRenaming = state.renamingPath == entry.path,
                            onClick = { if (isActive) onFileClick(entry) else onPanelTap() },
                            onLongClick = { onPanelTap(); onLongPressItem(entry) },
                            onIconClick = { if (isActive) onIconClick(entry) else onPanelTap() },
                            onRenameConfirm = onRenameConfirm,
                            onRenameCancel = onRenameCancel,
                            isGridMode = isGridMode,
                            tagColors = entryTagColors,
                            contentSnippet = if (state.searchInContent) state.contentSearchSnippets?.get(entry.path) else null,
                            searchQuery = if (state.searchInContent) state.searchQuery else "",
                            isDragHovered = hoveredFolderPath == entry.path && entry.isDirectory,
                            marqueeEnabled = marqueeEnabled,
                            folderSize = if (entry.isDirectory) folderSizeCache[entry.path] else null,
                            cloudAuthHeader = cloudAuthHeader,
                            archiveThumbnailCache = archiveThumbnailCache,
                            archivePath = state.archivePath,
                            archivePassword = state.archivePassword,
                            thumbnailVersion = state.thumbnailVersion,
                            isFocused = state.focusedIndex == index,
                            deepSearchPath = if (state.isSearchActive && state.searchQuery.isNotBlank()) {
                                val parentDir = java.io.File(entry.path).parent ?: ""
                                if (parentDir != state.currentPath && parentDir.startsWith(state.currentPath)) {
                                    parentDir.removePrefix(state.currentPath).removePrefix("/")
                                } else null
                            } else null,
                            onLoadProtectedThumbnail = onLoadProtectedThumbnail,
                            onDeepSearchPathClick = if (state.isSearchActive && state.searchQuery.isNotBlank()) {
                                val parentDir = java.io.File(entry.path).parent ?: ""
                                if (parentDir != state.currentPath && parentDir.startsWith(state.currentPath)) {
                                    {
                                        // Navigate to parent folder of found file
                                        val folderEntry = FileEntry(
                                            name = java.io.File(parentDir).name,
                                            path = parentDir,
                                            isDirectory = true,
                                            size = 0L,
                                            lastModified = 0L,
                                            extension = "",
                                            isHidden = false,
                                            childCount = 0
                                        )
                                        onFileClick(folderEntry)
                                    }
                                } else null
                            } else null
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
    gridColumns: Int,
    expandPx: Float = 12f
): Int {
    // Try to find the item under the touch point with expanded hit area
    for (item in layoutInfo.visibleItemsInfo) {
        val left = item.offset.x.toFloat() - expandPx
        val top = item.offset.y.toFloat() - expandPx
        val right = left + item.size.width.toFloat() + expandPx * 2
        val bottom = top + item.size.height.toFloat() + expandPx * 2
        if (position.x in left..right && position.y in top..bottom) {
            return item.index
        }
    }
    // No item directly under the touch point — empty space
    return -1
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
