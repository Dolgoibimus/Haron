package com.vamp.haron.common.util

import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.HWPFOldDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import java.io.File
import java.util.zip.ZipFile

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
    val hasBorderBottom: Boolean = false,     // paragraph bottom border → horizontal line
    val isTable: Boolean = false,
    val tableCells: List<List<DocSpan>>? = null,
    val tableColWidths: List<Float>? = null,
    val tableCellAligns: List<ParaAlignment>? = null,
    val tableCellBgs: List<Long>? = null,
    val tableCellVMerge: List<Boolean>? = null,
    val tableCellGridSpans: List<Int>? = null, // horizontal merge: how many cols each cell spans
    val tableCellVAligns: List<String>? = null, // "top","center","bottom"
    val tableCellBorders: List<Int>? = null,   // per cell border bitmask: 1=left 2=right 4=top 8=bottom
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
    val fontFamily: String? = null,           // font name from document
    val verticalAlign: VerticalAlign = VerticalAlign.NORMAL,
    val hyperlink: String? = null
)

private data class OdtStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val color: Long = 0,
    val fontSize: Float = 0f
)

// DOCX style resolution helpers
private data class DocxRunDef(
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val fontSize: Float = 0f,
    val fontFamily: String? = null,
    val textColor: Long = 0
)
private data class DocxParaDef(
    val alignment: ParaAlignment? = null,
    val indentLeft: Int = 0,
    val indentFirstLine: Int = 0,
    val spacingBefore: Int = 0,
    val spacingAfter: Int = 0,
    val lineSpacing: Float = 0f,
    val runDef: DocxRunDef? = null
)
private data class DocxNumLevel(
    val numFmt: String = "bullet",
    val lvlText: String = "\u2022",
    val start: Int = 1
)
private data class DocxTabStop(val pos: Int, val leader: String?)

/**
 * Parses documents (DOCX, DOC, ODT, RTF, FB2) into a list of [DocParagraph]
 * preserving formatting and embedded images.
 */
object DocumentParser {

    fun parse(file: File): List<DocParagraph> {
        com.vamp.core.logger.EcosystemLogger.d("Haron", "DocumentParser.parse: ${file.absolutePath}, exists=${file.exists()}, canRead=${file.canRead()}, size=${file.length()}")
        val nameLc = file.name.lowercase()
        if (nameLc.endsWith(".fb2.zip") || (nameLc.endsWith(".zip") && nameLc.contains(".fb2"))) {
            return parseFb2FromZip(file)
        }
        return when (file.extension.lowercase()) {
            "docx" -> parseDocx(file)
            "doc" -> parseDoc(file)
            "odt" -> parseOdt(file)
            "rtf" -> parseRtf(file)
            "fb2" -> parseFb2(file)
            "xlsx" -> parseXlsx(file)
            "xls" -> parseXls(file)
            "csv", "tsv" -> parseCsv(file)
            else -> emptyList()
        }
    }

    fun parseFb2FromZip(zipFile: File): List<DocParagraph> {
        ZipFile(zipFile).use { zip ->
            val fb2Entry = zip.entries().toList().firstOrNull { entry ->
                entry.name.lowercase().endsWith(".fb2")
            } ?: return emptyList()
            val rawBytes = zip.getInputStream(fb2Entry).readBytes()
            return parseFb2Internal(rawBytes)
        }
    }

    // ======================== DOCX ========================

