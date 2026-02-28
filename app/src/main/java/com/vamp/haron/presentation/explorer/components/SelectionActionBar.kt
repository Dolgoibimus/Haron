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
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vamp.haron.R
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
    onTag: () -> Unit,
    onProtect: () -> Unit = {},
    onSend: () -> Unit = {},
    onCast: () -> Unit = {},
    onCompare: () -> Unit = {},
    onHideInFile: () -> Unit = {},
    onInfo: () -> Unit,
    onOpenWith: () -> Unit,
    hasProtectedFiles: Boolean = false,
    isSizeCalculating: Boolean = false,
    isArchiveMode: Boolean = false,
    onExtract: () -> Unit = {},
    onExtractAll: () -> Unit = {},
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
                    text = if (isArchiveMode) {
                        formatFileCount(dirCount, fileCount)
                    } else {
                        "${formatFileCount(dirCount, fileCount)} \u00B7 ${totalSize.toFileSize()}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isSizeCalculating && !isArchiveMode) {
                    Spacer(Modifier.width(6.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp
                    )
                }
            }
            if (isArchiveMode) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onExtractAll,
                        modifier = Modifier.height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.extract_all),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Button(
                        onClick = onExtract,
                        modifier = Modifier.height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.extract_count_format, totalCount),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.copy_action), modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onMove, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = stringResource(R.string.move_action), modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = stringResource(R.string.to_trash),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onRename,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.rename_action), modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onZip, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Archive, contentDescription = stringResource(R.string.zip_action), modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onAddToShelf, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Inventory2, contentDescription = stringResource(R.string.to_shelf_action), modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onTag, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Label, contentDescription = stringResource(R.string.tags_action), modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onSend, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.transfer_send), modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onCast, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Cast, contentDescription = stringResource(R.string.cast_title), modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = onCompare,
                        enabled = totalCount == 2,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Filled.Compare, contentDescription = stringResource(R.string.compare_action), modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = onHideInFile,
                        enabled = totalCount == 1 && dirCount == 0,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Filled.VisibilityOff, contentDescription = stringResource(R.string.stego_hide_action), modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onProtect, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.Shield,
                            contentDescription = if (hasProtectedFiles) stringResource(R.string.unprotect_action) else stringResource(R.string.protect_action),
                            modifier = Modifier.size(20.dp),
                            tint = if (hasProtectedFiles) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onInfo,
                        enabled = totalCount == 1,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.properties_action), modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = onOpenWith,
                        enabled = totalCount == 1 && dirCount == 0,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.open_with_action), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun formatFileCount(dirs: Int, files: Int): String {
    val folderLabel = pluralForm(
        dirs,
        stringResource(R.string.plural_folders_nom),
        stringResource(R.string.plural_folders_gen_few),
        stringResource(R.string.plural_folders_genitive)
    )
    val fileLabel = pluralForm(
        files,
        stringResource(R.string.plural_files_nom),
        stringResource(R.string.plural_files_gen_few),
        stringResource(R.string.plural_files_genitive)
    )
    val andConj = stringResource(R.string.and_conjunction)
    val zeroFiles = stringResource(R.string.zero_files)
    val parts = buildList {
        if (dirs > 0) add("$dirs $folderLabel")
        if (files > 0) add("$files $fileLabel")
    }
    return parts.joinToString(andConj).ifEmpty { zeroFiles }
}

private fun pluralForm(count: Int, nom: String, genFew: String, genitive: String): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> genitive
        mod10 == 1 -> nom
        mod10 in 2..4 -> genFew
        else -> genitive
    }
}
