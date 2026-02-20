package com.vamp.haron.presentation.explorer.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vamp.haron.data.model.SortDirection
import com.vamp.haron.data.model.SortField
import com.vamp.haron.data.model.SortOrder

@Composable
fun SortMenu(
    currentOrder: SortOrder,
    onSortChanged: (SortOrder) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = "Сортировка",
                modifier = Modifier.size(18.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
        SortField.entries.forEach { field ->
            val label = when (field) {
                SortField.NAME -> "По имени"
                SortField.DATE -> "По дате"
                SortField.SIZE -> "По размеру"
                SortField.EXTENSION -> "По типу"
            }
            val isSelected = currentOrder.field == field
            DropdownMenuItem(
                text = { Text(label) },
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
                trailingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null
                        )
                    }
                } else null
            )
        }
        }
    }
}
