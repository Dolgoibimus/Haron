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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vamp.haron.R
import com.vamp.haron.data.ftp.FtpTransferProgress
import com.vamp.haron.data.webdav.WebDavFileInfo
import com.vamp.haron.domain.model.PanelId
import com.vamp.haron.presentation.explorer.components.PanelDivider
import com.vamp.haron.presentation.transfer.WebDavSavedServer
import com.vamp.haron.presentation.transfer.WebDavUiState
import com.vamp.haron.presentation.transfer.WebDavViewModel

@Composable
fun WebDavBrowserTab(
    viewModel: WebDavViewModel
) {
    val state by viewModel.state.collectAsState()

    if (state.serverListMode) {
        WebDavServerList(
            savedServers = state.savedServers,
            isConnecting = state.isConnecting,
            onSavedServerTap = { viewModel.onSavedServerTap(it) },
            onRemoveSaved = { viewModel.onRemoveSavedServer(it.url) },
            onAddServer = { viewModel.onShowAuthDialog() }
        )
    } else {
        WebDavDualPanelLayout(state = state, viewModel = viewModel)
    }

    WebDavDialogs(state = state, viewModel = viewModel)
}

@Composable
private fun WebDavDualPanelLayout(
    state: WebDavUiState,
    viewModel: WebDavViewModel
) {
    var totalHeightPx by remember { mutableStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { totalHeightPx = it.height.toFloat() }
        ) {
            // Top panel (WebDAV)
            WebDavPanel(
                state = state,
                viewModel = viewModel,
                isActive = state.activePanel == PanelId.TOP,
                onPanelTap = { viewModel.setActivePanel(PanelId.TOP) },
                modifier = Modifier.weight(state.panelRatio)
            )

            // Divider
            PanelDivider(
                totalSize = totalHeightPx,
                topFileCount = state.files.size,
                bottomFileCount = state.localPanel.files.size,
                isTopActive = state.activePanel == PanelId.TOP,
                onDrag = { delta -> viewModel.applyPanelRatioDelta(delta) },
                onDragEnd = { },
                onDoubleTap = { viewModel.resetPanelRatio() }
            )

            // Bottom panel (Local)
            LocalFilePanel(
                state = state.localPanel,
                isActive = state.activePanel == PanelId.BOTTOM,
                onPanelTap = { viewModel.setActivePanel(PanelId.BOTTOM) },
                onFileTap = { viewModel.onLocalFileTap(it) },
                onFileLongPress = { viewModel.onLocalFileLongPress(it) },
                onBreadcrumbTap = { viewModel.navigateLocalBreadcrumb(it) },
                breadcrumbs = viewModel.getLocalBreadcrumbs(),
                hasSmb = true,
                onUploadToSmb = { viewModel.uploadFromLocalPanel() },
                onCreateFolder = { viewModel.showCreateFolderDialog(); viewModel.setActivePanel(PanelId.BOTTOM) },
                onDeleteSelected = { viewModel.onDeleteSelectedInActivePanel() },
                onRenameSelected = {
                    val path = state.localPanel.selectedPaths.firstOrNull() ?: return@LocalFilePanel
                    val name = path.substringAfterLast("/")
                    viewModel.showRenameDialog(path, name)
                    viewModel.setActivePanel(PanelId.BOTTOM)
                },
                onClearSelection = { viewModel.clearLocalSelection() },
                onRefresh = { viewModel.loadLocalFiles() },
                modifier = Modifier.weight(1f - state.panelRatio)
            )
        }

        // Transfer progress overlay
        if (state.transferProgress != null) {
            WebDavTransferProgressCard(
                progress = state.transferProgress,
                onCancel = { viewModel.cancelTransfer() },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun WebDavPanel(
    state: WebDavUiState,
    viewModel: WebDavViewModel,
    isActive: Boolean,
    onPanelTap: () -> Unit,
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
        WebDavBreadcrumb(
            crumbs = viewModel.getBreadcrumbs(),
            onCrumbTap = { onPanelTap(); viewModel.navigateToBreadcrumb(it) }
        )

        // Action bar
        WebDavActionBar(
            hasSelection = state.selectedFiles.isNotEmpty(),
            selectionCount = state.selectedFiles.size,
            onDownloadToLocal = { onPanelTap(); viewModel.downloadToLocalPanel() },
            onCreateFolder = { viewModel.showCreateFolderDialog(); viewModel.setActivePanel(PanelId.TOP) },
            onDisconnect = { viewModel.onDisconnect() },
            onRefresh = { onPanelTap(); viewModel.refreshFiles() },
            onDeleteSelected = { onPanelTap(); viewModel.onDeleteSelected() },
            onRenameSelected = {
                val path = state.selectedFiles.firstOrNull() ?: return@WebDavActionBar
                val name = java.net.URLDecoder.decode(path.trimEnd('/').substringAfterLast("/"), "UTF-8")
                onPanelTap()
                viewModel.showRenameDialog(path, name)
            },
            onClearSelection = { onPanelTap(); viewModel.clearSelection() }
        )

        // Loading
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
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
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        state.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    TextButton(onClick = { viewModel.refreshFiles() }) {
                        Text(stringResource(R.string.transfer_retry))
                    }
                }
            }
        }

        // File list
        WebDavFileList(
            files = state.files,
            selectedFiles = state.selectedFiles,
            onFileTap = { onPanelTap(); viewModel.onFileTap(it) },
            onFileLongPress = { onPanelTap(); viewModel.onFileLongPress(it) }
        )
    }
}

