package com.vamp.haron.presentation.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamp.haron.R
import com.vamp.haron.common.util.toFileSize
import com.vamp.haron.common.util.toRelativeDate
import com.vamp.haron.data.db.entity.FileIndexEntity
import com.vamp.haron.domain.model.FileEntry
import com.vamp.haron.domain.repository.DateFilter
import com.vamp.haron.domain.repository.FileCategory
import com.vamp.haron.domain.repository.SizeFilter
import com.vamp.haron.presentation.explorer.components.QuickPreviewDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToFile: (parentPath: String) -> Unit = {},
    onOpenMediaPlayer: (startIndex: Int) -> Unit = {},
    onOpenTextEditor: (filePath: String, fileName: String) -> Unit = { _, _ -> },
    onOpenGallery: (startIndex: Int) -> Unit = {},
    onOpenPdfReader: (filePath: String, fileName: String) -> Unit = { _, _ -> },
    onOpenArchiveViewer: (filePath: String, fileName: String) -> Unit = { _, _ -> }
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is SearchNavigationEvent.NavigateToFile -> {
                    onNavigateToFile(event.parentPath)
                }
                is SearchNavigationEvent.OpenFile -> {
                    openFileByType(
                        entry = event.entry,
                        onOpenMediaPlayer = onOpenMediaPlayer,
                        onOpenTextEditor = onOpenTextEditor,
                        onOpenGallery = onOpenGallery,
                        onOpenPdfReader = onOpenPdfReader,
                        onOpenArchiveViewer = onOpenArchiveViewer
                    )
                }
            }
        }
    }

    // Pagination trigger
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= state.results.size - 20 && state.hasMore && !state.isSearching
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    // QuickPreview dialog — single file, no pager (VLC can't handle multiple video surfaces in pager)
    val preview = state.previewDialog
    if (preview != null) {
        QuickPreviewDialog(
            entry = preview.entry,
            previewData = preview.previewData,
            isLoading = preview.isLoading,
            error = preview.error,
            adjacentFiles = listOf(preview.entry),
            currentFileIndex = 0,
            onDismiss = { viewModel.dismissPreview() },
            onFullscreenPlay = { _ ->
                viewModel.dismissPreview()
                openFileByType(
                    entry = preview.entry,
                    onOpenMediaPlayer = onOpenMediaPlayer,
                    onOpenTextEditor = onOpenTextEditor,
                    onOpenGallery = onOpenGallery,
                    onOpenPdfReader = onOpenPdfReader,
                    onOpenArchiveViewer = onOpenArchiveViewer
                )
            },
            onEdit = {
                viewModel.dismissPreview()
                onOpenTextEditor(preview.entry.path, preview.entry.name)
            },
            onOpenGallery = {
                viewModel.dismissPreview()
                openFileByType(
                    entry = preview.entry,
                    onOpenMediaPlayer = onOpenMediaPlayer,
                    onOpenTextEditor = onOpenTextEditor,
                    onOpenGallery = onOpenGallery,
                    onOpenPdfReader = onOpenPdfReader,
                    onOpenArchiveViewer = onOpenArchiveViewer
                )
            },
            onOpenPdf = {
                viewModel.dismissPreview()
                onOpenPdfReader(preview.entry.path, preview.entry.name)
            },
            onOpenArchive = {
                viewModel.dismissPreview()
                onOpenArchiveViewer(preview.entry.path, preview.entry.name)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.global_search)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (state.indexProgress.isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = { viewModel.startIndexing() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.reindex))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search field
            OutlinedTextField(
                value = state.query,
                onValueChange = { viewModel.setQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .focusRequester(focusRequester),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.clear))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() })
            )

            // Index status
            if (state.indexProgress.isRunning) {
                LinearProgressIndicator(
                    progress = {
                        if (state.indexProgress.totalFiles > 0) {
                            state.indexProgress.processedFiles.toFloat() / state.indexProgress.totalFiles
                        } else 0f
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
                Text(
                    text = stringResource(
                        R.string.indexing_progress,
                        state.indexProgress.processedFiles,
                        state.indexProgress.totalFiles
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            } else if (state.indexedCount > 0) {
                val timeText = state.lastIndexedTime?.toRelativeDate(context) ?: ""
                Text(
                    text = stringResource(R.string.index_status, state.indexedCount, timeText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            } else {
                Text(
                    text = stringResource(R.string.index_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Category chips
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ChipItem(stringResource(R.string.filter_all), state.category == FileCategory.ALL) {
                    viewModel.setCategory(FileCategory.ALL)
                }
                ChipItem(stringResource(R.string.filter_documents), state.category == FileCategory.DOCUMENTS) {
                    viewModel.setCategory(FileCategory.DOCUMENTS)
                }
                ChipItem(stringResource(R.string.filter_images), state.category == FileCategory.IMAGES) {
                    viewModel.setCategory(FileCategory.IMAGES)
                }
                ChipItem(stringResource(R.string.filter_audio), state.category == FileCategory.AUDIO) {
                    viewModel.setCategory(FileCategory.AUDIO)
                }
                ChipItem(stringResource(R.string.filter_video), state.category == FileCategory.VIDEO) {
                    viewModel.setCategory(FileCategory.VIDEO)
                }
                ChipItem(stringResource(R.string.filter_archives), state.category == FileCategory.ARCHIVES) {
                    viewModel.setCategory(FileCategory.ARCHIVES)
                }
            }

            // Size filter chips
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ChipItem("< 1 MB", state.sizeFilter == SizeFilter.UNDER_1MB) {
                    viewModel.setSizeFilter(if (state.sizeFilter == SizeFilter.UNDER_1MB) SizeFilter.ALL else SizeFilter.UNDER_1MB)
                }
                ChipItem("1-10 MB", state.sizeFilter == SizeFilter.MB_1_10) {
                    viewModel.setSizeFilter(if (state.sizeFilter == SizeFilter.MB_1_10) SizeFilter.ALL else SizeFilter.MB_1_10)
                }
                ChipItem("10-100 MB", state.sizeFilter == SizeFilter.MB_10_100) {
                    viewModel.setSizeFilter(if (state.sizeFilter == SizeFilter.MB_10_100) SizeFilter.ALL else SizeFilter.MB_10_100)
                }
                ChipItem("100M-1G", state.sizeFilter == SizeFilter.MB_100_1GB) {
                    viewModel.setSizeFilter(if (state.sizeFilter == SizeFilter.MB_100_1GB) SizeFilter.ALL else SizeFilter.MB_100_1GB)
                }
                ChipItem("> 1 GB", state.sizeFilter == SizeFilter.OVER_1GB) {
                    viewModel.setSizeFilter(if (state.sizeFilter == SizeFilter.OVER_1GB) SizeFilter.ALL else SizeFilter.OVER_1GB)
                }
            }

            // Date filter chips
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ChipItem(stringResource(R.string.filter_today), state.dateFilter == DateFilter.TODAY) {
                    viewModel.setDateFilter(if (state.dateFilter == DateFilter.TODAY) DateFilter.ALL else DateFilter.TODAY)
                }
                ChipItem(stringResource(R.string.filter_week), state.dateFilter == DateFilter.WEEK) {
                    viewModel.setDateFilter(if (state.dateFilter == DateFilter.WEEK) DateFilter.ALL else DateFilter.WEEK)
                }
                ChipItem(stringResource(R.string.filter_month), state.dateFilter == DateFilter.MONTH) {
                    viewModel.setDateFilter(if (state.dateFilter == DateFilter.MONTH) DateFilter.ALL else DateFilter.MONTH)
                }
                ChipItem(stringResource(R.string.filter_year), state.dateFilter == DateFilter.YEAR) {
                    viewModel.setDateFilter(if (state.dateFilter == DateFilter.YEAR) DateFilter.ALL else DateFilter.YEAR)
                }
                ChipItem(stringResource(R.string.filter_older), state.dateFilter == DateFilter.OLDER) {
                    viewModel.setDateFilter(if (state.dateFilter == DateFilter.OLDER) DateFilter.ALL else DateFilter.OLDER)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Results
            if (state.isSearching && state.results.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (state.results.isEmpty() && state.query.isNotBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_results),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = state.results,
                        key = { it.path }
                    ) { entity ->
                        SearchResultItem(
                            entity = entity,
                            onIconClick = { viewModel.onIconClick(entity) },
                            onNameClick = { viewModel.onNameClick(entity) },
                            onNameLongClick = { viewModel.onNameLongPress(entity) }
                        )
                    }

                    if (state.isSearching && state.results.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Route file opening based on type — mirrors ExplorerViewModel.onFileClick logic.
 */
private fun openFileByType(
    entry: FileEntry,
    onOpenMediaPlayer: (Int) -> Unit,
    onOpenTextEditor: (String, String) -> Unit,
    onOpenGallery: (Int) -> Unit,
    onOpenPdfReader: (String, String) -> Unit,
    onOpenArchiveViewer: (String, String) -> Unit
) {
    val ext = entry.extension.lowercase()
    when {
        // Audio only — video excluded from search to avoid VLC single-surface conflicts
        ext in listOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "wma", "opus") -> {
            com.vamp.haron.domain.model.PlaylistHolder.items = listOf(
                com.vamp.haron.domain.model.PlaylistHolder.PlaylistItem(
                    filePath = entry.path, fileName = entry.name,
                    fileType = "audio"
                )
            )
            com.vamp.haron.domain.model.PlaylistHolder.startIndex = 0
            onOpenMediaPlayer(0)
        }
        ext in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg") -> {
            com.vamp.haron.domain.model.GalleryHolder.items = listOf(
                com.vamp.haron.domain.model.GalleryHolder.GalleryItem(
                    filePath = entry.path, fileName = entry.name, fileSize = entry.size
                )
            )
            com.vamp.haron.domain.model.GalleryHolder.startIndex = 0
            onOpenGallery(0)
        }
        ext in listOf("txt", "md", "log", "json", "xml", "yml", "yaml", "conf", "cfg", "ini",
            "properties", "env", "toml", "fb2", "sql", "gradle",
            "kt", "java", "py", "js", "ts", "html", "css", "sh", "bat", "c", "cpp", "h") -> {
            onOpenTextEditor(entry.path, entry.name)
        }
        ext in listOf("pdf", "doc", "docx", "odt", "rtf") -> {
            onOpenPdfReader(entry.path, entry.name)
        }
        ext in listOf("zip", "rar", "7z", "tar", "gz", "bz2") -> {
            onOpenArchiveViewer(entry.path, entry.name)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultItem(
    entity: FileIndexEntity,
    onIconClick: () -> Unit,
    onNameClick: () -> Unit,
    onNameLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon: tap → preview (for files), tap → navigate (for folders)
        Icon(
            imageVector = getFileIcon(entity),
            contentDescription = null,
            modifier = Modifier
                .size(36.dp)
                .clickable { onIconClick() },
            tint = getFileIconTint(entity)
        )
        Spacer(modifier = Modifier.width(12.dp))
        // Name area: tap → open file, long tap → go to location
        Column(
            modifier = Modifier
                .weight(1f)
                .combinedClickable(
                    onClick = onNameClick,
                    onLongClick = onNameLongClick
                )
        ) {
            Text(
                text = entity.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = entity.parentPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            if (!entity.isDirectory) {
                Text(
                    text = entity.size.toFileSize(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = entity.lastModified.toRelativeDate(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun getFileIcon(entity: FileIndexEntity) = when {
    entity.isDirectory -> Icons.Filled.Folder
    entity.extension in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "heic", "heif") -> Icons.Filled.Image
    entity.extension in listOf("mp3", "wav", "ogg", "flac", "aac", "wma", "m4a", "opus") -> Icons.Filled.AudioFile
    entity.extension in listOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "3gp") -> Icons.Filled.VideoFile
    entity.extension in listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "rtf") -> Icons.Filled.Description
    entity.extension in listOf("zip", "rar", "7z", "tar", "gz", "apk") -> Icons.Filled.Archive
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

@Composable
private fun getFileIconTint(entity: FileIndexEntity) = when {
    entity.isDirectory -> MaterialTheme.colorScheme.primary
    entity.extension in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "heic", "heif") -> MaterialTheme.colorScheme.tertiary
    entity.extension in listOf("mp3", "wav", "ogg", "flac", "aac", "wma", "m4a", "opus") -> MaterialTheme.colorScheme.secondary
    entity.extension in listOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "3gp") -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun ChipItem(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}
