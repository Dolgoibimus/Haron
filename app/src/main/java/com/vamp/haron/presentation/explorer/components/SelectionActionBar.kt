package com.vamp.haron.presentation.explorer.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vamp.haron.common.util.toFileSize

@Composable
fun SelectionActionBar(
    dirCount: Int,
    fileCount: Int,
    totalSize: Long,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onZip: () -> Unit,
    onAddToShelf: () -> Unit,
    onInfo: () -> Unit,
    onOpenWith: () -> Unit,
    isSizeCalculating: Boolean = false,
    modifier: Modifier = Modifier
) {
    val totalCount = dirCount + fileCount

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${formatFileCount(dirCount, fileCount)} \u00B7 ${totalSize.toFileSize()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isSizeCalculating) {
                    Spacer(Modifier.width(6.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Копировать", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onMove, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Переместить", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "В корзину",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onRename,
                    enabled = totalCount == 1,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Переименовать", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onZip, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Archive, contentDescription = "ZIP", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onAddToShelf, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Inventory2, contentDescription = "На полку", modifier = Modifier.size(20.dp))
                }
                IconButton(
                    onClick = onInfo,
                    enabled = totalCount == 1,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Filled.Info, contentDescription = "Свойства", modifier = Modifier.size(20.dp))
                }
                IconButton(
                    onClick = onOpenWith,
                    enabled = totalCount == 1 && dirCount == 0,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = "Открыть в...", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

private fun formatFileCount(dirs: Int, files: Int): String {
    val parts = buildList {
        if (dirs > 0) add("$dirs ${pluralDirs(dirs)}")
        if (files > 0) add("$files ${pluralFiles(files)}")
    }
    return parts.joinToString(" и ").ifEmpty { "0 файлов" }
}

private fun pluralDirs(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> "папок"
        mod10 == 1 -> "папка"
        mod10 in 2..4 -> "папки"
        else -> "папок"
    }
}

private fun pluralFiles(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> "файлов"
        mod10 == 1 -> "файл"
        mod10 in 2..4 -> "файла"
        else -> "файлов"
    }
}
