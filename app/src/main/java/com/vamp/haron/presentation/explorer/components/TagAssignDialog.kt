package com.vamp.haron.presentation.explorer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vamp.haron.R
import com.vamp.haron.domain.model.FileTag
import com.vamp.haron.domain.model.TagColors

@Composable
fun TagAssignDialog(
    paths: List<String>,
    tagDefinitions: List<FileTag>,
    fileTags: Map<String, List<String>>,
    onConfirm: (paths: List<String>, tagNames: List<String>) -> Unit,
    onManageTags: () -> Unit,
    onDismiss: () -> Unit
) {
    // Compute initial checked state per tag
    val checkedState = remember(paths, fileTags, tagDefinitions) {
        val map = mutableStateMapOf<String, Boolean>()
        tagDefinitions.forEach { tag ->
            val count = paths.count { path ->
                fileTags[path]?.contains(tag.name) == true
            }
            map[tag.name] = count == paths.size
        }
        map
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.tags_assign),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onManageTags) {
                    Text(stringResource(R.string.tags_manage))
                }
            }
        },
        text = {
            if (tagDefinitions.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.tags_no_tags),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onManageTags) {
                        Text(stringResource(R.string.tags_create_first))
                    }
                }
            } else {
                LazyColumn {
                    items(tagDefinitions, key = { it.name }) { tag ->
                        val isChecked = checkedState[tag.name] == true
                        val color = TagColors.palette.getOrElse(tag.colorIndex) { TagColors.palette[0] }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    checkedState[tag.name] = !isChecked
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checkedState[tag.name] = it }
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = tag.name,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (tagDefinitions.isNotEmpty()) {
                TextButton(onClick = {
                    val selected = checkedState.filter { it.value }.keys.toList()
                    onConfirm(paths, selected)
                }) {
                    Text(stringResource(R.string.tags_apply))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
