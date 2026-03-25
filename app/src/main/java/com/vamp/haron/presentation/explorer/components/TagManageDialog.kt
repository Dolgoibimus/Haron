package com.vamp.haron.presentation.explorer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vamp.haron.R
import com.vamp.haron.common.util.swipeBackFromLeft
import com.vamp.haron.domain.model.FileTag
import com.vamp.haron.domain.model.TagColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagManageDialog(
    tagDefinitions: List<FileTag>,
    onAddTag: (name: String, colorIndex: Int) -> Unit,
    onEditTag: (oldName: String, newName: String, colorIndex: Int) -> Unit,
    onDeleteTag: (name: String) -> Unit,
    onDismiss: () -> Unit
) {
    // Sub-screen: null = list, FileTag = edit, "create" sentinel
    var subScreen by remember { mutableStateOf<Any?>(null) }
    var deleteConfirmTag by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        when (val screen = subScreen) {
            is FileTag -> {
                // Edit tag screen
                TagCreateEditScreen(
                    title = stringResource(R.string.tags_edit),
                    initialName = screen.name,
                    initialColorIndex = screen.colorIndex,
                    existingNames = tagDefinitions.map { it.name }.filter { it != screen.name }.toSet(),
                    confirmLabel = stringResource(R.string.save),
                    onConfirm = { name, colorIndex ->
                        onEditTag(screen.name, name, colorIndex)
                        subScreen = null
                    },
                    onBack = { subScreen = null }
                )
            }
            "create" -> {
                // Create tag screen
                TagCreateEditScreen(
                    title = stringResource(R.string.tag_create_title),
                    initialName = "",
                    initialColorIndex = 0,
                    existingNames = tagDefinitions.map { it.name }.toSet(),
                    confirmLabel = stringResource(R.string.tags_create),
                    onConfirm = { name, colorIndex ->
                        onAddTag(name, colorIndex)
                        subScreen = null
                    },
                    onBack = { subScreen = null }
                )
            }
            else -> {
                // Main list screen
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.tags_manage)) },
                            navigationIcon = {
                                IconButton(onClick = onDismiss) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                                }
                            }
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(onClick = { subScreen = "create" }) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.tags_create))
                        }
                    }
                ) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .swipeBackFromLeft(onBack = onDismiss)
                            .padding(paddingValues)
                    ) {
                        if (tagDefinitions.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.tags_no_tags),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                items(tagDefinitions, key = { it.name }) { tag ->
                                    val color = TagColors.palette.getOrElse(tag.colorIndex) { TagColors.palette[0] }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            text = tag.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { subScreen = tag }) {
                                            Icon(
                                                Icons.Filled.Edit,
                                                contentDescription = stringResource(R.string.tags_edit),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        IconButton(onClick = { deleteConfirmTag = tag.name }) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = stringResource(R.string.tags_delete),
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation
    if (deleteConfirmTag != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirmTag = null },
            title = { Text(stringResource(R.string.tags_delete)) },
            text = { Text(stringResource(R.string.tags_delete_confirm, deleteConfirmTag!!)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteTag(deleteConfirmTag!!)
                    deleteConfirmTag = null
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmTag = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagCreateEditScreen(
    title: String,
    initialName: String,
    initialColorIndex: Int,
    existingNames: Set<String>,
    confirmLabel: String,
    onConfirm: (name: String, colorIndex: Int) -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var colorIndex by remember { mutableIntStateOf(initialColorIndex) }
    var nameError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val trimmed = name.trim()
                            if (trimmed.isEmpty()) return@TextButton
                            if (trimmed in existingNames) {
                                nameError = "exists"
                                return@TextButton
                            }
                            onConfirm(trimmed, colorIndex)
                        },
                        enabled = name.trim().isNotEmpty()
                    ) {
                        Text(confirmLabel)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .swipeBackFromLeft(onBack = onBack)
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    nameError = null
                },
                label = { Text(stringResource(R.string.tags_name_hint)) },
                isError = nameError != null,
                supportingText = if (nameError != null) {
                    { Text(stringResource(R.string.tags_name_exists)) }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) return@KeyboardActions
                    if (trimmed in existingNames) {
                        nameError = "exists"
                        return@KeyboardActions
                    }
                    onConfirm(trimmed, colorIndex)
                }),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.tags_color),
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TagColors.palette.forEachIndexed { idx, color ->
                    val isSelected = idx == colorIndex
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                else Modifier
                            )
                            .clickable { colorIndex = idx },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.surface
                            )
                        }
                    }
                }
            }
        }
    }
}
