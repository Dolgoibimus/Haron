package com.vamp.haron.presentation.explorer.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vamp.haron.R
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.common.util.toFileSize
import com.vamp.haron.common.util.toRelativeDate
import com.vamp.haron.domain.model.TrashEntry
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashDialog(
    entries: List<TrashEntry>,
    totalSize: Long,
    maxSizeMb: Int = 0,
    onRestore: (List<String>) -> Unit,
    onDeletePermanently: (List<String>) -> Unit,
    onEmptyTrash: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            // Header
            Text(
                text = stringResource(R.string.trash),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            val pluralLabel = pluralItems(entries.size)
            val sizeText = if (maxSizeMb > 0) {
                val maxBytes = maxSizeMb.toLong() * 1024 * 1024
                stringResource(R.string.trash_items_summary_limit, entries.size, pluralLabel, totalSize.toFileSize(), maxBytes.toFileSize())
            } else {
                stringResource(R.string.trash_items_summary, entries.size, pluralLabel, totalSize.toFileSize())
            }
            Text(
                text = sizeText,
                style = MaterialTheme.typography.bodySmall,
                color = if (maxSizeMb > 0 && totalSize >= maxSizeMb.toLong() * 1024 * 1024)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (selectedIds.isNotEmpty()) {
                    TextButton(onClick = {
                        onRestore(selectedIds.toList())
                        selectedIds = emptySet()
                    }) {
                        Icon(Icons.Filled.RestoreFromTrash, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.restore_action, selectedIds.size))
                    }
                    TextButton(onClick = {
                        onDeletePermanently(selectedIds.toList())
                        selectedIds = emptySet()
                    }) {
                        Icon(
                            Icons.Filled.DeleteForever,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.delete_forever), color = MaterialTheme.colorScheme.error)
                    }
                } else if (entries.isNotEmpty()) {
                    TextButton(onClick = onEmptyTrash) {
                        Icon(
                            Icons.Filled.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.empty_trash), color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.trash_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .height(360.dp)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        TrashItem(
                            entry = entry,
                            isSelected = entry.id in selectedIds,
                            onClick = {
                                selectedIds = if (entry.id in selectedIds) {
                                    selectedIds - entry.id
                                } else {
                                    selectedIds + entry.id
                                }
                            },
                            onRestore = { onRestore(listOf(entry.id)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashItem(
    entry: TrashEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRestore: () -> Unit
) {
    val now = System.currentTimeMillis()
    val ttlMs = TimeUnit.DAYS.toMillis(HaronConstants.TRASH_TTL_DAYS.toLong())
    val remainingMs = (entry.trashedAt + ttlMs - now).coerceAtLeast(0)
    val remainingDays = TimeUnit.MILLISECONDS.toDays(remainingMs).toInt()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.width(8.dp))

        Icon(
            imageVector = if (entry.isDirectory) Icons.Filled.Folder
            else Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.originalPath.substringAfterLast('/'),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.trash_item_details, entry.size.toFileSize(), entry.trashedAt.toRelativeDate(), remainingDays, stringResource(R.string.days_short)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        IconButton(onClick = onRestore, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.RestoreFromTrash,
                contentDescription = stringResource(R.string.restore),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun pluralItems(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> stringResource(R.string.plural_items_many)
        mod10 == 1 -> stringResource(R.string.plural_items_one)
        mod10 in 2..4 -> stringResource(R.string.plural_items_few)
        else -> stringResource(R.string.plural_items_many)
    }
}
