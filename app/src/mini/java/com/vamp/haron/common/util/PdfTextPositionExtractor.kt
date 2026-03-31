package com.vamp.haron.common.util

import android.content.Context
import android.graphics.RectF
import java.io.File

data class PdfWordPosition(val text: String, val x: Float, val y: Float, val width: Float, val height: Float)
data class PdfMatch(val pageIndex: Int, val rects: List<RectF>)

/** No-op stub for mini variant. PDF text search disabled (no PDFBox). */
class PdfTextPositionExtractor(context: Context) {
    fun findMatches(pdfFile: File, query: String, pageCount: Int): List<PdfMatch> = emptyList()
}
