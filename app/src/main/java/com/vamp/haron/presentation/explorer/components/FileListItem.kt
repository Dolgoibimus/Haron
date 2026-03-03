package com.vamp.haron.presentation.explorer.components

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.vamp.haron.R
import androidx.compose.material.icons.filled.Lock
import com.vamp.haron.common.util.ThumbnailCache
import com.vamp.haron.common.util.iconRes
import com.vamp.haron.common.util.toFileSize
import com.vamp.haron.domain.model.FileEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    entry: FileEntry,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isRenaming: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onIconClick: () -> Unit,
    onRenameConfirm: (String) -> Unit,
    onRenameCancel: () -> Unit,
    isGridMode: Boolean = false,
    tagColors: List<Color> = emptyList(),
    contentSnippet: String? = null,
    searchQuery: String = "",
    isDragHovered: Boolean = false,
    marqueeEnabled: Boolean = true,
    folderSize: Long? = null,
    modifier: Modifier = Modifier
) {
    val bgColor = when {
        isDragHovered -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        isRenaming -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.surface
    }

    val clickModifier = when {
        isRenaming -> Modifier
        isSelectionMode -> Modifier.clickable(onClick = onClick)
        else -> Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    }

    val fileType = entry.iconRes()

    val fileIcon = when (fileType) {
        "folder" -> Icons.Filled.Folder
        "image" -> Icons.Filled.Image
        "video" -> Icons.Filled.VideoFile
        "audio" -> Icons.Filled.AudioFile
        "text", "code", "document" -> Icons.Filled.Description
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }

    val isEmptyFolder = entry.isDirectory && entry.childCount == 0

    val iconTint = when {
        isEmptyFolder -> MaterialTheme.colorScheme.outline
        entry.isDirectory -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    // Thumbnail loading
    val isFb2Zip = entry.name.lowercase().endsWith(".fb2.zip")
    val showThumbnail = fileType in listOf("image", "video", "text", "code", "apk", "document", "pdf") || isFb2Zip
    var thumbnail by remember(entry.path) {
        mutableStateOf<Bitmap?>(if (showThumbnail) ThumbnailCache.get(entry.path) else null)
    }

    if (showThumbnail && thumbnail == null) {
        val context = LocalContext.current
        LaunchedEffect(entry.path) {
            thumbnail = ThumbnailCache.loadThumbnail(
                context, entry.path, entry.isContentUri, fileType
            )
        }
    }

    if (isGridMode) {
        // Grid mode: square container, image fitted inside preserving aspect ratio
        Box(
            modifier = modifier
                .background(bgColor)
                .then(clickModifier)
                .padding(2.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 2.dp, horizontal = 2.dp)
            ) {
                // Square icon area — always 1:1, thumbnails fitted inside
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onIconClick),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (thumbnail != null) {
                        // Fit inside square — centered for even corner rounding
                        // APK icons slightly smaller (85%) for aesthetics
                        Image(
                            bitmap = thumbnail!!.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = if (fileType == "apk") Modifier.fillMaxSize(0.85f)
                                       else Modifier.fillMaxSize()
                        )
                    } else {
                        // Standard Material icon — 90% of cell, pinned to bottom
                        val emptyFolderBorder = if (isEmptyFolder) Modifier.border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(6.dp)
                        ) else Modifier
                        Icon(
                            imageVector = fileIcon,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize(0.9f)
                                .align(Alignment.BottomCenter)
                                .then(emptyFolderBorder)
                                .padding(if (isEmptyFolder) 4.dp else 0.dp),
                            tint = iconTint
                        )
                    }
                    // Extension badge (bottom-center)
                    if (!entry.isDirectory && entry.extension.isNotEmpty()) {
                        Text(
                            text = entry.extension.take(4).uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                                lineHeight = 10.sp
                            ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(y = 2.dp)
                                .background(
                                    MaterialTheme.colorScheme.secondaryContainer.copy(
                                        alpha = if (thumbnail != null) 0.85f else 1f
                                    ),
                                    RoundedCornerShape(3.dp)
                                )
                                .padding(horizontal = 3.dp, vertical = 1.dp)
                        )
                    }
                    // Selection indicator (top-end)
                    if (isSelectionMode && !isRenaming) {
                        Icon(
                            imageVector = if (isSelected) {
                                Icons.Filled.CheckCircle
                            } else {
                                Icons.Filled.RadioButtonUnchecked
                            },
                            contentDescription = null,
                            modifier = Modifier
                                .size(20.dp)
                                .align(Alignment.TopEnd)
                                .padding(2.dp),
                            tint = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    // Protected file lock badge (top-start)
                    if (entry.isProtected) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            modifier = Modifier
                                .size(16.dp)
                                .align(Alignment.TopStart)
                                .padding(2.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                if (isRenaming) {
                    InlineRenameField(
                        currentName = entry.name,
                        onConfirm = onRenameConfirm,
                        onCancel = onRenameCancel,
                        isGridMode = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (tagColors.isNotEmpty()) {
                        Row(
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                        ) {
                            tagColors.take(3).forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .padding(end = 2.dp)
                                )
                                Spacer(Modifier.width(2.dp))
                            }
                            if (tagColors.size > 3) {
                                Text(
                                    "+${tagColors.size - 3}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        // List mode: horizontal Row layout
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(bgColor)
                .then(clickModifier)
                .padding(horizontal = 8.dp, vertical = 2.dp),
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

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onIconClick),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val emptyFolderBorderList = if (isEmptyFolder) Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(4.dp)
                    ) else Modifier
                    Icon(
                        imageVector = fileIcon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(26.dp)
                            .then(emptyFolderBorderList)
                            .padding(if (isEmptyFolder) 2.dp else 0.dp),
                        tint = iconTint
                    )
                }
                // Extension badge (bottom-center)
                if (!entry.isDirectory && entry.extension.isNotEmpty() && thumbnail == null) {
                    Text(
                        text = entry.extension.take(4).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 7.sp,
                            lineHeight = 9.sp
                        ),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 2.dp)
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer,
                                RoundedCornerShape(2.dp)
                            )
                            .padding(horizontal = 2.dp)
                    )
                }
                // Protected file lock badge (top-start)
                if (entry.isProtected) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        modifier = Modifier
                            .size(12.dp)
                            .align(Alignment.TopStart),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

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
                        overflow = if (marqueeEnabled) TextOverflow.Clip else TextOverflow.Ellipsis,
                        modifier = if (marqueeEnabled) Modifier.basicMarquee() else Modifier
                    )
                    val dateFormat = remember { SimpleDateFormat("dd.MM.yy HH:mm:ss", Locale.getDefault()) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (entry.isDirectory) {
                                val sizeStr = if (folderSize != null && folderSize > 0) " \u00B7 ${folderSize.toFileSize()}" else ""
                                stringResource(R.string.items_count, entry.childCount) +
                                    sizeStr +
                                    " \u00B7 " + dateFormat.format(Date(entry.lastModified))
                            } else {
                                "${entry.size.toFileSize()} \u00B7 ${dateFormat.format(Date(entry.lastModified))}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (tagColors.isNotEmpty()) {
                            Spacer(Modifier.width(6.dp))
                            tagColors.take(3).forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Spacer(Modifier.width(2.dp))
                            }
                            if (tagColors.size > 3) {
                                Text(
                                    "+${tagColors.size - 3}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    // Content search snippet
                    if (contentSnippet != null && searchQuery.isNotBlank()) {
                        val highlightColor = MaterialTheme.colorScheme.primary
                        Text(
                            text = buildHighlightedSnippet(contentSnippet, searchQuery, highlightColor),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun buildHighlightedSnippet(
    snippet: String,
    query: String,
    highlightColor: Color
) = buildAnnotatedString {
    val lowerSnippet = snippet.lowercase()
    val lowerQuery = query.lowercase()
    var pos = 0
    while (pos < snippet.length) {
        val idx = lowerSnippet.indexOf(lowerQuery, pos)
        if (idx < 0) {
            append(snippet.substring(pos))
            break
        }
        append(snippet.substring(pos, idx))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = highlightColor)) {
            append(snippet.substring(idx, idx + query.length))
        }
        pos = idx + query.length
    }
}

@Composable
private fun InlineRenameField(
    currentName: String,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
    isGridMode: Boolean = false,
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
    val textStyle = if (isGridMode) {
        MaterialTheme.typography.bodySmall.copy(
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    } else {
        MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface
        )
    }

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
        singleLine = !isGridMode,
        maxLines = if (isGridMode) 2 else 1,
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
