package com.vamp.haron.presentation.explorer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vamp.haron.R
import com.vamp.haron.presentation.explorer.state.DragOperation

@Composable
fun PanelDivider(
    totalSize: Float,
    topFileCount: Int,
    bottomFileCount: Int,
    isTopActive: Boolean,
    isLandscape: Boolean = false,
    isDragging: Boolean = false,
    dragOperation: DragOperation = DragOperation.MOVE,
    topSelectedSize: String = "",
    bottomSelectedSize: String = "",
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDoubleTap: () -> Unit,
    onBookmarkTap: () -> Unit = {},
    onRightZoneTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    val copyColor = MaterialTheme.colorScheme.tertiary
    val moveColor = MaterialTheme.colorScheme.primary
    val copyActiveAlpha = if (dragOperation == DragOperation.COPY) 1f else 0.5f
    val moveActiveAlpha = if (dragOperation == DragOperation.MOVE) 1f else 0.5f

    val sizeModifier = if (isLandscape) {
        Modifier.fillMaxHeight().width(24.dp)
    } else {
        Modifier.fillMaxWidth().height(24.dp)
    }

    Box(
        modifier = modifier
            .then(sizeModifier)
            .pointerInput(totalSize, isLandscape) {
                detectDragGestures(
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                ) { change, dragAmount ->
                    change.consume()
                    if (totalSize > 0f) {
                        val delta = if (isLandscape) dragAmount.x else dragAmount.y
                        onDrag(delta / totalSize)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (isLandscape) {
            // Landscape: vertical layout
            Column(
                modifier = Modifier.fillMaxHeight().width(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top zone: first panel file count + tap → bookmarks
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .width(24.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { onBookmarkTap() })
                        },
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        text = topFileCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isTopActive) activeColor else inactiveColor,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                // Top size / COPY zone (always present for stable layout)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .width(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDragging) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = stringResource(R.string.dnd_copy),
                            modifier = Modifier.size(14.dp),
                            tint = copyColor.copy(alpha = copyActiveAlpha)
                        )
                    } else if (topSelectedSize.isNotEmpty()) {
                        Text(
                            text = topSelectedSize,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isTopActive) activeColor else inactiveColor
                        )
                    }
                }

                // Center zone: tap → reset 50/50
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .width(24.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { onDoubleTap() })
                        },
                    contentAlignment = Alignment.Center
                ) { }

                // Bottom size / MOVE zone (always present for stable layout)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .width(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDragging) {
                        Icon(
                            Icons.AutoMirrored.Filled.DriveFileMove,
                            contentDescription = stringResource(R.string.dnd_move),
                            modifier = Modifier.size(14.dp),
                            tint = moveColor.copy(alpha = moveActiveAlpha)
                        )
                    } else if (bottomSelectedSize.isNotEmpty()) {
                        Text(
                            text = bottomSelectedSize,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (!isTopActive) activeColor else inactiveColor
                        )
                    }
                }

                // Bottom zone: second panel file count + tap → tools
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .width(24.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { onRightZoneTap() })
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        text = bottomFileCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (!isTopActive) activeColor else inactiveColor,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }

            // Vertical drag handle indicator
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        } else {
            // Portrait: horizontal layout
            Row(
                modifier = Modifier.fillMaxWidth().height(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left zone: top panel file count + tap → bookmarks
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { onBookmarkTap() })
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = topFileCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isTopActive) activeColor else inactiveColor,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }

                // Top size / COPY zone (always present for stable layout)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDragging) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = stringResource(R.string.dnd_copy),
                            modifier = Modifier.size(14.dp),
                            tint = copyColor.copy(alpha = copyActiveAlpha)
                        )
                    } else if (topSelectedSize.isNotEmpty()) {
                        Text(
                            text = topSelectedSize,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isTopActive) activeColor else inactiveColor
                        )
                    }
                }

                // Center zone: tap → reset 50/50
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { onDoubleTap() })
                        },
                    contentAlignment = Alignment.Center
                ) { }

                // Bottom size / MOVE zone (always present for stable layout)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDragging) {
                        Icon(
                            Icons.AutoMirrored.Filled.DriveFileMove,
                            contentDescription = stringResource(R.string.dnd_move),
                            modifier = Modifier.size(14.dp),
                            tint = moveColor.copy(alpha = moveActiveAlpha)
                        )
                    } else if (bottomSelectedSize.isNotEmpty()) {
                        Text(
                            text = bottomSelectedSize,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (!isTopActive) activeColor else inactiveColor
                        )
                    }
                }

                // Right zone: bottom panel file count + tap → tools
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { onRightZoneTap() })
                        },
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = bottomFileCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (!isTopActive) activeColor else inactiveColor,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            }

            // Horizontal drag handle indicator
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}
