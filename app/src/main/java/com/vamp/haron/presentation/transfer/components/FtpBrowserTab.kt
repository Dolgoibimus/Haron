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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vamp.haron.R
import com.vamp.haron.common.util.toFileSize
import com.vamp.haron.presentation.common.ProgressInfoRow
import com.vamp.haron.data.ftp.FtpFileInfo
import com.vamp.haron.data.ftp.FtpTransferProgress
import com.vamp.haron.domain.model.PanelId
import com.vamp.haron.presentation.explorer.components.PanelDivider
import com.vamp.haron.presentation.transfer.FtpSavedServer
import com.vamp.haron.presentation.transfer.FtpServerUiState
import com.vamp.haron.presentation.transfer.FtpServerViewModel
import com.vamp.haron.presentation.transfer.FtpUiState
import com.vamp.haron.presentation.transfer.FtpViewModel
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Switch
import androidx.compose.material.icons.filled.Storage

@Composable
fun FtpBrowserTab(
    viewModel: FtpViewModel,
    ftpServerViewModel: FtpServerViewModel
) {
    var selectedSubTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedSubTab) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = { Text(stringResource(R.string.ftp_tab_client)) }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = { Text(stringResource(R.string.ftp_tab_server)) }
            )
        }

        when (selectedSubTab) {
            0 -> FtpClientContent(viewModel = viewModel)
            1 -> {
                val ftpServerState by ftpServerViewModel.state.collectAsState()
                FtpServerContent(
                    state = ftpServerState,
                    onStart = { ftpServerViewModel.startServer() },
                    onStop = { ftpServerViewModel.stopServer() },
                    onAnonymousChanged = { ftpServerViewModel.setAnonymousAccess(it) },
                    onReadOnlyChanged = { ftpServerViewModel.setReadOnly(it) },
                    onUsernameChanged = { ftpServerViewModel.setUsername(it) },
                    onPasswordChanged = { ftpServerViewModel.setPassword(it) }
                )
            }
        }
    }
}

@Composable
private fun FtpClientContent(viewModel: FtpViewModel) {
    val state by viewModel.state.collectAsState()

    if (state.serverListMode) {
        FtpServerList(
            savedServers = state.savedServers,
            isConnecting = state.isConnecting,
            onSavedServerTap = { viewModel.onSavedServerTap(it) },
            onRemoveSaved = { viewModel.onRemoveSavedServer(it) },
            onManualConnect = { viewModel.onShowManualConnect() }
        )
    } else {
        FtpDualPanelLayout(state = state, viewModel = viewModel)
    }

    FtpDialogs(state = state, viewModel = viewModel)
}

