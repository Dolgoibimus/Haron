package com.vamp.haron.presentation.explorer.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vamp.haron.common.util.toFileSize
import com.vamp.haron.domain.model.ConflictFileInfo
import com.vamp.haron.domain.model.ConflictPair
import com.vamp.haron.domain.model.ConflictResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConflictComparisonCard(
    pair: ConflictPair,
    currentIndex: Int,
    totalCount: Int,
    onResolve: (ConflictResolution, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var applyToAll by remember { mutableStateOf(false) }
    val remaining = totalCount - currentIndex - 1

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Text(
                    text = "Файл уже существует (${currentIndex + 1} из $totalCount)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Comparison columns
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Source column
                    FileInfoColumn(
                        label = "Откуда",
                        info = pair.source,
                        isNewer = pair.source.lastModified > pair.destination.lastModified,
                        isLarger = pair.source.size > pair.destination.size,
                        modifier = Modifier.weight(1f)
                    )

                    // Destination column
                    FileInfoColumn(
                        label = "Куда",
                        info = pair.destination,
                        isNewer = pair.destination.lastModified > pair.source.lastModified,
                        isLarger = pair.destination.size > pair.source.size,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Apply to all checkbox
                if (remaining > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = applyToAll,
                            onCheckedChange = { applyToAll = it }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Применить ко всем (ещё $remaining)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action buttons — adaptive: 1 row if fits, otherwise 2 rows
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    if (maxWidth >= 360.dp) {
                        // Wide enough — all 3 in one row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TextButton(onClick = { onResolve(ConflictResolution.SKIP, applyToAll) }) {
                                Text("Пропустить")
                            }
                            TextButton(onClick = { onResolve(ConflictResolution.RENAME, applyToAll) }) {
                                Text("Переименовать")
                            }
                            TextButton(onClick = { onResolve(ConflictResolution.REPLACE, applyToAll) }) {
                                Text("Заменить")
                            }
                        }
                    } else {
                        // Narrow — "Переименовать" сверху, остальные снизу рядом
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            TextButton(onClick = { onResolve(ConflictResolution.RENAME, applyToAll) }) {
                                Text("Переименовать")
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                TextButton(onClick = { onResolve(ConflictResolution.SKIP, applyToAll) }) {
                                    Text("Пропустить")
                                }
                                TextButton(onClick = { onResolve(ConflictResolution.REPLACE, applyToAll) }) {
                                    Text("Заменить")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileInfoColumn(
    label: String,
    info: ConflictFileInfo,
    isNewer: Boolean,
    isLarger: Boolean,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }
    val context = LocalContext.current

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Preview / icon
        if (info.isImage) {
            var bitmap by remember(info.path) { mutableStateOf<android.graphics.Bitmap?>(null) }
            LaunchedEffect(info.path) {
                bitmap = withContext(Dispatchers.IO) {
                    try {
                        val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                        if (info.path.startsWith("content://")) {
                            val uri = android.net.Uri.parse(info.path)
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                BitmapFactory.decodeStream(stream, null, options)
                            }
                        } else {
                            BitmapFactory.decodeFile(info.path, options)
                        }
                    } catch (_: Exception) { null }
                }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = info.name,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Filled.Image,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Icon(
                Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // File name
        Text(
            text = info.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Size with arrow indicator
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = info.size.toFileSize(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isLarger) FontWeight.Bold else FontWeight.Normal,
                color = if (isLarger) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            if (isLarger) {
                Spacer(Modifier.width(2.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Date with arrow indicator
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isNewer) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Spacer(Modifier.width(2.dp))
            }
            Text(
                text = dateFormat.format(Date(info.lastModified)),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isNewer) FontWeight.Bold else FontWeight.Normal,
                color = if (isNewer) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
