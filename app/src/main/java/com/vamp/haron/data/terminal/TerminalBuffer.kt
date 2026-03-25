package com.vamp.haron.data.terminal

import androidx.compose.ui.graphics.Color
import com.vamp.core.logger.EcosystemLogger

private const val TLOG = "Haron/TermBuf"

private val DEFAULT_FG = Color(0xFFD4D4D4)
private val DEFAULT_BG = Color.Transparent

/**
 * Terminal character cell — one position in the grid.
 */
data class TermCell(
    var char: Char = ' ',
    var displayWidth: Int = 1,
    var isWideTrail: Boolean = false,
    var fg: Color = DEFAULT_FG,
    var bg: Color = DEFAULT_BG,
    var bold: Boolean = false,
    var italic: Boolean = false,
    var underline: Boolean = false
)

/**
 * Terminal screen buffer — full VT100/xterm emulation.
 * Patterns from Termux TerminalEmulator.java:
 * - aboutToAutoWrap flag (cursor stays in last col, wraps on next char)
 * - Erase with current style (not default)
 * - Separate saved states for main/alt screen
 * - Configurable tab stops
 * - lineFeed below scroll region = move down without scroll
 * - Wide character support (wcWidth)
 * - Alternate screen buffer, scroll regions, DEC private modes
 */
