package com.vamp.haron.presentation.archive

import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamp.haron.R
import com.vamp.haron.common.util.toFileSize
import com.vamp.haron.domain.model.ArchiveEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveViewerScreen(
    archivePath: String,
    archiveName: String,
    onBack: () -> Unit,
    viewModel: ArchiveViewerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(archivePath) {
        viewModel.init(archivePath, archiveName)
    }

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler(enabled = state.isSelectionMode || state.virtualPath.isNotEmpty()) {
        when {
            state.isSelectionMode -> viewModel.clearSelection()
            state.virtualPath.isNotEmpty() -> viewModel.navigateUp()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = archiveName.ifEmpty { stringResource(R.string.archive_title) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.isSelectionMode) viewModel.clearSelection()
                        else onBack()
                    }) {
                        Icon(
                            if (state.isSelectionMode) Icons.Filled.Close
                            else Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (state.isSelectionMode) {
                        IconButton(onClick = {
                            if (state.selectedEntries.size == state.entries.size) {
                                viewModel.clearSelection()
                            } else {
                                state.entries.forEach { viewModel.toggleSelection(it.fullPath) }
                            }
                        }) {
                            Icon(Icons.Filled.SelectAll, stringResource(R.string.select_all))
                        }
                    }
                }
            )
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Extract progress
                val progress = state.extractProgress
                if (progress != null && !progress.isComplete) {
                    LinearProgressIndicator(
                        progress = { if (progress.total > 0) progress.current.toFloat() / progress.total else 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(R.string.extraction_progress_format, progress.current, progress.total, progress.fileName),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                // Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val downloadDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    ).absolutePath

                    Button(
                        onClick = { viewModel.extractAll(downloadDir) },
                        modifier = Modifier.weight(1f),
                        enabled = state.extractProgress == null
                    ) {
                        Icon(Icons.Filled.Unarchive, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.extract_all))
                    }

                    if (state.isSelectionMode) {
                        OutlinedButton(
                            onClick = { viewModel.extractSelected(downloadDir) },
                            modifier = Modifier.weight(1f),
                            enabled = state.extractProgress == null && state.selectedEntries.isNotEmpty()
                        ) {
                            Text(stringResource(R.string.extract_count_format, state.selectedEntries.size))
                        }
                    }
                }

                // Info
                if (!state.isLoading && state.entries.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.items_count, state.entries.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Breadcrumbs
            if (state.breadcrumbs.size > 1) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(state.breadcrumbs) { crumb ->
                        val isLast = crumb == state.breadcrumbs.last()
                        val label = if (crumb.isEmpty()) "/" else crumb.substringAfterLast('/')
                        Text(
                            text = if (isLast) label else "$label >",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                            color = if (isLast) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable(enabled = !isLast) {
                                    viewModel.navigateToBreadcrumb(crumb)
                                }
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }
                }
                HorizontalDivider()
            }

            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = state.error!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(onClick = onBack) {
                                Text(stringResource(R.string.back))
                            }
                        }
                    }
                }
                state.entries.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.empty_folder_item),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.entries, key = { it.fullPath }) { entry ->
                            ArchiveEntryItem(
                                entry = entry,
                                isSelected = entry.fullPath in state.selectedEntries,
                                isSelectionMode = state.isSelectionMode,
                                onClick = {
                                    if (state.isSelectionMode) {
                                        viewModel.toggleSelection(entry.fullPath)
                                    } else if (entry.isDirectory) {
                                        viewModel.navigateInto(entry.fullPath)
                                    }
                                },
                                onLongClick = {
                                    viewModel.toggleSelection(entry.fullPath)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArchiveEntryItem(
    entry: ArchiveEntry,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val bgColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode && isSelected) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        } else {
            Icon(
                imageVector = if (entry.isDirectory) Icons.Filled.Folder
                else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!entry.isDirectory) {
                Text(
                    text = entry.size.toFileSize(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
