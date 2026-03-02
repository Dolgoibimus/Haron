package com.vamp.haron.presentation.explorer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.vamp.haron.R
import com.vamp.haron.data.model.SortDirection
import com.vamp.haron.data.model.SortField
import com.vamp.haron.data.model.SortOrder
import com.vamp.haron.domain.model.FileTag
import com.vamp.haron.domain.model.TagColors

@Composable
fun SortMenu(
    currentOrder: SortOrder,
    onSortChanged: (SortOrder) -> Unit,
    tagDefinitions: List<FileTag> = emptyList(),
    activeTagFilter: String? = null,
    onTagFilterChanged: ((String?) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = stringResource(R.string.sort_label),
                    modifier = Modifier.size(18.dp)
                )
                // Small indicator dot when tag filter is active
                if (activeTagFilter != null) {
                    val filterTag = tagDefinitions.find { it.name == activeTagFilter }
                    if (filterTag != null) {
                        val dotColor = TagColors.palette.getOrElse(filterTag.colorIndex) { TagColors.palette[0] }
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                                .align(Alignment.TopEnd)
                        )
                    }
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            SortField.entries.forEach { field ->
                val label = when (field) {
                    SortField.NAME -> stringResource(R.string.sort_by_name)
                    SortField.DATE -> stringResource(R.string.sort_by_date)
                    SortField.SIZE -> stringResource(R.string.sort_by_size)
                    SortField.EXTENSION -> stringResource(R.string.sort_by_type)
                }
                val isSelected = currentOrder.field == field
                DropdownMenuItem(
                    modifier = Modifier.height(36.dp),
                    text = {
                        Text(
                            text = label,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        val newDirection = if (isSelected) {
                            if (currentOrder.direction == SortDirection.ASCENDING) {
                                SortDirection.DESCENDING
                            } else {
                                SortDirection.ASCENDING
                            }
                        } else {
                            SortDirection.ASCENDING
                        }
                        onSortChanged(SortOrder(field, newDirection))
                        expanded = false
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = if (currentOrder.direction == SortDirection.ASCENDING) {
                                    Icons.Filled.ArrowUpward
                                } else {
                                    Icons.Filled.ArrowDownward
                                },
                                contentDescription = if (currentOrder.direction == SortDirection.ASCENDING) {
                                    stringResource(R.string.ascending)
                                } else {
                                    stringResource(R.string.descending)
                                },
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null
                )
            }

            // Tag filter section
            if (tagDefinitions.isNotEmpty() && onTagFilterChanged != null) {
                HorizontalDivider()
                DropdownMenuItem(
                    modifier = Modifier.height(28.dp),
                    text = {
                        Text(
                            stringResource(R.string.tags_filter_by),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = {},
                    enabled = false,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                )
                // "All files" option
                DropdownMenuItem(
                    modifier = Modifier.height(36.dp),
                    text = {
                        Text(
                            stringResource(R.string.tags_filter_all),
                            color = if (activeTagFilter == null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onTagFilterChanged(null)
                        expanded = false
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)
                )
                tagDefinitions.forEach { tag ->
                    val color = TagColors.palette.getOrElse(tag.colorIndex) { TagColors.palette[0] }
                    val isActive = activeTagFilter == tag.name
                    DropdownMenuItem(
                        modifier = Modifier.height(36.dp),
                        text = {
                            Box {
                                androidx.compose.foundation.layout.Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        tag.name,
                                        color = if (isActive) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        },
                        onClick = {
                            onTagFilterChanged(tag.name)
                            expanded = false
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
