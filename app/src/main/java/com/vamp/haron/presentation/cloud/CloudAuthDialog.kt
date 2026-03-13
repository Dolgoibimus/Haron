package com.vamp.haron.presentation.cloud

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import com.vamp.haron.domain.model.CloudAccount
import com.vamp.haron.domain.model.CloudProvider

@Composable
fun CloudAuthDialog(
    connectedAccounts: List<CloudAccount>,
    onSignIn: (CloudProvider) -> Unit,
    onSignOut: (CloudProvider) -> Unit,
    onNavigateToCloud: (CloudProvider) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.cloud_auth_title))
        },
        text = {
            Column {
                Text(
                    stringResource(R.string.cloud_auth_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                CloudProvider.entries.forEach { provider ->
                    val account = connectedAccounts.find { it.provider == provider }
                    val isConnected = account != null

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isConnected) {
                                    onNavigateToCloud(provider)
                                    onDismiss()
                                } else {
                                    onSignIn(provider)
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isConnected) Icons.Filled.CloudDone else Icons.Filled.Cloud,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = if (isConnected) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                when (provider) {
                                    CloudProvider.GOOGLE_DRIVE -> stringResource(R.string.cloud_google_drive)
                                    CloudProvider.DROPBOX -> stringResource(R.string.cloud_dropbox)
                                    CloudProvider.ONEDRIVE -> stringResource(R.string.cloud_onedrive)
                                    CloudProvider.YANDEX_DISK -> stringResource(R.string.cloud_yandex_disk)
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (isConnected && account?.email?.isNotEmpty() == true) {
                                Text(
                                    account.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else if (!isConnected) {
                                Text(
                                    stringResource(R.string.cloud_not_connected),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (isConnected) {
                            TextButton(onClick = { onSignOut(provider) }) {
                                Text(stringResource(R.string.cloud_sign_out))
                            }
                        } else {
                            TextButton(onClick = { onSignIn(provider) }) {
                                Text(stringResource(R.string.cloud_sign_in))
                            }
                        }
                    }

                    if (provider != CloudProvider.entries.last()) {
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}