    private fun parseDocx(file: File): List<DocParagraph> {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("word/document.xml") ?: return emptyList()
            val xml = zip.getInputStream(entry).bufferedReader().readText()

            // Load relationships (hyperlinks + images)
            val relsEntry = zip.getEntry("word/_rels/document.xml.rels")
            val hyperlinks = mutableMapOf<String, String>() // rId -> URL
            val imageRels = mutableMapOf<String, String>()   // rId -> media path
            if (relsEntry != null) {
                val relsXml = zip.getInputStream(relsEntry).bufferedReader().readText()
                val relTag = Regex("""<Relationship\s[^>]*/>""")
                for (m in relTag.findAll(relsXml)) {
                    val tag = m.value
                    val id = Regex("""Id="([^"]+)"""").find(tag)?.groupValues?.get(1) ?: continue
                    val target = Regex("""Target="([^"]+)"""").find(tag)?.groupValues?.get(1) ?: continue
                    val type = Regex("""Type="([^"]+)"""").find(tag)?.groupValues?.get(1) ?: ""
                    if (tag.contains("TargetMode=\"External\"")) {
                        hyperlinks[id] = target
                    } else if (type.contains("/image")) {
                        imageRels[id] = "word/$target"
                    }
                }
            }

            // Load styles.xml for named style definitions
            val (paraStyles, docRunDef) = parseDocxStyles(zip)
            // Load numbering.xml for list formats
            val numberingDefs = parseDocxNumbering(zip)
            val numCounters = mutableMapOf<String, Int>()

            val result = mutableListOf<DocParagraph>()
            val rowPattern = Regex("""<w:tr\b.*?</w:tr>""", RegexOption.DOT_MATCHES_ALL)
            val cellPattern = Regex("""<w:tc\b.*?</w:tc>""", RegexOption.DOT_MATCHES_ALL)
            val topLevel = Regex("""<w:tbl\b.*?</w:tbl>|<w:p[\s>].*?</w:p>""", RegexOption.DOT_MATCHES_ALL)

            for (elem in topLevel.findAll(xml)) {
                val eXml = elem.value
                if (eXml.startsWith("<w:tbl")) {
                    // Extract grid column widths from w:tblGrid
                    val gridWidths = Regex("""<w:gridCol w:w="(\d+)"""")
                        .findAll(eXml).map { it.groupValues[1].toFloatOrNull() ?: 1f }.toList()

                    // Check if table has visible borders
                    val tblPr = extractTagContent(eXml, "w:tblPr")
                    val tblBordersXml = extractTagContent(tblPr, "w:tblBorders")
                    val tableHasBorders = tblBordersXml.isNotEmpty() &&
                        Regex("""w:val="([^"]+)"""").findAll(tblBordersXml)
                            .any { it.groupValues[1] != "none" }

                    for (rm in rowPattern.findAll(eXml)) {
                        val cells = mutableListOf<List<DocSpan>>()
                        val cellWidths = mutableListOf<Float>()
                        val cellAligns = mutableListOf<ParaAlignment>()
                        val cellBgs = mutableListOf<Long>()
                        val cellVMerge = mutableListOf<Boolean>()
                        val cellGridSpans = mutableListOf<Int>()
                        val cellVAligns = mutableListOf<String>()
                        for (cm in cellPattern.findAll(rm.value)) {
                            val cellSpans = mutableListOf<DocSpan>()
                            val cellParas = Regex("""<w:p[\s>].*?</w:p>""", RegexOption.DOT_MATCHES_ALL)
                            var cellAlign = ParaAlignment.LEFT
                            for (cp in cellParas.findAll(cm.value)) {
                                if (cellSpans.isNotEmpty()) cellSpans += DocSpan(" ")
                                cellSpans.addAll(docxRunSpans(cp.value, hyperlinks,
                    defFontFamily = docRunDef?.fontFamily,
                    defFontSize = docRunDef?.fontSize ?: 0f))
                                if (cellAlign == ParaAlignment.LEFT) {
                                    val cpPr = extractTagContent(cp.value, "w:pPr")
                                    val jc = Regex("""<w:jc w:val="([^"]+)"""").find(cpPr)?.groupValues?.get(1)
                                    cellAlign = docxAlignment(jc)
                                }
                            }
                            cells.add(cellSpans)
                            cellAligns.add(cellAlign)

                            val tcPr = extractTagContent(cm.value, "w:tcPr")
                            val tcW = Regex("""<w:tcW w:w="(\d+)"""").find(tcPr)
                                ?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
                            cellWidths.add(tcW)
                            val cellShd = Regex("""<w:shd\b([^/]*)/>""").find(tcPr)?.groupValues?.get(1) ?: ""
                            val cellFill = Regex("""w:fill="([0-9A-Fa-f]{6})"""").find(cellShd)?.groupValues?.get(1)
                            val bg = if (cellFill != null && cellFill.lowercase() !in listOf("auto", "ffffff")) {
                                (0xFF000000 or cellFill.toLong(16))
                            } else 0L
                            cellBgs.add(bg)
                            val isVMergeContinue = tcPr.contains("<w:vMerge") &&
                                !tcPr.contains("""w:val="restart"""")
                            cellVMerge.add(isVMergeContinue)
                            // Horizontal merge (gridSpan)
                            val gridSpan = Regex("""<w:gridSpan w:val="(\d+)"""").find(tcPr)
                                ?.groupValues?.get(1)?.toIntOrNull() ?: 1
                            cellGridSpans.add(gridSpan)
                            // Vertical alignment
                            val vAlign = Regex("""<w:vAlign w:val="([^"]+)"""").find(tcPr)
                                ?.groupValues?.get(1) ?: "top"
                            cellVAligns.add(vAlign)
                        }
                        if (cells.isNotEmpty()) {
                            val hasGridSpan = cellGridSpans.any { it > 1 }
                            val widths = if (gridWidths.isNotEmpty()) gridWidths
                                else if (!hasGridSpan && cellWidths.any { it > 0f }) cellWidths
                                else null
                            result.add(DocParagraph(
                                spans = emptyList(), isTable = true,
                                tableCells = cells,
                                tableColWidths = widths,
                                tableCellAligns = cellAligns,
                                tableCellBgs = cellBgs.takeIf { it.any { c -> c != 0L } },
                                tableCellVMerge = cellVMerge.takeIf { it.any { v -> v } },
                                tableCellGridSpans = cellGridSpans.takeIf { it.any { g -> g > 1 } },
                                tableCellVAligns = cellVAligns.takeIf { it.any { v -> v != "top" } },
                                tableHasBorders = tableHasBorders
                            ))
                        }
                    }
                } else {
                    // Check for embedded image
                    val imgRId = Regex("""r:embed="([^"]+)"""").find(eXml)?.groupValues?.get(1)
                    val imgPath = imgRId?.let { imageRels[it] }
                    if (imgPath != null) {
                        val imgBytes = try {
                            zip.getEntry(imgPath)?.let { zip.getInputStream(it).readBytes() }
                        } catch (_: Exception) { null }
                        if (imgBytes != null) {
                            result.add(DocParagraph(spans = emptyList(), imageData = imgBytes))
                        }
                    }
                    val para = docxParagraph(eXml, hyperlinks, paraStyles, docRunDef, numberingDefs, numCounters)
                    if (para != null) result.add(para)
                }
            }

            return result
        }
    }

    private fun docxParagraph(
        pXml: String,
        hyperlinks: Map<String, String>,
        paraStyles: Map<String, DocxParaDef> = emptyMap(),
        docRunDef: DocxRunDef? = null,
        numbering: Map<Int, Map<Int, DocxNumLevel>> = emptyMap(),
        numCounters: MutableMap<String, Int> = mutableMapOf()
    ): DocParagraph? {
        val pPr = extractTagContent(pXml, "w:pPr")
        val headingLevel = docxHeadingLevel(pPr)

        // Resolve named paragraph style
        val pStyleId = Regex("""<w:pStyle w:val="([^"]+)"""").find(pPr)?.groupValues?.get(1)
        val pStyle = pStyleId?.let { paraStyles[it] }

        // Alignment (inline overrides style)
        val jcVal = Regex("""<w:jc w:val="([^"]+)"""").find(pPr)?.groupValues?.get(1)
        val alignment = if (jcVal != null) docxAlignment(jcVal)
            else pStyle?.alignment ?: ParaAlignment.LEFT

        // Indentation
        val indMatch = Regex("""<w:ind\b([^/]*)/>""").find(pPr)?.groupValues?.get(1) ?: ""
        val leftTwips = Regex("""w:left="(\d+)"""").find(indMatch)?.groupValues?.get(1)?.toIntOrNull()
        val firstTwips = Regex("""w:firstLine="(\d+)"""").find(indMatch)?.groupValues?.get(1)?.toIntOrNull()
        val indentLeft = if (leftTwips != null) (leftTwips / 30).coerceAtMost(200)
            else pStyle?.indentLeft ?: 0
        val indentFirst = if (firstTwips != null) (firstTwips / 30).coerceAtMost(100)
            else pStyle?.indentFirstLine ?: 0

        // Spacing (inline overrides style)
        val spacingTag = Regex("""<w:spacing\b([^/]*)/>""").find(pPr)?.groupValues?.get(1) ?: ""
        val beforeTwips = Regex("""w:before="(\d+)"""").find(spacingTag)?.groupValues?.get(1)?.toIntOrNull()
        val afterTwips = Regex("""w:after="(\d+)"""").find(spacingTag)?.groupValues?.get(1)?.toIntOrNull()
        val lineVal = Regex("""w:line="(\d+)"""").find(spacingTag)?.groupValues?.get(1)?.toIntOrNull()
        val lineRule = Regex("""w:lineRule="([^"]+)"""").find(spacingTag)?.groupValues?.get(1) ?: ""
        val spacingBefore = if (beforeTwips != null) (beforeTwips / 20).coerceAtMost(60)
            else pStyle?.spacingBefore ?: 0
        val spacingAfter = if (afterTwips != null) (afterTwips / 20).coerceAtMost(60)
            else pStyle?.spacingAfter ?: 0
        val lineSpacing = if (lineVal != null && lineVal > 0 && lineRule != "exact")
            (lineVal / 240f).coerceIn(0.8f, 3f) else pStyle?.lineSpacing ?: 0f

        // Paragraph shading/background
        val shdTag = Regex("""<w:shd\b([^/]*)/>""").find(pPr)?.groupValues?.get(1) ?: ""
        val bgHex = Regex("""w:fill="([0-9A-Fa-f]{6})"""").find(shdTag)?.groupValues?.get(1)
        val backgroundColor = if (bgHex != null && bgHex.lowercase() !in listOf("auto", "ffffff")) {
            (0xFF000000 or bgHex.toLong(16))
        } else 0L

        // Paragraph bottom border → horizontal line
        val pBdr = extractTagContent(pPr, "w:pBdr")
        val hasBorderBottom = pBdr.contains("<w:bottom") && !pBdr.contains("""w:val="none""")

        // Tab stops with leaders
        val tabStops = mutableListOf<DocxTabStop>()
        val tabsContent = extractTagContent(pPr, "w:tabs")
        for (tm in Regex("""<w:tab\b([^/]*)/>""").findAll(tabsContent)) {
            val attrs = tm.groupValues[1]
            val pos = Regex("""w:pos="(\d+)"""").find(attrs)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val leader = Regex("""w:leader="([^"]+)"""").find(attrs)?.groupValues?.get(1)
            tabStops.add(DocxTabStop(pos, leader))
        }

        // List numbering from numbering.xml
        var listBullet: String? = null
        var listLevel = 0
        val numPr = extractTagContent(pPr, "w:numPr")
        if (numPr.isNotEmpty()) {
            listLevel = Regex("""<w:ilvl w:val="(\d+)"""").find(numPr)
                ?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val numId = Regex("""<w:numId w:val="(\d+)"""").find(numPr)
                ?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val levelDef = numbering[numId]?.get(listLevel)
            if (levelDef != null) {
                val counterKey = "$numId:$listLevel"
                val counter = numCounters.getOrDefault(counterKey, levelDef.start - 1) + 1
                numCounters[counterKey] = counter
                listBullet = formatNumBullet(levelDef, counter)
            } else {
                listBullet = "\u2022"
            }
        }

        // Run defaults: merge docDefaults ← style ← paragraph rPr
        val styleRunDef = pStyle?.runDef
        val pRpr = extractTagContent(pPr, "w:rPr")
        val defBold = docxFlag(pRpr, "w:b") ?: styleRunDef?.bold ?: docRunDef?.bold
        val defItalic = docxFlag(pRpr, "w:i") ?: styleRunDef?.italic ?: docRunDef?.italic
        val defFontFamily = styleRunDef?.fontFamily ?: docRunDef?.fontFamily
        val defFontSize = styleRunDef?.fontSize ?: docRunDef?.fontSize ?: 0f

        val spans = docxRunSpans(pXml, hyperlinks, defBold, defItalic, defFontFamily, defFontSize, tabStops)

        // Empty paragraphs with spacing act as vertical spacers
        if (spans.isEmpty()) {
            val totalSpacing = spacingBefore + spacingAfter
            return if (totalSpacing > 0 || pXml.contains("<w:br")) {
                DocParagraph(
                    spans = listOf(DocSpan(" ")),
                    spacingBeforeDp = spacingBefore.coerceAtLeast(6),
                    spacingAfterDp = spacingAfter.coerceAtLeast(6)
                )
            } else null
        }

        return DocParagraph(
            spans = spans,
            headingLevel = headingLevel,
            alignment = alignment,
            indentLeft = indentLeft + listLevel * 16,
            indentFirstLine = indentFirst,
            spacingBeforeDp = spacingBefore,
            spacingAfterDp = spacingAfter,
            lineSpacingMultiplier = lineSpacing,
            backgroundColor = backgroundColor,
            hasBorderBottom = hasBorderBottom,
            listBullet = listBullet,
            listLevel = listLevel
        )
    }

    private fun docxRunSpans(
        pXml: String,
        hyperlinks: Map<String, String>,
        defBold: Boolean? = null,
        defItalic: Boolean? = null,
        defFontFamily: String? = null,
        defFontSize: Float = 0f,
        tabStops: List<DocxTabStop> = emptyList()
    ): List<DocSpan> {
        val spans = mutableListOf<DocSpan>()
        val hyperlinkPattern = Regex("""<w:hyperlink\b[^>]*>(.*?)</w:hyperlink>""", RegexOption.DOT_MATCHES_ALL)
        val runPattern = Regex("""<w:r[\s>].*?</w:r>""", RegexOption.DOT_MATCHES_ALL)
        val hlRanges = hyperlinkPattern.findAll(pXml).toList()
        var tabIndex = 0

        fun isInHyperlink(pos: Int): Pair<Boolean, String?> {
            for (hl in hlRanges) {
                if (pos in hl.range) {
                    val rId = Regex("""r:id="([^"]+)"""").find(hl.value)?.groupValues?.get(1)
                    val url = rId?.let { hyperlinks[it] }
                    return true to url
                }
            }
            return false to null
        }

        for (rm in runPattern.findAll(pXml)) {
            val rXml = rm.value
            // Collect text from <w:t>, <w:tab/>, <w:br/>
            val textParts = Regex("""<w:t[^>]*>([^<]*)</w:t>|<w:tab\s*/>|<w:br\s*[^/]*/>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(rXml)
                .map { m ->
                    val tText = m.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() }
                    when {
                        tText != null -> tText
                        m.value.contains("<w:tab") -> {
                            val ts = tabStops.getOrNull(tabIndex++)
                            if (ts?.leader != null) {
                                val charCount = (ts.pos / 140).coerceIn(10, 80)
                                when (ts.leader) {
                                    "underscore", "heavy" -> "_".repeat(charCount)
                                    "dot" -> "\u00B7".repeat(charCount)
                                    "hyphen" -> "-".repeat(charCount)
                                    else -> "\t"
                                }
                            } else "\t"
                        }
                        m.value.contains("<w:br") -> "\n"
                        else -> ""
                    }
                }
            val text = textParts.joinToString("")
            if (text.isEmpty()) continue

            val rPr = extractTagContent(rXml, "w:rPr")
            val bold = docxFlag(rPr, "w:b") ?: defBold ?: false
            val italic = docxFlag(rPr, "w:i") ?: defItalic ?: false
            val underline = rPr.contains("<w:u ") && !rPr.contains("w:val=\"none\"")
            val strike = rPr.contains("<w:strike") && !rPr.contains("w:val=\"false\"") && !rPr.contains("w:val=\"0\"")

            val szVal = Regex("""<w:sz w:val="(\d+)"""").find(rPr)?.groupValues?.get(1)?.toFloatOrNull()
            val fontSize = if (szVal != null) (szVal / 2f).coerceIn(8f, 72f)
                else if (defFontSize > 0f) defFontSize else 0f

            val colorHex = Regex("""<w:color w:val="([0-9A-Fa-f]{6})"""").find(rPr)?.groupValues?.get(1)
            val textColor = if (colorHex != null && colorHex != "000000" && colorHex.lowercase() != "auto") {
                (0xFF000000 or colorHex.toLong(16))
            } else 0L

            val hlVal = Regex("""<w:highlight w:val="([^"]+)"""").find(rPr)?.groupValues?.get(1)
            val highlightColor = highlightNameToColor(hlVal)

            val vaVal = Regex("""<w:vertAlign w:val="([^"]+)"""").find(rPr)?.groupValues?.get(1)
            val vertAlign = when (vaVal) {
                "superscript" -> VerticalAlign.SUPERSCRIPT
                "subscript" -> VerticalAlign.SUBSCRIPT
                else -> VerticalAlign.NORMAL
            }

            // Font family from w:rFonts
            val rFonts = Regex("""<w:rFonts[^>]*w:ascii="([^"]+)"""").find(rPr)?.groupValues?.get(1)
            val fontFamily = rFonts ?: defFontFamily

            val (_, url) = isInHyperlink(rm.range.first)

            spans.add(DocSpan(
                text = decodeEntities(text),
                bold = bold,
                italic = italic,
                underline = underline || url != null,
                strikethrough = strike,
                fontSize = fontSize,
                textColor = if (url != null && textColor == 0L) 0xFF1A73E8 else textColor,
                highlightColor = highlightColor,
                fontFamily = fontFamily,
                verticalAlign = vertAlign,
                hyperlink = url
            ))
        }

        return spans
    }

    private fun docxHeadingLevel(pPr: String): Int {
        val style = Regex("""<w:pStyle w:val="([^"]+)"""").find(pPr)
            ?.groupValues?.get(1) ?: ""
        if (style.startsWith("Heading", ignoreCase = true)) {
            return style.filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 6) ?: 1
        }
        if (style.equals("Title", ignoreCase = true)) return 1
        if (style.equals("Subtitle", ignoreCase = true)) return 2
        val ol = Regex("""<w:outlineLvl w:val="(\d+)"""").find(pPr)
        if (ol != null) return ((ol.groupValues[1].toIntOrNull() ?: 0) + 1).coerceIn(1, 6)
        return 0
    }

    private fun docxAlignment(jcVal: String?): ParaAlignment = when (jcVal) {
        "center" -> ParaAlignment.CENTER
        "right" -> ParaAlignment.RIGHT
        "both", "distribute" -> ParaAlignment.JUSTIFY
        else -> ParaAlignment.LEFT
    }

    private fun parseDocxStyles(zip: ZipFile): Pair<Map<String, DocxParaDef>, DocxRunDef?> {
        val entry = zip.getEntry("word/styles.xml")
            ?: return emptyMap<String, DocxParaDef>() to null
        val xml = zip.getInputStream(entry).bufferedReader().readText()

        // Document defaults
        var docRunDef: DocxRunDef? = null
        val ddMatch = Regex("""<w:docDefaults>(.*?)</w:docDefaults>""", RegexOption.DOT_MATCHES_ALL).find(xml)
        if (ddMatch != null) {
            val rPr = extractTagContent(ddMatch.groupValues[1], "w:rPr")
            if (rPr.isNotEmpty()) {
                val font = Regex("""<w:rFonts[^>]*w:ascii="([^"]+)"""").find(rPr)?.groupValues?.get(1)
                val sz = Regex("""<w:sz w:val="(\d+)"""").find(rPr)?.groupValues?.get(1)?.toFloatOrNull()
                docRunDef = DocxRunDef(fontFamily = font, fontSize = if (sz != null) sz / 2f else 0f)
            }
        }

        // Named styles
        val styleMap = mutableMapOf<String, DocxParaDef>()
        val styleRe = Regex("""<w:style\b[^>]*w:styleId="([^"]+)"[^>]*>(.*?)</w:style>""", RegexOption.DOT_MATCHES_ALL)
        for (m in styleRe.findAll(xml)) {
            val styleId = m.groupValues[1]
            val body = m.groupValues[2]
            val pPr = extractTagContent(body, "w:pPr")
            val rPr = extractTagContent(body, "w:rPr")

            val jc = Regex("""<w:jc w:val="([^"]+)"""").find(pPr)?.groupValues?.get(1)
            val ind = Regex("""<w:ind\b([^/]*)/>""").find(pPr)?.groupValues?.get(1) ?: ""
            val spacing = Regex("""<w:spacing\b([^/]*)/>""").find(pPr)?.groupValues?.get(1) ?: ""
            val lineRule = Regex("""w:lineRule="([^"]+)"""").find(spacing)?.groupValues?.get(1) ?: ""
            val lineV = Regex("""w:line="(\d+)"""").find(spacing)?.groupValues?.get(1)?.toIntOrNull() ?: 0

            val bold = docxFlag(rPr, "w:b")
            val italic = docxFlag(rPr, "w:i")
            val font = Regex("""<w:rFonts[^>]*w:ascii="([^"]+)"""").find(rPr)?.groupValues?.get(1)
            val sz = Regex("""<w:sz w:val="(\d+)"""").find(rPr)?.groupValues?.get(1)?.toFloatOrNull()
            val color = Regex("""<w:color w:val="([0-9A-Fa-f]{6})"""").find(rPr)?.groupValues?.get(1)
            val textColor = if (color != null && color != "000000" && color.lowercase() != "auto")
                (0xFF000000 or color.toLong(16)) else 0L

            val runDef = if (bold != null || italic != null || font != null || sz != null || textColor != 0L)
                DocxRunDef(bold, italic, if (sz != null) (sz / 2f).coerceIn(8f, 72f) else 0f, font, textColor)
            else null

            styleMap[styleId] = DocxParaDef(
                alignment = if (jc != null) docxAlignment(jc) else null,
                indentLeft = (Regex("""w:left="(\d+)"""").find(ind)?.groupValues?.get(1)?.toIntOrNull()?.let { it / 30 } ?: 0).coerceAtMost(200),
                indentFirstLine = (Regex("""w:firstLine="(\d+)"""").find(ind)?.groupValues?.get(1)?.toIntOrNull()?.let { it / 30 } ?: 0).coerceAtMost(100),
                spacingBefore = (Regex("""w:before="(\d+)"""").find(spacing)?.groupValues?.get(1)?.toIntOrNull()?.let { it / 20 } ?: 0).coerceAtMost(60),
                spacingAfter = (Regex("""w:after="(\d+)"""").find(spacing)?.groupValues?.get(1)?.toIntOrNull()?.let { it / 20 } ?: 0).coerceAtMost(60),
                lineSpacing = if (lineV > 0 && lineRule != "exact") (lineV / 240f).coerceIn(0.8f, 3f) else 0f,
                runDef = runDef
            )
        }
        return styleMap to docRunDef
    }

    private fun parseDocxNumbering(zip: ZipFile): Map<Int, Map<Int, DocxNumLevel>> {
        val entry = zip.getEntry("word/numbering.xml") ?: return emptyMap()
        val xml = zip.getInputStream(entry).bufferedReader().readText()

        val abstractNums = mutableMapOf<Int, Map<Int, DocxNumLevel>>()
        val absRe = Regex("""<w:abstractNum\s+w:abstractNumId="(\d+)"[^>]*>(.*?)</w:abstractNum>""", RegexOption.DOT_MATCHES_ALL)
        for (m in absRe.findAll(xml)) {
            val absId = m.groupValues[1].toIntOrNull() ?: continue
            val levels = mutableMapOf<Int, DocxNumLevel>()
            val lvlRe = Regex("""<w:lvl\s+w:ilvl="(\d+)"[^>]*>(.*?)</w:lvl>""", RegexOption.DOT_MATCHES_ALL)
            for (lm in lvlRe.findAll(m.groupValues[2])) {
                val ilvl = lm.groupValues[1].toIntOrNull() ?: continue
                val body = lm.groupValues[2]
                val numFmt = Regex("""<w:numFmt w:val="([^"]+)"""").find(body)?.groupValues?.get(1) ?: "bullet"
                val lvlText = Regex("""<w:lvlText w:val="([^"]*?)"""").find(body)?.groupValues?.get(1) ?: "\u2022"
                val start = Regex("""<w:start w:val="(\d+)"""").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                levels[ilvl] = DocxNumLevel(numFmt, lvlText, start)
            }
            abstractNums[absId] = levels
        }

        val result = mutableMapOf<Int, Map<Int, DocxNumLevel>>()
        val numRe = Regex("""<w:num\s+w:numId="(\d+)"[^>]*>(.*?)</w:num>""", RegexOption.DOT_MATCHES_ALL)
        for (m in numRe.findAll(xml)) {
            val numId = m.groupValues[1].toIntOrNull() ?: continue
            val absId = Regex("""<w:abstractNumId w:val="(\d+)"""").find(m.groupValues[2])
                ?.groupValues?.get(1)?.toIntOrNull() ?: continue
            abstractNums[absId]?.let { result[numId] = it }
        }
        return result
    }

    private fun formatNumBullet(level: DocxNumLevel, counter: Int): String {
        val formatted = when (level.numFmt) {
            "decimal" -> counter.toString()
            "lowerLetter" -> ('a' + (counter - 1) % 26).toString()
            "upperLetter" -> ('A' + (counter - 1) % 26).toString()
            "lowerRoman" -> toRoman(counter).lowercase()
            "upperRoman" -> toRoman(counter)
            "bullet" -> return level.lvlText.ifEmpty { "\u2022" }
            "none" -> return ""
            else -> counter.toString()
        }
        var text = level.lvlText
        text = text.replace(Regex("""%\d"""), formatted)
        return text
    }

    private fun toRoman(n: Int): String {
        if (n <= 0 || n > 3999) return n.toString()
        val values = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
        val symbols = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
        val sb = StringBuilder()
        var remaining = n
        for (i in values.indices) {
            while (remaining >= values[i]) { sb.append(symbols[i]); remaining -= values[i] }
        }
        return sb.toString()
    }

    private fun docxFlag(rPr: String, tag: String): Boolean? {
        if (!rPr.contains("<$tag")) return null
        if (rPr.contains("<$tag/>")) return true
        val v = Regex("""<$tag w:val="([^"]+)"""").find(rPr)?.groupValues?.get(1)?.lowercase()
        return v != "false" && v != "0"
    }

    private fun highlightNameToColor(name: String?): Long = when (name?.lowercase()) {
        "yellow" -> 0xFFFFFF00
        "green" -> 0xFF00FF00
        "cyan" -> 0xFF00FFFF
        "magenta" -> 0xFFFF00FF
        "blue" -> 0xFF0000FF
        "red" -> 0xFFFF0000
        "darkblue" -> 0xFF00008B
        "darkcyan" -> 0xFF008B8B
        "darkgreen" -> 0xFF006400
        "darkmagenta" -> 0xFF8B008B
        "darkred" -> 0xFF8B0000
        "darkyellow" -> 0xFF808000
        "darkgray" -> 0xFFA9A9A9
        "lightgray" -> 0xFFD3D3D3
        "black" -> 0xFF000000
        else -> 0L
    }

    // ======================== DOC (Apache POI) ========================

    @Suppress("DEPRECATION")
    private fun parseDoc(file: File): List<DocParagraph> {
        // Layer 1: Range-based with full formatting
        try { return parseDocWithRange(file) }
        catch (_: org.apache.poi.poifs.filesystem.OfficeXmlFileException) { return parseDocx(file) }
        catch (_: Throwable) { /* continue to fallbacks (Throwable: NoClassDefFoundError from R8) */ }

        // Layer 2: HWPFDocument.getText() — raw text from pieces, bypasses Range
        try {
            file.inputStream().use { input ->
                val doc = HWPFDocument(input)
                val text = doc.text.toString()
                doc.close()
                val result = textToParagraphs(text)
                if (result.isNotEmpty()) return result
            }
        } catch (_: org.apache.poi.poifs.filesystem.OfficeXmlFileException) { return parseDocx(file) }
        catch (_: Throwable) { /* continue */ }

        // Layer 3: WordExtractor.getTextFromPieces() — another path to text pieces
        try {
            file.inputStream().use { input ->
                val extractor = WordExtractor(input)
                @Suppress("DEPRECATION")
                val text = extractor.textFromPieces
                extractor.close()
                val result = textToParagraphs(text)
                if (result.isNotEmpty()) return result
            }
        } catch (_: org.apache.poi.poifs.filesystem.OfficeXmlFileException) { return parseDocx(file) }
        catch (_: Throwable) { /* continue */ }

        // Layer 4: HWPFOldDocument — Word 6/95 format
        try {
            file.inputStream().use { input ->
                val doc = HWPFOldDocument(POIFSFileSystem(input))
                val text = doc.text.toString()
                doc.close()
                val result = textToParagraphs(text)
                if (result.isNotEmpty()) return result
            }
        } catch (_: Throwable) { /* continue */ }

        // Layer 5: Maybe it's RTF saved as .doc
        try {
            val header = file.inputStream().use { it.readNBytes(5) }
            if (String(header, Charsets.ISO_8859_1).startsWith("{\\rtf")) {
                return parseRtf(file)
            }
        } catch (_: Exception) { /* continue */ }

        // Layer 6: Parse FIB + piece table directly (MS-DOC binary spec)
        try {
            val result = parseDocWithFib(file)
            if (result.isNotEmpty()) return result
        } catch (_: Exception) { /* continue */ }

        // Layer 7: Brute-force binary extraction from OLE2 WordDocument stream
        try {
            val result = parseDocBinary(file)
            if (result.isNotEmpty()) return result
        } catch (_: Exception) { /* continue */ }

        return emptyList()
    }

    // ---- binary helpers for reading little-endian integers ----
    private fun leUInt16(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)

    private fun leInt32(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or
        ((b[off + 1].toInt() and 0xFF) shl 8) or
        ((b[off + 2].toInt() and 0xFF) shl 16) or
        ((b[off + 3].toInt() and 0xFF) shl 24)

    /** SPRM operand size from spra bits */
    private fun sprmOpSize(sprm: Int, bytes: ByteArray, off: Int, limit: Int): Int {
        val spra = (sprm ushr 13) and 7
        return when (spra) {
            0, 1 -> 1; 2, 4, 5 -> 2; 3 -> 4; 7 -> 3
            6 -> if (off + 2 < limit) (bytes[off + 2].toInt() and 0xFF) + 1 else 1
            else -> 1
        }
    }

    /**
     * Parse DOC via FIB + piece table + PAPX/CHPX formatting.
     * MS-DOC binary format: FIB → Table stream → CLX → PlcPcd → text + formatting.
     */
    private fun parseDocWithFib(file: File): List<DocParagraph> {
        file.inputStream().use { input ->
            val fs = POIFSFileSystem(input)

            // ---- Read OLE2 streams ----
            val wdEntry = fs.root.getEntry("WordDocument") as org.apache.poi.poifs.filesystem.DocumentEntry
            val wd = ByteArray(wdEntry.size)
            fs.createDocumentInputStream("WordDocument").use { it.readFully(wd) }

            if (wd.size < 0x01AA || leUInt16(wd, 0) != 0xA5EC) { fs.close(); return emptyList() }

            val flags = leUInt16(wd, 0x0A)
            val tblName = if ((flags and 0x0200) != 0) "1Table" else "0Table"
            val tblEntry = try {
                fs.root.getEntry(tblName) as org.apache.poi.poifs.filesystem.DocumentEntry
            } catch (_: Exception) { fs.close(); return emptyList() }
            val tbl = ByteArray(tblEntry.size)
            fs.createDocumentInputStream(tblName).use { it.readFully(tbl) }

            val ccpText = leInt32(wd, 0x4C)
            if (ccpText <= 0) { fs.close(); return emptyList() }

            // ---- Parse piece table (CLX → Pcdt → PlcPcd) ----
            val fcClx = leInt32(wd, 0x01A2)
            val lcbClx = leInt32(wd, 0x01A6)
            if (fcClx < 0 || fcClx >= tbl.size || lcbClx <= 0) { fs.close(); return emptyList() }

            var off = fcClx
            while (off < fcClx + lcbClx && off < tbl.size && tbl[off] == 0x01.toByte()) {
                off += 3 + leUInt16(tbl, off + 1)
            }
            if (off >= tbl.size || tbl[off] != 0x02.toByte()) { fs.close(); return emptyList() }

            val lcbPlcPcd = leInt32(tbl, off + 1)
            off += 5
            val nPieces = (lcbPlcPcd - 4) / 12
            if (nPieces <= 0) { fs.close(); return emptyList() }

            val cps = IntArray(nPieces + 1) { leInt32(tbl, off + it * 4) }
            val pcdBase = off + (nPieces + 1) * 4

            // Build piece descriptors
            data class Piece(val cpStart: Int, val cpEnd: Int, val byteStart: Int, val compressed: Boolean)
            val pieces = mutableListOf<Piece>()
            for (i in 0 until nPieces) {
                val pcdOff = pcdBase + i * 8
                val fcRaw = leInt32(tbl, pcdOff + 2)
                val comp = (fcRaw and 0x40000000) != 0
                val fc = fcRaw and 0x3FFFFFFF
                pieces.add(Piece(cps[i], cps[i + 1], if (comp) fc / 2 else fc, comp))
            }

            // ---- Extract full text ----
            val fullText = StringBuilder()
            var totalChars = 0
            for (p in pieces) {
                if (totalChars >= ccpText) break
                val cnt = minOf(p.cpEnd - p.cpStart, ccpText - totalChars)
                if (p.compressed) {
                    for (j in 0 until cnt) { val idx = p.byteStart + j; if (idx in wd.indices) fullText.append((wd[idx].toInt() and 0xFF).toChar()) }
                } else {
                    for (j in 0 until cnt) { val idx = p.byteStart + j * 2; if (idx + 1 < wd.size) { fullText.append((((wd[idx + 1].toInt() and 0xFF) shl 8) or (wd[idx].toInt() and 0xFF)).toChar()) } }
                }
                totalChars += cnt
            }

            // ---- FC-to-CP converter (FKP FCs are byte offsets in WordDocument) ----
            fun fcToCp(fc: Int): Int {
                for (p in pieces) {
                    val byteLen = if (p.compressed) (p.cpEnd - p.cpStart) else (p.cpEnd - p.cpStart) * 2
                    if (fc in p.byteStart until (p.byteStart + byteLen)) {
                        return p.cpStart + if (p.compressed) (fc - p.byteStart) else (fc - p.byteStart) / 2
                    }
                }
                return -1
            }

            // ---- Parse paragraph formatting (PlcBtePapx → FKP pages → SPRMs) ----
            data class PFmt(val cpS: Int, val cpE: Int, val align: ParaAlignment,
                            val indL: Int, val indF: Int, val spB: Int, val spA: Int)
            val paraFmt = mutableListOf<PFmt>()
            try {
                val fcP = leInt32(wd, 0x0102); val lcbP = leInt32(wd, 0x0106)
                if (fcP > 0 && lcbP > 8 && fcP + lcbP <= tbl.size) {
                    val nBte = (lcbP / 4 - 1) / 2
                    for (k in 0 until nBte) {
                        val pgOff = (leInt32(tbl, fcP + (nBte + 1) * 4 + k * 4) and 0x3FFFFF) * 512
                        if (pgOff < 0 || pgOff + 512 > wd.size) continue
                        val crun = wd[pgOff + 511].toInt() and 0xFF
                        for (r in 0 until crun) {
                            val cpS = fcToCp(leInt32(wd, pgOff + r * 4))
                            val cpE = fcToCp(leInt32(wd, pgOff + (r + 1) * 4))
                            if (cpS < 0 || cpE < 0 || cpS >= cpE) continue
                            // BX: 13 bytes (1 bOffset + 12 PHE)
                            val bxOff = pgOff + (crun + 1) * 4 + r * 13
                            if (bxOff >= pgOff + 512) continue
                            val bOfs = wd[bxOff].toInt() and 0xFF
                            if (bOfs == 0) { paraFmt.add(PFmt(cpS, cpE, ParaAlignment.LEFT, 0, 0, 0, 0)); continue }
                            val papxOff = pgOff + bOfs * 2
                            if (papxOff >= pgOff + 511) continue
                            var cb = wd[papxOff].toInt() and 0xFF
                            var ds = papxOff + 1
                            if (cb == 0 && papxOff + 1 < pgOff + 511) { cb = wd[papxOff + 1].toInt() and 0xFF; ds = papxOff + 2 }
                            if (cb == 0) { paraFmt.add(PFmt(cpS, cpE, ParaAlignment.LEFT, 0, 0, 0, 0)); continue }
                            val sEnd = minOf(ds + cb * 2, pgOff + 512)
                            val ss = ds + 2 // skip istd
                            var al = ParaAlignment.LEFT; var iL = 0; var iF = 0; var sB = 0; var sA = 0
                            var s = ss
                            while (s + 2 <= sEnd) {
                                val sp = leUInt16(wd, s); val os = sprmOpSize(sp, wd, s, sEnd)
                                if (s + 2 + os > sEnd) break
                                when (sp) {
                                    0x2403, 0x2461 -> al = when (wd[s + 2].toInt() and 0xFF) { 1 -> ParaAlignment.CENTER; 2 -> ParaAlignment.RIGHT; 3 -> ParaAlignment.JUSTIFY; else -> ParaAlignment.LEFT }
                                    0x840F, 0x845E -> iL = leUInt16(wd, s + 2).toShort().toInt()
                                    0x8411, 0x8460 -> iF = leUInt16(wd, s + 2).toShort().toInt()
                                    0xA413 -> sB = leUInt16(wd, s + 2)
                                    0xA414 -> sA = leUInt16(wd, s + 2)
                                }
                                s += 2 + os
                            }
                            paraFmt.add(PFmt(cpS, cpE, al, iL, iF, sB, sA))
                        }
                    }
                }
            } catch (_: Exception) { /* formatting parse failed, continue without */ }

            // ---- Parse character formatting (PlcBteChpx → FKP pages → SPRMs) ----
            data class CFmt(val cpS: Int, val cpE: Int, val bold: Boolean, val italic: Boolean,
                            val underline: Boolean, val fontSize: Float)
            val charFmt = mutableListOf<CFmt>()
            try {
                val fcC = leInt32(wd, 0x00FA); val lcbC = leInt32(wd, 0x00FE)
                if (fcC > 0 && lcbC > 8 && fcC + lcbC <= tbl.size) {
                    val nBte = (lcbC / 4 - 1) / 2
                    for (k in 0 until nBte) {
                        val pgOff = (leInt32(tbl, fcC + (nBte + 1) * 4 + k * 4) and 0x3FFFFF) * 512
                        if (pgOff < 0 || pgOff + 512 > wd.size) continue
                        val crun = wd[pgOff + 511].toInt() and 0xFF
                        for (r in 0 until crun) {
                            val cpS = fcToCp(leInt32(wd, pgOff + r * 4))
                            val cpE = fcToCp(leInt32(wd, pgOff + (r + 1) * 4))
                            if (cpS < 0 || cpE < 0 || cpS >= cpE) continue
                            // CHPX BX: 1 byte (bOffset only)
                            val bxOff = pgOff + (crun + 1) * 4 + r
                            if (bxOff >= pgOff + 512) continue
                            val bOfs = wd[bxOff].toInt() and 0xFF
                            if (bOfs == 0) continue
                            val chpxOff = pgOff + bOfs * 2
                            if (chpxOff >= pgOff + 511) continue
                            val cb = wd[chpxOff].toInt() and 0xFF
                            var bo = false; var it2 = false; var ul = false; var fs2 = 0f
                            var s = chpxOff + 1; val sEnd = minOf(s + cb, pgOff + 512)
                            while (s + 2 <= sEnd) {
                                val sp = leUInt16(wd, s); val os = sprmOpSize(sp, wd, s, sEnd)
                                if (s + 2 + os > sEnd) break
                                when (sp) {
                                    0x0835 -> bo = (wd[s + 2].toInt() and 0xFF) != 0
                                    0x0836 -> it2 = (wd[s + 2].toInt() and 0xFF) != 0
                                    0x2A3E -> ul = (wd[s + 2].toInt() and 0xFF) != 0
                                    0x4A43 -> fs2 = leUInt16(wd, s + 2) / 2f
                                    0x2A48 -> fs2 = (wd[s + 2].toInt() and 0xFF) / 2f
                                }
                                s += 2 + os
                            }
                            if (bo || it2 || ul || fs2 > 0f) charFmt.add(CFmt(cpS, cpE, bo, it2, ul, fs2))
                        }
                    }
                }
            } catch (_: Exception) { /* formatting parse failed */ }

            fs.close()

            // ---- Extract pictures via POI (HWPFDocument can be created even if Range fails) ----
            val pictures: List<ByteArray> = try {
                file.inputStream().use { fis ->
                    val doc = HWPFDocument(fis)
                    val pics = doc.picturesTable.allPictures.map { it.content }
                    doc.close()
                    pics
                }
            } catch (_: Throwable) { emptyList() }

            // ---- Build formatted DocParagraphs ----
            val text = fullText.toString()
            if (text.isBlank()) return emptyList()

            val result = mutableListOf<DocParagraph>()
            var cp = 0
            var picIdx = 0

            for (segment in text.split('\r')) {
                val segLen = segment.length

                // Insert image paragraphs for 0x01 picture placeholders in this segment
                if (pictures.isNotEmpty()) {
                    for (ch in segment) {
                        if (ch == '\u0001' && picIdx < pictures.size) {
                            result.add(DocParagraph(
                                spans = listOf(DocSpan("")),
                                imageData = pictures[picIdx]
                            ))
                            picIdx++
                        }
                    }
                }

                if (segLen > 0) {
                    // Clean control chars but keep tab
                    val cleaned = segment
                        .replace('\u000B', '\n').replace('\u000C', '\n')
                        .replace(Regex("[\\x00-\\x08\\x0E-\\x1F\\x07]"), "")
                        .trim()

                    if (cleaned.isNotBlank()) {
                        // Paragraph formatting
                        val pp = paraFmt.firstOrNull { cp >= it.cpS && cp < it.cpE }

                        // Character formatting — build spans
                        val spans = mutableListOf<DocSpan>()
                        var pos = 0
                        while (pos < cleaned.length) {
                            val charCp = cp + pos
                            val cf = charFmt.firstOrNull { charCp >= it.cpS && charCp < it.cpE }
                            var end = pos + 1
                            while (end < cleaned.length) {
                                val nCp = cp + end
                                val nCf = charFmt.firstOrNull { nCp >= it.cpS && nCp < it.cpE }
                                if (nCf != cf) break
                                end++
                            }
                            spans.add(DocSpan(
                                text = cleaned.substring(pos, end),
                                bold = cf?.bold ?: false,
                                italic = cf?.italic ?: false,
                                underline = cf?.underline ?: false,
                                fontSize = cf?.fontSize ?: 0f
                            ))
                            pos = end
                        }

                        result.add(DocParagraph(
                            spans = if (spans.isEmpty()) listOf(DocSpan(cleaned)) else spans,
                            alignment = pp?.align ?: ParaAlignment.LEFT,
                            indentLeft = maxOf(0, (pp?.indL ?: 0) / 20),
                            indentFirstLine = maxOf(0, (pp?.indF ?: 0) / 20),
                            spacingBeforeDp = (pp?.spB ?: 0) / 20,
                            spacingAfterDp = (pp?.spA ?: 0) / 20
                        ))
                    }
                }
                cp += segLen + 1 // +1 for \r
            }

            return if (result.isNotEmpty()) result else textToParagraphs(text)
        }
    }

    /** Last-resort: read text directly from OLE2 binary stream */
    private fun parseDocBinary(file: File): List<DocParagraph> {
        // Try OLE2 container first
        try {
            file.inputStream().use { input ->
                val fs = POIFSFileSystem(input)
                val wordDocEntry = try {
                    fs.root.getEntry("WordDocument") as? org.apache.poi.poifs.filesystem.DocumentEntry
                } catch (_: Exception) { null }
                if (wordDocEntry != null) {
                    val bytes = ByteArray(wordDocEntry.size)
                    fs.createDocumentInputStream("WordDocument").use { it.readFully(bytes) }
                    fs.close()
                    // Word 97+ stores text in UTF-16LE — try it first
                    val utf16 = extractUtf16Runs(bytes)
                    if (utf16.length > 50) return textToParagraphs(utf16)
                    val cp1251 = extractPrintableRuns(bytes, charset("windows-1251"))
                    if (cp1251.length > 50) return textToParagraphs(cp1251)
                } else {
                    fs.close()
                }
            }
        } catch (_: Exception) { /* not OLE2 */ }

        // Absolute last resort: scan raw file bytes
        val bytes = file.readBytes()
        val utf16 = extractUtf16Runs(bytes)
        if (utf16.length > 50) return textToParagraphs(utf16)
        val cp1251 = extractPrintableRuns(bytes, charset("windows-1251"))
        if (cp1251.length > 50) return textToParagraphs(cp1251)
        val utf8 = extractPrintableRuns(bytes, Charsets.UTF_8)
        if (utf8.length > 50) return textToParagraphs(utf8)
        return emptyList()
    }

    /** Scan bytes for runs of printable chars using given charset, filtering garbage */
    private fun extractPrintableRuns(bytes: ByteArray, cs: java.nio.charset.Charset): String {
        val text = String(bytes, cs)
        val sb = StringBuilder()
        val run = StringBuilder()
        for (ch in text) {
            if (ch.isLetterOrDigit() || ch in " \t\n\r.,;:!?-()\"'[]{}/@#\u0024%&*+=<>\u2014\u2013\u00AB\u00BB") {
                run.append(ch)
            } else {
                if (run.length > 15 && isLikelyRealText(run)) { sb.append(run); sb.append('\n') }
                run.clear()
            }
        }
        if (run.length > 15 && isLikelyRealText(run)) sb.append(run)
        return sb.toString()
    }

    /** Scan bytes for UTF-16LE printable text runs, filtering out binary garbage */
    private fun extractUtf16Runs(bytes: ByteArray): String {
        val sb = StringBuilder()
        val run = StringBuilder()
        var i = 0
        while (i + 1 < bytes.size) {
            val ch = (bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt() and 0xFF) shl 8)
            val c = ch.toChar()
            if (c.isLetterOrDigit() || c in " \t\n\r.,;:!?-()\"'[]{}/@#\u0024%&*+=<>\u2014\u2013\u00AB\u00BB:") {
                run.append(c)
            } else {
                if (run.length > 15 && isLikelyRealText(run)) {
                    sb.append(run); sb.append('\n')
                }
                run.clear()
            }
            i += 2
        }
        if (run.length > 15 && isLikelyRealText(run)) sb.append(run)
        return sb.toString()
    }

    /** Check that a text run is mostly Cyrillic/Latin/digits/punctuation, not binary garbage */
    private fun isLikelyRealText(run: CharSequence): Boolean {
        var good = 0
        for (c in run) {
            if (c in '\u0400'..'\u04FF' || c in 'A'..'Z' || c in 'a'..'z' ||
                c in '0'..'9' || c == ' ' || c == '\t' || c == '\n' || c == '\r' ||
                c in ".,;:!?-()\"'\u00AB\u00BB\u2014\u2013") {
                good++
            }
        }
        return good.toFloat() / run.length > 0.6f
    }

    /** Full Range-based DOC parsing with formatting */
    @Suppress("DEPRECATION")
    private fun parseDocWithRange(file: File): List<DocParagraph> {
        file.inputStream().use { input ->
            val doc = HWPFDocument(input)
            val range = doc.range
            val styleSheet = doc.styleSheet
            val result = mutableListOf<DocParagraph>()

            for (i in 0 until range.numParagraphs()) {
                try {
                val para = range.getParagraph(i)

                var heading = 0
                try {
                    val sd = styleSheet.getStyleDescription(para.styleIndex.toInt())
                    val sn = sd?.name ?: ""
                    if (sn.startsWith("heading", ignoreCase = true) ||
                        sn.startsWith("Heading")
                    ) {
                        heading = sn.filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 6) ?: 1
                    }
                } catch (_: Exception) { }

                val alignment = when (para.justification.toInt()) {
                    1 -> ParaAlignment.CENTER
                    2 -> ParaAlignment.RIGHT
                    3 -> ParaAlignment.JUSTIFY
                    else -> ParaAlignment.LEFT
                }

                val indentLeft = (para.indentFromLeft / 30).coerceIn(0, 200)

                val isInList = try { para.isInList } catch (_: Exception) { false }
                var listBullet: String? = null
                var listLevel = 0
                if (isInList) {
                    try {
                        listLevel = para.ilvl
                        listBullet = "\u2022"
                    } catch (_: Exception) { }
                }

                val spans = mutableListOf<DocSpan>()
                for (j in 0 until para.numCharacterRuns()) {
                    try {
                    val run = para.getCharacterRun(j)
                    val raw = run.text()
                        .replace('\u000B', '\n').replace('\u000C', '\n')
                        .replace(Regex("[\\x00-\\x08\\x0E-\\x1F]"), "")
                        .replace("\r", "")
                    if (raw.isEmpty()) continue

                    val fs = run.fontSize.toFloat() / 2f
                    val fontSize = if (fs in 9f..70f && fs != 12f) fs else 0f

                    val colorIdx = run.color
                    val textColor = poiColorToArgb(colorIdx)

                    val va = run.subSuperScriptIndex.toInt()
                    val vertAlign = when (va) {
                        1 -> VerticalAlign.SUPERSCRIPT
                        2 -> VerticalAlign.SUBSCRIPT
                        else -> VerticalAlign.NORMAL
                    }

                    spans.add(DocSpan(
                        text = raw,
                        bold = run.isBold,
                        italic = run.isItalic,
                        underline = run.underlineCode != 0,
                        strikethrough = run.isStrikeThrough,
                        fontSize = fontSize,
                        textColor = textColor,
                        verticalAlign = vertAlign
                    ))
                    } catch (_: Exception) { /* skip broken run */ }
                }

                if (spans.isNotEmpty() && spans.any { it.text.isNotBlank() }) {
                    result.add(DocParagraph(
                        spans = spans,
                        headingLevel = heading,
                        alignment = alignment,
                        indentLeft = indentLeft + listLevel * 16,
                        listBullet = listBullet,
                        listLevel = listLevel
                    ))
                }
                } catch (_: Exception) { /* skip broken paragraph */ }
            }
            doc.close()
            return result
        }
    }

    /** Split raw text into DocParagraphs (fallback for broken DOC files) */
    private fun textToParagraphs(text: String): List<DocParagraph> {
        if (text.isBlank()) return emptyList()
        // Strip Word control characters, keep tab/newline/CR
        val cleaned = text
            .replace('\u000B', '\n').replace('\u000C', '\n')
            .replace(Regex("[\\x00-\\x08\\x0E-\\x1F]"), "")
        return cleaned.split(Regex("[\r\n]+"))
            .filter { it.isNotBlank() }
            .map { DocParagraph(spans = listOf(DocSpan(it.trim()))) }
    }

    private fun poiColorToArgb(colorIdx: Int): Long {
        return when (colorIdx) {
            1 -> 0xFF000000; 2 -> 0xFF0000FF; 3 -> 0xFF00FFFF
            4 -> 0xFF00FF00; 5 -> 0xFFFF00FF; 6 -> 0xFFFF0000
            7 -> 0xFFFFFF00; 8 -> 0xFFFFFFFF; 9 -> 0xFF000080
            10 -> 0xFF008080; 11 -> 0xFF008000; 12 -> 0xFF800080
            13 -> 0xFF800000; 14 -> 0xFF808000; 15 -> 0xFF808080
            16 -> 0xFFC0C0C0; else -> 0L
        }
    }

    // ======================== ODT ========================

    private fun parseOdt(file: File): List<DocParagraph> {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("content.xml") ?: return emptyList()
            val xml = zip.getInputStream(entry).bufferedReader().readText()

            // --- Parse styles ---
            val textStyles = mutableMapOf<String, OdtStyle>()
            val styleRe = Regex(
                """<style:style\s[^>]*style:name="([^"]+)"[^>]*style:family="text"[^>]*>(.*?)</style:style>""",
                RegexOption.DOT_MATCHES_ALL
            )
            // Also match reversed order: family before name
            val styleRe2 = Regex(
                """<style:style\s[^>]*style:family="text"[^>]*style:name="([^"]+)"[^>]*>(.*?)</style:style>""",
                RegexOption.DOT_MATCHES_ALL
            )
            for (m in (styleRe.findAll(xml) + styleRe2.findAll(xml))) {
                val name = m.groupValues[1]
                if (name in textStyles) continue
                val body = m.groupValues[2]
                textStyles[name] = odtParseStyle(body)
            }

            // Paragraph styles
            data class OdtParaStyle(
                val alignment: ParaAlignment = ParaAlignment.LEFT,
                val textStyle: OdtStyle? = null
            )

            val paraStyles = mutableMapOf<String, OdtParaStyle>()
            val pStyleRe = Regex(
                """<style:style\s[^>]*style:name="([^"]+)"[^>]*style:family="paragraph"[^>]*>(.*?)</style:style>""",
                RegexOption.DOT_MATCHES_ALL
            )
            val pStyleRe2 = Regex(
                """<style:style\s[^>]*style:family="paragraph"[^>]*style:name="([^"]+)"[^>]*>(.*?)</style:style>""",
                RegexOption.DOT_MATCHES_ALL
            )
            for (m in (pStyleRe.findAll(xml) + pStyleRe2.findAll(xml))) {
                val name = m.groupValues[1]
                if (name in paraStyles) continue
                val body = m.groupValues[2]
                val align = Regex("""fo:text-align="([^"]+)"""").find(body)?.groupValues?.get(1) ?: ""
                val a = when (align) {
                    "center" -> ParaAlignment.CENTER
                    "end", "right" -> ParaAlignment.RIGHT
                    "justify" -> ParaAlignment.JUSTIFY
                    else -> ParaAlignment.LEFT
                }
                // Paragraph style can also define text properties
                val ts = odtParseStyle(body)
                paraStyles[name] = OdtParaStyle(a, if (ts != OdtStyle()) ts else null)
            }

            // --- Extract body ---
            val bodyStart = xml.indexOf("<office:text")
            val bodyEnd = xml.lastIndexOf("</office:text>")
            if (bodyStart < 0 || bodyEnd < 0) return emptyList()
            val rawBody = xml.substring(bodyStart, bodyEnd + "</office:text>".length)
            val body = odtNormalize(rawBody)

            // --- Find ranges ---
            val tableRe = Regex("""<table:table\b[^>]*>.*?</table:table>""", RegexOption.DOT_MATCHES_ALL)
            val tableMatches = tableRe.findAll(body).toList()
            val tableRanges = tableMatches.map { it.range }

            val listRe = Regex("""<text:list\b[^>]*>.*?</text:list>""", RegexOption.DOT_MATCHES_ALL)
            val listRanges = listRe.findAll(body).map { it.range }.toList()

            fun isInsideTable(pos: Int) = tableRanges.any { pos in it }
            fun isInsideList(pos: Int) = listRanges.any { pos in it }

            // --- Collect elements in document order ---
            data class DocElement(val pos: Int, val paragraph: DocParagraph)
            val elements = mutableListOf<DocElement>()

            // Process paragraphs outside tables
            val paraRe = Regex(
                """<text:(h|p)\b([^>]*)>(.*?)</text:\1>""",
                RegexOption.DOT_MATCHES_ALL
            )
            for (m in paraRe.findAll(body)) {
                if (isInsideTable(m.range.first)) continue

                val tag = m.groupValues[1]
                val attrs = m.groupValues[2]
                val content = m.groupValues[3]

                val isH = tag == "h"
                val heading = if (isH) {
                    Regex("""text:outline-level="(\d+)"""").find(attrs)
                        ?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 6) ?: 1
                } else 0

                val styleName = Regex("""text:style-name="([^"]+)"""").find(attrs)?.groupValues?.get(1)
                val pStyle = styleName?.let { paraStyles[it] }
                val alignment = pStyle?.alignment ?: ParaAlignment.LEFT
                val defTextStyle = pStyle?.textStyle

                // Check for embedded image
                val imageHref = Regex("""<draw:image[^>]*xlink:href="([^"]+)"[^>]*/?>""")
                    .find(content)?.groupValues?.get(1)
                if (imageHref != null) {
                    val imgBytes = try {
                        zip.getEntry(imageHref)?.let { zip.getInputStream(it).readBytes() }
                    } catch (_: Exception) { null }
                    if (imgBytes != null) {
                        elements.add(DocElement(m.range.first, DocParagraph(spans = emptyList(), imageData = imgBytes)))
                    }
                }

                // Extract text (remove draw:frame blocks)
                val textContent = content.replace(
                    Regex("""<draw:frame\b.*?</draw:frame>""", RegexOption.DOT_MATCHES_ALL), ""
                )
                val listBullet = if (isInsideList(m.range.first)) "•" else null
                val spans = odtSpans(textContent, textStyles, defTextStyle)
                if (spans.isNotEmpty()) {
                    elements.add(DocElement(
                        m.range.first + (if (imageHref != null) 1 else 0),
                        DocParagraph(
                            spans = spans,
                            headingLevel = heading,
                            alignment = alignment,
                            listBullet = listBullet
                        )
                    ))
                }
            }

            // Process tables
            val rowRe = Regex("""<table:table-row\b[^>]*>.*?</table:table-row>""", RegexOption.DOT_MATCHES_ALL)
            val cellRe = Regex("""<table:table-cell\b[^>]*>.*?</table:table-cell>""", RegexOption.DOT_MATCHES_ALL)

            // Parse ODT table column styles (style:column-width)
            val colStyleWidths = mutableMapOf<String, Float>()
            val colStyleRe = Regex(
                """<style:style\s[^>]*style:name="([^"]+)"[^>]*style:family="table-column"[^>]*>(.*?)</style:style>""",
                RegexOption.DOT_MATCHES_ALL
            )
            for (csm in colStyleRe.findAll(xml)) {
                val name = csm.groupValues[1]
                val widthStr = Regex("""style:column-width="([^"]+)"""").find(csm.groupValues[2])?.groupValues?.get(1) ?: continue
                val w = odtParseLength(widthStr)
                if (w > 0f) colStyleWidths[name] = w
            }

            // Parse table-cell styles for border info (count bordered sides per style)
            val cellStyleBorderSides = mutableMapOf<String, Int>()
            val cellStyleRe = Regex(
                """<style:style\s[^>]*style:name="([^"]+)"[^>]*style:family="table-cell"[^>]*>(.*?)</style:style>""",
                RegexOption.DOT_MATCHES_ALL
            )
            val cellStyleRe2 = Regex(
                """<style:style\s[^>]*style:family="table-cell"[^>]*style:name="([^"]+)"[^>]*>(.*?)</style:style>""",
                RegexOption.DOT_MATCHES_ALL
            )
            for (csm in (cellStyleRe.findAll(xml) + cellStyleRe2.findAll(xml))) {
                val name = csm.groupValues[1]
                if (name in cellStyleBorderSides) continue
                val props = csm.groupValues[2]
                // Shorthand fo:border applies to all 4 sides
                val shorthand = Regex("""fo:border="([^"]+)"""").find(props)?.groupValues?.get(1)
                val sides = if (shorthand != null && shorthand != "none") {
                    4
                } else {
                    // Count individual non-none borders
                    Regex("""fo:border-(top|left|bottom|right)="(?!none)[^"]+"""")
                        .findAll(props).count()
                }
                cellStyleBorderSides[name] = sides
            }

            for (tm in tableMatches) {
                val tableXml = tm.value
                // Extract column widths from <table:table-column> elements
                val colDefs = Regex("""<table:table-column\b([^/]*)/?>""").findAll(tableXml).toList()
                val odtColWidths = mutableListOf<Float>()
                for (cd in colDefs) {
                    val styleName = Regex("""table:style-name="([^"]+)"""").find(cd.groupValues[1])?.groupValues?.get(1)
                    val repeat = Regex("""table:number-columns-repeated="(\d+)"""").find(cd.groupValues[1])
                        ?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    val w = styleName?.let { colStyleWidths[it] } ?: 1f
                    repeat(repeat) { odtColWidths.add(w) }
                }
                val tableWidths = if (odtColWidths.isNotEmpty() && odtColWidths.any { it > 1f }) odtColWidths else null

                // Table has grid borders if any cell has >= 2 bordered sides
                // (layout tables use 0-1 sides for underline effects)
                val tableCellStyles = Regex("""<table:table-cell\b[^>]*table:style-name="([^"]+)"""")
                    .findAll(tableXml).map { it.groupValues[1] }.toList()
                val tableHasBorders = tableCellStyles.isEmpty() ||
                    tableCellStyles.any { (cellStyleBorderSides[it] ?: 0) >= 2 }

                for (rm in rowRe.findAll(tableXml)) {
                    val cells = mutableListOf<List<DocSpan>>()
                    val cellGridSpans = mutableListOf<Int>()
                    for (cm in cellRe.findAll(rm.value)) {
                        // Parse horizontal column span
                        val colSpan = Regex("""table:number-columns-spanned="(\d+)"""")
                            .find(cm.value)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                        cellGridSpans.add(colSpan)

                        val cellSpans = mutableListOf<DocSpan>()
                        val pElems = Regex(
                            """<text:(?:h|p)\b[^>]*>(.*?)</text:(?:h|p)>""",
                            RegexOption.DOT_MATCHES_ALL
                        )
                        for (pe in pElems.findAll(cm.value)) {
                            if (cellSpans.isNotEmpty()) cellSpans += DocSpan(" ")
                            cellSpans.addAll(odtSpans(odtNormalize(pe.groupValues[1]), textStyles))
                        }
                        cells.add(cellSpans)
                    }
                    if (cells.isNotEmpty()) {
                        elements.add(DocElement(
                            tm.range.first + rm.range.first,
                            DocParagraph(
                                spans = emptyList(), isTable = true,
                                tableCells = cells,
                                tableColWidths = tableWidths,
                                tableHasBorders = tableHasBorders,
                                tableCellGridSpans = cellGridSpans.takeIf { it.any { g -> g > 1 } }
                            )
                        ))
                    }
                }
            }

            return elements.sortedBy { it.pos }.map { it.paragraph }
        }
    }

    private fun odtParseStyle(body: String): OdtStyle {
        val bold = body.contains("fo:font-weight=\"bold\"")
        val italic = body.contains("fo:font-style=\"italic\"")
        val underline = body.contains("style:text-underline-style=\"solid\"")
        val strike = body.contains("style:text-line-through-style=\"solid\"")
        val colorHex = Regex("""fo:color="#([0-9A-Fa-f]{6})"""").find(body)?.groupValues?.get(1)
        val color = if (colorHex != null && colorHex != "000000") (0xFF000000 or colorHex.toLong(16)) else 0L
        val fs = Regex("""fo:font-size="([\d.]+)pt"""").find(body)?.groupValues?.get(1)?.toFloatOrNull()
        return OdtStyle(bold, italic, underline, strike, color, fs ?: 0f)
    }

    /** Parse ODT length value (e.g. "5.5cm", "2in", "50mm") to a relative float */
    private fun odtParseLength(value: String): Float {
        val num = Regex("""([\d.]+)""").find(value)?.groupValues?.get(1)?.toFloatOrNull() ?: return 0f
        return when {
            value.endsWith("cm") -> num * 100
            value.endsWith("mm") -> num * 10
            value.endsWith("in") -> num * 254
            value.endsWith("pt") -> num * 3.5f
            else -> num
        }
    }

    /** Replace ODT special elements with actual characters */
    private fun odtNormalize(content: String): String {
        return content
            .replace(Regex("""<text:s text:c="(\d+)"\s*/>""")) { m ->
                " ".repeat(m.groupValues[1].toIntOrNull() ?: 1)
            }
            .replace(Regex("""<text:s\s*/>"""), " ")
            .replace(Regex("""<text:tab\s*/>"""), "\t")
            .replace(Regex("""<text:line-break\s*/>"""), "\n")
    }

    private fun odtSpans(
        content: String,
        textStyles: Map<String, OdtStyle>,
        defStyle: OdtStyle? = null
    ): List<DocSpan> {
        val spans = mutableListOf<DocSpan>()
        val normalized = odtNormalize(content)

        // Match <text:a> (hyperlinks) and <text:span> elements
        val elemRe = Regex(
            """<text:a\b[^>]*xlink:href="([^"]+)"[^>]*>(.*?)</text:a>|<text:span\s+text:style-name="([^"]+)">(.*?)</text:span>""",
            RegexOption.DOT_MATCHES_ALL
        )

        var last = 0
        for (m in elemRe.findAll(normalized)) {
            // Plain text before this element
            val before = stripTags(normalized.substring(last, m.range.first))
            if (before.isNotEmpty()) {
                spans += DocSpan(
                    text = decodeEntities(before),
                    bold = defStyle?.bold ?: false,
                    italic = defStyle?.italic ?: false,
                    underline = defStyle?.underline ?: false,
                    strikethrough = defStyle?.strike ?: false,
                    fontSize = defStyle?.fontSize ?: 0f,
                    textColor = defStyle?.color ?: 0L
                )
            }

            val href = m.groupValues[1].takeIf { it.isNotEmpty() }
            val linkText = m.groupValues[2].takeIf { it.isNotEmpty() }
            val spanStyle = m.groupValues[3].takeIf { it.isNotEmpty() }
            val spanText = m.groupValues[4].takeIf { it.isNotEmpty() }

            if (href != null && linkText != null) {
                // Hyperlink
                val text = stripTags(linkText)
                if (text.isNotEmpty()) {
                    spans += DocSpan(
                        text = decodeEntities(text),
                        underline = true,
                        textColor = 0xFF1A73E8,
                        hyperlink = href
                    )
                }
            } else if (spanStyle != null) {
                // Styled span
                val text = stripTags(spanText ?: "")
                if (text.isNotEmpty()) {
                    val s = textStyles[spanStyle]
                    spans += DocSpan(
                        text = decodeEntities(text),
                        bold = s?.bold ?: defStyle?.bold ?: false,
                        italic = s?.italic ?: defStyle?.italic ?: false,
                        underline = s?.underline ?: defStyle?.underline ?: false,
                        strikethrough = s?.strike ?: defStyle?.strike ?: false,
                        fontSize = s?.fontSize ?: defStyle?.fontSize ?: 0f,
                        textColor = s?.color ?: defStyle?.color ?: 0L
                    )
                }
            }
            last = m.range.last + 1
        }

        // Remaining text after last element
        val after = stripTags(normalized.substring(last))
        if (after.isNotEmpty()) {
            spans += DocSpan(
                text = decodeEntities(after),
                bold = defStyle?.bold ?: false,
                italic = defStyle?.italic ?: false,
                underline = defStyle?.underline ?: false,
                strikethrough = defStyle?.strike ?: false,
                fontSize = defStyle?.fontSize ?: 0f,
                textColor = defStyle?.color ?: 0L
            )
        }

        return spans
    }

    // ======================== RTF ========================

    private fun parseRtf(file: File): List<DocParagraph> {
        val rawBytes = file.readBytes().let { if (it.size > 2_000_000) it.copyOf(2_000_000) else it }
        val rawLatin = String(rawBytes, Charsets.ISO_8859_1)

        // Detect code page for \'XX decoding
        val cpMatch = Regex("""\\ansicpg(\d+)""").find(rawLatin)
        val rtfCharset = when (cpMatch?.groupValues?.get(1)?.toIntOrNull()) {
            1251 -> charset("windows-1251")
            1250 -> charset("windows-1250")
            1252 -> charset("windows-1252")
            1253 -> charset("windows-1253")
            1254 -> charset("windows-1254")
            1256 -> charset("windows-1256")
            else -> null // fallback: treat \'XX as latin1
        }

        val result = mutableListOf<DocParagraph>()
        val parts = rawLatin.split(Regex("""\\par[d]?\s?"""))
        for (part in parts) {
            val spans = rtfSpans(part, rtfCharset)
            if (spans.isNotEmpty() && spans.any { it.text.isNotBlank() }) {
                result.add(DocParagraph(spans))
            }
        }
        return result
    }

    private fun rtfSpans(text: String, rtfCharset: java.nio.charset.Charset?): List<DocSpan> {
        val spans = mutableListOf<DocSpan>()
        var bold = false
        var italic = false
        var strike = false
        var fontSize = 0f
        val buf = StringBuilder()
        var i = 0

        fun flush() {
            if (buf.isNotEmpty()) {
                spans += DocSpan(buf.toString(), bold, italic, strikethrough = strike, fontSize = fontSize)
                buf.clear()
            }
        }

        while (i < text.length) {
            when {
                text[i] == '\\' && i + 1 < text.length -> {
                    if (text[i + 1] in "\\{}") {
                        buf.append(text[i + 1]); i += 2; continue
                    }
                    if (text[i + 1] == '\'' && i + 3 < text.length) {
                        val hex = text.substring(i + 2, i + 4)
                        val byteVal = hex.toIntOrNull(16)
                        if (byteVal != null) {
                            if (rtfCharset != null) {
                                buf.append(String(byteArrayOf(byteVal.toByte()), rtfCharset))
                            } else {
                                buf.append(byteVal.toChar())
                            }
                        }
                        i += 4; continue
                    }
                    val cw = Regex("""\\([a-z]+)(-?\d*)\s?""").find(text, i)
                    if (cw != null && cw.range.first == i) {
                        val word = cw.groupValues[1]
                        val param = cw.groupValues[2]
                        when (word) {
                            "b" -> { flush(); bold = param != "0" }
                            "i" -> { flush(); italic = param != "0" }
                            "strike" -> { flush(); strike = param != "0" }
                            "fs" -> {
                                flush()
                                val halfPt = param.toFloatOrNull()
                                fontSize = if (halfPt != null) (halfPt / 2f).coerceIn(8f, 72f) else 0f
                            }
                        }
                        i = cw.range.last + 1
                    } else {
                        i++
                    }
                }
                text[i] == '{' || text[i] == '}' -> i++
                text[i] == '\n' || text[i] == '\r' -> i++
                else -> { buf.append(text[i]); i++ }
            }
        }
        flush()
        return spans
    }

    // ======================== FB2 ========================

    private fun parseFb2(file: File): List<DocParagraph> {
        return parseFb2Internal(file.readBytes())
    }

    private fun parseFb2Internal(rawBytes: ByteArray): List<DocParagraph> {
        val charset = detectFb2Charset(rawBytes)
        val raw = String(rawBytes, charset)

        val bodyMatch = Regex("""<body[^>]*>(.*)</body>""", RegexOption.DOT_MATCHES_ALL).find(raw)
        val body = bodyMatch?.groupValues?.get(1) ?: raw
        val result = mutableListOf<DocParagraph>()
        val pRe = Regex("""<p>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)

        // Extract base64-encoded images: <binary id="..." ...>base64</binary>
        val binaries = mutableMapOf<String, ByteArray>()
        val binRe = Regex("""<binary\s+[^>]*id="([^"]+)"[^>]*>(.*?)</binary>""", RegexOption.DOT_MATCHES_ALL)
        // Also match id before other attrs
        val binRe2 = Regex("""<binary\s+id="([^"]+)"[^>]*>(.*?)</binary>""", RegexOption.DOT_MATCHES_ALL)
        for (re in listOf(binRe, binRe2)) {
            for (m in re.findAll(raw)) {
                try {
                    val id = m.groupValues[1]
                    if (id in binaries) continue
                    val b64 = m.groupValues[2].replace(Regex("\\s"), "")
                    if (b64.length > 10) {
                        binaries[id] = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                    }
                } catch (_: Exception) { }
            }
        }

        // ── Cover image from <description><title-info><coverpage> ──
        val coverHref = Regex(
            """<coverpage>.*?href="[#]?([^"]+)".*?</coverpage>""",
            RegexOption.DOT_MATCHES_ALL
        ).find(raw)?.groupValues?.get(1)
        val coverData = coverHref?.let { binaries[it] }
        if (coverData != null) {
            result += DocParagraph(spans = emptyList(), imageData = coverData)
        }

        // ── Annotation from <description><title-info><annotation> ──
        val annotationMatch = Regex(
            """<annotation>(.*?)</annotation>""",
            RegexOption.DOT_MATCHES_ALL
        ).find(raw)
        if (annotationMatch != null) {
            val annotationBody = annotationMatch.groupValues[1]
            for (ap in pRe.findAll(annotationBody)) {
                val spans = fb2Spans(ap.groupValues[1]).map { it.copy(italic = true) }
                if (spans.isNotEmpty()) {
                    result += DocParagraph(spans)
                }
            }
            // Separator after annotation
            result += DocParagraph(spans = listOf(DocSpan("———")), alignment = ParaAlignment.CENTER)
        }

        val elements = Regex(
            """<title>(.*?)</title>|<image\s[^>]*/?>|<p>(.*?)</p>""",
            RegexOption.DOT_MATCHES_ALL
        )

        for (m in elements.findAll(body)) {
            val titleContent = m.groups[1]?.value
            val pContent = m.groups[2]?.value

            if (titleContent != null) {
                for (tp in pRe.findAll(titleContent)) {
                    val spans = fb2Spans(tp.groupValues[1])
                    if (spans.isNotEmpty()) result += DocParagraph(spans, headingLevel = 2)
                }
            } else if (m.value.startsWith("<image")) {
                val href = Regex("""(?:l:|xlink:)?href="[#]?([^"]+)"""").find(m.value)?.groupValues?.get(1)
                val imgData = href?.let { binaries[it] }
                if (imgData != null) {
                    result += DocParagraph(spans = emptyList(), imageData = imgData)
                }
            } else if (pContent != null) {
                // Check for inline images in paragraph
                val inlineImg = Regex("""<image\s[^>]*(?:l:|xlink:)?href="[#]?([^"]+)"[^>]*/?>""").find(pContent)
                if (inlineImg != null) {
                    val imgData = binaries[inlineImg.groupValues[1]]
                    if (imgData != null) {
                        result += DocParagraph(spans = emptyList(), imageData = imgData)
                    }
                }
                val textContent = pContent.replace(Regex("""<image\s[^>]*/?>"""), "")
                val spans = fb2Spans(textContent)
                if (spans.isNotEmpty()) result += DocParagraph(spans)
            }
        }
        return result
    }

    /** Detect charset from FB2 XML declaration */
    private fun detectFb2Charset(bytes: ByteArray): java.nio.charset.Charset {
        // UTF-8 BOM
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return Charsets.UTF_8
        }
        // Read first 200 bytes as ASCII to find encoding declaration
        val header = String(bytes, 0, minOf(200, bytes.size), Charsets.ISO_8859_1)
        val enc = Regex("""encoding=["\']([^"\']+)["\']""").find(header)?.groupValues?.get(1)?.trim()?.lowercase()
        if (enc != null) {
            return try {
                java.nio.charset.Charset.forName(enc)
            } catch (_: Exception) { Charsets.UTF_8 }
        }
        return Charsets.UTF_8
    }

    private fun fb2Spans(content: String): List<DocSpan> {
        val spans = mutableListOf<DocSpan>()
        val tagRe = Regex("""<(emphasis|strong|strikethrough|sup|sub|a\b[^>]*)>(.*?)</(?:emphasis|strong|strikethrough|sup|sub|a)>""", RegexOption.DOT_MATCHES_ALL)
        var last = 0
        for (m in tagRe.findAll(content)) {
            val before = stripTags(content.substring(last, m.range.first))
            if (before.isNotBlank()) spans += DocSpan(decodeEntities(before))

            val tag = m.groupValues[1]
            val text = stripTags(m.groupValues[2])
            if (text.isNotBlank()) {
                val href = if (tag.startsWith("a")) {
                    Regex("""xlink:href="([^"]+)"""").find(tag)?.groupValues?.get(1)
                        ?: Regex("""l:href="([^"]+)"""").find(tag)?.groupValues?.get(1)
                } else null

                spans += DocSpan(
                    decodeEntities(text),
                    bold = tag == "strong",
                    italic = tag == "emphasis",
                    strikethrough = tag == "strikethrough",
                    verticalAlign = when {
                        tag == "sup" -> VerticalAlign.SUPERSCRIPT
                        tag == "sub" -> VerticalAlign.SUBSCRIPT
                        else -> VerticalAlign.NORMAL
                    },
                    hyperlink = href,
                    underline = href != null,
                    textColor = if (href != null) 0xFF1A73E8 else 0L
                )
            }
            last = m.range.last + 1
        }
        val after = stripTags(content.substring(last))
        if (after.isNotBlank()) spans += DocSpan(decodeEntities(after))
        return spans
    }

    // ======================== XLS (Apache POI HSSF) ========================

    @Suppress("DEPRECATION")
    private fun parseXls(file: File): List<DocParagraph> {
        try {
            file.inputStream().use { input ->
                val workbook = HSSFWorkbook(input)
                val result = mutableListOf<DocParagraph>()
                for (sheetIdx in 0 until workbook.numberOfSheets) {
                    val sheet = workbook.getSheetAt(sheetIdx)
                    val sheetName = workbook.getSheetName(sheetIdx)
                    if (workbook.numberOfSheets > 1) {
                        result.add(DocParagraph(
                            spans = listOf(DocSpan(sheetName, bold = true)),
                            headingLevel = 3
                        ))
                    }
                    for (rowIdx in 0..sheet.lastRowNum) {
                        val row = sheet.getRow(rowIdx) ?: continue
                        val cells = mutableListOf<List<DocSpan>>()
                        val lastCell = row.lastCellNum.toInt()
                        for (cellIdx in 0 until lastCell) {
                            val cell = row.getCell(cellIdx)
                            val rawText = cell?.toString()?.trim() ?: ""
                            val text = if (rawText.endsWith(".0")) rawText.dropLast(2) else rawText
                            val bold = try {
                                val font = workbook.getFontAt(cell?.cellStyle?.fontIndex?.toInt() ?: 0)
                                font.bold
                            } catch (_: Exception) { false }
                            cells.add(listOf(DocSpan(text, bold = bold)))
                        }
                        if (cells.isNotEmpty() && cells.any { it.any { s -> s.text.isNotBlank() } }) {
                            result.add(DocParagraph(
                                spans = emptyList(), isTable = true, tableCells = cells
                            ))
                        }
                    }
                }
                workbook.close()
                return result
            }
        } catch (_: org.apache.poi.poifs.filesystem.OfficeXmlFileException) {
            return parseXlsx(file)
        } catch (_: Throwable) {
            return emptyList()
        }
    }

    // ======================== XLSX (ZIP-based) ========================

    private fun parseXlsx(file: File): List<DocParagraph> {
        ZipFile(file).use { zip ->
            // Read shared strings
            val sharedStrings = mutableListOf<String>()
            val ssEntry = zip.getEntry("xl/sharedStrings.xml")
            if (ssEntry != null) {
                val ssXml = zip.getInputStream(ssEntry).bufferedReader().readText()
                val siRe = Regex("""<si>(.*?)</si>""", RegexOption.DOT_MATCHES_ALL)
                for (m in siRe.findAll(ssXml)) {
                    val tRe = Regex("""<t[^>]*>(.*?)</t>""", RegexOption.DOT_MATCHES_ALL)
                    val text = tRe.findAll(m.groupValues[1]).map { it.groupValues[1] }.joinToString("")
                    sharedStrings.add(decodeEntities(text))
                }
            }

            // Read sheet names from workbook.xml
            val sheetNames = mutableListOf<String>()
            val wbEntry = zip.getEntry("xl/workbook.xml")
            if (wbEntry != null) {
                val wbXml = zip.getInputStream(wbEntry).bufferedReader().readText()
                val sheetRe = Regex("""<sheet\s+name="([^"]+)"""")
                for (m in sheetRe.findAll(wbXml)) {
                    sheetNames.add(decodeEntities(m.groupValues[1]))
                }
            }

            // Parse styles.xml for per-cell border info
            // bitmask: 1=left, 2=right, 4=top, 8=bottom, 15=all
            val styleBorderMasks = mutableListOf<Int>()
            val xfBorderIds = mutableListOf<Int>()
            val stylesEntry = zip.getEntry("xl/styles.xml")
            if (stylesEntry != null) {
                val stylesXml = zip.getInputStream(stylesEntry).bufferedReader().readText()
                val bordersContent = Regex("""<borders\b[^>]*>(.*?)</borders>""", RegexOption.DOT_MATCHES_ALL)
                    .find(stylesXml)?.groupValues?.get(1) ?: ""
                for (bm in Regex("""<border\b[^>]*>.*?</border>""", RegexOption.DOT_MATCHES_ALL).findAll(bordersContent)) {
                    val bXml = bm.value
                    var mask = 0
                    if (Regex("""<left\s+style=""").containsMatchIn(bXml)) mask = mask or 1
                    if (Regex("""<right\s+style=""").containsMatchIn(bXml)) mask = mask or 2
                    if (Regex("""<top\s+style=""").containsMatchIn(bXml)) mask = mask or 4
                    if (Regex("""<bottom\s+style=""").containsMatchIn(bXml)) mask = mask or 8
                    styleBorderMasks.add(mask)
                }
                val cellXfsContent = Regex("""<cellXfs\b[^>]*>(.*?)</cellXfs>""", RegexOption.DOT_MATCHES_ALL)
                    .find(stylesXml)?.groupValues?.get(1) ?: ""
                for (xm in Regex("""<xf\b[^>]*""").findAll(cellXfsContent)) {
                    val bid = Regex("""borderId="(\d+)""").find(xm.value)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    xfBorderIds.add(bid)
                }
            }
            fun cellBorderMask(styleIdx: Int): Int {
                val borderId = xfBorderIds.getOrNull(styleIdx) ?: 0
                return styleBorderMasks.getOrNull(borderId) ?: 15
            }

            val result = mutableListOf<DocParagraph>()
            var sheetIndex = 1
            while (true) {
                val sheetEntry = zip.getEntry("xl/worksheets/sheet$sheetIndex.xml") ?: break
                val sheetXml = zip.getInputStream(sheetEntry).bufferedReader().readText()

                if (sheetNames.size > 1) {
                    val name = sheetNames.getOrNull(sheetIndex - 1) ?: "Sheet $sheetIndex"
                    result.add(DocParagraph(
                        spans = listOf(DocSpan(name, bold = true)),
                        headingLevel = 3
                    ))
                }

                // Parse column widths from <cols> section
                val xlsxColWidths = mutableMapOf<Int, Float>()
                val colsSection = extractTagContent(sheetXml, "cols")
                if (colsSection.isNotEmpty()) {
                    val colRe = Regex("""<col\b[^/]*/?>""")
                    for (cm in colRe.findAll(colsSection)) {
                        val tag = cm.value
                        val minCol = Regex("""min="(\d+)"""").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: continue
                        val maxCol = Regex("""max="(\d+)"""").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: minCol
                        val width = Regex("""width="([\d.]+)"""").find(tag)?.groupValues?.get(1)?.toFloatOrNull() ?: continue
                        if (maxCol - minCol < 100) {
                            for (c in minCol..maxCol) {
                                xlsxColWidths[c - 1] = width
                            }
                        }
                    }
                }

                // First pass: parse all rows (both self-closing and regular cells)
                val rowRe = Regex("""<row\b[^>]*>(.*?)</row>""", RegexOption.DOT_MATCHES_ALL)
                val cellRe = Regex("""<c\b([^>]*?)(?:>(.*?)</c>|/>)""", RegexOption.DOT_MATCHES_ALL)
                data class ParsedRow(
                    val cellMap: Map<Int, List<DocSpan>>,
                    val borderMap: Map<Int, Int>,
                    val maxCol: Int
                )
                val parsedRows = mutableListOf<ParsedRow>()
                var globalMinCol = Int.MAX_VALUE
                var globalMaxCol = -1

                for (rowMatch in rowRe.findAll(sheetXml)) {
                    val cellMap = mutableMapOf<Int, List<DocSpan>>()
                    val borderMap = mutableMapOf<Int, Int>()
                    var maxCol = -1
                    for (cellMatch in cellRe.findAll(rowMatch.groupValues[1])) {
                        val attrs = cellMatch.groupValues[1]
                        val content = cellMatch.groupValues[2] // empty for self-closing

                        val colRef = Regex("""r="([A-Z]+)\d+""").find(attrs)?.groupValues?.get(1)
                        val colIdx = if (colRef != null) xlsxColIndex(colRef)
                            else maxOf(cellMap.size, borderMap.size)

                        // Border info from style
                        val styleIdx = Regex("""s="(\d+)""").find(attrs)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        borderMap[colIdx] = cellBorderMask(styleIdx)

                        // Content (only for non-self-closing cells with content)
                        if (content.isNotEmpty()) {
                            val value = Regex("""<v>(.*?)</v>""").find(content)?.groupValues?.get(1) ?: ""
                            val isShared = attrs.contains("""t="s"""")
                            val isInline = attrs.contains("""t="inlineStr"""")
                            val text = when {
                                isShared -> {
                                    val idx = value.toIntOrNull() ?: 0
                                    sharedStrings.getOrNull(idx) ?: value
                                }
                                isInline -> {
                                    Regex("""<t[^>]*>(.*?)</t>""").find(content)?.groupValues?.get(1)
                                        ?.let { decodeEntities(it) } ?: ""
                                }
                                else -> decodeEntities(value)
                            }
                            val rawText = text.trim()
                            if (rawText.isNotEmpty()) {
                                cellMap[colIdx] = listOf(DocSpan(
                                    if (rawText.endsWith(".0")) rawText.dropLast(2) else rawText
                                ))
                            }
                        }
                        maxCol = maxOf(maxCol, colIdx)
                    }
                    if (maxCol >= 0) {
                        val minCol = minOf(
                            cellMap.keys.minOrNull() ?: Int.MAX_VALUE,
                            borderMap.keys.minOrNull() ?: Int.MAX_VALUE
                        )
                        if (minCol != Int.MAX_VALUE) globalMinCol = minOf(globalMinCol, minCol)
                        globalMaxCol = maxOf(globalMaxCol, maxCol)
                        parsedRows.add(ParsedRow(cellMap, borderMap, maxCol))
                    }
                }

                // Second pass: detect title rows and build DocParagraphs
                if (globalMinCol == Int.MAX_VALUE) globalMinCol = 0
                for (row in parsedRows) {
                    // Title row: all cells have non-full-grid borders (no cell has mask==15)
                    val allNonGrid = (globalMinCol..globalMaxCol).all { col ->
                        (row.borderMap[col] ?: 0) != 15
                    }
                    val hasContent = row.cellMap.values.any { spans -> spans.any { it.text.isNotBlank() } }

                    if (allNonGrid && hasContent) {
                        val text = row.cellMap.values
                            .firstOrNull { spans -> spans.any { it.text.isNotBlank() } }
                            ?.joinToString("") { it.text }?.trim() ?: ""
                        if (text.isNotBlank()) {
                            result.add(DocParagraph(
                                spans = listOf(DocSpan(text, bold = true)),
                                alignment = ParaAlignment.CENTER
                            ))
                        }
                        continue
                    }

                    val cells = (globalMinCol..globalMaxCol).map {
                        row.cellMap[it] ?: listOf(DocSpan(""))
                    }
                    val cellBorders = (globalMinCol..globalMaxCol).map {
                        row.borderMap[it] ?: 15
                    }
                    if (cells.any { it.any { s -> s.text.isNotBlank() } } || cellBorders.any { it != 0 }) {
                        val colWidths = if (xlsxColWidths.isNotEmpty()) {
                            (globalMinCol..globalMaxCol).map { xlsxColWidths[it] ?: 9f }
                        } else null
                        result.add(DocParagraph(
                            spans = emptyList(), isTable = true,
                            tableCells = cells,
                            tableColWidths = colWidths,
                            tableCellBorders = cellBorders
                        ))
                    }
                }
                sheetIndex++
            }
            return result
        }
    }

    private fun xlsxColIndex(ref: String): Int {
        var idx = 0
        for (c in ref) {
            if (c.isLetter()) idx = idx * 26 + (c.uppercaseChar() - 'A' + 1)
        }
        return idx - 1
    }

    // ======================== CSV / TSV ========================

    private fun parseCsv(file: File): List<DocParagraph> {
        val result = mutableListOf<DocParagraph>()
        val lines = file.readLines()
        for (line in lines) {
            if (line.isBlank()) continue
            val cells = parseCsvLine(line).map { listOf(DocSpan(it.trim())) }
            if (cells.isNotEmpty()) {
                result.add(DocParagraph(spans = emptyList(), isTable = true, tableCells = cells))
            }
        }
        return result
    }

    private fun parseCsvLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val buf = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && !inQuotes -> inQuotes = true
                c == '"' && inQuotes -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        buf.append('"'); i++
                    } else {
                        inQuotes = false
                    }
                }
                (c == ',' || c == ';' || c == '\t') && !inQuotes -> {
                    cells.add(buf.toString()); buf.clear()
                }
                else -> buf.append(c)
            }
            i++
        }
        cells.add(buf.toString())
        return cells
    }

    // ======================== Helpers ========================

    private fun extractTagContent(xml: String, tag: String): String {
        val m = Regex("""<$tag>(.*?)</$tag>""", RegexOption.DOT_MATCHES_ALL).find(xml)
        return m?.groupValues?.get(1) ?: ""
    }

    private fun stripTags(s: String) = s.replace(Regex("<[^>]+>"), "")

    private fun decodeEntities(text: String): String = text
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#160;", " ")
        .replace(Regex("&#(\\d+);")) { m ->
            val c = m.groupValues[1].toIntOrNull()
            if (c != null) c.toChar().toString() else ""
        }
}
