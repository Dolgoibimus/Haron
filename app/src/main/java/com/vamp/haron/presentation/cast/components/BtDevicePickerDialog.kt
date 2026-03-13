package com.vamp.haron.presentation.cast.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vamp.haron.R
import com.vamp.haron.domain.model.HidConnectionState

@Composable
fun BtDevicePickerDialog(
    connectionState: HidConnectionState,
    waitingForTv: Boolean,
    onMakeDiscoverable: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bt_remote_title)) },
        text = {
            Column {
                when (connectionState) {
                    is HidConnectionState.NotSupported -> {
                        Text(
                            stringResource(R.string.bt_hid_not_supported),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    is HidConnectionState.Connected -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.bt_hid_connected),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    else -> {
                        if (waitingForTv) {
                            // Waiting for TV to connect
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    stringResource(R.string.bt_hid_waiting),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.bt_hid_instructions),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            // Initial state — explain and offer button
                            Text(
                                stringResource(R.string.bt_hid_setup_info),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = onMakeDiscoverable,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Filled.Bluetooth,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.bt_hid_make_discoverable))
                            }
                        }

                        if (connectionState is HidConnectionState.Error) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                connectionState.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
