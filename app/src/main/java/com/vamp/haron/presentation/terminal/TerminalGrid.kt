package com.vamp.haron.presentation.terminal

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
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
 *
 * @param fontSizeSp font size in sp — controlled externally (pinch zoom from parent)
 * @param onFontSizeChanged callback when pinch changes font size
 * @param onSizeCalculated callback with (rows, cols) after debounce when grid dimensions change
 */
@Composable
fun TerminalGrid(
    buffer: TerminalBuffer,
    fontSizeSp: Float,
    onFontSizeChanged: (Float) -> Unit,
    onSizeCalculated: ((rows: Int, cols: Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val gridContext = LocalContext.current
    val density = LocalDensity.current

    val fontSizePx = with(density) { fontSizeSp.sp.toPx() }

    val termTypeface = remember {
        try {
            val tf = android.graphics.Typeface.createFromAsset(gridContext.assets, "fonts/JetBrainsMono.ttf")
            android.util.Log.d("TermGrid", "Font loaded: DejaVuSansMono, bold=${tf.isBold}, italic=${tf.isItalic}")
            tf
        } catch (e: Exception) {
            android.util.Log.e("TermGrid", "Font load FAILED: ${e.message}, using MONOSPACE")
            android.graphics.Typeface.MONOSPACE
        }
    }
    val textPaint = remember(fontSizeSp) { android.graphics.Paint().apply {
        isAntiAlias = true
        typeface = termTypeface
        textSize = fontSizePx
        color = android.graphics.Color.parseColor("#D4D4D4")
    } }

    val charWidth = textPaint.measureText("M")
    val charHeight = run {
        val fm = textPaint.fontMetrics
        fm.descent - fm.ascent
    }
    val baseline = -textPaint.fontMetrics.ascent

    // Track canvas pixel size
    var lastCanvasWidth by remember { mutableFloatStateOf(0f) }
    var lastCanvasHeight by remember { mutableFloatStateOf(0f) }

    // Calculate grid dimensions
    val padXPx = 4f * density.density * 2 // both sides
    val currentCols = if (lastCanvasWidth > 0 && charWidth > 0) {
        ((lastCanvasWidth - padXPx) / charWidth).toInt().coerceIn(20, 300)
    } else 80
    val currentRows = if (lastCanvasHeight > 0 && charHeight > 0) {
        (lastCanvasHeight / charHeight).toInt().coerceIn(5, 200)
    } else 40

    // Resize buffer synchronously when dimensions change
    if (currentCols != buffer.cols || currentRows != buffer.rows) {
        buffer.resize(currentRows, currentCols)
    }

    // Debounced resize notification — trailing edge, always sends last value
    LaunchedEffect(currentRows, currentCols) {
        delay(250) // debounce: wait for pinch to settle
        onSizeCalculated?.invoke(currentRows, currentCols)
    }

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

    // Selection state (row, col)
    var selStart by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var selEnd by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var isSelecting by remember { mutableStateOf(false) }

    fun posToCell(offset: Offset): Pair<Int, Int> {
        val col = (offset.x / charWidth).toInt().coerceIn(0, buffer.cols - 1)
        val row = (offset.y / charHeight).toInt().coerceIn(0, buffer.rows - 1)
        return row to col
    }

    fun getSelectedText(): String {
        val start = selStart ?: return ""
        val end = selEnd ?: return ""
        val (r1, c1) = if (start.first < end.first || (start.first == end.first && start.second <= end.second)) start else end
        val (r2, c2) = if (start.first < end.first || (start.first == end.first && start.second <= end.second)) end else start
        val sb = StringBuilder()
        for (r in r1..r2) {
            val fromCol = if (r == r1) c1 else 0
            val toCol = if (r == r2) c2 else buffer.cols - 1
            for (c in fromCol..toCol) {
                sb.append(buffer.grid[r][c].char)
            }
            if (r < r2) sb.append('\n')
        }
        return sb.toString().trimEnd()
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures(panZoomLock = true) { _, _, zoom, _ ->
                    if (zoom != 1f) {
                        val newSize = (fontSizeSp * zoom).coerceIn(4f, 24f)
                        onFontSizeChanged(newSize)
                    }
                }
            }
            .onSizeChanged { size ->
                lastCanvasWidth = size.width.toFloat()
                lastCanvasHeight = size.height.toFloat()
            }
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val padX = 4f * density.density // 4dp horizontal padding

        // Background
        drawRect(BG_COLOR, Offset.Zero, Size(canvasWidth, canvasHeight))

        val grid = buffer.grid
        val rows = buffer.rows
        val cols = buffer.cols

        for (row in 0 until rows) {
            val y = row * charHeight
            if (y > canvasHeight) break

            for (col in 0 until cols) {
                val x = padX + col * charWidth
                if (x + charWidth > canvasWidth) break

                val cell = grid[row][col]

                // Skip wide char trail cells (drawn as part of lead cell)
                if (cell.isWideTrail) continue

                // Cell width (1 or 2 for wide chars)
                val cellWidth = if (cell.displayWidth == 2) charWidth * 2 else charWidth

                // Cell background
                if (cell.bg != Color.Transparent) {
                    drawRect(
                        color = cell.bg,
                        topLeft = Offset(x, y),
                        size = Size(cellWidth, charHeight)
                    )
                }

                // Selection highlight
                if (selStart != null && selEnd != null) {
                    val s = selStart!!; val e = selEnd!!
                    val (r1, c1) = if (s.first < e.first || (s.first == e.first && s.second <= e.second)) s else e
                    val (r2, c2) = if (s.first < e.first || (s.first == e.first && s.second <= e.second)) e else s
                    val inSelection = when {
                        row in (r1 + 1) until r2 -> true
                        row == r1 && row == r2 -> col in c1..c2
                        row == r1 -> col >= c1
                        row == r2 -> col <= c2
                        else -> false
                    }
                    if (inSelection) {
                        drawRect(
                            color = Color(0xFF264F78),
                            topLeft = Offset(x, y),
                            size = Size(cellWidth, charHeight)
                        )
                    }
                }

                // Cursor — bar style (thin vertical line like Termux)
                if (row == buffer.cursorRow && col == buffer.cursorCol && cursorVisible && buffer.cursorVisible) {
                    drawRect(
                        color = CURSOR_COLOR,
                        topLeft = Offset(x, y),
                        size = Size(2f * density.density, charHeight)
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
                            end = Offset(x + cellWidth, y + charHeight - 2f),
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
