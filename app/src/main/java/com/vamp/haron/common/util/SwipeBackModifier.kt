package com.vamp.haron.common.util

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.geometry.Offset

/**
 * Edge swipe from left to trigger back navigation.
 * Touch must start within [edgeWidthDp] from left edge,
 * and drag right must exceed [thresholdDp].
 */
fun Modifier.swipeBackFromLeft(
    onBack: () -> Unit,
    edgeWidthDp: Int = 40,
    thresholdDp: Int = 80
): Modifier = composed {
    val density = LocalDensity.current
    val edgePx = with(density) { edgeWidthDp.dp.toPx() }
    val thresholdPx = with(density) { thresholdDp.dp.toPx() }

    var startX = 0f
    var totalDragX = 0f
    var fired = false

    this.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragStart = { offset: Offset ->
                startX = offset.x
                totalDragX = 0f
                fired = false
            },
            onDragEnd = {
                startX = 0f
                totalDragX = 0f
                fired = false
            },
            onDragCancel = {
                startX = 0f
                totalDragX = 0f
                fired = false
            },
            onHorizontalDrag = { change, dragAmount ->
                if (startX < edgePx && !fired) {
                    totalDragX += dragAmount
                    if (totalDragX > thresholdPx) {
                        fired = true
                        change.consume()
                        onBack()
                    }
                }
            }
        )
    }
}
