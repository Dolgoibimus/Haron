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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vamp.haron.R
import com.vamp.haron.domain.model.CloudAccount
import com.vamp.haron.domain.model.CloudProvider

@Composable
fun CloudAuthDialog(
    connectedAccounts: List<CloudAccount>,
    onSignIn: (CloudProvider) -> Unit,
    onSignOut: (String) -> Unit,
    onNavigateToCloud: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showAddMenu by remember { mutableStateOf(false) }

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

                val grouped = connectedAccounts.groupBy { it.provider }

                CloudProvider.entries.forEach { provider ->
                    val accounts = grouped[provider] ?: emptyList()

                    if (accounts.isEmpty()) {
                        // Not connected — show sign in button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSignIn(provider) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Cloud,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    provider.displayName,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    stringResource(R.string.cloud_not_connected),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { onSignIn(provider) }) {
                                Text(stringResource(R.string.cloud_sign_in))
                            }
                        }
                    } else {
                        // Connected — show first account with optional dropdown for more
                        val primary = accounts.first()
                        var showDropdown by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onNavigateToCloud(primary.accountId)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Dropdown arrow for 2+ accounts
                            if (accounts.size > 1) {
                                IconButton(
                                    onClick = { showDropdown = true },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.ArrowDropDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                DropdownMenu(
                                    expanded = showDropdown,
                                    onDismissRequest = { showDropdown = false }
                                ) {
                                    accounts.drop(1).forEach { account ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(
                                                        account.email.ifEmpty { account.displayName },
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            },
                                            onClick = {
                                                showDropdown = false
                                                onNavigateToCloud(account.accountId)
                                                onDismiss()
                                            },
                                            trailingIcon = {
                                                TextButton(onClick = {
                                                    showDropdown = false
                                                    onSignOut(account.accountId)
                                                }) {
                                                    Text(
                                                        stringResource(R.string.cloud_sign_out),
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                                Spacer(Modifier.width(4.dp))
                            }

                            Icon(
                                Icons.Filled.CloudDone,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    provider.displayName,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (primary.email.isNotEmpty()) {
                                    Text(
                                        primary.email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            TextButton(onClick = { onSignOut(primary.accountId) }) {
                                Text(stringResource(R.string.cloud_sign_out))
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
            Row {
                // "+ Add" button with dropdown
                TextButton(onClick = { showAddMenu = true }) {
                    Text(stringResource(R.string.cloud_add_account))
                    DropdownMenu(
                        expanded = showAddMenu,
                        onDismissRequest = { showAddMenu = false }
                    ) {
                        CloudProvider.entries.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider.displayName) },
                                onClick = {
                                    showAddMenu = false
                                    onSignIn(provider)
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    )
}
