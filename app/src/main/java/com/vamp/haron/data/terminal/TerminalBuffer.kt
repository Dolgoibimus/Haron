package com.vamp.haron.data.terminal

import androidx.compose.ui.graphics.Color

/**
 * Terminal character cell — one position in the grid.
 */
data class TermCell(
    var char: Char = ' ',
    var fg: Color = Color(0xFFD4D4D4),
    var bg: Color = Color.Transparent,
    var bold: Boolean = false,
    var italic: Boolean = false,
    var underline: Boolean = false
)

/**
 * Terminal screen buffer — rows × cols grid with cursor and ANSI processing.
 * Handles CSI sequences for cursor movement, erase, scroll, and SGR (colors).
 */
class TerminalBuffer(
    var rows: Int = 40,
    var cols: Int = 120
) {
    // Screen grid
    var grid: Array<Array<TermCell>> = createGrid()
        private set

    // Cursor position (0-based)
    var cursorRow: Int = 0
        private set
    var cursorCol: Int = 0
        private set

    // Current style
    private var curFg: Color = Color(0xFFD4D4D4)
    private var curBg: Color = Color.Transparent
    private var curBold = false
    private var curItalic = false
    private var curUnderline = false

    // Scrollback buffer
    private val scrollback = mutableListOf<Array<TermCell>>()
    val scrollbackLines: List<Array<TermCell>> get() = scrollback
    var maxScrollback = 1000

    // ANSI parsing state
    private var parseState = ParseState.NORMAL
    private val csiParams = StringBuilder()

    // Version counter for Compose recomposition
    var version: Long = 0L
        private set

    private enum class ParseState { NORMAL, ESC, CSI, OSC }

    private fun createGrid(): Array<Array<TermCell>> {
        return Array(rows) { Array(cols) { TermCell() } }
    }

    /**
     * Process raw output from PTY.
     */
    fun processOutput(data: String) {
        for (ch in data) {
            processChar(ch)
        }
        version++
    }

    private fun processChar(ch: Char) {
        when (parseState) {
            ParseState.NORMAL -> when (ch) {
                '\u001B' -> parseState = ParseState.ESC
                '\n' -> lineFeed()
                '\r' -> cursorCol = 0
                '\b' -> if (cursorCol > 0) cursorCol--
                '\t' -> {
                    val next = ((cursorCol / 8) + 1) * 8
                    cursorCol = minOf(next, cols - 1)
                }
                '\u0007' -> {} // Bell — ignore
                else -> {
                    if (ch.code >= 32) {
                        putChar(ch)
                    }
                }
            }
            ParseState.ESC -> {
                when (ch) {
                    '[' -> {
                        parseState = ParseState.CSI
                        csiParams.clear()
                    }
                    ']' -> {
                        parseState = ParseState.OSC
                        csiParams.clear()
                    }
                    '(' , ')' -> parseState = ParseState.NORMAL // Charset select — ignore next char
                    '=' , '>' -> parseState = ParseState.NORMAL // Keypad mode — ignore
                    'M' -> { // Reverse index (scroll down)
                        if (cursorRow == 0) scrollDown() else cursorRow--
                        parseState = ParseState.NORMAL
                    }
                    'c' -> { // Reset
                        resetAll()
                        parseState = ParseState.NORMAL
                    }
                    else -> parseState = ParseState.NORMAL
                }
            }
            ParseState.CSI -> {
                if (ch in '0'..'9' || ch == ';' || ch == '?') {
                    csiParams.append(ch)
                } else {
                    processCsi(ch)
                    parseState = ParseState.NORMAL
                }
            }
            ParseState.OSC -> {
                // OSC sequences end with BEL (0x07) or ST (ESC \)
                if (ch == '\u0007') {
                    parseState = ParseState.NORMAL
                } else if (ch == '\\' && csiParams.isNotEmpty() && csiParams.last() == '\u001B') {
                    parseState = ParseState.NORMAL
                } else {
                    csiParams.append(ch)
                }
            }
        }
    }

    private fun putChar(ch: Char) {
        if (cursorCol >= cols) {
            cursorCol = 0
            lineFeed()
        }
        val cell = grid[cursorRow][cursorCol]
        cell.char = ch
        cell.fg = curFg
        cell.bg = curBg
        cell.bold = curBold
        cell.italic = curItalic
        cell.underline = curUnderline
        cursorCol++
    }

    private fun lineFeed() {
        if (cursorRow < rows - 1) {
            cursorRow++
        } else {
            scrollUp()
        }
    }

    private fun scrollUp() {
        // Save top line to scrollback
        if (scrollback.size >= maxScrollback) scrollback.removeAt(0)
        scrollback.add(grid[0].map { it.copy() }.toTypedArray())
        // Shift lines up
        for (r in 0 until rows - 1) {
            grid[r] = grid[r + 1]
        }
        grid[rows - 1] = Array(cols) { TermCell() }
    }

    private fun scrollDown() {
        // Shift lines down
        for (r in rows - 1 downTo 1) {
            grid[r] = grid[r - 1]
        }
        grid[0] = Array(cols) { TermCell() }
    }

    private fun processCsi(finalChar: Char) {
        val paramStr = csiParams.toString().removePrefix("?")
        val params = paramStr.split(';').mapNotNull { it.toIntOrNull() }
        val p1 = params.getOrElse(0) { 0 }
        val p2 = params.getOrElse(1) { 0 }

        when (finalChar) {
            'A' -> cursorRow = maxOf(0, cursorRow - maxOf(1, p1)) // Cursor up
            'B' -> cursorRow = minOf(rows - 1, cursorRow + maxOf(1, p1)) // Cursor down
            'C' -> cursorCol = minOf(cols - 1, cursorCol + maxOf(1, p1)) // Cursor right
            'D' -> cursorCol = maxOf(0, cursorCol - maxOf(1, p1)) // Cursor left
            'E' -> { cursorCol = 0; cursorRow = minOf(rows - 1, cursorRow + maxOf(1, p1)) } // Next line
            'F' -> { cursorCol = 0; cursorRow = maxOf(0, cursorRow - maxOf(1, p1)) } // Prev line
            'G' -> cursorCol = maxOf(0, minOf(cols - 1, (if (p1 > 0) p1 - 1 else 0))) // Cursor horizontal absolute
            'H', 'f' -> { // Cursor position
                cursorRow = maxOf(0, minOf(rows - 1, (if (p1 > 0) p1 - 1 else 0)))
                cursorCol = maxOf(0, minOf(cols - 1, (if (p2 > 0) p2 - 1 else 0)))
            }
            'J' -> { // Erase in display
                when (p1) {
                    0 -> eraseFromCursorToEnd()
                    1 -> eraseFromStartToCursor()
                    2, 3 -> eraseScreen()
                }
            }
            'K' -> { // Erase in line
                when (p1) {
                    0 -> eraseFromCursorToLineEnd()
                    1 -> eraseFromLineStartToCursor()
                    2 -> eraseLine(cursorRow)
                }
            }
            'L' -> { // Insert lines
                val n = maxOf(1, p1)
                for (i in 0 until n) {
                    if (cursorRow < rows - 1) {
                        for (r in rows - 1 downTo cursorRow + 1) {
                            grid[r] = grid[r - 1]
                        }
                        grid[cursorRow] = Array(cols) { TermCell() }
                    }
                }
            }
            'M' -> { // Delete lines
                val n = maxOf(1, p1)
                for (i in 0 until n) {
                    if (cursorRow < rows - 1) {
                        for (r in cursorRow until rows - 1) {
                            grid[r] = grid[r + 1]
                        }
                        grid[rows - 1] = Array(cols) { TermCell() }
                    }
                }
            }
            'P' -> { // Delete characters
                val n = maxOf(1, p1)
                for (c in cursorCol until cols) {
                    val src = c + n
                    grid[cursorRow][c] = if (src < cols) grid[cursorRow][src].copy() else TermCell()
                }
            }
            '@' -> { // Insert characters
                val n = maxOf(1, p1)
                for (c in cols - 1 downTo cursorCol + n) {
                    grid[cursorRow][c] = grid[cursorRow][c - n].copy()
                }
                for (c in cursorCol until minOf(cursorCol + n, cols)) {
                    grid[cursorRow][c] = TermCell()
                }
            }
            'd' -> cursorRow = maxOf(0, minOf(rows - 1, (if (p1 > 0) p1 - 1 else 0))) // Vertical position absolute
            'm' -> processSgr(params) // Colors/styles
            'r' -> {} // Set scrolling region — ignore for now
            'h', 'l' -> {} // Set/reset mode — ignore
            'n' -> {} // Device status report — ignore
            'S' -> { // Scroll up
                val n = maxOf(1, p1)
                repeat(n) { scrollUp() }
            }
            'T' -> { // Scroll down
                val n = maxOf(1, p1)
                repeat(n) { scrollDown() }
            }
        }
    }

    private fun processSgr(params: List<Int>) {
        if (params.isEmpty()) {
            resetStyle()
            return
        }
        var idx = 0
        while (idx < params.size) {
            when (val code = params[idx]) {
                0 -> resetStyle()
                1 -> curBold = true
                3 -> curItalic = true
                4 -> curUnderline = true
                22 -> curBold = false
                23 -> curItalic = false
                24 -> curUnderline = false
                in 30..37 -> curFg = TerminalColorPalette.ansi16(code - 30, curBold)
                39 -> curFg = Color(0xFFD4D4D4)
                in 40..47 -> curBg = TerminalColorPalette.ansi16(code - 40, false)
                49 -> curBg = Color.Transparent
                in 90..97 -> curFg = TerminalColorPalette.ansi16(code - 90 + 8, false)
                in 100..107 -> curBg = TerminalColorPalette.ansi16(code - 100 + 8, false)
                38 -> {
                    if (idx + 1 < params.size) {
                        when (params[idx + 1]) {
                            5 -> { if (idx + 2 < params.size) { curFg = TerminalColorPalette.ansi256(params[idx + 2]); idx += 2 } }
                            2 -> { if (idx + 4 < params.size) { curFg = Color(params[idx + 2], params[idx + 3], params[idx + 4]); idx += 4 } }
                        }
                        idx++
                    }
                }
                48 -> {
                    if (idx + 1 < params.size) {
                        when (params[idx + 1]) {
                            5 -> { if (idx + 2 < params.size) { curBg = TerminalColorPalette.ansi256(params[idx + 2]); idx += 2 } }
                            2 -> { if (idx + 4 < params.size) { curBg = Color(params[idx + 2], params[idx + 3], params[idx + 4]); idx += 4 } }
                        }
                        idx++
                    }
                }
            }
            idx++
        }
    }

    private fun resetStyle() {
        curFg = Color(0xFFD4D4D4)
        curBg = Color.Transparent
        curBold = false
        curItalic = false
        curUnderline = false
    }

    private fun eraseScreen() {
        grid = createGrid()
        cursorRow = 0
        cursorCol = 0
    }

    private fun eraseFromCursorToEnd() {
        eraseFromCursorToLineEnd()
        for (r in cursorRow + 1 until rows) eraseLine(r)
    }

    private fun eraseFromStartToCursor() {
        eraseFromLineStartToCursor()
        for (r in 0 until cursorRow) eraseLine(r)
    }

    private fun eraseLine(row: Int) {
        for (c in 0 until cols) grid[row][c] = TermCell()
    }

    private fun eraseFromCursorToLineEnd() {
        for (c in cursorCol until cols) grid[cursorRow][c] = TermCell()
    }

    private fun eraseFromLineStartToCursor() {
        for (c in 0..cursorCol) grid[cursorRow][c] = TermCell()
    }

    private fun resetAll() {
        resetStyle()
        eraseScreen()
        scrollback.clear()
    }

    /**
     * Resize the buffer. Preserves content where possible.
     */
    fun resize(newRows: Int, newCols: Int) {
        val oldGrid = grid
        rows = newRows
        cols = newCols
        grid = createGrid()
        for (r in 0 until minOf(oldGrid.size, newRows)) {
            for (c in 0 until minOf(oldGrid[r].size, newCols)) {
                grid[r][c] = oldGrid[r][c]
            }
        }
        cursorRow = minOf(cursorRow, rows - 1)
        cursorCol = minOf(cursorCol, cols - 1)
        version++
    }
}
