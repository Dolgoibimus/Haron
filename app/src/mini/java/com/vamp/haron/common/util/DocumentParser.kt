package com.vamp.haron.common.util

import java.io.File

/**
 * Mini variant — no POI (no .doc/.xls parsing).
 * DOCX, ODT, RTF, FB2, XLSX, CSV still work (XML/ZIP based, no external libs).
 * DOC and XLS return empty list.
 */
object DocumentParser {

    fun parse(file: File): List<DocParagraph> {
        val nameLc = file.name.lowercase()
        if (nameLc.endsWith(".fb2.zip") || (nameLc.endsWith(".zip") && nameLc.contains(".fb2"))) {
            return parseFb2FromZip(file)
        }
        return when (file.extension.lowercase()) {
            "docx" -> parseDocx(file)
            "odt" -> parseOdt(file)
            "rtf" -> parseRtf(file)
            "fb2" -> parseFb2(file)
            "xlsx" -> parseXlsx(file)
            "csv", "tsv" -> parseCsv(file)
            // doc, xls — not supported in mini (no Apache POI)
            else -> emptyList()
        }
    }

    fun parseFb2FromZip(zipFile: File): List<DocParagraph> {
        java.util.zip.ZipFile(zipFile).use { zip ->
            val fb2Entry = zip.entries().toList().firstOrNull { entry ->
                entry.name.lowercase().endsWith(".fb2")
            } ?: return emptyList()
            val rawBytes = zip.getInputStream(fb2Entry).readBytes()
            return parseFb2Internal(rawBytes)
        }
    }

    // Stub: full implementations of parseDocx/parseOdt/parseRtf/parseFb2/parseXlsx/parseCsv
    // are too large to duplicate here. They will be loaded from notMini source set for full/play.
    // For mini, return simple text extraction.

    private fun parseDocx(file: File): List<DocParagraph> {
        return try {
            java.util.zip.ZipFile(file).use { zip ->
                val entry = zip.getEntry("word/document.xml") ?: return emptyList()
                val xml = zip.getInputStream(entry).bufferedReader().readText()
                xml.split("</w:p>").mapNotNull { para ->
                    val text = Regex("<w:t[^>]*>([^<]*)</w:t>").findAll(para)
                        .map { it.groupValues[1] }.joinToString("")
                    if (text.isNotBlank()) DocParagraph(spans = listOf(DocSpan(text))) else null
                }
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun parseOdt(file: File): List<DocParagraph> {
        return try {
            java.util.zip.ZipFile(file).use { zip ->
                val entry = zip.getEntry("content.xml") ?: return emptyList()
                val xml = zip.getInputStream(entry).bufferedReader().readText()
                xml.split("</text:p>").mapNotNull { para ->
                    val text = para.replace(Regex("<[^>]+>"), "").trim()
                    if (text.isNotBlank()) DocParagraph(spans = listOf(DocSpan(text))) else null
                }
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun parseRtf(file: File): List<DocParagraph> {
        return try {
            val raw = file.readText(Charsets.ISO_8859_1)
            val text = raw
                .replace(Regex("\\\\[a-z]+[\\d]*\\s?"), " ")
                .replace(Regex("\\\\[{}\\\\]"), "")
                .replace(Regex("[{}]"), "")
                .replace(Regex("\\s+"), " ").trim()
            text.split("\n").filter { it.isNotBlank() }.map {
                DocParagraph(spans = listOf(DocSpan(it.trim())))
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun parseFb2(file: File): List<DocParagraph> = parseFb2Internal(file.readBytes())

    private fun parseFb2Internal(rawBytes: ByteArray): List<DocParagraph> {
        return try {
            val xml = String(rawBytes, Charsets.UTF_8)
            val bodyMatch = Regex("<body[^>]*>(.*)</body>", RegexOption.DOT_MATCHES_ALL).find(xml)
            val body = bodyMatch?.groupValues?.get(1) ?: return emptyList()
            body.split("</p>").mapNotNull { para ->
                val text = para.replace(Regex("<[^>]+>"), "").trim()
                    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                    .replace("&quot;", "\"").replace("&apos;", "'")
                if (text.isNotBlank()) DocParagraph(spans = listOf(DocSpan(text))) else null
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun parseXlsx(file: File): List<DocParagraph> {
        return try {
            java.util.zip.ZipFile(file).use { zip ->
                // Simple: extract shared strings + sheet data
                val ssEntry = zip.getEntry("xl/sharedStrings.xml")
                val strings = mutableListOf<String>()
                if (ssEntry != null) {
                    val ssXml = zip.getInputStream(ssEntry).bufferedReader().readText()
                    Regex("<t[^>]*>([^<]*)</t>").findAll(ssXml).forEach { strings.add(it.groupValues[1]) }
                }
                val sheetEntry = zip.getEntry("xl/worksheets/sheet1.xml") ?: return emptyList()
                val sheetXml = zip.getInputStream(sheetEntry).bufferedReader().readText()
                val rows = sheetXml.split("</row>")
                rows.mapNotNull { row ->
                    val cells = Regex("""<c[^>]*(?:t="s"[^>]*)?>.*?<v>(\d+)</v>""").findAll(row)
                        .mapNotNull { strings.getOrNull(it.groupValues[1].toIntOrNull() ?: -1) }
                        .toList()
                    if (cells.isNotEmpty()) DocParagraph(
                        spans = emptyList(), isTable = true,
                        tableCells = cells.map { listOf(DocSpan(it)) }
                    ) else null
                }
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun parseCsv(file: File): List<DocParagraph> {
        return try {
            file.bufferedReader().lineSequence().take(1000).mapNotNull { line ->
                val cells = line.split(",").map { listOf(DocSpan(it.trim().removeSurrounding("\""))) }
                if (cells.isNotEmpty()) DocParagraph(spans = emptyList(), isTable = true, tableCells = cells)
                else null
            }.toList()
        } catch (_: Exception) { emptyList() }
    }
}
