package com.vamp.haron.presentation.explorer.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val toolLabels = listOf(
    "Корзина",
    "Анализатор",
    "Дупликатор",
    "APK",
    "Плеер",
    "Читалка"
)

@Composable
fun ToolsPopup(
    onToolSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val radiusDp = 120.dp
    val itemWidthDp = 80.dp
    val itemHeightDp = 36.dp

    Popup(
        alignment = Alignment.CenterEnd,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false)
    ) {
        Layout(
            content = {
                for (index in 0 until 6) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        shadowElevation = 6.dp,
                        tonalElevation = 4.dp,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                        modifier = Modifier
                            .size(width = itemWidthDp, height = itemHeightDp)
                            .clickable { onToolSelected(index) }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = toolLabels[index],
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        ) { measurables, constraints ->
            val radius = with(density) { radiusDp.toPx() }
            val itemWidth = with(density) { itemWidthDp.roundToPx() }
            val itemHeight = with(density) { itemHeightDp.roundToPx() }

            val placeables = measurables.map {
                it.measure(Constraints.fixed(itemWidth, itemHeight))
            }

            // Semicircle opening to the LEFT from right edge
            // 6 items, step = 180 / 5 = 36°
            // Angles: 90° (top) to 270° (bottom)
            val step = 31.0
            val startAngle = 180.0 - (step * 5) / 2.0  // center the arc

            val width = (radius + itemWidth).toInt()
            val height = (radius * 2 + itemHeight).toInt()
            val centerX = width - itemWidth / 2  // right side
            val centerY = height / 2

            val nudge = with(density) { 10.dp.roundToPx() }

            layout(width, height) {
                placeables.forEachIndexed { index, placeable ->
                    val angleDeg = startAngle + index * step
                    val angleRad = angleDeg * PI / 180.0
                    val x = centerX + (radius * cos(angleRad)).toInt() - itemWidth / 2
                    val yNudge = when (index) {
                        1 -> nudge   // second from top → down
                        4 -> -nudge  // second from bottom → up
                        else -> 0
                    }
                    val y = centerY - (radius * sin(angleRad)).toInt() - itemHeight / 2 + yNudge
                    placeable.placeRelative(x, y)
                }
            }
        }
    }
}
