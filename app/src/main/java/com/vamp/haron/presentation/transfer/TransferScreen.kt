package com.vamp.haron.presentation.transfer

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.vamp.haron.domain.model.PanelId
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamp.haron.R
import com.vamp.haron.domain.model.TransferState
import com.vamp.haron.presentation.transfer.components.DeviceList
import com.vamp.haron.presentation.transfer.components.QrCodeDialog
import com.vamp.haron.presentation.transfer.components.QrScannerDialog
import com.vamp.haron.presentation.transfer.components.ReceiveDialog
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import com.vamp.haron.presentation.transfer.components.FtpBrowserTab
import com.vamp.haron.presentation.transfer.components.SmbBrowserTab
import com.vamp.haron.presentation.transfer.components.TransferProgressCard
import com.vamp.haron.presentation.transfer.components.WebDavBrowserTab
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TransferScreen(
    onBack: () -> Unit,
    onOpenFolder: (String) -> Unit = {},
    openScanner: Boolean = false,
    viewModel: TransferViewModel = hiltViewModel(),
    smbViewModel: SmbViewModel = hiltViewModel(),
    ftpViewModel: FtpViewModel = hiltViewModel(),
    ftpServerViewModel: FtpServerViewModel = hiltViewModel(),
    webDavViewModel: WebDavViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val smbState by smbViewModel.state.collectAsState()
    val ftpState by ftpViewModel.state.collectAsState()
    val ftpServerState by ftpServerViewModel.state.collectAsState()
    val webDavState by webDavViewModel.state.collectAsState()
    val context = LocalContext.current
    var showScanner by remember { mutableStateOf(openScanner) }
    var selectedTab by remember { mutableIntStateOf(0) }

    // Runtime permissions for Bluetooth and Wi-Fi Direct
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Start discovery regardless of result — Wi-Fi Direct/NSD may still work
        viewModel.startDiscovery()
    }

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        smbViewModel.toastMessage.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        ftpViewModel.toastMessage.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        webDavViewModel.toastMessage.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            viewModel.startDiscovery()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopDiscovery()
            viewModel.stopReceiving()
        }
    }

    // Back button for SMB tab
    BackHandler(enabled = selectedTab == 1 && !smbState.serverListMode) {
        if (smbState.activePanel == PanelId.BOTTOM && smbState.localPanel.selectedPaths.isNotEmpty()) {
            smbViewModel.clearLocalSelection()
        } else if (smbState.activePanel == PanelId.TOP && smbState.selectedFiles.isNotEmpty()) {
            smbViewModel.clearSelection()
        } else if (!smbViewModel.onNavigateUpActivePanel()) {
            if (smbState.activePanel == PanelId.BOTTOM) {
                smbViewModel.setActivePanel(PanelId.TOP)
            } else {
                smbViewModel.onNavigateUp()
            }
        }
    }

    // Back button for FTP tab
    BackHandler(enabled = selectedTab == 2 && !ftpState.serverListMode) {
        if (ftpState.activePanel == PanelId.BOTTOM && ftpState.localPanel.selectedPaths.isNotEmpty()) {
            ftpViewModel.clearLocalSelection()
        } else if (ftpState.activePanel == PanelId.TOP && ftpState.selectedFiles.isNotEmpty()) {
            ftpViewModel.clearSelection()
        } else if (!ftpViewModel.onNavigateUpActivePanel()) {
            if (ftpState.activePanel == PanelId.BOTTOM) {
                ftpViewModel.setActivePanel(PanelId.TOP)
            } else {
                ftpViewModel.onNavigateUp()
            }
        }
    }

    // Back button for WebDAV tab
    BackHandler(enabled = selectedTab == 3 && !webDavState.serverListMode) {
        if (webDavState.activePanel == PanelId.BOTTOM && webDavState.localPanel.selectedPaths.isNotEmpty()) {
            webDavViewModel.clearLocalSelection()
        } else if (webDavState.activePanel == PanelId.TOP && webDavState.selectedFiles.isNotEmpty()) {
            webDavViewModel.clearSelection()
        } else if (!webDavViewModel.onNavigateUpActivePanel()) {
            if (webDavState.activePanel == PanelId.BOTTOM) {
                webDavViewModel.setActivePanel(PanelId.TOP)
            } else {
                webDavViewModel.onNavigateUp()
            }
        }
    }

    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp, horizontal = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            when {
                                selectedTab == 1 && !smbState.serverListMode ->
                                    smbViewModel.onNavigateUpActivePanel()
                                selectedTab == 2 && !ftpState.serverListMode ->
                                    ftpViewModel.onNavigateUpActivePanel()
                                selectedTab == 3 && !webDavState.serverListMode ->
                                    webDavViewModel.onNavigateUpActivePanel()
                                else -> {
                                    viewModel.cancelTransfer()
                                    onBack()
                                }
                            }
                        },
                        modifier = Modifier.size(35.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(30.dp))
                    }
                    Text(
                        stringResource(R.string.transfer_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                        modifier = Modifier.padding(start = 4.dp).weight(1f)
                    )
                    // Receive button
                    Box(
                        modifier = Modifier
                            .size(35.dp)
                            .combinedClickable(
                                role = Role.Button,
                                onClick = {
                                    if (state.isReceiving) viewModel.stopReceiving()
                                    else viewModel.startReceiving()
                                },
                                onLongClick = { onOpenFolder(viewModel.getReceiveFolder()) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Download,
                            contentDescription = stringResource(R.string.transfer_receive),
                            modifier = Modifier.size(30.dp),
                            tint = if (state.isReceiving) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // QR button
                    Box(
                        modifier = Modifier
                            .size(35.dp)
                            .combinedClickable(
                                role = Role.Button,
                                onClick = {
                                    if (state.files.isNotEmpty()) viewModel.startHttpServer()
                                    else Toast.makeText(context, context.getString(R.string.transfer_select_files), Toast.LENGTH_SHORT).show()
                                },
                                onLongClick = { showScanner = true }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.QrCode, contentDescription = stringResource(R.string.transfer_qr_title), modifier = Modifier.size(30.dp))
                    }
                    IconButton(
                        onClick = { viewModel.startDiscovery() },
                        modifier = Modifier.size(35.dp)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.reindex), modifier = Modifier.size(30.dp))
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab row
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.transfer_tab_title)) },
                    icon = { Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.smb_tab_title)) },
                    icon = { Icon(Icons.Filled.Computer, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text(stringResource(R.string.ftp_tab_title)) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text(stringResource(R.string.webdav_tab_title)) }
                )
            }

            when (selectedTab) {
                0 -> TransferTabContent(
                    state = state,
                    viewModel = viewModel,
                    onOpenFolder = onOpenFolder,
                    ftpServerViewModel = ftpServerViewModel,
                    ftpServerState = ftpServerState
                )
                1 -> SmbBrowserTab(
                    viewModel = smbViewModel
                )
                2 -> FtpBrowserTab(
                    viewModel = ftpViewModel
                )
                3 -> WebDavBrowserTab(
                    viewModel = webDavViewModel
                )
            }
        }
    }

    // QR Dialog
    if (state.showQrDialog && state.serverUrl != null) {
        QrCodeDialog(
            url = state.serverUrl!!,
            hotspotSsid = state.hotspotSsid,
            hotspotPassword = state.hotspotPassword,
            hotspotUrl = state.hotspotUrl,
            isHotspotMode = state.isHotspotMode,
            onToggleHotspot = { viewModel.toggleHotspotMode() },
            onDismiss = { viewModel.dismissQrDialog() },
            onStopServer = { viewModel.stopHttpServer() }
        )
    }

    // Receive Dialog
    if (state.showReceiveDialog) {
        ReceiveDialog(
            deviceName = state.incomingDeviceName,
            fileCount = state.incomingFileCount,
            onAccept = { viewModel.acceptIncoming() },
            onDecline = { viewModel.declineIncoming() }
        )
    }

    // Hotspot needed dialog
    if (state.showWifiOffDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissWifiOffDialog() },
            title = { Text(stringResource(R.string.transfer_hotspot_needed_title)) },
            text = { Text(stringResource(R.string.transfer_hotspot_needed_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissWifiOffDialog()
                    try {
                        context.startActivity(Intent("android.settings.TETHERING_SETTINGS"))
                    } catch (_: Exception) {
                        try {
                            context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                        } catch (_: Exception) {
                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                        }
                    }
                }) {
                    Text(stringResource(R.string.transfer_open_hotspot_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissWifiOffDialog() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // QR Scanner Dialog
    if (showScanner) {
        QrScannerDialog(
            onResult = { result ->
                showScanner = false
                EcosystemLogger.d(HaronConstants.TAG, "QR scanned: result='$result', isLocalNetwork=${isLocalNetworkUrl(result)}")

                // Check for Haron combined QR (hotspot auto-connect)
                val haronQr = tryParseHaronQr(result)
                if (haronQr != null) {
                    EcosystemLogger.d(HaronConstants.TAG, "QR: Haron combined QR detected, ssid=${haronQr.ssid}, url=${haronQr.url}")
                    viewModel.connectAndDownload(haronQr.ssid, haronQr.password, haronQr.url)
                    return@QrScannerDialog
                }

                when {
                    // Local network URL → Haron-to-Haron transfer download
                    (result.startsWith("http://") || result.startsWith("https://")) &&
                        isLocalNetworkUrl(result) -> {
                        EcosystemLogger.d(HaronConstants.TAG, "QR: local network URL detected, starting download from $result")
                        viewModel.downloadFromQr(result)
                    }
                    // Regular URL → open in browser
                    result.startsWith("http://") || result.startsWith("https://") -> {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result)))
                        } catch (_: Exception) {
                            Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                        }
                    }
                    // Other URI schemes (tel:, mailto:, geo:, wifi:, etc.)
                    result.contains("://") || result.startsWith("tel:") ||
                        result.startsWith("mailto:") || result.startsWith("geo:") -> {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result)))
                        } catch (_: Exception) {
                            Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                        }
                    }
                    // Plain text
                    else -> {
                        Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDismiss = { showScanner = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransferTabContent(
    state: TransferUiState,
    viewModel: TransferViewModel,
    onOpenFolder: (String) -> Unit,
    ftpServerViewModel: FtpServerViewModel,
    ftpServerState: FtpServerUiState
) {
    Column(modifier = Modifier.fillMaxSize()) {
            // Battery saver warning
            if (state.batteryWarning) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.transfer_battery_saver_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.dismissBatteryWarning() }) {
                            Text("OK")
                        }
                    }
                }
            }

            // Receive mode indicator
            if (state.isReceiving && state.transferState != TransferState.TRANSFERRING) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.transfer_receive_mode),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            if (state.todayReceivedCount > 0) {
                                Text(
                                    stringResource(R.string.transfer_today_received, state.todayReceivedCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            // File info (hide when completed)
            if (state.files.isNotEmpty() && state.transferState != TransferState.COMPLETED) {
                Text(
                    stringResource(R.string.transfer_select_files) + ": ${state.files.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Transfer progress
            if (state.transferState == TransferState.TRANSFERRING ||
                state.transferState == TransferState.PAUSED
            ) {
                TransferProgressCard(
                    progress = state.progress,
                    onCancel = { viewModel.cancelTransfer() }
                )
            }

            // Transfer completed
            if (state.transferState == TransferState.COMPLETED) {
                val fileCount = if (state.isReceiveTransfer) {
                    state.progress.totalFiles
                } else {
                    state.files.size
                }
                val completeText = if (state.isReceiveTransfer) {
                    stringResource(R.string.receive_complete, fileCount)
                } else {
                    stringResource(R.string.transfer_complete, fileCount)
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            completeText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        if (state.isReceiveTransfer) {
                            TextButton(onClick = {
                                onOpenFolder(viewModel.getReceiveFolder())
                            }) {
                                Text(stringResource(R.string.transfer_open_folder))
                            }
                        }
                    }
                }
            }

            // Error
            if (state.transferState == TransferState.FAILED && state.errorMessage != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.transfer_failed, state.errorMessage ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text(stringResource(R.string.transfer_retry))
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // FTP Server section
            FtpServerSection(
                state = ftpServerState,
                onStart = { ftpServerViewModel.startServer() },
                onStop = { ftpServerViewModel.stopServer() },
                onAnonymousChanged = { ftpServerViewModel.setAnonymousAccess(it) },
                onReadOnlyChanged = { ftpServerViewModel.setReadOnly(it) },
                onUsernameChanged = { ftpServerViewModel.setUsername(it) },
                onPasswordChanged = { ftpServerViewModel.setPassword(it) }
            )

            Spacer(Modifier.height(4.dp))

            // Scanning indicator
            if (state.isScanning) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.transfer_scanning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Device list or empty state (shown alongside scanning indicator)
            if (state.devices.isNotEmpty()) {
                DeviceList(
                    devices = state.devices,
                    onDeviceClick = { device -> viewModel.sendToDevice(device) },
                    onToggleTrust = { device -> viewModel.toggleDeviceTrust(device) },
                    onRenameDevice = { device, alias -> viewModel.renameDevice(device, alias) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            } else if (!state.isScanning) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.transfer_no_devices),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
    }
}

@Composable
private fun FtpServerSection(
    state: FtpServerUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onAnonymousChanged: (Boolean) -> Unit,
    onReadOnlyChanged: (Boolean) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
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

/** Check if URL points to a local/private network address (for Haron-to-Haron transfers). */
private fun isLocalNetworkUrl(url: String): Boolean {
    val host = Uri.parse(url).host ?: return false
    val isLocal = host.startsWith("192.168.") ||
        host.startsWith("10.") ||
        host.matches(Regex("172\\.(1[6-9]|2[0-9]|3[01])\\..*"))
    return isLocal
}

private data class HaronQrData(val ssid: String, val password: String?, val url: String)

/**
 * Try to parse a Haron combined QR code JSON: {"haron":1,"ssid":"...","pass":"...","url":"..."}
 * Returns null if the string is not a valid Haron QR.
 */
private fun tryParseHaronQr(text: String): HaronQrData? {
    val trimmed = text.trim()
    if (!trimmed.startsWith("{") || !trimmed.contains("\"haron\"")) return null
    return try {
        // Minimal JSON parsing without a library
        val haronMatch = """"haron"\s*:\s*1""".toRegex().find(trimmed) ?: return null
        if (haronMatch.value.isEmpty()) return null

        val ssid = """"ssid"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""".toRegex()
            .find(trimmed)?.groupValues?.get(1)?.replace("\\\"", "\"")?.replace("\\\\", "\\")
            ?: return null

        val pass = """"pass"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""".toRegex()
            .find(trimmed)?.groupValues?.get(1)?.replace("\\\"", "\"")?.replace("\\\\", "\\")

        val url = """"url"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""".toRegex()
            .find(trimmed)?.groupValues?.get(1)?.replace("\\\"", "\"")?.replace("\\\\", "\\")
            ?: return null

        HaronQrData(ssid = ssid, password = pass?.ifEmpty { null }, url = url)
    } catch (_: Exception) {
        null
    }
}
