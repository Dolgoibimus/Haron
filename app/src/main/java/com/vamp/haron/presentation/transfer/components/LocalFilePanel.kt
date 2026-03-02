package com.vamp.haron.presentation.transfer.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vamp.haron.R
import com.vamp.haron.presentation.transfer.state.LocalFileEntry
import com.vamp.haron.presentation.transfer.state.LocalPanelState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalFilePanel(
    state: LocalPanelState,
    isActive: Boolean,
    onPanelTap: () -> Unit,
    onFileTap: (LocalFileEntry) -> Unit,
    onFileLongPress: (LocalFileEntry) -> Unit,
    onBreadcrumbTap: (Int) -> Unit,
    breadcrumbs: List<String>,
    hasSmb: Boolean,
    onUploadToSmb: () -> Unit,
    onCreateFolder: () -> Unit,
    onDeleteSelected: () -> Unit,
    onRenameSelected: () -> Unit,
    onClearSelection: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isActive) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (isActive) 2.dp else 1.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
    ) {
        // Breadcrumb
        LocalBreadcrumb(crumbs = breadcrumbs, onCrumbTap = { onPanelTap(); onBreadcrumbTap(it) })

        // Action bar
        LocalActionBar(
            hasSelection = state.selectedPaths.isNotEmpty(),
            selectionCount = state.selectedPaths.size,
            hasSmb = hasSmb,
            onUploadToSmb = { onPanelTap(); onUploadToSmb() },
            onCreateFolder = { onPanelTap(); onCreateFolder() },
            onDeleteSelected = { onPanelTap(); onDeleteSelected() },
            onRenameSelected = { onPanelTap(); onRenameSelected() },
            onClearSelection = { onPanelTap(); onClearSelection() },
            onRefresh = { onPanelTap(); onRefresh() }
        )

        // Loading
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }

        // Error
        if (state.error != null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    state.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        // File list
        if (state.files.isEmpty() && !state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.folder_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.files, key = { it.path }) { file ->
                    val isSelected = state.selectedPaths.contains(file.path)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 1.dp)
                            .combinedClickable(
                                role = Role.Button,
                                onClick = { onPanelTap(); onFileTap(file) },
                                onLongClick = { onPanelTap(); onFileLongPress(file) }
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (file.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = if (file.isDirectory) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    file.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!file.isDirectory && file.size > 0) {
                                    Text(
                                        formatLocalSize(file.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (file.isDirectory) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalBreadcrumb(
    crumbs: List<String>,
    onCrumbTap: (Int) -> Unit
) {
    if (crumbs.isEmpty()) return
    val scrollState = rememberScrollState()

    LaunchedEffect(crumbs) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        crumbs.forEachIndexed { index, crumb ->
            val isLast = index == crumbs.lastIndex
            Text(
                text = crumb,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isLast) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.primary,
                modifier = if (!isLast) Modifier.clickable { onCrumbTap(index) }
                else Modifier
            )
            if (!isLast) {
                Text(
                    text = " \u203A ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LocalActionBar(
    hasSelection: Boolean,
    selectionCount: Int,
    hasSmb: Boolean,
    onUploadToSmb: () -> Unit,
    onCreateFolder: () -> Unit,
    onDeleteSelected: () -> Unit,
    onRenameSelected: () -> Unit,
    onClearSelection: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hasSelection) {
            Text(
                stringResource(R.string.selected_count, selectionCount.toString()),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
            if (hasSmb) {
                IconButton(onClick = onUploadToSmb) {
                    Icon(Icons.Filled.Upload, contentDescription = stringResource(R.string.smb_upload_to_smb))
                }
            }
            if (selectionCount == 1) {
                IconButton(onClick = onRenameSelected) {
                    Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = stringResource(R.string.smb_rename))
                }
            }
            IconButton(onClick = onDeleteSelected) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
            }
            IconButton(onClick = onClearSelection) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel))
            }
        } else {
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onCreateFolder) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = stringResource(R.string.smb_create_folder))
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
            }
        }
    }
}

private fun formatLocalSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}
