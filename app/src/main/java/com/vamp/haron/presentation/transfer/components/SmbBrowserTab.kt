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
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vamp.haron.R
import com.vamp.haron.data.network.NetworkDevice
import com.vamp.haron.data.smb.SmbFileInfo
import com.vamp.haron.data.smb.SmbShareInfo
import com.vamp.haron.data.smb.SmbTransferProgress
import com.vamp.haron.domain.model.PanelId
import com.vamp.haron.presentation.explorer.components.PanelDivider
import com.vamp.haron.presentation.transfer.SmbSavedServer
import com.vamp.haron.presentation.transfer.SmbUiState
import com.vamp.haron.presentation.transfer.SmbViewModel

@Composable
fun SmbBrowserTab(
    viewModel: SmbViewModel
) {
    val state by viewModel.state.collectAsState()

    if (state.serverListMode) {
        SmbServerList(
            discoveredServers = state.discoveredServers,
            savedServers = state.savedServers,
            isConnecting = state.isConnecting,
            onServerTap = { viewModel.onServerTap(it) },
            onSavedServerTap = { viewModel.onSavedServerTap(it) },
            onRemoveSaved = { viewModel.onRemoveSavedServer(it) },
            onManualConnect = { viewModel.onShowManualConnect() },
            onRefresh = { viewModel.onRefreshServers() }
        )
    } else {
        // Dual-panel mode
        DualPanelLayout(state = state, viewModel = viewModel)
    }

    // Dialogs
    SmbDialogs(state = state, viewModel = viewModel)
}

@Composable
private fun DualPanelLayout(
    state: SmbUiState,
    viewModel: SmbViewModel
) {
    var totalHeightPx by remember { mutableStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { totalHeightPx = it.height.toFloat() }
        ) {
            // --- Top panel (SMB) ---
            SmbPanel(
                state = state,
                viewModel = viewModel,
                isActive = state.activePanel == PanelId.TOP,
                onPanelTap = { viewModel.setActivePanel(PanelId.TOP) },
                modifier = Modifier.weight(state.panelRatio)
            )

            // --- Divider ---
            PanelDivider(
                totalSize = totalHeightPx,
                topFileCount = if (state.currentShare == null) state.shares.size else state.files.size,
                bottomFileCount = state.localPanel.files.size,
                isTopActive = state.activePanel == PanelId.TOP,
                onDrag = { delta ->
                    viewModel.applyPanelRatioDelta(delta)
                },
                onDragEnd = { },
                onDoubleTap = { viewModel.resetPanelRatio() }
            )

            // --- Bottom panel (Local) ---
            LocalFilePanel(
                state = state.localPanel,
                isActive = state.activePanel == PanelId.BOTTOM,
                onPanelTap = { viewModel.setActivePanel(PanelId.BOTTOM) },
                onFileTap = { viewModel.onLocalFileTap(it) },
                onFileLongPress = { viewModel.onLocalFileLongPress(it) },
                onBreadcrumbTap = { viewModel.navigateLocalBreadcrumb(it) },
                breadcrumbs = viewModel.getLocalBreadcrumbs(),
                hasSmb = state.currentShare != null,
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
            SmbTransferProgressCard(
                progress = state.transferProgress,
                onCancel = { viewModel.cancelTransfer() },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun SmbPanel(
    state: SmbUiState,
    viewModel: SmbViewModel,
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
        // Breadcrumb (tap activates panel)
        SmbBreadcrumb(
            crumbs = viewModel.getBreadcrumbs(),
            onCrumbTap = { onPanelTap(); viewModel.navigateToBreadcrumb(it) }
        )

        // Action bar
        SmbActionBar(
            hasSelection = state.selectedFiles.isNotEmpty(),
            selectionCount = state.selectedFiles.size,
            isInShareList = state.currentShare == null,
            onDownloadToLocal = { onPanelTap(); viewModel.downloadToLocalPanel() },
            onCreateFolder = { viewModel.showCreateFolderDialog(); viewModel.setActivePanel(PanelId.TOP) },
            onDisconnect = { viewModel.onDisconnect() },
            onRefresh = { onPanelTap(); viewModel.refreshFiles() },
            onDeleteSelected = { onPanelTap(); viewModel.onDeleteSelected() },
            onRenameSelected = {
                val path = state.selectedFiles.firstOrNull() ?: return@SmbActionBar
                val name = path.substringAfterLast("\\")
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

        // Share list or file list
        if (state.currentShare == null) {
            ShareList(
                shares = state.shares,
                onShareTap = { onPanelTap(); viewModel.onShareTap(it) }
            )
        } else {
            SmbFileList(
                files = state.files,
                selectedFiles = state.selectedFiles,
                onFileTap = { onPanelTap(); viewModel.onFileTap(it) },
                onFileLongPress = { onPanelTap(); viewModel.onFileLongPress(it) }
            )
        }
    }
}

@Composable
private fun SmbDialogs(
    state: SmbUiState,
    viewModel: SmbViewModel
) {
    // Auth dialog
    if (state.showAuthDialog && state.authDialogHost != null) {
        SmbAuthDialog(
            host = state.authDialogHost,
            deviceName = state.authDialogDeviceName,
            isConnecting = state.isConnecting,
            error = state.error,
            onConnect = { cred, save ->
                viewModel.onConnect(state.authDialogHost, state.authDialogPort, cred, save)
            },
            onConnectAsGuest = {
                viewModel.onConnectAsGuest(state.authDialogHost, state.authDialogPort)
            },
            onDismiss = { viewModel.dismissAuthDialog() }
        )
    }

    // Create folder dialog
    if (state.showCreateFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.dismissCreateFolderDialog() },
            title = { Text(stringResource(R.string.smb_create_folder)) },
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
            title = { Text(stringResource(R.string.smb_rename)) },
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

    // Manual connect dialog
    if (state.showManualConnectDialog) {
        var ipAddress by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.onDismissManualConnect() },
            title = { Text(stringResource(R.string.smb_manual_connect)) },
            text = {
                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = { ipAddress = it },
                    label = { Text("IP") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.onManualConnect(ipAddress) },
                    enabled = ipAddress.isNotBlank()
                ) {
                    Text(stringResource(R.string.smb_connect))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onDismissManualConnect() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

// --- Server list (unchanged) ---

@Composable
private fun SmbServerList(
    discoveredServers: List<NetworkDevice>,
    savedServers: List<SmbSavedServer>,
    isConnecting: Boolean,
    onServerTap: (NetworkDevice) -> Unit,
    onSavedServerTap: (String) -> Unit,
    onRemoveSaved: (String) -> Unit,
    onManualConnect: () -> Unit,
    onRefresh: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.smb_discovered),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.refresh),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (discoveredServers.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.smb_no_servers),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        } else {
            items(discoveredServers, key = { it.id }) { device ->
                SmbServerItem(
                    name = device.displayName,
                    subtitle = "${device.address}:${device.port}",
                    isConnecting = isConnecting,
                    onClick = { onServerTap(device) }
                )
            }
        }

        if (savedServers.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.smb_saved_servers),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(savedServers, key = { it.host }) { server ->
                SmbSavedServerItem(
                    host = server.host,
                    onClick = { onSavedServerTap(server.host) },
                    onRemove = { onRemoveSaved(server.host) }
                )
            }
        }

        item {
            TextButton(
                onClick = onManualConnect,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.smb_manual_connect))
            }
        }
    }
}

