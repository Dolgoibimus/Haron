package com.vamp.haron.presentation.explorer.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.DeleteOutline
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vamp.haron.data.model.SortOrder
import com.vamp.haron.domain.model.FileEntry
import com.vamp.haron.presentation.explorer.state.PanelUiState

@Composable
fun FilePanel(
    state: PanelUiState,
    isActive: Boolean,
    canNavigateUp: Boolean,
    isFavorite: Boolean,
    onFileClick: (FileEntry) -> Unit,
    onNavigateUp: () -> Unit,
    onSortChanged: (SortOrder) -> Unit,
    onToggleHidden: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onPanelTap: () -> Unit,
    onSearchChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShowFavorites: () -> Unit,
    onSelectRange: (fromIndex: Int, toIndex: Int) -> Unit,
    onLongPressItem: (FileEntry) -> Unit,
    onRenameConfirm: (String) -> Unit,
    onRenameCancel: () -> Unit,
    onCreateNew: () -> Unit,
    onShowTrash: () -> Unit,
    onDragStarted: ((List<String>, Offset) -> Unit)? = null,
    onDragMoved: ((Offset) -> Unit)? = null,
    onDragEnded: (() -> Unit)? = null,
    isDragTarget: Boolean = false,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        isDragTarget -> MaterialTheme.colorScheme.tertiary
        isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    }
    val borderWidth = if (isDragTarget) 3.dp else if (isActive) 2.dp else 1.dp

    var showOverflow by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }

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
                // Navigation icon
                when {
                    showSearch -> {
                        IconButton(
                            onClick = {
                                showSearch = false
                                onClearSearch()
                            },
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
                            onClick = onClearSelection,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Отменить выделение",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    canNavigateUp -> {
                        IconButton(
                            onClick = onNavigateUp,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Назад",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    else -> {
                        Spacer(Modifier.width(4.dp))
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
                            modifier = Modifier.weight(1f)
                        )
                    }
                    state.isSelectionMode -> {
                        val selected = state.files.filter { it.path in state.selectedPaths }
                        val dirs = selected.count { it.isDirectory }
                        val files = selected.size - dirs
                        Text(
                            text = "Выбрано: ${formatFileCount(dirs, files)}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    else -> {
                        Text(
                            text = state.displayPath.substringAfterLast('/').ifEmpty { "Хранилище" },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onPanelTap() }
                        )
                    }
                }

                // Actions
                when {
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
                    }
                    else -> {
                        IconButton(
                            onClick = {
                                showSearch = true
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
                                onShowFavorites()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = "Избранное",
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
                                DropdownMenuItem(
                                    text = { Text("Корзина") },
                                    onClick = {
                                        onShowTrash()
                                        showOverflow = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.DeleteOutline,
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

        if (!state.isSelectionMode && !showSearch) {
            Box(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onPanelTap() }
            ) {
                BreadcrumbBar(displayPath = state.displayPath)
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
                val listState = rememberLazyListState()
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

                // Track list position in root for global offset calc
                var listRootOffset by remember { mutableStateOf(Offset.Zero) }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onGloballyPositioned { coords ->
                            listRootOffset = coords.positionInRoot()
                        }
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    currentOnPanelTap()
                                    val index = findItemIndexAtOffset(
                                        listState.layoutInfo, offset.y
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
                                        val currentIndex = findItemIndexAtOffset(
                                            listState.layoutInfo, change.position.y
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
                        key = { _, entry -> entry.path }
                    ) { _, entry ->
                        FileListItem(
                            entry = entry,
                            isSelected = entry.path in state.selectedPaths,
                            isSelectionMode = state.isSelectionMode,
                            isRenaming = state.renamingPath == entry.path,
                            onClick = { onFileClick(entry) },
                            onLongClick = { onLongPressItem(entry) },
                            onRenameConfirm = onRenameConfirm,
                            onRenameCancel = onRenameCancel
                        )
                    }
                }
            }
        }
    }
}

private fun findItemIndexAtOffset(
    layoutInfo: androidx.compose.foundation.lazy.LazyListLayoutInfo,
    yOffset: Float
): Int {
    for (item in layoutInfo.visibleItemsInfo) {
        val top = item.offset.toFloat()
        val bottom = (item.offset + item.size).toFloat()
        if (yOffset in top..bottom) {
            return item.index
        }
    }
    val first = layoutInfo.visibleItemsInfo.firstOrNull() ?: return -1
    val last = layoutInfo.visibleItemsInfo.lastOrNull() ?: return -1
    return if (yOffset < first.offset) first.index else last.index
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
