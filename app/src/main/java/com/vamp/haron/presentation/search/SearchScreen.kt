package com.vamp.haron.presentation.search

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.ui.graphics.Color
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamp.haron.R
import com.vamp.haron.common.util.toFileSize
import com.vamp.haron.presentation.common.ProgressInfoRow
import com.vamp.haron.common.util.toRelativeDate
import com.vamp.haron.data.db.entity.FileIndexEntity
import com.vamp.haron.domain.model.FileEntry
import com.vamp.haron.domain.model.SearchSource
import com.vamp.haron.domain.model.WebSearchResult
import com.vamp.haron.domain.repository.DateFilter
import com.vamp.haron.domain.repository.FileCategory
import com.vamp.haron.domain.repository.IndexMode
import com.vamp.haron.domain.repository.SizeFilter
import com.vamp.haron.presentation.explorer.components.QuickPreviewDialog
import com.vamp.haron.service.DownloadProgress
import com.vamp.haron.service.WebDownloadService

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
    onOpenArchiveViewer: (filePath: String, fileName: String) -> Unit = { _, _ -> },
    onOpenDocumentViewer: (filePath: String, fileName: String) -> Unit = { _, _ -> }
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(state.selectedTab) {
        if (state.selectedTab == 0) {
            focusRequester.requestFocus()
        }
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
                        onOpenArchiveViewer = onOpenArchiveViewer,
                        onOpenDocumentViewer = onOpenDocumentViewer
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
                    onOpenArchiveViewer = onOpenArchiveViewer,
                    onOpenDocumentViewer = onOpenDocumentViewer
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
                    onOpenArchiveViewer = onOpenArchiveViewer,
                    onOpenDocumentViewer = onOpenDocumentViewer
                )
            },
            onOpenPdf = {
                viewModel.dismissPreview()
                onOpenPdfReader(preview.entry.path, preview.entry.name)
            },
            onOpenDocument = {
                viewModel.dismissPreview()
                onOpenDocumentViewer(preview.entry.path, preview.entry.name)
            },
            onOpenArchive = {
                viewModel.dismissPreview()
                onOpenArchiveViewer(preview.entry.path, preview.entry.name)
            }
        )
    }

    // Block system back — exit only via title tap
    BackHandler { /* consume, do nothing */ }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.global_search),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onBack() }
                    )
                },
                actions = {
                    if (state.selectedTab == 0) {
                        val isRunning = state.indexProgress.isRunning
                        val activeMode = state.indexProgress.mode

                        if (isRunning && activeMode == IndexMode.BASIC) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp).padding(4.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(
                                onClick = { viewModel.startBasicIndex() },
                                enabled = !isRunning
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.index_basic))
                            }
                        }

                        if (isRunning && activeMode == IndexMode.MEDIA) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp).padding(4.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(
                                onClick = { viewModel.startMediaIndex() },
                                enabled = !isRunning
                            ) {
                                Icon(Icons.Filled.MusicNote, contentDescription = stringResource(R.string.index_media))
                            }
                        }

                        if (isRunning && activeMode == IndexMode.VISUAL) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp).padding(4.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(
                                onClick = { viewModel.startVisualIndex() },
                                enabled = !isRunning
                            ) {
                                Icon(Icons.Filled.PhotoCamera, contentDescription = stringResource(R.string.index_visual))
                            }
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
            // Tab row
            PrimaryTabRow(selectedTabIndex = state.selectedTab) {
                Tab(
                    selected = state.selectedTab == 0,
                    onClick = { viewModel.setSelectedTab(0) },
                    text = { Text(stringResource(R.string.search_tab_device)) },
                    icon = { Icon(Icons.Filled.PhoneAndroid, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = state.selectedTab == 1,
                    onClick = { viewModel.setSelectedTab(1) },
                    text = { Text(stringResource(R.string.search_tab_internet)) },
                    icon = { Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            if (state.selectedTab == 0) {
                DeviceSearchTab(state, viewModel, focusRequester, keyboardController, context, listState)
            } else {
                InternetSearchTab(state, viewModel, context)
            }
        }
    }
}

@Composable
private fun DeviceSearchTab(
    state: SearchUiState,
    viewModel: SearchViewModel,
    focusRequester: FocusRequester,
    keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?,
    context: android.content.Context,
    listState: androidx.compose.foundation.lazy.LazyListState
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
            Row {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setQuery("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.clear))
                    }
                }
                IconButton(onClick = { viewModel.setSearchMode(false) }) {
                    Icon(
                        Icons.Filled.TextFields,
                        contentDescription = stringResource(R.string.search_by_name),
                        tint = if (!state.searchInContent) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { viewModel.setSearchMode(true) }) {
                    Icon(
                        Icons.Filled.FindInPage,
                        contentDescription = stringResource(R.string.search_by_content),
                        tint = if (state.searchInContent) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
        val modeText = when (state.indexProgress.mode) {
            IndexMode.BASIC -> stringResource(R.string.indexing_basic)
            IndexMode.MEDIA -> stringResource(R.string.indexing_media)
            IndexMode.VISUAL -> stringResource(R.string.indexing_visual)
            null -> stringResource(
                R.string.indexing_progress,
                state.indexProgress.processedFiles,
                state.indexProgress.totalFiles
            )
        }
        Text(
            text = if (state.indexProgress.mode != null) {
                "$modeText ${state.indexProgress.processedFiles} / ${state.indexProgress.totalFiles}"
            } else modeText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        state.indexProgress.currentFileName?.let { fileName ->
            Text(
                text = fileName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    } else if (state.indexedCount > 0) {
        val timeText = state.lastIndexedTime?.toRelativeDate(context) ?: ""
        Text(
            text = stringResource(R.string.index_status, state.indexedCount, timeText),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )
        if (state.contentIndexSize > 0) {
            Text(
                text = stringResource(R.string.content_index_size, state.contentIndexSize.toFileSize()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }
    } else {
        Text(
            text = stringResource(R.string.index_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }

    // Skipped files indicator
    if (state.indexProgress.skippedFiles.isNotEmpty() && !state.indexProgress.isRunning) {
        var showSkipped by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showSkipped = !showSkipped }
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.skipped_files, state.indexProgress.skippedFiles.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (showSkipped) Icons.Filled.KeyboardArrowUp
                              else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
        AnimatedVisibility(
            visible = showSkipped,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                state.indexProgress.skippedFiles.forEach { path ->
                    Text(
                        text = path.substringAfterLast("/"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    // Content search hint
    AnimatedVisibility(
        visible = state.searchInContent,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Text(
            text = stringResource(R.string.content_search_hint),
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
                    searchInContent = state.searchInContent,
                    query = state.query,
                    isSnippetExpanded = state.expandedSnippetPath == entity.path,
                    onIconClick = { viewModel.onIconClick(entity) },
                    onNameClick = { viewModel.onNameClick(entity) },
                    onNameLongClick = { viewModel.onNameLongPress(entity) },
                    onToggleSnippet = { viewModel.toggleSnippet(entity.path) }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InternetSearchTab(
    state: SearchUiState,
    viewModel: SearchViewModel,
    context: android.content.Context
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val downloads by WebDownloadService.downloads.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Search field + button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.webQuery,
                onValueChange = { viewModel.setWebQuery(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.web_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Language, contentDescription = null) },
                trailingIcon = {
                    if (state.webQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setWebQuery("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.clear))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboardController?.hide()
                    viewModel.searchWeb()
                })
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    keyboardController?.hide()
                    viewModel.searchWeb()
                },
                enabled = state.webQuery.isNotBlank() && !state.isWebSearching
            ) {
                Text(stringResource(R.string.web_search_button))
            }
        }

        // Active downloads
        val activeDownloads = downloads.filter { !it.isComplete || it.error != null }
        val completedDownloads = downloads.filter { it.isComplete && it.error == null }
        if (activeDownloads.isNotEmpty() || completedDownloads.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                activeDownloads.forEach { dl ->
                    DownloadProgressItem(dl)
                }
                completedDownloads.take(3).forEach { dl ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = dl.fileName,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Loading / Error / Results
        if (state.isWebSearching) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.web_searching),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (state.webError != null && state.webResults.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.webError,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (state.webResults.isNotEmpty()) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(
                    items = state.webResults,
                    key = { it.url }
                ) { result ->
                    WebSearchResultItem(
                        result = result,
                        onTap = {
                            viewModel.downloadFile(result)
                            Toast.makeText(context, context.getString(R.string.web_download_started), Toast.LENGTH_SHORT).show()
                        },
                        onLongTap = {
                            viewModel.copyLink(result)
                            Toast.makeText(context, context.getString(R.string.web_link_copied), Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadProgressItem(dl: DownloadProgress) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dl.fileName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (dl.total > 0) {
                LinearProgressIndicator(
                    progress = { (dl.current.toFloat() / dl.total).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
            }
            ProgressInfoRow(
                speed = if (dl.speed > 0) "${dl.speed.toFileSize()}/s" else "",
                percent = if (dl.total > 0) "${(dl.current * 100 / dl.total).toInt()}%" else ""
            )
        }
        if (dl.error != null) {
            Icon(
                Icons.Filled.Cancel,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WebSearchResultItem(
    result: WebSearchResult,
    onTap: () -> Unit,
    onLongTap: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongTap
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Source icon
        Icon(
            imageVector = when (result.source) {
                SearchSource.OPEN_DIRECTORY -> Icons.Filled.Folder
                SearchSource.TORRENT -> Icons.Filled.Link
                SearchSource.INTERNET_ARCHIVE -> Icons.Filled.Archive
            },
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = when (result.source) {
                SearchSource.OPEN_DIRECTORY -> MaterialTheme.colorScheme.primary
                SearchSource.TORRENT -> MaterialTheme.colorScheme.error
                SearchSource.INTERNET_ARCHIVE -> MaterialTheme.colorScheme.tertiary
            }
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Source badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = when (result.source) {
                        SearchSource.OPEN_DIRECTORY -> MaterialTheme.colorScheme.primaryContainer
                        SearchSource.TORRENT -> MaterialTheme.colorScheme.errorContainer
                        SearchSource.INTERNET_ARCHIVE -> MaterialTheme.colorScheme.tertiaryContainer
                    }
                ) {
                    Text(
                        text = when (result.source) {
                            SearchSource.OPEN_DIRECTORY -> stringResource(R.string.web_source_od)
                            SearchSource.TORRENT -> stringResource(R.string.web_source_torrent)
                            SearchSource.INTERNET_ARCHIVE -> stringResource(R.string.web_source_archive)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
                if (result.size > 0) {
                    Text(
                        text = result.size.toFileSize(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (result.seeders > 0) {
                    Text(
                        text = stringResource(R.string.web_seeders, result.seeders),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (result.extension.isNotEmpty()) {
                    Text(
                        text = ".${result.extension}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Icon(
            Icons.Filled.Download,
            contentDescription = stringResource(R.string.web_download_started),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
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
    onOpenArchiveViewer: (String, String) -> Unit,
    onOpenDocumentViewer: (String, String) -> Unit = { p, n -> onOpenPdfReader(p, n) }
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
            "properties", "env", "toml", "sql", "gradle",
            "kt", "java", "py", "js", "ts", "html", "css", "sh", "bat", "c", "cpp", "h") -> {
            onOpenTextEditor(entry.path, entry.name)
        }
        ext == "pdf" -> {
            onOpenPdfReader(entry.path, entry.name)
        }
        ext in listOf("doc", "docx", "odt", "rtf", "fb2") -> {
            onOpenDocumentViewer(entry.path, entry.name)
        }
        ext in listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz", "tbz2", "txz", "gtar") -> {
            onOpenArchiveViewer(entry.path, entry.name)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultItem(
    entity: FileIndexEntity,
    searchInContent: Boolean,
    query: String,
    isSnippetExpanded: Boolean,
    onIconClick: () -> Unit,
    onNameClick: () -> Unit,
    onNameLongClick: () -> Unit,
    onToggleSnippet: () -> Unit
) {
    val hasSnippet = searchInContent && entity.contentSnippet.isNotBlank()
    Column(modifier = Modifier.fillMaxWidth()) {
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
            if (hasSnippet) {
                IconButton(
                    onClick = onToggleSnippet,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isSnippetExpanded) Icons.Filled.KeyboardArrowUp
                                      else Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.expand_snippet),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        // Snippet
        if (hasSnippet) {
            AnimatedVisibility(
                visible = isSnippetExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                val snippetText = buildSnippetText(entity.contentSnippet, query)
                Text(
                    text = snippetText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 60.dp, end = 12.dp, bottom = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun buildSnippetText(
    snippet: String,
    query: String,
    highlightColor: Color = MaterialTheme.colorScheme.primary
) = buildAnnotatedString {
    if (query.isBlank()) {
        append(snippet.take(180))
        return@buildAnnotatedString
    }
    val lowerSnippet = snippet.lowercase()
    val lowerQuery = query.lowercase()
    val idx = lowerSnippet.indexOf(lowerQuery)
    if (idx < 0) {
        append(snippet.take(180))
        return@buildAnnotatedString
    }
    // Show ±60 chars around the match
    val start = (idx - 60).coerceAtLeast(0)
    val end = (idx + query.length + 60).coerceAtMost(snippet.length)
    val window = snippet.substring(start, end)
    val matchStart = idx - start
    val matchEnd = matchStart + query.length

    if (start > 0) append("…")
    append(window.substring(0, matchStart))
    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = highlightColor)) {
        append(window.substring(matchStart, matchEnd))
    }
    append(window.substring(matchEnd))
    if (end < snippet.length) append("…")
}

@Composable
private fun getFileIcon(entity: FileIndexEntity) = when {
    entity.isDirectory -> Icons.Filled.Folder
    entity.extension in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "heic", "heif") -> Icons.Filled.Image
    entity.extension in listOf("mp3", "wav", "ogg", "flac", "aac", "wma", "m4a", "opus") -> Icons.Filled.AudioFile
    entity.extension in listOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "3gp") -> Icons.Filled.VideoFile
    entity.extension in listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "rtf") -> Icons.Filled.Description
    entity.extension in listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz", "tbz2", "txz", "gtar", "apk") -> Icons.Filled.Archive
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
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                else Color.Transparent,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.outline
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
