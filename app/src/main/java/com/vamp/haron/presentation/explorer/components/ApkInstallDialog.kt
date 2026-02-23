package com.vamp.haron.presentation.explorer.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vamp.haron.R
import com.vamp.haron.common.util.toFileSize
import com.vamp.haron.domain.model.ApkInstallInfo
import com.vamp.haron.domain.model.ApkPermissionInfo
import com.vamp.haron.domain.model.SignatureStatus

@Composable
fun ApkInstallDialog(
    apkInfo: ApkInstallInfo?,
    isLoading: Boolean,
    error: String?,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(16.dp))
                                Text(stringResource(R.string.analyzing_apk), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    error != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(error, color = MaterialTheme.colorScheme.error)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                        }
                    }
                    apkInfo != null -> {
                        ApkInfoContent(
                            info = apkInfo,
                            onInstall = onInstall,
                            onDismiss = onDismiss
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApkInfoContent(
    info: ApkInstallInfo,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    // Icon + Name header
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (info.icon != null) {
            Image(
                bitmap = info.icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = info.appName ?: stringResource(R.string.unknown_app),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }

    Spacer(Modifier.height(12.dp))
    HorizontalDivider()
    Spacer(Modifier.height(8.dp))

    // Scrollable info
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Package info
        InfoRow(stringResource(R.string.package_label), info.packageName ?: "—")
        InfoRow(stringResource(R.string.version_label), "${info.versionName ?: "?"} (${info.versionCode})")
        InfoRow(stringResource(R.string.apk_size_label), info.fileSize.toFileSize())

        // Upgrade / Downgrade badge
        if (info.isAlreadyInstalled) {
            Spacer(Modifier.height(8.dp))
            val (text, color, icon) = if (info.isUpgrade) {
                Triple(
                    stringResource(R.string.version_upgrade, info.installedVersionName ?: "", info.versionName ?: ""),
                    MaterialTheme.colorScheme.primary,
                    Icons.Filled.ArrowUpward
                )
            } else if (info.isDowngrade) {
                Triple(
                    stringResource(R.string.version_downgrade, info.installedVersionName ?: "", info.versionName ?: ""),
                    MaterialTheme.colorScheme.error,
                    Icons.Filled.ArrowDownward
                )
            } else {
                Triple(
                    stringResource(R.string.version_reinstall, info.versionName ?: ""),
                    MaterialTheme.colorScheme.tertiary,
                    Icons.Filled.ArrowUpward
                )
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, null, Modifier.size(18.dp), tint = color)
                    Spacer(Modifier.width(8.dp))
                    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
                }
            }
        }

        // Signature warning
        if (info.signatureStatus == SignatureStatus.MISMATCH) {
            Spacer(Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Warning, null,
                        Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.signature_mismatch),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Permissions
        val dangerousPerms = info.permissions.filter { it.isDangerous }
        val normalPerms = info.permissions.filter { !it.isDangerous }
        val totalPerms = info.permissions.size

        if (totalPerms > 0) {
            Spacer(Modifier.height(12.dp))
            var expanded by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Security, null,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.permissions_count, totalPerms),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) stringResource(R.string.collapse_permissions) else stringResource(R.string.show_permissions))
                }
            }

            if (expanded) {
                Spacer(Modifier.height(4.dp))
                var expandedPermName by remember { mutableStateOf<String?>(null) }
                // Dangerous first
                dangerousPerms.forEach { perm ->
                    PermissionItem(
                        perm = perm,
                        isDangerous = true,
                        isExpanded = expandedPermName == perm.name,
                        onToggle = { expandedPermName = if (expandedPermName == perm.name) null else perm.name }
                    )
                }
                normalPerms.forEach { perm ->
                    PermissionItem(
                        perm = perm,
                        isDangerous = false,
                        isExpanded = expandedPermName == perm.name,
                        onToggle = { expandedPermName = if (expandedPermName == perm.name) null else perm.name }
                    )
                }
            } else if (dangerousPerms.isNotEmpty()) {
                // Show count of dangerous permissions
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.dangerous_permissions_count, dangerousPerms.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    // Buttons
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
    ) {
        OutlinedButton(onClick = onDismiss) {
            Text(stringResource(R.string.cancel))
        }
        Button(onClick = onInstall) {
            Text(stringResource(R.string.install_action))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).padding(start = 8.dp)
        )
    }
}

@Composable
private fun PermissionItem(
    perm: ApkPermissionInfo,
    isDangerous: Boolean,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val hasDescription = perm.description.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (hasDescription) Modifier.clickable(onClick = onToggle)
                else Modifier
            )
            .padding(vertical = 3.dp, horizontal = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        if (isDangerous) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(3.dp)
                    )
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = perm.label,
                style = MaterialTheme.typography.bodySmall,
                color = if (isDangerous) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isExpanded && hasDescription) {
            Text(
                text = perm.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp, top = 2.dp)
            )
        }
    }
}
