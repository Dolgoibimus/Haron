package com.vamp.haron.domain.model

import androidx.compose.ui.graphics.Color

data class FileTag(val name: String, val colorIndex: Int)

object TagColors {
    val palette = listOf(
        Color(0xFFE53935), // Red
        Color(0xFFFB8C00), // Orange
        Color(0xFFFDD835), // Yellow
        Color(0xFF43A047), // Green
        Color(0xFF00897B), // Teal
        Color(0xFF1E88E5), // Blue
        Color(0xFF8E24AA), // Purple
        Color(0xFFD81B60)  // Pink
    )
}