@Composable
private fun FtpServerContent(
    state: FtpServerUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onAnonymousChanged: (Boolean) -> Unit,
    onReadOnlyChanged: (Boolean) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (state.isRunning) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Storage,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.ftp_server_title),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { if (state.isRunning) onStop() else onStart() }
                    ) {
                        Text(
                            stringResource(
                                if (state.isRunning) R.string.ftp_server_stop
                                else R.string.ftp_server_start
                            )
                        )
                    }
                }

                if (state.isRunning && state.serverUrl != null) {
                    Text(
                        state.serverUrl,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 28.dp, top = 4.dp)
                    )
                }

                if (!state.isRunning) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 28.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.ftp_server_anonymous),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = state.anonymousAccess,
                            onCheckedChange = onAnonymousChanged
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.ftp_server_read_only),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = state.readOnly,
                            onCheckedChange = onReadOnlyChanged
                        )
                    }
                    if (!state.anonymousAccess) {
                        OutlinedTextField(
                            value = state.username,
                            onValueChange = onUsernameChanged,
                            label = { Text(stringResource(R.string.ftp_username)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 28.dp, end = 4.dp, top = 4.dp)
                        )
                        OutlinedTextField(
                            value = state.password,
                            onValueChange = onPasswordChanged,
                            label = { Text(stringResource(R.string.ftp_password)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 28.dp, end = 4.dp, top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FtpDualPanelLayout(
    state: FtpUiState,
    viewModel: FtpViewModel
) {
    var totalHeightPx by remember { mutableStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { totalHeightPx = it.height.toFloat() }
        ) {
            // Top panel (FTP)
            FtpPanel(
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
            FtpTransferProgressCard(
                progress = state.transferProgress,
                onCancel = { viewModel.cancelTransfer() },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun FtpPanel(
    state: FtpUiState,
    viewModel: FtpViewModel,
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
        FtpBreadcrumb(
            crumbs = viewModel.getBreadcrumbs(),
            onCrumbTap = { onPanelTap(); viewModel.navigateToBreadcrumb(it) }
        )

        // Action bar
        FtpActionBar(
            hasSelection = state.selectedFiles.isNotEmpty(),
            selectionCount = state.selectedFiles.size,
            onDownloadToLocal = { onPanelTap(); viewModel.downloadToLocalPanel() },
            onCreateFolder = { viewModel.showCreateFolderDialog(); viewModel.setActivePanel(PanelId.TOP) },
            onDisconnect = { viewModel.onDisconnect() },
            onRefresh = { onPanelTap(); viewModel.refreshFiles() },
            onDeleteSelected = { onPanelTap(); viewModel.onDeleteSelected() },
            onRenameSelected = {
                val path = state.selectedFiles.firstOrNull() ?: return@FtpActionBar
                val name = path.substringAfterLast("/")
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
        FtpFileList(
            files = state.files,
            selectedFiles = state.selectedFiles,
            onFileTap = { onPanelTap(); viewModel.onFileTap(it) },
            onFileLongPress = { onPanelTap(); viewModel.onFileLongPress(it) }
        )
    }
}

@Composable
private fun FtpDialogs(
    state: FtpUiState,
    viewModel: FtpViewModel
) {
    // Auth dialog
    if (state.showAuthDialog && state.authDialogHost != null) {
        FtpAuthDialog(
            host = state.authDialogHost,
            port = state.authDialogPort,
            isConnecting = state.isConnecting,
            error = state.error,
            isSftp = state.isSftp,
            onConnect = { cred, save -> viewModel.onConnect(cred, save) },
            onConnectSftp = { host, port, user, pass, save ->
                viewModel.onConnectSftp(host, port, user, pass, save)
            },
            onConnectAnonymous = {
                viewModel.onConnectAnonymous(state.authDialogHost, state.authDialogPort)
            },
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

    // Manual connect dialog
    if (state.showManualConnectDialog) {
        var ipAddress by remember { mutableStateOf("") }
        var isSftpManual by remember { mutableStateOf(false) }
        var portText by remember { mutableStateOf("21") }
        AlertDialog(
            onDismissRequest = { viewModel.onDismissManualConnect() },
            title = { Text(stringResource(R.string.ftp_manual_connect)) },
            text = {
                Column {
                    // Protocol toggle: FTP / SFTP
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (isSftpManual) {
                                        isSftpManual = false
                                        portText = "21"
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = !isSftpManual,
                                onClick = { isSftpManual = false; portText = "21" }
                            )
                            Text("FTP", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (!isSftpManual) {
                                        isSftpManual = true
                                        portText = "22"
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = isSftpManual,
                                onClick = { isSftpManual = true; portText = "22" }
                            )
                            Text("SFTP", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ipAddress,
                        onValueChange = { ipAddress = it },
                        label = { Text(stringResource(R.string.ftp_host)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it },
                        label = { Text(stringResource(R.string.ftp_port)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val defaultPort = if (isSftpManual) 22 else 21
                        viewModel.onManualConnect(ipAddress, portText.toIntOrNull() ?: defaultPort, isSftpManual)
                    },
                    enabled = ipAddress.isNotBlank()
                ) {
                    Text(stringResource(R.string.ftp_connect))
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

// --- Server list ---

@Composable
private fun FtpServerList(
    savedServers: List<FtpSavedServer>,
    isConnecting: Boolean,
    onSavedServerTap: (FtpSavedServer) -> Unit,
    onRemoveSaved: (FtpSavedServer) -> Unit,
    onManualConnect: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (savedServers.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.ftp_saved_servers),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(savedServers, key = { "${it.host}:${it.port}" }) { server ->
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
                                "${server.host}:${server.port}" +
                                    when {
                                        server.isSftp -> " (SFTP)"
                                        server.useFtps -> " (FTPS)"
                                        else -> " (FTP)"
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                onClick = onManualConnect,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.ftp_manual_connect))
            }
        }
    }
}

// --- FTP panel internals ---

@Composable
private fun FtpBreadcrumb(
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
private fun FtpActionBar(
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
                Icon(Icons.Filled.LinkOff, contentDescription = stringResource(R.string.ftp_disconnect))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FtpFileList(
    files: List<FtpFileInfo>,
    selectedFiles: Set<String>,
    onFileTap: (FtpFileInfo) -> Unit,
    onFileLongPress: (FtpFileInfo) -> Unit
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
private fun FtpTransferProgressCard(
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
                val fraction = progress.bytesTransferred.toFloat() / progress.totalBytes
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth()
                )
                ProgressInfoRow(
                    counter = "${progress.bytesTransferred.toFileSize()} / ${progress.totalBytes.toFileSize()}",
                    percent = "${(fraction * 100).toInt()}%"
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
