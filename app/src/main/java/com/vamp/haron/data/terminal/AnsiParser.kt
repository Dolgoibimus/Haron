package com.vamp.haron.data.terminal

import androidx.compose.ui.graphics.Color

data class StyledSpan(
    val text: String,
    val fg: Color? = null,
    val bg: Color? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false
)

data class ParsedLine(
    val spans: List<StyledSpan>
) {
    val plainText: String get() = spans.joinToString("") { it.text }
}

class AnsiParser {

    private var currentFg: Color? = null
    private var currentBg: Color? = null
    private var bold = false
    private var italic = false
    private var underline = false

    fun parseLine(raw: String): ParsedLine {
        val spans = mutableListOf<StyledSpan>()
        val text = StringBuilder()
        var i = 0

        while (i < raw.length) {
            if (raw[i] == '\u001B' && i + 1 < raw.length && raw[i + 1] == '[') {
                // Flush current text
                if (text.isNotEmpty()) {
                    spans.add(StyledSpan(text.toString(), currentFg, currentBg, bold, italic, underline))
                    text.clear()
                }
                // Parse CSI sequence
                i += 2
                val params = StringBuilder()
                while (i < raw.length && raw[i] in '0'..'9' || (i < raw.length && raw[i] == ';')) {
                    params.append(raw[i])
                    i++
                }
                val finalChar = if (i < raw.length) raw[i] else ' '
                i++
                if (finalChar == 'm') {
                    processSgr(params.toString())
                }
                // Other CSI sequences (cursor movement, clear, etc.) are silently consumed
            } else {
                text.append(raw[i])
                i++
            }
        }

        if (text.isNotEmpty()) {
            spans.add(StyledSpan(text.toString(), currentFg, currentBg, bold, italic, underline))
        }

        if (spans.isEmpty()) {
            spans.add(StyledSpan(""))
        }

        return ParsedLine(spans)
    }

    fun reset() {
        currentFg = null
        currentBg = null
        bold = false
        italic = false
        underline = false
    }

    private fun processSgr(params: String) {
        if (params.isEmpty()) {
            reset()
            return
        }
        val codes = params.split(';').mapNotNull { it.toIntOrNull() }
        var idx = 0
        while (idx < codes.size) {
            when (val code = codes[idx]) {
                0 -> reset()
                1 -> bold = true
                3 -> italic = true
                4 -> underline = true
                22 -> bold = false
                23 -> italic = false
                24 -> underline = false
                in 30..37 -> currentFg = TerminalColorPalette.ansi16(code - 30, bold)
                39 -> currentFg = null
                in 40..47 -> currentBg = TerminalColorPalette.ansi16(code - 40, false)
                49 -> currentBg = null
                in 90..97 -> currentFg = TerminalColorPalette.ansi16(code - 90 + 8, false)
                in 100..107 -> currentBg = TerminalColorPalette.ansi16(code - 100 + 8, false)
                38 -> {
                    // Extended foreground: 38;5;N or 38;2;R;G;B
                    if (idx + 1 < codes.size) {
                        when (codes[idx + 1]) {
                            5 -> {
                                if (idx + 2 < codes.size) {
                                    currentFg = TerminalColorPalette.ansi256(codes[idx + 2])
                                    idx += 2
                                }
                            }
                            2 -> {
                                if (idx + 4 < codes.size) {
                                    currentFg = Color(codes[idx + 2], codes[idx + 3], codes[idx + 4])
                                    idx += 4
                                }
                            }
                        }
                        idx++
                    }
                }
                48 -> {
                    // Extended background: 48;5;N or 48;2;R;G;B
                    if (idx + 1 < codes.size) {
                        when (codes[idx + 1]) {
                            5 -> {
                                if (idx + 2 < codes.size) {
                                    currentBg = TerminalColorPalette.ansi256(codes[idx + 2])
                                    idx += 2
                                }
                            }
                            2 -> {
                                if (idx + 4 < codes.size) {
                                    currentBg = Color(codes[idx + 2], codes[idx + 3], codes[idx + 4])
                                    idx += 4
                                }
                            }
                        }
                        idx++
                    }
                }
            }
            idx++
        }
    }
}
