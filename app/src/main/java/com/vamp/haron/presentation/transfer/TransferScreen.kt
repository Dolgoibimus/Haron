package com.vamp.haron.presentation.transfer

import android.Manifest
import android.os.Build
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamp.haron.R
import com.vamp.haron.domain.model.TransferState
import com.vamp.haron.presentation.transfer.components.DeviceList
import com.vamp.haron.presentation.transfer.components.QrCodeDialog
import com.vamp.haron.presentation.transfer.components.QrScannerDialog
import com.vamp.haron.presentation.transfer.components.ReceiveDialog
import com.vamp.haron.presentation.transfer.components.SmbBrowserTab
import com.vamp.haron.presentation.transfer.components.TransferProgressCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TransferScreen(
    onBack: () -> Unit,
    onOpenFolder: (String) -> Unit = {},
    viewModel: TransferViewModel = hiltViewModel(),
    smbViewModel: SmbViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val smbState by smbViewModel.state.collectAsState()
    val context = LocalContext.current
    var showScanner by remember { mutableStateOf(false) }
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

    // Back button: clear selection → navigate up active panel → switch panel → disconnect
    BackHandler(enabled = selectedTab == 1 && !smbState.serverListMode) {
        // First clear any selection in active panel
        if (smbState.activePanel == PanelId.BOTTOM && smbState.localPanel.selectedPaths.isNotEmpty()) {
            smbViewModel.clearLocalSelection()
        } else if (smbState.activePanel == PanelId.TOP && smbState.selectedFiles.isNotEmpty()) {
            smbViewModel.clearSelection()
        } else if (!smbViewModel.onNavigateUpActivePanel()) {
            // Can't go up in active panel — switch to other panel or disconnect
            if (smbState.activePanel == PanelId.BOTTOM) {
                smbViewModel.setActivePanel(PanelId.TOP)
            } else {
                smbViewModel.onNavigateUp()
            }
        }
    }

    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (selectedTab == 1 && !smbState.serverListMode) {
                                smbViewModel.onNavigateUpActivePanel()
                            } else {
                                viewModel.cancelTransfer()
                                onBack()
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        stringResource(R.string.transfer_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 4.dp).weight(1f)
                    )
                    // Receive button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
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
                            modifier = Modifier.size(20.dp),
                            tint = if (state.isReceiving) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // QR button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
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
                        Icon(Icons.Filled.QrCode, contentDescription = stringResource(R.string.transfer_qr_title), modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = { viewModel.startDiscovery() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.reindex), modifier = Modifier.size(20.dp))
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
            }

            when (selectedTab) {
                0 -> TransferTabContent(
                    state = state,
                    viewModel = viewModel,
                    onOpenFolder = onOpenFolder
                )
                1 -> SmbBrowserTab(
                    viewModel = smbViewModel
                )
            }
        }
    }

    // QR Dialog
    if (state.showQrDialog && state.serverUrl != null) {
        QrCodeDialog(
            url = state.serverUrl!!,
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

    // QR Scanner Dialog
    if (showScanner) {
        QrScannerDialog(
            onResult = { url ->
                showScanner = false
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    viewModel.downloadFromQr(url)
                } else {
                    Toast.makeText(context, url, Toast.LENGTH_LONG).show()
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
    onOpenFolder: (String) -> Unit
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

            // File info
            if (state.files.isNotEmpty()) {
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