@Composable
private fun SmbServerItem(
    name: String,
    subtitle: String,
    isConnecting: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
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
                Icons.Filled.Computer,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SmbSavedServerItem(
    host: String,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
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
            Text(host, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.LinkOff,
                    contentDescription = stringResource(R.string.remove_item),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// --- SMB panel internals ---

@Composable
private fun SmbBreadcrumb(
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
private fun SmbActionBar(
    hasSelection: Boolean,
    selectionCount: Int,
    isInShareList: Boolean,
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
                Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.smb_download_to_local))
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
            if (!isInShareList) {
                IconButton(onClick = onCreateFolder) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = stringResource(R.string.smb_create_folder))
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
            }
            IconButton(onClick = onDisconnect) {
                Icon(Icons.Filled.LinkOff, contentDescription = stringResource(R.string.smb_disconnect))
            }
        }
    }
}

@Composable
private fun ShareList(
    shares: List<SmbShareInfo>,
    onShareTap: (SmbShareInfo) -> Unit
) {
    if (shares.isEmpty()) return
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                stringResource(R.string.smb_shares),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        items(shares, key = { it.name }) { share ->
            Card(
                onClick = { onShareTap(share) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 1.dp)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.FolderShared,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(share.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SmbFileList(
    files: List<SmbFileInfo>,
    selectedFiles: Set<String>,
    onFileTap: (SmbFileInfo) -> Unit,
    onFileLongPress: (SmbFileInfo) -> Unit
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
                        if (!file.isDirectory) {
                            Text(
                                formatSize(file.size),
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
private fun SmbTransferProgressCard(
    progress: SmbTransferProgress,
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
                    if (progress.isUpload) stringResource(R.string.smb_uploading)
                    else stringResource(R.string.smb_downloading),
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
                    "${formatSize(progress.bytesTransferred)} / ${formatSize(progress.totalBytes)}",
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

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}
