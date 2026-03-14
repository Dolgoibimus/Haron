package com.vamp.haron.presentation.explorer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vamp.haron.presentation.explorer.state.DragOperation

@Composable
fun DragOverlay(
    previewName: String,
    fileCount: Int,
    offset: Offset,
    dragOperation: DragOperation = DragOperation.MOVE
) {
    val density = LocalDensity.current
    val xPx = offset.x.toInt() - with(density) { 40.dp.roundToPx() }
    val yPx = offset.y.toInt() - with(density) { 24.dp.roundToPx() }

    Box {
        // Operation indicator — floating above text, ~2-3rd char from name start
        Icon(
            if (dragOperation == DragOperation.COPY) Icons.Filled.ContentCopy
            else Icons.AutoMirrored.Filled.DriveFileMove,
            contentDescription = null,
            modifier = Modifier
                .offset {
                    // 12dp padding + 20dp icon + 8dp spacer = 40dp to text start, +2 chars ≈ +14dp
                    val iconX = xPx + with(density) { 54.dp.roundToPx() }
                    val iconY = yPx - with(density) { 14.dp.roundToPx() }
                    IntOffset(iconX, iconY)
                }
                .size(14.dp),
            tint = if (dragOperation == DragOperation.COPY) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.primary
        )
        Surface(
            modifier = Modifier
                .offset { IntOffset(xPx, yPx) },
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                // 7-8 char gap before file name
                Spacer(Modifier.width(56.dp))
                Text(
                    text = previewName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (fileCount > 1) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$fileCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}
