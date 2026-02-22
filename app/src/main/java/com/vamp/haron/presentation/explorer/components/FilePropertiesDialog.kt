package com.vamp.haron.presentation.explorer.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vamp.haron.common.util.toFileSize
import com.vamp.haron.domain.usecase.FileProperties
import com.vamp.haron.domain.usecase.HashResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FilePropertiesDialog(
    properties: FileProperties?,
    hashResult: HashResult?,
    isHashCalculating: Boolean,
    onCalculateHash: () -> Unit,
    onCopyHash: (String) -> Unit,
    onRemoveExif: () -> Unit,
    onDismiss: () -> Unit,
    isContentUri: Boolean = false,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
        title = {
            Text(
                text = properties?.name ?: "Свойства",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // === General section ===
                item {
                    SectionHeader("Общее")
                }

                if (properties != null) {
                    item { PropertyRow("Путь", properties.path) }
                    item {
                        PropertyRow(
                            "Размер",
                            if (properties.isDirectory) {
                                "${properties.totalSize.toFileSize()} (${properties.childCount} файлов)"
                            } else {
                                "${properties.size.toFileSize()} (${properties.size} байт)"
                            }
                        )
                    }
                    item {
                        PropertyRow(
                            "Дата изменения",
                            SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
                                .format(Date(properties.lastModified))
                        )
                    }
                    item { PropertyRow("MIME-тип", properties.mimeType) }
                    if (properties.permissions.isNotEmpty()) {
                        item { PropertyRow("Разрешения", properties.permissions) }
                    }
                } else {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }

                // === EXIF section ===
                if (properties != null && properties.exifData.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        SectionHeader("EXIF-данные")
                    }

                    val exifEntries = properties.exifData.entries.toList()
                    items(exifEntries, key = { it.key }) { (key, value) ->
                        PropertyRow(key, value)
                    }

                    // Remove EXIF button
                    if (!isContentUri) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onRemoveExif,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(
                                    Icons.Filled.DeleteSweep,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Удалить EXIF")
                            }
                        }
                    }
                }

                // === Hash section ===
                if (properties != null && !properties.isDirectory) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        SectionHeader("Хеш-суммы")
                    }

                    if (hashResult == null && !isHashCalculating) {
                        item {
                            Button(
                                onClick = onCalculateHash,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Filled.Tag,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Вычислить хеш")
                            }
                        }
                    }

                    if (isHashCalculating && (hashResult == null || hashResult.md5.isEmpty())) {
                        item {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "Вычисление...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { hashResult?.progress ?: 0f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                )
                            }
                        }
                    }

                    if (hashResult != null && hashResult.md5.isNotEmpty()) {
                        item {
                            HashRow("MD5", hashResult.md5, onCopy = { onCopyHash(hashResult.md5) })
                        }
                        item {
                            HashRow("SHA-256", hashResult.sha256, onCopy = { onCopyHash(hashResult.sha256) })
                        }
                    }
                }
            }
        },
        modifier = modifier
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun PropertyRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HashRow(label: String, hash: String, onCopy: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "Копировать",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Text(
            text = hash,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
