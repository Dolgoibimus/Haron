package com.vamp.haron.presentation.appmanager

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamp.haron.R
import com.vamp.haron.common.util.toFileSize
import com.vamp.haron.domain.model.InstalledAppInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagerScreen(
    onBack: () -> Unit,
    viewModel: AppManagerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    @Suppress("DEPRECATION")
    val uninstallLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onUninstallResult(result.resultCode == android.app.Activity.RESULT_OK)
    }

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_manager_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search
            TextField(
                value = state.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text(stringResource(R.string.search_apps)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Filled.Close, stringResource(R.string.clear))
                        }
                    }
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            )

            // Sort chips + system toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AppSortMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.sortMode == mode,
                        onClick = { viewModel.setSortMode(mode) },
                        label = { Text(stringResource(mode.labelRes), style = MaterialTheme.typography.labelSmall) }
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.system_apps), style = MaterialTheme.typography.labelSmall)
                Switch(
                    checked = state.showSystemApps,
                    onCheckedChange = { viewModel.toggleSystemApps() }
                )
            }

            HorizontalDivider()

            // App count
            if (!state.isLoading) {
                Text(
                    text = stringResource(R.string.app_count, state.apps.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // App list
            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.loading_apps))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    items(state.apps, key = { it.packageName }) { app ->
                        AppItem(
                            app = app,
                            onClick = { viewModel.selectApp(app) },
                            isRemoving = state.removingPackage == app.packageName,
                            onRemovalDone = { viewModel.onRemovalAnimationDone(app.packageName) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }

    // Bottom sheet for actions
    val selectedApp = state.selectedApp
    if (selectedApp != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.selectApp(null) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        ) {
            AppActionsSheet(
                app = selectedApp,
                appFilesInfo = state.appFilesInfo,
                isLoadingFiles = state.isLoadingFiles,
                onExtract = { viewModel.extractApk(selectedApp) },
                onUninstall = {
                    val pkg = selectedApp.packageName
                    try {
                        @Suppress("DEPRECATION")
                        uninstallLauncher.launch(
                            Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$pkg")).apply {
                                putExtra(Intent.EXTRA_RETURN_RESULT, true)
                            }
                        )
                        viewModel.markUninstalling(selectedApp)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                },
                onSettings = { viewModel.openAppSettings(selectedApp) },
                onLoadFiles = { viewModel.loadAppFiles(selectedApp.packageName) }
            )
        }
    }
}

@Composable
private fun AppItem(
    app: InstalledAppInfo,
    onClick: () -> Unit,
    isRemoving: Boolean = false,
    onRemovalDone: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sweepFraction = remember { Animatable(1f) }
    val rowAlpha = remember { Animatable(1f) }
    val errorColor = MaterialTheme.colorScheme.error

    LaunchedEffect(isRemoving) {
        if (isRemoving) {
            sweepFraction.animateTo(0f, tween(800, easing = LinearEasing))
            rowAlpha.animateTo(0f, tween(400))
            onRemovalDone()
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = rowAlpha.value }
            .clickable(enabled = !isRemoving, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(40.dp)) {
            if (app.icon != null) {
                Image(
                    bitmap = app.icon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerLow,
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Android, null,
                        Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isRemoving) {
                Canvas(modifier = Modifier.fillMaxSize().padding(1.dp)) {
                    drawArc(
                        color = errorColor,
                        startAngle = -90f,
                        sweepAngle = sweepFraction.value * 360f,
                        useCenter = false,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.appName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = app.versionName ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = app.apkSize.toFileSize(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
        Text(
            text = dateFormat.format(Date(app.lastUpdateDate)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AppActionsSheet(
    app: InstalledAppInfo,
    appFilesInfo: com.vamp.haron.domain.model.AppFilesInfo?,
    isLoadingFiles: Boolean,
    onExtract: () -> Unit,
    onUninstall: () -> Unit,
    onSettings: () -> Unit,
    onLoadFiles: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        // App header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (app.icon != null) {
                Image(
                    bitmap = app.icon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    app.appName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${app.packageName} • ${app.apkSize.toFileSize()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()

        // Actions
        ActionItem(
            icon = { Icon(Icons.Filled.Download, null) },
            title = stringResource(R.string.extract_apk),
            subtitle = stringResource(R.string.save_to_downloads),
            onClick = onExtract
        )
        ActionItem(
            icon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = stringResource(R.string.uninstall),
            subtitle = if (app.isSystemApp) stringResource(R.string.system_app) else stringResource(R.string.uninstall_app),
            onClick = onUninstall
        )
        ActionItem(
            icon = { Icon(Icons.Filled.Settings, null) },
            title = stringResource(R.string.app_settings),
            subtitle = stringResource(R.string.open_in_system_settings),
            onClick = onSettings
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Files section
        if (appFilesInfo == null && !isLoadingFiles) {
            ActionItem(
                icon = { Icon(Icons.Filled.FolderOpen, null) },
                title = stringResource(R.string.app_files),
                subtitle = stringResource(R.string.app_files_subtitle),
                onClick = onLoadFiles
            )
        } else if (isLoadingFiles) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.app_files_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (appFilesInfo != null) {
            // Header with total size
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.FolderOpen, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.app_files_total, appFilesInfo.totalSize.toFileSize()),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // File entries — expandable
            appFilesInfo.entries.forEach { entry ->
                AppFileRowExpandable(entry = entry)
            }

            // Shizuku banner if not available and there are locked entries
            if (!appFilesInfo.shizukuAvailable && appFilesInfo.entries.any { !it.isAccessible }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Lock, null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.app_files_shizuku_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AppFileRowExpandable(
    entry: com.vamp.haron.domain.model.AppFileEntry,
    indent: Int = 0
) {
    var expanded by remember { mutableStateOf(false) }
    val hasChildren = entry.children.isNotEmpty() || entry.isDirectory
    val color = if (entry.isAccessible) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val startPadding = (24 + indent * 16).dp

    Column {
        // Main row — clickable to expand/collapse
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(start = startPadding, end = 20.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Expand/lock icon
            if (!entry.isAccessible) {
                Icon(
                    Icons.Filled.Lock, null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            } else if (hasChildren) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(Modifier.width(16.dp))
            }
            Spacer(Modifier.width(4.dp))

            // Name
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (entry.isDirectory) FontWeight.Medium else FontWeight.Normal,
                color = color,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Size
            Text(
                text = if (entry.size >= 0) entry.size.toFileSize() else "—",
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }

        // Expanded: show path + children with animation
        androidx.compose.animation.AnimatedVisibility(visible = expanded) {
            Column {
                // Path — full, no line limit
                Text(
                    text = entry.path,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = startPadding + 20.dp, end = 20.dp, bottom = 2.dp)
                )

                // Children
                entry.children.forEach { child ->
                    AppFileRowExpandable(entry = child, indent = indent + 1)
                }
            }
        }
    }
}

@Composable
private fun ActionItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
