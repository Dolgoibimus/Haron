package com.vamp.haron.common.util

import android.content.Context
import android.graphics.RectF
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File

data class PdfWordPosition(
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

data class PdfMatch(
    val pageIndex: Int,
    val rects: List<RectF>
)

class PdfTextPositionExtractor(context: Context) {

    private val scratchDir: File by lazy {
        File(context.cacheDir, "pdfbox_scratch").also { it.mkdirs() }
    }

    fun findMatches(pdfFile: File, query: String, pageCount: Int): List<PdfMatch> {
        if (query.isBlank()) return emptyList()
        val lowerQuery = query.lowercase()
        val allMatches = mutableListOf<PdfMatch>()
        val memSetting = MemoryUsageSetting.setupTempFileOnly().setTempDir(scratchDir)

        PDDocument.load(pdfFile, memSetting).use { doc ->
            val totalPages = doc.numberOfPages.coerceAtMost(pageCount)
            for (pageIdx in 0 until totalPages) {
                val words = extractWordsFromPage(doc, pageIdx)
                if (words.isEmpty()) continue

                val matches = findMatchesOnPage(words, lowerQuery, pageIdx)
                allMatches.addAll(matches)
            }
        }
        return allMatches
    }

    private fun extractWordsFromPage(doc: PDDocument, pageIndex: Int): List<PdfWordPosition> {
        val words = mutableListOf<PdfWordPosition>()

        val stripper = object : PDFTextStripper() {
            init {
                startPage = pageIndex + 1
                endPage = pageIndex + 1
                sortByPosition = true
            }

            override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
                if (textPositions.isNullOrEmpty()) return

                var wordChars = StringBuilder()
                var wordX = 0f
                var wordY = 0f
                var wordWidth = 0f
                var wordHeight = 0f
                var prevEndX = -1f
                var prevWidthDir = 0f

                for (tp in textPositions) {
                    val ch = tp.unicode ?: continue
                    if (ch.isBlank()) {
                        // Flush current word
                        if (wordChars.isNotEmpty()) {
                            words.add(PdfWordPosition(wordChars.toString(), wordX, wordY, wordWidth, wordHeight))
                            wordChars = StringBuilder()
                        }
                        prevEndX = -1f
                        continue
                    }

                    val charX = tp.xDirAdj
                    val rawH = tp.heightDir
                    // yDirAdj = baseline from top; heightDir often underestimates
                    // Extend rect upward by 15% and add 30% total height padding
                    val charY = tp.yDirAdj - rawH * 1.15f
                    val charW = tp.widthDirAdj
                    val charH = rawH * 1.3f

                    // Check gap between characters
                    if (wordChars.isNotEmpty() && prevEndX >= 0) {
                        val gap = charX - prevEndX
                        if (gap > prevWidthDir * 0.3f) {
                            // Large gap — flush and start new word
                            words.add(PdfWordPosition(wordChars.toString(), wordX, wordY, wordWidth, wordHeight))
                            wordChars = StringBuilder()
                        }
                    }

                    if (wordChars.isEmpty()) {
                        wordX = charX
                        wordY = charY
                        wordHeight = charH
                        wordWidth = 0f
                    }

                    wordChars.append(ch)
                    wordWidth = (charX + charW) - wordX
                    wordHeight = maxOf(wordHeight, charH)
                    // Keep wordY as min (top edge)
                    if (charY < wordY) wordY = charY

                    prevEndX = charX + charW
                    prevWidthDir = charW
                }

                // Flush last word
                if (wordChars.isNotEmpty()) {
                    words.add(PdfWordPosition(wordChars.toString(), wordX, wordY, wordWidth, wordHeight))
                }
            }
        }

        stripper.getText(doc)
        return words
    }

    private fun findMatchesOnPage(
        words: List<PdfWordPosition>,
        lowerQuery: String,
        pageIndex: Int
    ): List<PdfMatch> {
        if (words.isEmpty()) return emptyList()

        // Build concatenated text with space-separated words and track char→word mapping
        val sb = StringBuilder()
        // charToWordIndex[charPos] = index in words list
        val charToWordIndex = mutableListOf<Int>()

        for ((idx, word) in words.withIndex()) {
            if (idx > 0) {
                sb.append(' ')
                charToWordIndex.add(-1) // space char
            }
            for (c in word.text) {
                sb.append(c)
                charToWordIndex.add(idx)
            }
        }

        val fullText = sb.toString().lowercase()
        val matches = mutableListOf<PdfMatch>()

        var searchFrom = 0
        while (searchFrom < fullText.length) {
            val foundAt = fullText.indexOf(lowerQuery, searchFrom)
            if (foundAt < 0) break

            // Determine which words are involved
            val endAt = foundAt + lowerQuery.length - 1
            val involvedWords = mutableSetOf<Int>()
            for (ci in foundAt..endAt.coerceAtMost(charToWordIndex.size - 1)) {
                val wi = charToWordIndex[ci]
                if (wi >= 0) involvedWords.add(wi)
            }

            if (involvedWords.isNotEmpty()) {
                val rects = involvedWords.sorted().map { wi ->
                    val w = words[wi]
                    RectF(w.x, w.y, w.x + w.width, w.y + w.height)
                }
                matches.add(PdfMatch(pageIndex, rects))
            }

            searchFrom = foundAt + lowerQuery.length
        }

        return matches
    }
}
