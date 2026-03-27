package com.vamp.haron.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamp.haron.R
import com.vamp.haron.common.util.toFileSize
import com.vamp.haron.data.backup.BackupManager
import com.vamp.haron.domain.model.SecureFileEntry
import com.vamp.haron.domain.model.backup.BackupInfo
import com.vamp.haron.domain.model.backup.BackupResult
import com.vamp.haron.domain.model.backup.BackupSection
import com.vamp.haron.domain.model.backup.RestoreWarning
import com.vamp.haron.domain.model.backup.WarningType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf<BackupInfo?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<BackupInfo?>(null) }

    // Диалог результата восстановления
    state.restoreResult?.let { result ->
        RestoreResultDialog(
            result = result,
            onDismiss = { viewModel.clearMessages() }
        )
    }

    // Диалог подтверждения удаления
    showDeleteConfirm?.let { backup ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text(stringResource(R.string.backup_delete_title)) },
            text = { Text(stringResource(R.string.backup_delete_confirm, backup.file.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteBackup(backup.file)
                    showDeleteConfirm = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Диалог пароля для создания
    if (showPasswordDialog) {
        PasswordDialog(
            title = stringResource(R.string.backup_password_title),
            description = stringResource(R.string.backup_password_description),
            confirmText = stringResource(R.string.backup_create_button),
            onConfirm = { password ->
                showPasswordDialog = false
                viewModel.createBackup(password)
            },
            onDismiss = { showPasswordDialog = false }
        )
    }

    // Диалог пароля для восстановления
    showRestoreDialog?.let { backup ->
        val isEncrypted = backup.file.name.endsWith(BackupManager.ENCRYPTED_EXT)
        if (isEncrypted) {
            PasswordDialog(
                title = stringResource(R.string.backup_restore_password_title),
                description = stringResource(R.string.backup_restore_password_description),
                confirmText = stringResource(R.string.backup_restore_button),
                onConfirm = { password ->
                    showRestoreDialog = null
                    viewModel.restoreBackup(backup.file, password)
                },
                onDismiss = { showRestoreDialog = null }
            )
        } else {
            AlertDialog(
                onDismissRequest = { showRestoreDialog = null },
                title = { Text(stringResource(R.string.backup_restore_title)) },
                text = { Text(stringResource(R.string.backup_restore_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        showRestoreDialog = null
                        viewModel.restoreBackup(backup.file, null)
                    }) { Text(stringResource(R.string.backup_restore_button)) }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreDialog = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(Unit) {
                    val edgePx = 40.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (down.position.x > edgePx) return@awaitEachGesture
                        var totalDrag = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) {
                                if (totalDrag > 100.dp.toPx()) onBack()
                                break
                            }
                            totalDrag += change.positionChange().x
                        }
                    }
                }
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // === СОЗДАНИЕ БЭКАПА ===
            item {
                Text(
                    stringResource(R.string.backup_create_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
            }

            // Чекбоксы секций (блокируются во время бэкапа)
            item {
                SectionCheckbox(
                    label = stringResource(R.string.backup_section_settings),
                    description = stringResource(R.string.backup_section_settings_desc),
                    checked = BackupSection.SETTINGS in state.selectedSections,
                    enabled = !state.isCreating,
                    onCheckedChange = { viewModel.toggleSection(BackupSection.SETTINGS) }
                )
            }
            item {
                SectionCheckbox(
                    label = stringResource(R.string.backup_section_credentials),
                    description = stringResource(R.string.backup_section_credentials_desc),
                    checked = BackupSection.CREDENTIALS in state.selectedSections,
                    enabled = !state.isCreating,
                    onCheckedChange = { viewModel.toggleSection(BackupSection.CREDENTIALS) }
                )
            }
            item {
                SectionCheckbox(
                    label = stringResource(R.string.backup_section_books),
                    description = stringResource(R.string.backup_section_books_desc),
                    checked = BackupSection.BOOKS in state.selectedSections,
                    enabled = !state.isCreating,
                    onCheckedChange = { viewModel.toggleSection(BackupSection.BOOKS) }
                )
            }

            // Защищённая папка — разворачиваемый список с пофайловым выбором
            item {
                SecureFolderSection(
                    isSelected = BackupSection.SECURE_FOLDER in state.selectedSections,
                    onToggleSection = { viewModel.toggleSection(BackupSection.SECURE_FOLDER) },
                    secureFiles = state.secureFiles,
                    selectedIds = state.selectedSecureFileIds,
                    onToggleFile = { viewModel.toggleSecureFile(it) },
                    onToggleAll = { viewModel.toggleAllSecureFiles() },
                    enabled = !state.isCreating
                )
            }

            // Галочка "Защитить паролем" + кнопка создания
            item {
                Spacer(Modifier.height(4.dp))
                val secureFolderSelected = BackupSection.SECURE_FOLDER in state.selectedSections
                var wantPassword by remember { mutableStateOf(false) }
                // Для secure folder пароль обязателен
                val usePassword = secureFolderSelected || wantPassword

                if (!secureFolderSelected) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { wantPassword = !wantPassword }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = wantPassword, onCheckedChange = { wantPassword = it })
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.backup_optional_password),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = {
                        if (usePassword) {
                            showPasswordDialog = true
                        } else {
                            viewModel.createBackup(null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isCreating && state.selectedSections.isNotEmpty()
                ) {
                    if (state.isCreating) {
                        Text("${state.createProgressPercent}%")
                    } else {
                        Icon(Icons.Filled.Backup, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.backup_create_button))
                    }
                }
            }

            // Сообщения
            state.successMessage?.let { msg ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(msg, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            state.error?.let { msg ->
                item {
                    Card(
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
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(msg, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // === СПИСОК БЭКАПОВ ===
            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.backup_list_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (state.backups.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.backup_list_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }

            items(state.backups, key = { it.file.absolutePath }) { backup ->
                val isRestored = backup.file.absolutePath == state.restoredFilePath
                BackupItem(
                    backup = backup,
                    isRestoring = state.isRestoring,
                    isRestored = isRestored,
                    onRestore = { showRestoreDialog = backup },
                    onDelete = { showDeleteConfirm = backup }
                )
            }

            // Прогресс восстановления
            if (state.isRestoring) {
                item {
                    val progress = state.restoreProgress
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress.first / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${progress.first}% — ${progress.second}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.backup_restoring),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun SectionCheckbox(
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onCheckedChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, enabled = enabled, onCheckedChange = { onCheckedChange() })
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            )
        }
    }
}

@Composable
private fun BackupItem(
    backup: BackupInfo,
    isRestoring: Boolean,
    isRestored: Boolean = false,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    val isEncrypted = backup.file.name.endsWith(BackupManager.ENCRYPTED_EXT)

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (isEncrypted) Icons.Filled.Lock else Icons.Filled.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        dateFormat.format(Date(backup.file.lastModified())),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    val info = buildString {
                        append(backup.sizeBytes.toFileSize())
                        backup.manifest?.let { m ->
                            append(" • ${m.deviceName}")
                            append(" • v${m.appVersion}")
                        }
                        if (isEncrypted) append(" • \uD83D\uDD12")
                    }
                    Text(
                        info,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Секции
                    backup.manifest?.sections?.let { sections ->
                        Text(
                            sections.joinToString(", ") { sectionName(it) },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onDelete,
                    enabled = !isRestoring
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.delete))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onRestore,
                    enabled = !isRestoring && !isRestored
                ) {
                    Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (isRestored) stringResource(R.string.backup_already_restored)
                        else stringResource(R.string.backup_restore_button)
                    )
                }
            }
        }
    }
}

@Composable
private fun PasswordDialog(
    title: String,
    description: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(description, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.backup_password_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = password.isNotBlank()
            ) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/**
 * Диалог отчёта после восстановления.
 *
 * Показывает:
 * - ✅ зелёная галочка — всё OK
 * - ⚠️ жёлтое предупреждение — есть расхождения (мёртвые пути, протухшие токены)
 * - ❌ ошибка — восстановление не удалось
 */
@Composable
private fun RestoreResultDialog(
    result: BackupResult,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (result) {
                    is BackupResult.Success -> stringResource(R.string.backup_restore_success_title)
                    is BackupResult.PartialSuccess -> stringResource(R.string.backup_restore_partial_title)
                    is BackupResult.Error -> stringResource(R.string.backup_restore_error_title)
                }
            )
        },
        text = {
            Column {
                when (result) {
                    is BackupResult.Success -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(result.message)
                        }
                    }
                    is BackupResult.PartialSuccess -> {
                        Text(
                            result.message,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        for (warning in result.warnings) {
                            WarningItem(warning)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    is BackupResult.Error -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(result.message)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}

@Composable
private fun WarningItem(warning: RestoreWarning) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    warning.details,
                    style = MaterialTheme.typography.bodySmall
                )
                if (warning.affectedPaths.isNotEmpty() && warning.affectedPaths.size <= 5) {
                    Spacer(Modifier.height(4.dp))
                    for (path in warning.affectedPaths) {
                        Text(
                            path,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (warning.affectedPaths.size > 5) {
                    Spacer(Modifier.height(4.dp))
                    for (path in warning.affectedPaths.take(3)) {
                        Text(
                            path,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "...и ещё ${warning.affectedPaths.size - 3}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Секция защищённой папки — разворачиваемый список файлов с чекбоксами.
 *
 * Показывает общий размер выбранных файлов. Предупреждение: файлы будут
 * расшифрованы из Keystore и защищены только паролем ZIP.
 */
@Composable
private fun SecureFolderSection(
    isSelected: Boolean,
    onToggleSection: () -> Unit,
    secureFiles: List<SecureFileEntry>,
    selectedIds: Set<String>,
    onToggleFile: (String) -> Unit,
    onToggleAll: () -> Unit,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val totalSize = secureFiles.filter { it.id in selectedIds }.sumOf { it.originalSize }

    Column {
        // Заголовок секции с чекбоксом
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onToggleSection)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = isSelected, enabled = enabled, onCheckedChange = { onToggleSection() })
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.backup_section_secure), style = MaterialTheme.typography.bodyLarge)
                if (secureFiles.isEmpty()) {
                    Text(
                        stringResource(R.string.backup_section_secure_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        stringResource(
                            R.string.backup_section_secure_desc,
                            selectedIds.size,
                            secureFiles.size,
                            totalSize.toFileSize()
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Стрелка разворачивания (только если есть файлы и секция выбрана)
            if (secureFiles.isNotEmpty() && isSelected) {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = stringResource(R.string.backup_expand)
                    )
                }
            }
        }

        // Предупреждение
        if (isSelected && secureFiles.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
                modifier = Modifier.padding(start = 40.dp)
            ) {
                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.backup_secure_warning),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        // Развёрнутый список файлов
        if (expanded && isSelected) {
            Spacer(Modifier.height(4.dp))
            // Кнопка "Выбрать все / Снять все"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 40.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onToggleAll) {
                    Text(
                        if (selectedIds.size == secureFiles.size) {
                            stringResource(R.string.backup_deselect_all)
                        } else {
                            stringResource(R.string.backup_select_all)
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            for (entry in secureFiles) {
                if (entry.isDirectory) continue
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleFile(entry.id) }
                        .padding(start = 40.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = entry.id in selectedIds,
                        onCheckedChange = { onToggleFile(entry.id) },
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        entry.originalName,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    Text(
                        entry.originalSize.toFileSize(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun sectionName(section: BackupSection): String = when (section) {
    BackupSection.SETTINGS -> "Settings"
    BackupSection.CREDENTIALS -> "Credentials"
    BackupSection.BOOKS -> "Books"
    BackupSection.SECURE_FOLDER -> "Secure Folder"
}
