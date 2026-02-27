package com.vamp.haron.presentation.comparison.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vamp.haron.R
import com.vamp.haron.common.util.toFileSize
import com.vamp.haron.domain.model.ComparisonStatus
import com.vamp.haron.domain.model.FolderComparisonEntry

@Composable
fun FolderDiffView(
    entries: List<FolderComparisonEntry>,
    filterStatus: String?,
    onFilterChange: (String?) -> Unit,
    onOpenDiff: (FolderComparisonEntry) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val filtered = if (filterStatus == null) entries else {
        val status = ComparisonStatus.valueOf(filterStatus)
        entries.filter { it.status == status }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Filter chips
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = filterStatus == null,
                onClick = { onFilterChange(null) },
                label = { Text(stringResource(R.string.compare_filter_all, entries.size)) }
            )
            val statusCounts = entries.groupingBy { it.status }.eachCount()
            val identical = statusCounts[ComparisonStatus.IDENTICAL] ?: 0
            if (identical > 0) {
                FilterChip(
                    selected = filterStatus == ComparisonStatus.IDENTICAL.name,
                    onClick = { onFilterChange(ComparisonStatus.IDENTICAL.name) },
                    label = { Text("= $identical") }
                )
            }
            val diff = statusCounts[ComparisonStatus.DIFFERENT] ?: 0
            if (diff > 0) {
                FilterChip(
                    selected = filterStatus == ComparisonStatus.DIFFERENT.name,
                    onClick = { onFilterChange(ComparisonStatus.DIFFERENT.name) },
                    label = { Text("\u2260 $diff") }
                )
            }
            val left = statusCounts[ComparisonStatus.LEFT_ONLY] ?: 0
            if (left > 0) {
                FilterChip(
                    selected = filterStatus == ComparisonStatus.LEFT_ONLY.name,
                    onClick = { onFilterChange(ComparisonStatus.LEFT_ONLY.name) },
                    label = { Text("\u2190 $left") }
                )
            }
            val right = statusCounts[ComparisonStatus.RIGHT_ONLY] ?: 0
            if (right > 0) {
                FilterChip(
                    selected = filterStatus == ComparisonStatus.RIGHT_ONLY.name,
                    onClick = { onFilterChange(ComparisonStatus.RIGHT_ONLY.name) },
                    label = { Text("\u2192 $right") }
                )
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(filtered, key = { it.relativePath }) { entry ->
                FolderEntryRow(
                    entry = entry,
                    onClick = if (entry.status == ComparisonStatus.DIFFERENT && !entry.isDirectory) {
                        { onOpenDiff(entry) }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun FolderEntryRow(entry: FolderComparisonEntry, onClick: (() -> Unit)? = null) {
    val statusIcon = when (entry.status) {
        ComparisonStatus.IDENTICAL -> Icons.Filled.CheckCircle
        ComparisonStatus.DIFFERENT -> Icons.Filled.Difference
        ComparisonStatus.LEFT_ONLY -> Icons.Filled.Remove
        ComparisonStatus.RIGHT_ONLY -> Icons.Filled.Add
    }
    val statusColor = when (entry.status) {
        ComparisonStatus.IDENTICAL -> Color(0xFF4CAF50)
        ComparisonStatus.DIFFERENT -> Color(0xFFFFC107)
        ComparisonStatus.LEFT_ONLY -> Color(0xFFFF5722)
        ComparisonStatus.RIGHT_ONLY -> Color(0xFF2196F3)
    }
    val fileIcon = if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            statusIcon,
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            fileIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.relativePath,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val sizeInfo = buildString {
                if (entry.leftSize != null) append(entry.leftSize.toFileSize())
                if (entry.leftSize != null && entry.rightSize != null) append(" \u2194 ")
                if (entry.rightSize != null) append(entry.rightSize.toFileSize())
            }
            if (sizeInfo.isNotEmpty()) {
                Text(
                    text = sizeInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
