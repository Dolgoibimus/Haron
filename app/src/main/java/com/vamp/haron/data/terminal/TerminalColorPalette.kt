package com.vamp.haron.data.terminal

import androidx.compose.ui.graphics.Color

object TerminalColorPalette {

    // Standard 16 colors (0-7 normal, 8-15 bright)
    private val colors16 = arrayOf(
        Color(0xFF000000), // 0 Black
        Color(0xFFCD0000), // 1 Red
        Color(0xFF00CD00), // 2 Green
        Color(0xFFCDCD00), // 3 Yellow
        Color(0xFF0000EE), // 4 Blue
        Color(0xFFCD00CD), // 5 Magenta
        Color(0xFF00CDCD), // 6 Cyan
        Color(0xFFE5E5E5), // 7 White
        Color(0xFF7F7F7F), // 8 Bright Black (Gray)
        Color(0xFFFF0000), // 9 Bright Red
        Color(0xFF00FF00), // 10 Bright Green
        Color(0xFFFFFF00), // 11 Bright Yellow
        Color(0xFF5C5CFF), // 12 Bright Blue
        Color(0xFFFF00FF), // 13 Bright Magenta
        Color(0xFF00FFFF), // 14 Bright Cyan
        Color(0xFFFFFFFF)  // 15 Bright White
    )

    fun ansi16(index: Int, bright: Boolean): Color {
        val i = if (bright && index < 8) index + 8 else index
        return colors16.getOrElse(i.coerceIn(0, 15)) { colors16[7] }
    }

    fun ansi256(index: Int): Color {
        return when {
            index < 16 -> colors16[index]
            index < 232 -> {
                // 6x6x6 color cube
                val n = index - 16
                val r = (n / 36) * 51
                val g = ((n / 6) % 6) * 51
                val b = (n % 6) * 51
                Color(r, g, b)
            }
            else -> {
                // Grayscale ramp: 232-255
                val level = 8 + (index - 232) * 10
                Color(level, level, level)
            }
        }
    }
}
