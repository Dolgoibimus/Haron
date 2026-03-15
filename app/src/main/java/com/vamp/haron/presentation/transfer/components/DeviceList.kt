package com.vamp.haron.presentation.transfer.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vamp.haron.R
import com.vamp.haron.domain.model.DiscoveredDevice
import com.vamp.haron.domain.model.TransferProtocol

@Composable
fun DeviceList(
    devices: List<DiscoveredDevice>,
    onDeviceClick: (DiscoveredDevice) -> Unit,
    onToggleTrust: (DiscoveredDevice) -> Unit = {},
    onRenameDevice: (DiscoveredDevice, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var renameDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }

    LazyColumn(modifier = modifier) {
        items(devices, key = { it.id }) { device ->
            DeviceItem(
                device = device,
                onClick = { onDeviceClick(device) },
                onLongClick = { renameDevice = device },
                onToggleTrust = { onToggleTrust(device) }
            )
        }
    }

    renameDevice?.let { device ->
        RenameDeviceDialog(
            currentName = device.displayName,
            onConfirm = { newAlias ->
                onRenameDevice(device, newAlias)
                renameDevice = null
            },
            onDismiss = { renameDevice = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeviceItem(
    device: DiscoveredDevice,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleTrust: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = when {
            TransferProtocol.WIFI_DIRECT in device.supportedProtocols -> Icons.Filled.Wifi
            else -> Icons.Filled.PhoneAndroid
        }
        val tint = if (device.isHaron) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant

        Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = tint)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    device.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (device.isHaron) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.transfer_haron_device),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                device.supportedProtocols.joinToString(", ") { protocolLabel(it) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (device.isHaron) {
            IconButton(onClick = onToggleTrust) {
                Icon(
                    imageVector = if (device.isTrusted) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = stringResource(
                        if (device.isTrusted) R.string.device_trusted else R.string.device_untrusted
                    ),
                    tint = if (device.isTrusted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RenameDeviceDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.device_rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun protocolLabel(protocol: TransferProtocol): String {
    return when (protocol) {
        TransferProtocol.WIFI_DIRECT -> "Wi-Fi Direct"
        TransferProtocol.HTTP -> "HTTP"
        TransferProtocol.BLUETOOTH -> "Bluetooth"
    }
}
