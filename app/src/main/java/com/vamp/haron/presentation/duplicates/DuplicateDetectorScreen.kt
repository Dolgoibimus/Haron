package com.vamp.haron.presentation.duplicates

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamp.haron.common.util.toFileSize
import com.vamp.haron.domain.model.NavigationEvent
import com.vamp.haron.domain.usecase.DuplicateGroup
import com.vamp.haron.presentation.explorer.components.QuickPreviewDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateDetectorScreen(
    onBack: () -> Unit,
    onOpenMediaPlayer: (startIndex: Int) -> Unit = {},
    onOpenTextEditor: (filePath: String, fileName: String) -> Unit = { _, _ -> },
    onOpenGallery: (startIndex: Int) -> Unit = {},
    onOpenPdfReader: (filePath: String, fileName: String) -> Unit = { _, _ -> },
    onOpenArchiveViewer: (filePath: String, fileName: String) -> Unit = { _, _ -> },
    viewModel: DuplicateDetectorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is NavigationEvent.OpenMediaPlayer -> onOpenMediaPlayer(event.startIndex)
                is NavigationEvent.OpenTextEditor -> onOpenTextEditor(event.filePath, event.fileName)
                is NavigationEvent.OpenGallery -> onOpenGallery(event.startIndex)
                is NavigationEvent.OpenPdfReader -> onOpenPdfReader(event.filePath, event.fileName)
                is NavigationEvent.OpenArchiveViewer -> onOpenArchiveViewer(event.filePath, event.fileName)
                else -> {}
            }
        }
    }

    // QuickPreview dialog (full-featured like main screen)
    // All callbacks are always provided — QuickPreviewDialog decides which button
    // to show based on PreviewData type (TextPreview → Edit, ImagePreview → Gallery, etc.)
    val previewEntry = state.previewEntry
    if (previewEntry != null) {
        QuickPreviewDialog(
            entry = previewEntry,
            previewData = state.previewData,
            isLoading = state.previewLoading,
            error = state.previewError,
            onDismiss = { viewModel.dismissPreview() },
            onFullscreenPlay = { _ ->
                viewModel.dismissPreview()
                val idx = viewModel.buildPlaylistFromPreview(previewEntry, state.previewAdjacentFiles, state.previewCurrentIndex)
                onOpenMediaPlayer(idx)
            },
            onEdit = {
                viewModel.dismissPreview()
                onOpenTextEditor(previewEntry.path, previewEntry.name)
            },
            onOpenGallery = {
                viewModel.dismissPreview()
                val idx = viewModel.buildGalleryFromPreview(previewEntry, state.previewAdjacentFiles, state.previewCurrentIndex)
                onOpenGallery(idx)
            },
            onOpenPdf = {
                viewModel.dismissPreview()
                onOpenPdfReader(previewEntry.path, previewEntry.name)
            },
            onOpenArchive = {
                viewModel.dismissPreview()
                onOpenArchiveViewer(previewEntry.path, previewEntry.name)
            },
            adjacentFiles = state.previewAdjacentFiles,
            currentFileIndex = state.previewCurrentIndex,
            onFileChanged = { newIndex -> viewModel.onPreviewFileChanged(newIndex) },
            previewCache = state.previewCache
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (state.isOriginalFolderMode) "Папки-оригиналы" else "Дубликаты")
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.isOriginalFolderMode) viewModel.exitOriginalFolderMode()
                        else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    if (!state.isScanning && !state.isOriginalFolderMode && state.groups.isNotEmpty()) {
                        IconButton(onClick = { viewModel.toggleSelectAll() }) {
                            Icon(
                                Icons.Filled.SelectAll,
                                if (state.selectedPaths.isNotEmpty()) "Снять выделение" else "Выделить копии"
                            )
                        }
                        IconButton(onClick = {
                            val idx = viewModel.buildPlaylistFromAllGroups()
                            if (idx >= 0) onOpenMediaPlayer(idx)
                        }) {
                            Icon(Icons.Filled.PlayArrow, "Воспроизвести")
                        }
                        IconButton(onClick = { viewModel.startScan() }) {
                            Icon(Icons.Filled.Refresh, "Пересканировать")
                        }
                        Box {
                            IconButton(onClick = { showMenu = !showMenu }) {
                                Icon(Icons.Filled.MoreVert, "Меню")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Папки-оригиналы") },
                                    onClick = {
                                        showMenu = false
                                        viewModel.enterOriginalFolderMode()
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (state.isOriginalFolderMode) {
                FloatingActionButton(
                    onClick = { viewModel.exitOriginalFolderMode() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Done, "Готово")
                        Spacer(Modifier.width(8.dp))
                        Text("Готово")
                    }
                }
            } else if (state.selectedPaths.isNotEmpty() && !state.isDeleting) {
                FloatingActionButton(
                    onClick = { viewModel.deleteSelected() },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Delete, "Удалить")
                        Spacer(Modifier.width(8.dp))
                        Text("Удалить (${state.selectedPaths.size})")
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (state.isOriginalFolderMode) {
                // --- Original folder mode ---
                if (state.originalFolders.isNotEmpty()) {
                    Text(
                        "Отмечено: ${state.originalFolders.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.allFolderPaths, key = { it }) { folderPath ->
                        val isChecked = folderPath in state.originalFolders
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleOriginalFolder(folderPath) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { viewModel.toggleOriginalFolder(folderPath) },
                                modifier = Modifier.size(36.dp)
                            )
                            Icon(
                                Icons.Filled.Folder,
                                null,
                                Modifier.size(24.dp),
                                tint = if (isChecked) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    folderPath.substringAfterLast('/'),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    folderPath,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            } else {
                // --- Normal duplicate mode ---

                // Progress indicator
                if (state.isScanning) {
                    val progress = state.progress
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (progress != null) {
                            Text(
                                text = if (progress.phase == 1) "Фаза 1: Сканирование файлов..."
                                else "Фаза 2: Вычисление хешей...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(8.dp))
                            if (progress.totalFiles > 0) {
                                LinearProgressIndicator(
                                    progress = { progress.scannedFiles.toFloat() / progress.totalFiles },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "${progress.scannedFiles}/${progress.totalFiles}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    progress.currentFolder.substringAfterLast('/'),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                        } else {
                            CircularProgressIndicator()
                        }
                    }
                }

                // Summary card
                if (!state.isScanning && state.groups.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem("Групп", "${state.groups.size}")
                            StatItem("Файлов", "${state.totalDuplicateFiles}")
                            StatItem("Потрачено", state.totalWastedSpace.toFileSize())
                        }
                    }
                }

                // Empty state
                if (!state.isScanning && state.groups.isEmpty() && state.progress?.isComplete == true) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.CheckCircle, null,
                                Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text("Дубликатов не найдено", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                // Deleting indicator
                if (state.isDeleting) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Удаление...")
                    }
                }

                // Groups list
                if (!state.isScanning) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.groups, key = { it.hash }) { group ->
                            DuplicateGroupCard(
                                group = group,
                                isExpanded = state.expandedGroupHash == group.hash,
                                selectedPaths = state.selectedPaths,
                                originalPath = viewModel.getOriginalForGroup(group),
                                onToggleExpand = { viewModel.toggleGroup(group.hash) },
                                onToggleFile = { viewModel.toggleFileSelection(it) },
                                onKeepOldest = { viewModel.keepOldestInGroup(group.hash) },
                                onReassignOriginal = { path -> viewModel.reassignOriginal(group.hash, path) },
                                onPreviewFile = { path -> viewModel.loadPreview(path) }
                            )
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    isExpanded: Boolean,
    selectedPaths: Set<String>,
    originalPath: String?,
    onToggleExpand: () -> Unit,
    onToggleFile: (String) -> Unit,
    onKeepOldest: () -> Unit,
    onReassignOriginal: (String) -> Unit,
    onPreviewFile: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Description, null,
                Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${group.files.size} файлов \u00B7 ${group.size.toFileSize()} каждый",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Потрачено: ${group.wastedSpace.toFileSize()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            val groupSelectedCount = group.files.count { it.path in selectedPaths }
            if (groupSelectedCount > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Text("$groupSelectedCount")
                }
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                "Раскрыть",
                Modifier.size(24.dp)
            )
        }

        // Expanded files list
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onKeepOldest) {
                        Text("Оставить оригинал", style = MaterialTheme.typography.labelSmall)
                    }
                }
                group.files.forEach { file ->
                    val isOriginal = file.path == originalPath
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = file.path in selectedPaths,
                            onCheckedChange = { onToggleFile(file.path) },
                            modifier = Modifier.size(36.dp)
                        )
                        // Name + path: single tap = preview
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onPreviewFile(file.path) }
                        ) {
                            Text(
                                file.name,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                file.path.substringBeforeLast('/'),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        // Right zone: badge — long click = reassign original
                        Box(
                            modifier = Modifier
                                .width(76.dp)
                                .combinedClickable(
                                    onClick = { },
                                    onLongClick = { onReassignOriginal(file.path) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isOriginal) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ) {
                                    Text("Оригинал")
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
