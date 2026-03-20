package com.vamp.haron.presentation.terminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import com.vamp.haron.data.terminal.TerminalBuffer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val BG_COLOR = Color(0xFF1E1E1E)
private val CURSOR_COLOR = Color(0xFFD4D4D4)

/**
 * Terminal grid renderer using Canvas.
 * Draws a character grid with colors, cursor, and styles.
 * Auto-calculates rows/cols from available space.
 */
@Composable
fun TerminalGrid(
    buffer: TerminalBuffer,
    onSizeCalculated: ((rows: Int, cols: Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val fontSizeSp = 13.sp
    val fontSizePx = with(density) { fontSizeSp.toPx() }

    val textPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = fontSizePx
            color = android.graphics.Color.parseColor("#D4D4D4")
        }
    }

    val charWidth = remember(fontSizePx) { textPaint.measureText("M") }
    val charHeight = remember(fontSizePx) {
        val fm = textPaint.fontMetrics
        fm.descent - fm.ascent
    }
    val baseline = remember(fontSizePx) { -textPaint.fontMetrics.ascent }

    // Cursor blink
    var cursorVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(530)
            cursorVisible = !cursorVisible
        }
    }

    // Track buffer version for recomposition
    var lastVersion by remember { mutableLongStateOf(0L) }
    val currentVersion = buffer.version
    if (currentVersion != lastVersion) {
        lastVersion = currentVersion
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                if (charWidth > 0 && charHeight > 0) {
                    val newCols = (size.width / charWidth).toInt().coerceIn(20, 300)
                    val newRows = (size.height / charHeight).toInt().coerceIn(5, 200)
                    if (newCols != buffer.cols || newRows != buffer.rows) {
                        buffer.resize(newRows, newCols)
                        onSizeCalculated?.invoke(newRows, newCols)
                    }
                }
            }
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Background
        drawRect(BG_COLOR, Offset.Zero, Size(canvasWidth, canvasHeight))

        val grid = buffer.grid
        val rows = buffer.rows
        val cols = buffer.cols

        for (row in 0 until rows) {
            val y = row * charHeight
            if (y > canvasHeight) break

            for (col in 0 until cols) {
                val x = col * charWidth
                if (x > canvasWidth) break

                val cell = grid[row][col]

                // Cell background
                if (cell.bg != Color.Transparent) {
                    drawRect(
                        color = cell.bg,
                        topLeft = Offset(x, y),
                        size = Size(charWidth, charHeight)
                    )
                }

                // Cursor
                if (row == buffer.cursorRow && col == buffer.cursorCol && cursorVisible) {
                    drawRect(
                        color = CURSOR_COLOR.copy(alpha = 0.7f),
                        topLeft = Offset(x, y),
                        size = Size(charWidth, charHeight)
                    )
                }

                // Character
                if (cell.char != ' ' && cell.char.code >= 32) {
                    textPaint.color = cell.fg.toArgb()
                    textPaint.isFakeBoldText = cell.bold
                    textPaint.textSkewX = if (cell.italic) -0.25f else 0f

                    drawContext.canvas.nativeCanvas.drawText(
                        cell.char.toString(),
                        x,
                        y + baseline,
                        textPaint
                    )

                    if (cell.underline) {
                        drawLine(
                            color = cell.fg,
                            start = Offset(x, y + charHeight - 2f),
                            end = Offset(x + charWidth, y + charHeight - 2f),
                            strokeWidth = 1f
                        )
                    }
                }
            }
        }
    }
}

private fun Color.toArgb(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )
}
