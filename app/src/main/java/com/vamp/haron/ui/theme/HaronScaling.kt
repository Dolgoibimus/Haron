package com.vamp.haron.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class HaronScaling(
    val fontScale: Float = 1.0f,
    val iconScale: Float = 1.0f
)

val LocalHaronScaling = compositionLocalOf { HaronScaling() }

fun scaledIconDp(base: Dp, scale: Float = 1.0f): Dp {
    return (base.value * scale).dp
}
