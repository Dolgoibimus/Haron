package com.vamp.haron.presentation.explorer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun PanelDivider(
    totalHeight: Float,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDoubleTap: () -> Unit,
    onBookmarkTap: () -> Unit = {},
    onRightZoneTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(totalHeight) {
                detectDragGestures(
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                ) { change, dragAmount ->
                    change.consume()
                    if (totalHeight > 0f) {
                        onDrag(dragAmount.y / totalHeight)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left zone: tap → bookmarks pie menu
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onBookmarkTap() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) { }

            // Center zone: tap → reset 50/50
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onDoubleTap() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) { }

            // Right zone: tap → swap panels / TBD
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onRightZoneTap() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) { }
        }

        // Drag handle indicator (centered, on top)
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
