package com.vamp.haron.presentation.explorer.components

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.History
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vamp.haron.R

@Composable
fun DrawerMenu(
    favorites: List<String>,
    recentPaths: List<String>,
    safRoots: List<Pair<String, String>>,
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
    onToggleShield: () -> Unit = {},
    secureFolderInfo: String = "",
    onOpenSettings: () -> Unit,
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
            items(safRoots, key = { "vol_${it.first}" }) { (label, safUri) ->
                val hasAccess = safUri.isNotEmpty()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (hasAccess) Modifier.clickable { onNavigate(safUri) } else Modifier)
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
                        Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!hasAccess) {
                            Text(stringResource(R.string.no_access), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (hasAccess) {
                        IconButton(onClick = { onRevokeVolumeAccess(safUri) }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.disconnect), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        TextButton(onClick = onGrantVolumeAccess) { Text(stringResource(R.string.connect)) }
                    }
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

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
                DrawerItem(
                    icon = { Icon(Icons.Filled.Lock, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary) },
                    title = stringResource(R.string.secure_folder),
                    subtitle = secureFolderInfo.ifEmpty { null },
                    onClick = { onToggleShield(); onDismiss() }
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
