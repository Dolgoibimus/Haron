package com.vamp.haron.presentation.explorer.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.vamp.haron.R

private data class SplitPreset(val label: String, val sizeMb: Int)

@Composable
fun CreateArchiveDialog(
    onConfirm: (archiveName: String, password: String?, splitSizeMb: Int) -> Unit,
    onDismiss: () -> Unit,
    onOneToOne: (() -> Unit)? = null
) {
    var archiveName by remember { mutableStateOf("archive") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var splitEnabled by remember { mutableStateOf(false) }
    var splitSizeText by remember { mutableStateOf("100") }
    var presetExpanded by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    val customLabel = stringResource(R.string.archive_split_preset_custom)
    val presets = remember {
        listOf(
            SplitPreset("100", 100),
            SplitPreset("500", 500),
            SplitPreset("700", 700),
            SplitPreset("1024", 1024),
            SplitPreset("2048", 2048)
        )
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_zip_archive)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Archive name
                Text(
                    text = stringResource(R.string.archive_name_label),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = archiveName,
                    onValueChange = { archiveName = it },
                    singleLine = true,
                    suffix = { Text(".zip") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )

                Spacer(Modifier.height(16.dp))

                // Password
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.archive_password_label)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (passwordVisible) stringResource(R.string.archive_password_hide) else stringResource(R.string.archive_password_show)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                // Split toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.archive_split_toggle),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = splitEnabled,
                        onCheckedChange = { splitEnabled = it }
                    )
                }

                // Split size
                if (splitEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = splitSizeText,
                            onValueChange = { v -> splitSizeText = v.filter { it.isDigit() } },
                            label = { Text(stringResource(R.string.archive_split_size_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            suffix = { Text(stringResource(R.string.archive_split_size_mb)) },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        // Presets dropdown
                        Column {
                            Text(
                                text = customLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { presetExpanded = true }
                                    .padding(8.dp)
                            )
                            DropdownMenu(
                                expanded = presetExpanded,
                                onDismissRequest = { presetExpanded = false }
                            ) {
                                presets.forEach { preset ->
                                    DropdownMenuItem(
                                        text = { Text("${preset.sizeMb} ${stringResource(R.string.archive_split_size_mb)}") },
                                        onClick = {
                                            splitSizeText = preset.sizeMb.toString()
                                            presetExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(modifier = Modifier.fillMaxWidth()) {
                if (onOneToOne != null) {
                    TextButton(onClick = onOneToOne) {
                        Text(stringResource(R.string.archive_one_to_one))
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                TextButton(
                    onClick = {
                        val pwd = password.ifEmpty { null }
                        val splitMb = if (splitEnabled) splitSizeText.toIntOrNull() ?: 0 else 0
                        onConfirm(archiveName.trim(), pwd, splitMb)
                    },
                    enabled = archiveName.trim().isNotEmpty()
                ) {
                    Text(stringResource(R.string.create))
                }
            }
        }
    )
}
