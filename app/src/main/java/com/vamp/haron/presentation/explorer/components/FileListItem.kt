package com.vamp.haron.presentation.explorer.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vamp.haron.common.util.iconRes
import com.vamp.haron.common.util.toFileSize
import com.vamp.haron.common.util.toRelativeDate
import com.vamp.haron.domain.model.FileEntry

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    entry: FileEntry,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isRenaming: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRenameConfirm: (String) -> Unit,
    onRenameCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = when {
        isRenaming -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .then(
                when {
                    isRenaming -> Modifier
                    isSelectionMode -> Modifier.clickable(onClick = onClick)
                    else -> Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                }
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode && !isRenaming) {
            Icon(
                imageVector = if (isSelected) {
                    Icons.Filled.CheckCircle
                } else {
                    Icons.Filled.RadioButtonUnchecked
                },
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Icon(
            imageVector = when (entry.iconRes()) {
                "folder" -> Icons.Filled.Folder
                "image" -> Icons.Filled.Image
                "video" -> Icons.Filled.VideoFile
                "audio" -> Icons.Filled.AudioFile
                "text", "code", "document" -> Icons.Filled.Description
                else -> Icons.AutoMirrored.Filled.InsertDriveFile
            },
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = if (entry.isDirectory) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        Spacer(modifier = Modifier.width(12.dp))

        if (isRenaming) {
            InlineRenameField(
                currentName = entry.name,
                onConfirm = onRenameConfirm,
                onCancel = onRenameCancel,
                modifier = Modifier.weight(1f)
            )
        } else {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (entry.isDirectory) {
                        "${entry.childCount} элем."
                    } else {
                        "${entry.size.toFileSize()} \u00B7 ${entry.lastModified.toRelativeDate()}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InlineRenameField(
    currentName: String,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dotIndex = currentName.lastIndexOf('.')
    val selectionEnd = if (dotIndex > 0) dotIndex else currentName.length

    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = currentName,
                selection = TextRange(0, selectionEnd)
            )
        )
    }
    val focusRequester = remember { FocusRequester() }
    val primaryColor = MaterialTheme.colorScheme.primary
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface
    )

    // Track whether confirm was already called (to avoid cancel on focus loss after Done)
    var confirmed by remember { mutableStateOf(false) }
    var hasFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    BasicTextField(
        value = textFieldValue,
        onValueChange = { textFieldValue = it },
        textStyle = textStyle,
        singleLine = true,
        cursorBrush = SolidColor(primaryColor),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = {
                confirmed = true
                val newName = textFieldValue.text.trim()
                if (newName.isNotBlank() && newName != currentName) {
                    onConfirm(newName)
                } else {
                    onCancel()
                }
            }
        ),
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                if (hasFocused && !state.isFocused && !confirmed) {
                    onCancel()
                }
                if (state.isFocused) hasFocused = true
            }
            .drawBehind {
                drawLine(
                    color = primaryColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 2.dp.toPx()
                )
            }
    )
}
