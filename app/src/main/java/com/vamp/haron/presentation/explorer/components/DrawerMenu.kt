package com.vamp.haron.presentation.explorer.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Eject
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.SendToMobile
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vamp.haron.R
import com.vamp.haron.common.util.toFileSize
import com.vamp.haron.data.network.NetworkDevice
import com.vamp.haron.data.network.NetworkDeviceType
import com.vamp.haron.data.usb.UsbVolume
import com.vamp.haron.presentation.explorer.state.SafRootInfo

@Composable
fun DrawerMenu(
    favorites: List<String>,
    recentPaths: List<String>,
    safRoots: List<SafRootInfo>,
    themeMode: String,
    trashSizeInfo: String,
    onNavigate: (String) -> Unit,
    onRemoveFavorite: (String) -> Unit,
    onNavigateToInternalStorage: () -> Unit,
    onGrantVolumeAccess: () -> Unit,
    onRevokeVolumeAccess: (String) -> Unit,
    onShowTrash: () -> Unit,
    onOpenStorageAnalysis: () -> Unit,
    onOpenDuplicateDetector: () -> Unit,
    onOpenAppManager: () -> Unit,
    onFindEmptyFolders: () -> Unit,
    onForceDelete: () -> Unit,
    onManageTags: () -> Unit,
    onOpenTransfer: () -> Unit = {},
    usbVolumes: List<UsbVolume> = emptyList(),
    onNavigateUsb: (String) -> Unit = {},
    onEjectUsb: (String) -> Unit = {},
    networkDevices: List<NetworkDevice> = emptyList(),
    onNetworkDeviceTap: (NetworkDevice) -> Unit = {},

    onRefreshNetwork: () -> Unit = {},
    onOpenTerminal: () -> Unit = {},
    isListeningForTransfer: Boolean = false,
    onOpenSettings: () -> Unit,
    onOpenFeatures: () -> Unit = {},
    onOpenSupport: () -> Unit = {},
    onSetTheme: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(280.dp)
            .fillMaxHeight(),
        shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            // --- Storage ---
            item {
                SectionHeader(
                    icon = { Icon(Icons.Filled.Storage, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) },
                    title = stringResource(R.string.storages)
                )
            }
            item {
                DrawerItem(
                    icon = { Icon(Icons.Filled.PhoneAndroid, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary) },
                    title = stringResource(R.string.internal_storage),
                    onClick = { onNavigateToInternalStorage() }
                )
            }
            items(safRoots, key = { "vol_${it.label}" }) { root ->
                val context = LocalContext.current
                val hasAccess = root.safUri.isNotEmpty()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (hasAccess) Modifier.clickable { onNavigate(root.safUri) } else Modifier)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.SdCard, null,
                        Modifier.size(24.dp),
                        tint = if (hasAccess) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(root.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!hasAccess) {
                            Text(stringResource(R.string.no_access), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if (root.totalSpace > 0) {
                            Text(
                                stringResource(
                                    R.string.usb_free_space,
                                    root.freeSpace.toFileSize(context),
                                    root.totalSpace.toFileSize(context)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (hasAccess) {
                        IconButton(onClick = { onRevokeVolumeAccess(root.safUri) }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.disconnect), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        TextButton(onClick = onGrantVolumeAccess) { Text(stringResource(R.string.connect)) }
                    }
                }
            }

            // --- USB OTG ---
            if (usbVolumes.isNotEmpty()) {
                items(
                    usbVolumes.distinctBy { if (it.needsSaf) "saf_${it.uuid}" else it.path },
                    key = { if (it.needsSaf) "usb_saf_${it.uuid}" else "usb_${it.path}" }
                ) { volume ->
                    val context = LocalContext.current
                    if (volume.needsSaf) {
                        // Volume detected but no direct file access — offer SAF
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Usb, null,
                                Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    volume.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    stringResource(R.string.no_access),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = onGrantVolumeAccess) {
                                Text(stringResource(R.string.connect))
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateUsb(volume.path) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Usb, null,
                                Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    volume.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    stringResource(
                                        R.string.usb_free_space,
                                        volume.freeSpace.toFileSize(context),
                                        volume.totalSpace.toFileSize(context)
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onEjectUsb(volume.path) }) {
                                Icon(
                                    Icons.Filled.Eject,
                                    stringResource(R.string.usb_eject),
                                    Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // --- Favorites ---
            item {
                SectionHeader(
                    icon = { Icon(Icons.Filled.Star, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) },
                    title = stringResource(R.string.favorites)
                )
            }
            if (favorites.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_favorite_folders),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else {
                items(favorites, key = { "fav_$it" }) { path ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigate(path) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Folder, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                path.substringAfterLast('/'),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                path,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { onRemoveFavorite(path) }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.delete), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            // --- Recent ---
            item {
                SectionHeader(
                    icon = { Icon(Icons.Filled.History, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    title = stringResource(R.string.recent)
                )
            }
            if (recentPaths.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_recent_folders),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else {
                items(recentPaths, key = { "rec_$it" }) { path ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigate(path) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Folder, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                path.substringAfterLast('/'),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                path,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            // --- Tools ---
            item {
                SectionHeader(
                    icon = { Icon(Icons.Filled.Settings, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) },
                    title = stringResource(R.string.tools)
                )
            }
            item {
                DrawerItem(
                    icon = { Icon(Icons.Filled.DeleteOutline, null, Modifier.size(24.dp)) },
                    title = stringResource(R.string.trash),
                    subtitle = trashSizeInfo.ifEmpty { null },
                    onClick = { onShowTrash(); onDismiss() }
                )
            }
            item {
                DrawerItem(
                    icon = { Icon(Icons.Filled.PieChart, null, Modifier.size(24.dp)) },
                    title = stringResource(R.string.storage_analyzer),
                    onClick = { onOpenStorageAnalysis(); onDismiss() }
                )
            }
            item {
                DrawerItem(
                    icon = { Icon(Icons.Filled.FileCopy, null, Modifier.size(24.dp)) },
                    title = stringResource(R.string.duplicate_detector),
                    onClick = { onOpenDuplicateDetector(); onDismiss() }
                )
            }
            item {
                DrawerItem(
                    icon = { Icon(Icons.Filled.Apps, null, Modifier.size(24.dp)) },
                    title = stringResource(R.string.app_manager),
                    onClick = { onOpenAppManager(); onDismiss() }
                )
            }
            item {
                DrawerItem(
                    icon = { Icon(Icons.Filled.FolderOff, null, Modifier.size(24.dp)) },
                    title = stringResource(R.string.empty_folders),
                    onClick = { onFindEmptyFolders(); onDismiss() }
                )
            }
            item {
                DrawerItem(
                    icon = { Icon(Icons.Filled.DeleteOutline, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.error) },
                    title = stringResource(R.string.force_delete),
                    subtitle = stringResource(R.string.force_delete_subtitle),
                    onClick = { onForceDelete(); onDismiss() }
                )
            }
            item {
                DrawerItem(
                    icon = { Icon(Icons.Filled.Label, null, Modifier.size(24.dp)) },
                    title = stringResource(R.string.tags_title),
                    onClick = { onManageTags(); onDismiss() }
                )
            }
            item {
                if (isListeningForTransfer) {
                    val infiniteTransition = rememberInfiniteTransition(label = "transfer_pulse")
                    val alpha = infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "transfer_alpha"
                    )
                    DrawerItem(
                        icon = {
                            Icon(
                                Icons.AutoMirrored.Filled.SendToMobile, null,
                                Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha.value)
                            )
                        },
                        title = stringResource(R.string.transfer_drawer_item),
                        subtitle = stringResource(R.string.quick_send_drop_here),
                        onClick = { onOpenTransfer(); onDismiss() }
                    )
                } else {
                    DrawerItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.SendToMobile, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary) },
                        title = stringResource(R.string.transfer_drawer_item),
                        onClick = { onOpenTransfer(); onDismiss() }
                    )
                }
            }
            item {
                DrawerItem(
                    icon = { Icon(Icons.Filled.Code, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.tertiary) },
                    title = stringResource(R.string.terminal_title),
                    onClick = { onOpenTerminal(); onDismiss() }
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            // --- Theme ---
            item {
                SectionHeader(
                    icon = {
                        Icon(
                            when (themeMode) {
                                "light" -> Icons.Filled.LightMode
                                "dark" -> Icons.Filled.DarkMode
                                else -> Icons.Filled.BrightnessAuto
                            },
                            null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    title = stringResource(R.string.appearance)
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ThemeButton(stringResource(R.string.theme_system), themeMode == "system", Icons.Filled.BrightnessAuto) { onSetTheme("system") }
                    Spacer(Modifier.width(8.dp))
                    ThemeButton(stringResource(R.string.theme_light), themeMode == "light", Icons.Filled.LightMode) { onSetTheme("light") }
                    Spacer(Modifier.width(8.dp))
                    ThemeButton(stringResource(R.string.theme_dark), themeMode == "dark", Icons.Filled.DarkMode) { onSetTheme("dark") }
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            // --- Settings ---
            item {
                DrawerItem(
                    icon = { Icon(Icons.Filled.Settings, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary) },
                    title = stringResource(R.string.settings),
                    onClick = { onOpenSettings(); onDismiss() }
                )
            }

            // --- Features ---
            item {
                DrawerItem(
                    icon = { Icon(Icons.Filled.Info, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary) },
                    title = stringResource(R.string.features_title),
                    onClick = { onOpenFeatures(); onDismiss() }
                )
            }

            // --- Support ---
            item {
                DrawerItem(
                    icon = { Icon(Icons.Filled.Favorite, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary) },
                    title = stringResource(R.string.support_title),
                    onClick = { onOpenSupport(); onDismiss() }
                )
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(
    icon: @Composable () -> Unit,
    title: String
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun DrawerItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerItemLongClickable(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ThemeButton(
    label: String,
    isSelected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = if (isSelected) 0.dp else 1.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon, null, Modifier.size(20.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
