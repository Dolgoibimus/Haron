package com.vamp.haron.common.util

// ======================== Data model ========================

enum class ParaAlignment { LEFT, CENTER, RIGHT, JUSTIFY }
enum class VerticalAlign { NORMAL, SUPERSCRIPT, SUBSCRIPT }

data class DocParagraph(
    val spans: List<DocSpan>,
    val headingLevel: Int = 0,
    val alignment: ParaAlignment = ParaAlignment.LEFT,
    val indentLeft: Int = 0,
    val indentFirstLine: Int = 0,
    val spacingBeforeDp: Int = 0,
    val spacingAfterDp: Int = 0,
    val lineSpacingMultiplier: Float = 0f,
    val listBullet: String? = null,
    val listLevel: Int = 0,
    val backgroundColor: Long = 0,
    val hasBorderBottom: Boolean = false,
    val isTable: Boolean = false,
    val tableCells: List<List<DocSpan>>? = null,
    val tableColWidths: List<Float>? = null,
    val tableCellAligns: List<ParaAlignment>? = null,
    val tableCellBgs: List<Long>? = null,
    val tableCellVMerge: List<Boolean>? = null,
    val tableCellGridSpans: List<Int>? = null,
    val tableCellVAligns: List<String>? = null,
    val tableCellBorders: List<Int>? = null,
    val tableHasBorders: Boolean = true,
    val imageData: ByteArray? = null
)

data class DocSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val fontSize: Float = 0f,
    val textColor: Long = 0,
    val highlightColor: Long = 0,
    val fontFamily: String? = null,
    val verticalAlign: VerticalAlign = VerticalAlign.NORMAL,
    val hyperlink: String? = null
)
