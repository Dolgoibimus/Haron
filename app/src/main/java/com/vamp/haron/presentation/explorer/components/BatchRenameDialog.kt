package com.vamp.haron.presentation.explorer.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vamp.haron.R
import com.vamp.haron.domain.model.FileEntry
import java.util.Locale

private enum class CaseMode { NONE, LOWER, UPPER, TITLE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchRenameDialog(
    entries: List<FileEntry>,
    recentPatterns: List<String>,
    onConfirm: (renames: List<Pair<String, String>>) -> Unit,
    onSavePattern: (pattern: String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Все состояния ВНУТРИ Dialog — иначе субкомпозиция не видит обновления
        var template by remember { mutableStateOf("") }
        var startNumberText by remember { mutableStateOf("1") }
        var stepText by remember { mutableStateOf("1") }
        var findText by remember { mutableStateOf("") }
        var replaceText by remember { mutableStateOf("") }
        var caseMode by remember { mutableStateOf(CaseMode.NONE) }
        var prefix by remember { mutableStateOf("") }
        var suffix by remember { mutableStateOf("") }
        var showHistory by remember { mutableStateOf(false) }

        val startNumber = startNumberText.toIntOrNull() ?: 0
        val step = (stepText.toIntOrNull() ?: 1).coerceAtLeast(1)

        // Превью — пересчитывается при каждой рекомпозиции внутри Dialog
        val previewList = entries.mapIndexed { index, entry ->
            val oldName = entry.name
            val nameWithoutExt = oldName.substringBeforeLast('.', oldName)
            val ext = if (oldName.contains('.') && !entry.isDirectory)
                ".${oldName.substringAfterLast('.')}" else ""

            // Шаг 1: Шаблон или оригинальное имя
            var result = if (template.isNotBlank()) {
                applyTemplate(template, index, startNumber, step)
            } else {
                nameWithoutExt
            }

            // Шаг 2: Найти и заменить
            if (findText.isNotEmpty()) {
                result = result.replace(findText, replaceText)
            }

            // Шаг 3: Регистр
            result = when (caseMode) {
                CaseMode.NONE -> result
                CaseMode.LOWER -> result.lowercase(Locale.getDefault())
                CaseMode.UPPER -> result.uppercase(Locale.getDefault())
                CaseMode.TITLE -> result.toTitleCase()
            }

            // Шаг 4: Префикс + суффикс
            val newName = prefix + result + suffix + ext
            Triple(entry, oldName, newName)
        }

        val changedCount = previewList.count { (_, old, new) -> old != new }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.batch_rename_title)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        if (recentPatterns.isNotEmpty()) {
                            Box {
                                IconButton(onClick = { showHistory = true }) {
                                    Icon(Icons.Filled.History, contentDescription = stringResource(R.string.batch_rename_history))
                                }
                                DropdownMenu(
                                    expanded = showHistory,
                                    onDismissRequest = { showHistory = false }
                                ) {
                                    recentPatterns.forEach { pattern ->
                                        DropdownMenuItem(
                                            text = { Text(pattern) },
                                            onClick = {
                                                template = pattern
                                                showHistory = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        TextButton(
                            onClick = {
                                if (template.isNotBlank()) {
                                    onSavePattern(template)
                                }
                                val renames = previewList
                                    .filter { (_, old, new) -> old != new }
                                    .map { (entry, _, new) -> entry.path to new }
                                onConfirm(renames)
                            },
                            enabled = changedCount > 0
                        ) {
                            Text(stringResource(R.string.batch_rename_apply, changedCount))
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                // 1. Шаблон + нумерация
                OutlinedTextField(
                    value = template,
                    onValueChange = { template = it },
                    label = { Text(stringResource(R.string.batch_rename_template)) },
                    placeholder = { Text("photo_###") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.batch_rename_counter_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = startNumberText,
                        onValueChange = { v ->
                            if (v.isEmpty() || v.all { c -> c.isDigit() || (c == '-' && v.indexOf(c) == 0) }) {
                                startNumberText = v
                            }
                        },
                        label = { Text(stringResource(R.string.batch_rename_start_number)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = stepText,
                        onValueChange = { v ->
                            if (v.isEmpty() || v.all { c -> c.isDigit() }) {
                                stepText = v
                            }
                        },
                        label = { Text(stringResource(R.string.batch_rename_step)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(8.dp))

                // 2. Найти и заменить
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = findText,
                        onValueChange = { findText = it },
                        label = { Text(stringResource(R.string.batch_rename_find)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = replaceText,
                        onValueChange = { replaceText = it },
                        label = { Text(stringResource(R.string.batch_rename_replace_with)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(8.dp))

                // 3. Регистр
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = caseMode == CaseMode.NONE,
                        onClick = { caseMode = CaseMode.NONE },
                        label = { Text(stringResource(R.string.case_as_is)) }
                    )
                    FilterChip(
                        selected = caseMode == CaseMode.LOWER,
                        onClick = { caseMode = CaseMode.LOWER },
                        label = { Text(stringResource(R.string.case_lower)) }
                    )
                    FilterChip(
                        selected = caseMode == CaseMode.UPPER,
                        onClick = { caseMode = CaseMode.UPPER },
                        label = { Text(stringResource(R.string.case_upper)) }
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    FilterChip(
                        selected = caseMode == CaseMode.TITLE,
                        onClick = { caseMode = CaseMode.TITLE },
                        label = { Text(stringResource(R.string.case_title)) }
                    )
                }

                Spacer(Modifier.height(8.dp))

                // 4. Префикс / суффикс
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = prefix,
                        onValueChange = { prefix = it },
                        label = { Text(stringResource(R.string.batch_rename_prefix)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = suffix,
                        onValueChange = { suffix = it },
                        label = { Text(stringResource(R.string.batch_rename_suffix)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Предпросмотр
                Text(
                    text = stringResource(R.string.batch_rename_preview),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(previewList) { _, (_, oldName, newName) ->
                        val changed = oldName != newName
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = oldName,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = " \u2192 ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = newName,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                                color = if (changed) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

private fun applyTemplate(template: String, index: Int, startNumber: Int, step: Int): String {
    val number = startNumber + index * step
    val regex = Regex("#+")
    return regex.replace(template) { match ->
        val width = match.value.length
        number.toString().padStart(width, '0')
    }
}

private fun String.toTitleCase(): String {
    return split(" ", "_", "-").joinToString(" ") { word ->
        word.lowercase(Locale.getDefault()).replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
    }
}
