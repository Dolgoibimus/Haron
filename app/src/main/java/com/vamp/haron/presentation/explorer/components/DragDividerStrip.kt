package com.vamp.haron.presentation.explorer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vamp.haron.R
import com.vamp.haron.presentation.explorer.state.DragOperation

@Composable
fun DragDividerStrip(
    isLandscape: Boolean,
    dragOperation: DragOperation,
    dividerStartPx: Float,
    dividerEndPx: Float,
    crossStartPx: Float,
    crossEndPx: Float
) {
    val density = LocalDensity.current
    val stripSizeDp = 48.dp
    val stripSizePx = with(density) { stripSizeDp.roundToPx() }
    val dividerCenterPx = (dividerStartPx + dividerEndPx) / 2f
    val offsetPrimary = (dividerCenterPx - stripSizePx / 2f).toInt()

    val copyAlpha = if (dragOperation == DragOperation.COPY) 1f else 0.55f
    val moveAlpha = if (dragOperation == DragOperation.MOVE) 1f else 0.55f

    val copyColor = MaterialTheme.colorScheme.tertiary
    val moveColor = MaterialTheme.colorScheme.primary
    val copyTextColor = MaterialTheme.colorScheme.onTertiary
    val moveTextColor = MaterialTheme.colorScheme.onPrimary
    val dividerColor = MaterialTheme.colorScheme.outlineVariant

    if (isLandscape) {
        // Vertical strip along the divider
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetPrimary, crossStartPx.toInt()) }
                .width(stripSizeDp)
                .height(with(density) { (crossEndPx - crossStartPx).toDp() })
        ) {
            Column(modifier = Modifier.matchParentSize()) {
                // Top = COPY
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .alpha(copyAlpha)
                        .background(copyColor, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = copyTextColor
                        )
                        Text(
                            text = stringResource(R.string.dnd_copy),
                            style = MaterialTheme.typography.labelSmall,
                            color = copyTextColor
                        )
                    }
                }
                // Separator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(dividerColor)
                )
                // Bottom = MOVE
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .alpha(moveAlpha)
                        .background(moveColor, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.AutoMirrored.Filled.DriveFileMove,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = moveTextColor
                        )
                        Text(
                            text = stringResource(R.string.dnd_move),
                            style = MaterialTheme.typography.labelSmall,
                            color = moveTextColor
                        )
                    }
                }
            }
        }
    } else {
        // Horizontal strip along the divider
        Box(
            modifier = Modifier
                .offset { IntOffset(crossStartPx.toInt(), offsetPrimary) }
                .width(with(density) { (crossEndPx - crossStartPx).toDp() })
                .height(stripSizeDp)
        ) {
            Row(modifier = Modifier.matchParentSize()) {
                // Left = COPY
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .alpha(copyAlpha)
                        .background(copyColor, RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = copyTextColor
                        )
                        Text(
                            text = stringResource(R.string.dnd_copy),
                            style = MaterialTheme.typography.labelSmall,
                            color = copyTextColor,
                            modifier = Modifier.offset(x = 4.dp)
                        )
                    }
                }
                // Separator
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(dividerColor)
                )
                // Right = MOVE
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .alpha(moveAlpha)
                        .background(moveColor, RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Filled.DriveFileMove,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = moveTextColor
                        )
                        Text(
                            text = stringResource(R.string.dnd_move),
                            style = MaterialTheme.typography.labelSmall,
                            color = moveTextColor,
                            modifier = Modifier.offset(x = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