class TerminalBuffer(
    var rows: Int = 40,
    var cols: Int = 120
) {
    var grid: Array<Array<TermCell>> = createGrid()
        private set

    var cursorRow: Int = 0; private set
    var cursorCol: Int = 0; private set

    // Current style
    private var curFg: Color = DEFAULT_FG
    private var curBg: Color = DEFAULT_BG
    private var curBold = false
    private var curItalic = false
    private var curUnderline = false

    // Scrollback (main screen only)
    private val scrollback = mutableListOf<Array<TermCell>>()
    val scrollbackLines: List<Array<TermCell>> get() = scrollback
    var maxScrollback = 1000

    // Scroll region (0-based)
    private var scrollTop = 0
    private var scrollBottom = rows - 1

    // aboutToAutoWrap — Termux pattern: cursor stays in last col, wraps on next char
    private var aboutToAutoWrap = false

    // Alternate screen buffer
    private var altGrid: Array<Array<TermCell>>? = null
    private var isAltScreen = false

    // Saved cursor states — separate for main and alt (Termux pattern)
    private data class SavedState(
        var cursorRow: Int = 0, var cursorCol: Int = 0,
        var fg: Color = DEFAULT_FG, var bg: Color = DEFAULT_BG,
        var bold: Boolean = false, var italic: Boolean = false,
        var underline: Boolean = false,
        var originMode: Boolean = false, var autoWrapMode: Boolean = true
    )
    private var savedMain = SavedState()
    private var savedAlt = SavedState()

    // Modes
    private var originMode = false
    private var autoWrapMode = true
    var cursorVisible = true; private set

    // Tab stops — configurable (Termux pattern)
    private var tabStops = BooleanArray(cols) { it > 0 && it % 8 == 0 }

    // ANSI parsing
    private var parseState = ParseState.NORMAL
    private val csiParams = StringBuilder()
    var version: Long = 0L; private set

    private enum class ParseState { NORMAL, ESC, CSI, OSC, ESC_HASH, CHARSET }

    private fun createGrid() = Array(rows) { Array(cols) { TermCell() } }

    fun processOutput(data: String) {
        for (ch in data) processChar(ch)
        version++
    }

    private fun processChar(ch: Char) {
        when (parseState) {
            ParseState.NORMAL -> when (ch) {
                '\u001B' -> parseState = ParseState.ESC
                '\n' -> lineFeed()
                '\r' -> { cursorCol = 0; aboutToAutoWrap = false }
                '\b' -> { if (cursorCol > 0) cursorCol--; aboutToAutoWrap = false }
                '\t' -> { cursorCol = nextTabStop(1); aboutToAutoWrap = false }
                '\u0007' -> {} // Bell
                '\u000E', '\u000F' -> {} // Shift In/Out
                else -> { if (ch.code >= 32) putChar(ch) }
            }
            ParseState.ESC -> {
                when (ch) {
                    '[' -> { parseState = ParseState.CSI; csiParams.clear() }
                    ']' -> { parseState = ParseState.OSC; csiParams.clear() }
                    '(', ')', '*', '+' -> parseState = ParseState.CHARSET
                    '=', '>' -> parseState = ParseState.NORMAL
                    '#' -> parseState = ParseState.ESC_HASH
                    'M' -> { reverseIndex(); parseState = ParseState.NORMAL }
                    'D' -> { lineFeed(); parseState = ParseState.NORMAL }
                    'E' -> { cursorCol = 0; lineFeed(); parseState = ParseState.NORMAL }
                    'c' -> { resetAll(); parseState = ParseState.NORMAL }
                    '7' -> { saveCursor(); parseState = ParseState.NORMAL }
                    '8' -> { restoreCursor(); parseState = ParseState.NORMAL }
                    'H' -> { tabStops[cursorCol.coerceIn(0, cols - 1)] = true; parseState = ParseState.NORMAL }
                    else -> parseState = ParseState.NORMAL
                }
            }
            ParseState.ESC_HASH -> parseState = ParseState.NORMAL
            ParseState.CHARSET -> parseState = ParseState.NORMAL // consume one char
            ParseState.CSI -> {
                if (ch in '0'..'9' || ch == ';' || ch == '?' || ch == '>' || ch == '!' || ch == ' ') {
                    csiParams.append(ch)
                } else {
                    processCsi(ch)
                    parseState = ParseState.NORMAL
                }
            }
            ParseState.OSC -> {
                if (ch == '\u0007') parseState = ParseState.NORMAL
                else if (ch == '\\' && csiParams.isNotEmpty() && csiParams.last() == '\u001B') parseState = ParseState.NORMAL
                else csiParams.append(ch)
            }
        }
    }

    private fun putChar(ch: Char) {
        val displayWidth = wcWidth(ch.code)
        if (displayWidth <= 0) return // combining/zero-width — skip for now
        // Log non-space chars to trace where text lands
        if (ch != ' ' && ch.code >= 33) {
            EcosystemLogger.d(TLOG, "PUT '$ch'(U+${ch.code.toString(16).uppercase()}) @$cursorRow,$cursorCol w=$displayWidth")
        }

        val cursorInLastCol = cursorCol >= cols - 1

        if (autoWrapMode) {
            if (cursorInLastCol && ((aboutToAutoWrap && displayWidth == 1) || displayWidth == 2)) {
                cursorCol = 0
                lineFeed()
            }
        }

        // Write character
        if (cursorCol + displayWidth <= cols) {
            val cell = grid[cursorRow][cursorCol]
            cell.char = ch
            cell.displayWidth = displayWidth
            cell.isWideTrail = false
            cell.fg = curFg
            cell.bg = curBg
            cell.bold = curBold
            cell.italic = curItalic
            cell.underline = curUnderline

            // Wide char trail cell
            if (displayWidth == 2 && cursorCol + 1 < cols) {
                val trail = grid[cursorRow][cursorCol + 1]
                trail.char = ' '
                trail.displayWidth = 1
                trail.isWideTrail = true
                trail.fg = curFg
                trail.bg = curBg
                trail.bold = false
                trail.italic = false
                trail.underline = false
            }
        }

        aboutToAutoWrap = autoWrapMode && (cursorCol + displayWidth >= cols)
        cursorCol = minOf(cursorCol + displayWidth, cols - 1)
    }

    private fun lineFeed() {
        aboutToAutoWrap = false
        if (cursorRow == scrollBottom) {
            scrollUpRegion()
        } else if (cursorRow < rows - 1) {
            cursorRow++
        }
        // If below scroll region, just move down (Termux pattern)
    }

    private fun reverseIndex() {
        aboutToAutoWrap = false
        if (cursorRow == scrollTop) {
            scrollDownRegion()
        } else if (cursorRow > 0) {
            cursorRow--
        }
    }

    private fun scrollUpRegion() {
        if (!isAltScreen && scrollTop == 0) {
            if (scrollback.size >= maxScrollback) scrollback.removeAt(0)
            scrollback.add(grid[scrollTop].map { it.copy() }.toTypedArray())
        }
        for (r in scrollTop until scrollBottom) {
            grid[r] = grid[r + 1]
        }
        grid[scrollBottom] = Array(cols) { clearNewCell() }
    }

    private fun scrollDownRegion() {
        for (r in scrollBottom downTo scrollTop + 1) {
            grid[r] = grid[r - 1]
        }
        grid[scrollTop] = Array(cols) { clearNewCell() }
    }

    /** New cell for scroll — uses current bg color (Termux pattern) */
    private fun clearNewCell(): TermCell = TermCell(bg = curBg)

    /** Clear existing cell — uses current style (Termux pattern) */
    private fun clearCell(row: Int, col: Int) {
        if (row in 0 until rows && col in 0 until cols) {
            val cell = grid[row][col]
            cell.char = ' '
            cell.displayWidth = 1
            cell.isWideTrail = false
            cell.fg = curFg
            cell.bg = curBg
            cell.bold = false
            cell.italic = false
            cell.underline = false
        }
    }

    // --- Tab stops ---

    private fun nextTabStop(numTabs: Int): Int {
        var remaining = numTabs
        for (i in cursorCol + 1 until cols) {
            if (tabStops[i] && --remaining == 0) return minOf(i, cols - 1)
        }
        return cols - 1
    }

    // --- Cursor save/restore with separate main/alt states ---

    private fun saveCursor() {
        val state = if (isAltScreen) savedAlt else savedMain
        state.cursorRow = cursorRow
        state.cursorCol = cursorCol
        state.fg = curFg
        state.bg = curBg
        state.bold = curBold
        state.italic = curItalic
        state.underline = curUnderline
        state.originMode = originMode
        state.autoWrapMode = autoWrapMode
    }

    private fun restoreCursor() {
        val state = if (isAltScreen) savedAlt else savedMain
        cursorRow = state.cursorRow.coerceIn(0, rows - 1)
        cursorCol = state.cursorCol.coerceIn(0, cols - 1)
        curFg = state.fg
        curBg = state.bg
        curBold = state.bold
        curItalic = state.italic
        curUnderline = state.underline
        originMode = state.originMode
        autoWrapMode = state.autoWrapMode
        aboutToAutoWrap = false
    }

    // --- Alternate screen buffer ---

    private fun switchToAltScreen() {
        if (isAltScreen) return
        altGrid = grid
        grid = createGrid()
        cursorRow = 0
        cursorCol = 0
        scrollTop = 0
        scrollBottom = rows - 1
        aboutToAutoWrap = false
        isAltScreen = true
    }

    private fun switchToMainScreen() {
        if (!isAltScreen) return
        val saved = altGrid
        if (saved != null) grid = saved
        altGrid = null
        scrollTop = 0
        scrollBottom = rows - 1
        aboutToAutoWrap = false
        isAltScreen = false
    }

    // --- CSI processing ---

    private fun processCsi(finalChar: Char) {
        val raw = csiParams.toString()
        val isPrivate = raw.startsWith("?")
        val paramStr = raw.removePrefix("?").removePrefix(">").removePrefix("!")
        val params = paramStr.split(';').mapNotNull { it.toIntOrNull() }
        val p1 = params.getOrElse(0) { 0 }
        val p2 = params.getOrElse(1) { 0 }

        aboutToAutoWrap = false

        when (finalChar) {
            'A' -> { val old = cursorRow; cursorRow = maxOf(0, cursorRow - maxOf(1, p1)); EcosystemLogger.d(TLOG, "CUU($p1) row:$old→$cursorRow col:$cursorCol") }
            'B' -> { val old = cursorRow; cursorRow = minOf(rows - 1, cursorRow + maxOf(1, p1)); EcosystemLogger.d(TLOG, "CUD($p1) row:$old→$cursorRow col:$cursorCol") }
            'C' -> { val old = cursorCol; cursorCol = minOf(cols - 1, cursorCol + maxOf(1, p1)); EcosystemLogger.d(TLOG, "CUF($p1) col:$old→$cursorCol row:$cursorRow") }
            'D' -> cursorCol = maxOf(0, cursorCol - maxOf(1, p1))
            'E' -> { cursorCol = 0; cursorRow = minOf(rows - 1, cursorRow + maxOf(1, p1)) }
            'F' -> { cursorCol = 0; cursorRow = maxOf(0, cursorRow - maxOf(1, p1)) }
            'G' -> { val old = cursorCol; cursorCol = (if (p1 > 0) p1 - 1 else 0).coerceIn(0, cols - 1); EcosystemLogger.d(TLOG, "CHA($p1) col:$old→$cursorCol row:$cursorRow") }
            'H', 'f' -> { val oldR = cursorRow; val oldC = cursorCol; setCursorPosition(p1, p2); EcosystemLogger.d(TLOG, "CUP($p1,$p2) $oldR,$oldC→$cursorRow,$cursorCol") }
            'J' -> {
                when (p1) {
                    0 -> eraseFromCursorToEnd()
                    1 -> eraseFromStartToCursor()
                    2 -> eraseScreen()
                    3 -> { eraseScreen(); scrollback.clear() }
                }
            }
            'K' -> {
                when (p1) {
                    0 -> eraseFromCursorToLineEnd()
                    1 -> eraseFromLineStartToCursor()
                    2 -> eraseLine(cursorRow)
                }
            }
            'L' -> insertLines(maxOf(1, p1))
            'M' -> deleteLines(maxOf(1, p1))
            'P' -> deleteChars(maxOf(1, p1))
            '@' -> insertChars(maxOf(1, p1))
            'X' -> eraseChars(maxOf(1, p1))
            'd' -> { cursorRow = (if (p1 > 0) p1 - 1 else 0).coerceIn(0, rows - 1) }
            'm' -> processSgr(params)
            'r' -> { // DECSTBM — set scroll region
                scrollTop = (if (p1 > 0) p1 - 1 else 0).coerceIn(0, rows - 1)
                scrollBottom = (if (p2 > 0) p2 - 1 else rows - 1).coerceIn(scrollTop, rows - 1)
                setCursorPosition(0, 0)
            }
            's' -> saveCursor()
            'u' -> restoreCursor()
            'h' -> { if (isPrivate) for (p in params) setDecMode(p, true) }
            'l' -> { if (isPrivate) for (p in params) setDecMode(p, false) }
            'g' -> { // Tab stop clear
                when (p1) {
                    0 -> tabStops[cursorCol.coerceIn(0, cols - 1)] = false
                    3 -> tabStops.fill(false)
                }
            }
            'n' -> {} // DSR
            'S' -> repeat(maxOf(1, p1)) { scrollUpRegion() }
            'T' -> repeat(maxOf(1, p1)) { scrollDownRegion() }
            'c' -> {} // DA
            'q' -> {} // Cursor style
            't' -> {} // Window manipulation
            'b' -> {} // REP
        }
    }

    private fun setCursorPosition(p1: Int, p2: Int) {
        val targetRow = if (p1 > 0) p1 - 1 else 0
        val targetCol = if (p2 > 0) p2 - 1 else 0
        cursorRow = if (originMode) {
            (scrollTop + targetRow).coerceIn(scrollTop, scrollBottom)
        } else {
            targetRow.coerceIn(0, rows - 1)
        }
        cursorCol = targetCol.coerceIn(0, cols - 1)
    }

    private fun insertLines(n: Int) {
        val bottom = scrollBottom
        for (i in 0 until n) {
            if (cursorRow <= bottom) {
                for (r in bottom downTo cursorRow + 1) grid[r] = grid[r - 1]
                grid[cursorRow] = Array(cols) { clearNewCell() }
            }
        }
    }

    private fun deleteLines(n: Int) {
        val bottom = scrollBottom
        for (i in 0 until n) {
            if (cursorRow <= bottom) {
                for (r in cursorRow until bottom) grid[r] = grid[r + 1]
                grid[bottom] = Array(cols) { clearNewCell() }
            }
        }
    }

    private fun deleteChars(n: Int) {
        for (c in cursorCol until cols) {
            val src = c + n
            grid[cursorRow][c] = if (src < cols) grid[cursorRow][src].copy() else clearNewCell()
        }
    }

    private fun insertChars(n: Int) {
        for (c in cols - 1 downTo cursorCol + n) {
            grid[cursorRow][c] = grid[cursorRow][c - n].copy()
        }
        for (c in cursorCol until minOf(cursorCol + n, cols)) {
            clearCell(cursorRow, c)
        }
    }

    private fun eraseChars(n: Int) {
        for (c in cursorCol until minOf(cursorCol + n, cols)) {
            clearCell(cursorRow, c)
        }
    }

    private fun setDecMode(mode: Int, enable: Boolean) {
        when (mode) {
            1 -> {} // DECCKM
            3 -> { // DECCOLM — side effect: clear screen, reset margins
                if (enable || !enable) { scrollTop = 0; scrollBottom = rows - 1; eraseScreen(); setCursorPosition(0, 0) }
            }
            6 -> { originMode = enable; setCursorPosition(0, 0) }
            7 -> autoWrapMode = enable
            12 -> {} // Cursor blink
            25 -> cursorVisible = enable
            47 -> { if (enable) switchToAltScreen() else switchToMainScreen() }
            1000, 1002, 1003, 1006, 1015 -> {} // Mouse
            1004 -> {} // Focus
            1047 -> { if (enable) switchToAltScreen() else { switchToMainScreen() } }
            1048 -> { if (enable) saveCursor() else restoreCursor() }
            1049 -> {
                if (enable) { saveCursor(); switchToAltScreen(); eraseScreen() }
                else { switchToMainScreen(); restoreCursor() }
            }
            2004 -> {} // Bracketed paste
        }
    }

    private fun processSgr(params: List<Int>) {
        if (params.isEmpty()) { resetStyle(); return }
        var idx = 0
        while (idx < params.size) {
            when (val code = params[idx]) {
                0 -> resetStyle()
                1 -> curBold = true
                2 -> {} // Dim
                3 -> curItalic = true
                4 -> curUnderline = true
                5, 6 -> {} // Blink
                7 -> { val t = curFg; curFg = if (curBg == DEFAULT_BG) Color(0xFF1E1E1E) else curBg; curBg = if (t == DEFAULT_FG) Color(0xFFD4D4D4) else t }
                8 -> {} // Hidden
                9 -> {} // Strikethrough
                22 -> curBold = false
                23 -> curItalic = false
                24 -> curUnderline = false
                27 -> { val t = curFg; curFg = curBg; curBg = t } // Reverse off (swap back)
                in 30..37 -> curFg = TerminalColorPalette.ansi16(code - 30, curBold)
                39 -> curFg = DEFAULT_FG
                in 40..47 -> curBg = TerminalColorPalette.ansi16(code - 40, false)
                49 -> curBg = DEFAULT_BG
                in 90..97 -> curFg = TerminalColorPalette.ansi16(code - 90 + 8, false)
                in 100..107 -> curBg = TerminalColorPalette.ansi16(code - 100 + 8, false)
                38 -> { idx += parseSgrColor(params, idx, true); }
                48 -> { idx += parseSgrColor(params, idx, false); }
            }
            idx++
        }
    }

    private fun parseSgrColor(params: List<Int>, idx: Int, isFg: Boolean): Int {
        if (idx + 1 >= params.size) return 0
        return when (params[idx + 1]) {
            5 -> {
                if (idx + 2 < params.size) {
                    val c = TerminalColorPalette.ansi256(params[idx + 2])
                    if (isFg) curFg = c else curBg = c
                    2
                } else 1
            }
            2 -> {
                if (idx + 4 < params.size) {
                    val c = Color(params[idx + 2], params[idx + 3], params[idx + 4])
                    if (isFg) curFg = c else curBg = c
                    4
                } else 1
            }
            else -> 1
        }
    }

    private fun resetStyle() {
        curFg = DEFAULT_FG; curBg = DEFAULT_BG
        curBold = false; curItalic = false; curUnderline = false
    }

    // --- Erase operations (use current style — Termux pattern) ---

    private fun eraseScreen() {
        for (r in 0 until rows) for (c in 0 until cols) clearCell(r, c)
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
        if (row in 0 until rows) for (c in 0 until cols) clearCell(row, c)
    }

    private fun eraseFromCursorToLineEnd() {
        for (c in cursorCol until cols) clearCell(cursorRow, c)
    }

    private fun eraseFromLineStartToCursor() {
        for (c in 0..cursorCol.coerceAtMost(cols - 1)) clearCell(cursorRow, c)
    }

    private fun resetAll() {
        resetStyle(); eraseScreen(); scrollback.clear()
        scrollTop = 0; scrollBottom = rows - 1
        cursorRow = 0; cursorCol = 0
        originMode = false; autoWrapMode = true; cursorVisible = true
        aboutToAutoWrap = false; isAltScreen = false; altGrid = null
        tabStops = BooleanArray(cols) { it > 0 && it % 8 == 0 }
        savedMain = SavedState(); savedAlt = SavedState()
    }

    fun resize(newRows: Int, newCols: Int) {
        val oldGrid = grid
        rows = newRows; cols = newCols
        grid = createGrid()
        for (r in 0 until minOf(oldGrid.size, newRows)) {
            for (c in 0 until minOf(oldGrid[r].size, newCols)) grid[r][c] = oldGrid[r][c]
        }
        cursorRow = cursorRow.coerceIn(0, rows - 1)
        cursorCol = cursorCol.coerceIn(0, cols - 1)
        scrollTop = 0; scrollBottom = rows - 1
        aboutToAutoWrap = false
        // Resize tab stops
        val oldTabs = tabStops
        tabStops = BooleanArray(newCols) { if (it < oldTabs.size) oldTabs[it] else (it > 0 && it % 8 == 0) }
        // Resize alt grid
        altGrid?.let { old ->
            val newAlt = Array(newRows) { Array(newCols) { TermCell() } }
            for (r in 0 until minOf(old.size, newRows)) {
                for (c in 0 until minOf(old[r].size, newCols)) newAlt[r][c] = old[r][c]
            }
            altGrid = newAlt
        }
        version++
    }

    companion object {
        /** Simplified wcWidth — returns display width of a Unicode code point */
        fun wcWidth(codePoint: Int): Int {
            if (codePoint < 32 || codePoint in 0x7F until 0xA0) return 0
            if (codePoint in 0x0300..0x036F || codePoint in 0xFE20..0xFE2F) return 0 // Combining
            if (codePoint == 0x200B || codePoint in 0x200C..0x200F) return 0 // Zero-width
            if (codePoint in 0x2060..0x2064 || codePoint == 0xFEFF) return 0 // Zero-width
            // East Asian Wide
            if (codePoint in 0x1100..0x115F || codePoint == 0x2329 || codePoint == 0x232A ||
                codePoint in 0x2E80..0x303E || codePoint in 0x3041..0x33BF ||
                codePoint in 0x3400..0x4DBF || codePoint in 0x4E00..0x9FFF ||
                codePoint in 0xAC00..0xD7A3 || codePoint in 0xF900..0xFAFF ||
                codePoint in 0xFE10..0xFE19 || codePoint in 0xFE30..0xFE6B ||
                codePoint in 0xFF01..0xFF60 || codePoint in 0xFFE0..0xFFE6 ||
                codePoint in 0x20000..0x3FFFD) return 2
            // Emoji (common ranges)
            if (codePoint in 0x1F300..0x1F9FF || codePoint in 0x1FA00..0x1FA6F ||
                codePoint in 0x1FA70..0x1FAFF) return 2
            // Note: U+2600-U+27BF (Misc Symbols, Dingbats) are width 1 — NOT East Asian Wide
            return 1
        }
    }
}
