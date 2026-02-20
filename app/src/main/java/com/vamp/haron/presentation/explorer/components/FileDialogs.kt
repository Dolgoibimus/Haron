package com.vamp.haron.presentation.explorer.components

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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vamp.haron.presentation.explorer.state.FileTemplate

@Composable
fun DeleteConfirmDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("В корзину?") },
        text = {
            Text(
                if (count == 1) "Переместить выбранный элемент в корзину?"
                else "Переместить выбранные элементы ($count) в корзину?"
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("В корзину")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun CreateFromTemplateDialog(
    onConfirm: (template: FileTemplate, name: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTemplate by remember { mutableStateOf(FileTemplate.FOLDER) }
    var name by remember { mutableStateOf("") }
    val needsName = selectedTemplate != FileTemplate.DATED_FOLDER

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Создать") },
        text = {
            Column {
                FileTemplate.entries.forEach { template ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTemplate = template }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedTemplate == template,
                            onClick = { selectedTemplate = template }
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = templateIcon(template),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = template.label,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                if (needsName) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Имя") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedTemplate, name) },
                enabled = !needsName || name.isNotBlank()
            ) {
                Text("Создать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

private fun templateIcon(template: FileTemplate): ImageVector = when (template) {
    FileTemplate.FOLDER -> Icons.Filled.Folder
    FileTemplate.TXT -> Icons.Filled.TextSnippet
    FileTemplate.MARKDOWN -> Icons.Filled.Description
    FileTemplate.DATED_FOLDER -> Icons.Filled.CalendarMonth
}