@Composable
private fun WebDavDialogs(
    state: WebDavUiState,
    viewModel: WebDavViewModel
) {
    // Auth dialog
    if (state.showAuthDialog) {
        WebDavAuthDialog(
            isConnecting = state.isConnecting,
            error = state.error,
            onConnect = { url, user, pass, save -> viewModel.onConnect(url, user, pass, save) },
            onDismiss = { viewModel.dismissAuthDialog() }
        )
    }

    // Create folder dialog
    if (state.showCreateFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.dismissCreateFolderDialog() },
            title = { Text(stringResource(R.string.ftp_create_folder)) },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text(stringResource(R.string.name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.onCreateFolderInActivePanel(folderName) },
                    enabled = folderName.isNotBlank()
                ) {
                    Text(stringResource(R.string.create))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCreateFolderDialog() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Rename dialog
    if (state.showRenameDialog != null) {
        val (path, currentName) = state.showRenameDialog
        var newName by remember(path) { mutableStateOf(currentName) }
        AlertDialog(
            onDismissRequest = { viewModel.dismissRenameDialog() },
            title = { Text(stringResource(R.string.ftp_rename)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.onRenameInActivePanel(path, newName) },
                    enabled = newName.isNotBlank() && newName != currentName
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRenameDialog() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

// --- Server list ---

@Composable
private fun WebDavServerList(
    savedServers: List<WebDavSavedServer>,
    isConnecting: Boolean,
    onSavedServerTap: (WebDavSavedServer) -> Unit,
    onRemoveSaved: (WebDavSavedServer) -> Unit,
    onAddServer: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (savedServers.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.webdav_saved_servers),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(savedServers, key = { it.url }) { server ->
                Card(
                    onClick = { onSavedServerTap(server) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    enabled = !isConnecting
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Link,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(server.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                server.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { onRemoveSaved(server) }) {
                            Icon(
                                Icons.Filled.LinkOff,
                                contentDescription = stringResource(R.string.remove_item),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            TextButton(
                onClick = onAddServer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.webdav_add_server))
            }
        }
    }
}

// --- Auth dialog ---

@Composable
private fun WebDavAuthDialog(
    isConnecting: Boolean,
    error: String?,
    onConnect: (String, String, String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf("https://") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var saveCredentials by remember { mutableStateOf(true) }
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isConnecting) onDismiss() },
        title = { Text(stringResource(R.string.webdav_connect)) },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.webdav_url_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isConnecting
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.ftp_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isConnecting
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.ftp_password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isConnecting,
                    visualTransformation = if (passwordVisible)
                        VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Filled.VisibilityOff
                                else Icons.Filled.Visibility,
                                contentDescription = null
                            )
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = saveCredentials,
                        onCheckedChange = { saveCredentials = it },
                        enabled = !isConnecting
                    )
                    Text(
                        stringResource(R.string.ftp_save_credentials),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (isConnecting) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.ftp_connecting),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConnect(url, username, password, saveCredentials) },
                enabled = !isConnecting && url.isNotBlank() && url.length > 8
            ) {
                Text(stringResource(R.string.webdav_connect))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isConnecting
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// --- WebDAV panel internals ---

@Composable
private fun WebDavBreadcrumb(
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
private fun WebDavActionBar(
    hasSelection: Boolean,
    selectionCount: Int,
    onDownloadToLocal: () -> Unit,
    onCreateFolder: () -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onDeleteSelected: () -> Unit,
    onRenameSelected: () -> Unit,
    onClearSelection: () -> Unit
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
            IconButton(onClick = onDownloadToLocal) {
                Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.ftp_download))
            }
            if (selectionCount == 1) {
                IconButton(onClick = onRenameSelected) {
                    Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = stringResource(R.string.ftp_rename))
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
                Icon(Icons.Filled.CreateNewFolder, contentDescription = stringResource(R.string.ftp_create_folder))
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
            }
            IconButton(onClick = onDisconnect) {
                Icon(Icons.Filled.LinkOff, contentDescription = stringResource(R.string.webdav_disconnect))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WebDavFileList(
    files: List<WebDavFileInfo>,
    selectedFiles: Set<String>,
    onFileTap: (WebDavFileInfo) -> Unit,
    onFileLongPress: (WebDavFileInfo) -> Unit
) {
    if (files.isEmpty()) {
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
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(files, key = { it.path }) { file ->
            val isSelected = selectedFiles.contains(file.path)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 1.dp)
                    .combinedClickable(
                        role = Role.Button,
                        onClick = { onFileTap(file) },
                        onLongClick = { onFileLongPress(file) }
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
                                formatWebDavSize(file.size),
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

@Composable
private fun WebDavTransferProgressCard(
    progress: FtpTransferProgress,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (progress.isUpload) stringResource(R.string.ftp_uploading)
                    else stringResource(R.string.ftp_downloading),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.cancel))
                }
            }
            Text(
                progress.fileName,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            if (progress.totalBytes > 0) {
                LinearProgressIndicator(
                    progress = { progress.bytesTransferred.toFloat() / progress.totalBytes },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "${formatWebDavSize(progress.bytesTransferred)} / ${formatWebDavSize(progress.totalBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(top = 2.dp)
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private fun formatWebDavSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}
