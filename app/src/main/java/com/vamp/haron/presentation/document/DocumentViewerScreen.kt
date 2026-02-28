package com.vamp.haron.presentation.document

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vamp.haron.R
import com.vamp.haron.data.reading.ReadingPositionManager
import com.vamp.haron.common.util.DocParagraph
import com.vamp.haron.common.util.DocSpan
import com.vamp.haron.common.util.DocumentParser
import com.vamp.haron.common.util.ParaAlignment
import com.vamp.haron.common.util.VerticalAlign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import java.io.File

// ======================== Data model for flat items ========================

private sealed class DocItem {
    data class Para(val paragraph: DocParagraph) : DocItem()
    class Img(val data: ByteArray) : DocItem()
    class TblRow(
        val cells: List<List<DocSpan>>,
        val colWeights: FloatArray,
        val cellAligns: List<ParaAlignment>? = null,
        val cellBgs: List<Long>? = null,
        val cellVMerge: List<Boolean>? = null,
        val cellGridSpans: List<Int>? = null,
        val cellVAligns: List<String>? = null
    ) : DocItem()
}

/**
 * Groups consecutive table rows so they share proportional column weights,
 * then flattens everything into a list of [DocItem] for LazyColumn.
 */
private fun buildDocItems(paragraphs: List<DocParagraph>): List<DocItem> {
    val items = mutableListOf<DocItem>()
    val tableBuffer = mutableListOf<DocParagraph>()

    fun flushTable() {
        if (tableBuffer.isEmpty()) return
        val maxCols = tableBuffer.maxOf { p ->
            val spans = p.tableCellGridSpans
            if (spans != null && spans.isNotEmpty()) spans.sum()
            else p.tableCells?.size ?: 0
        }

        // Prefer document-specified column widths
        val docWidths = tableBuffer.firstNotNullOfOrNull { p ->
            p.tableColWidths?.takeIf { it.isNotEmpty() }
        }

        val colWeights: FloatArray = if (docWidths != null) {
            // Use document widths, pad or trim to match maxCols
            FloatArray(maxCols) { i ->
                if (i < docWidths.size) docWidths[i].coerceAtLeast(1f) else 1f
            }
        } else {
            // Fallback: content-based weights
            val colMaxLen = FloatArray(maxCols) { 1f }
            for (p in tableBuffer) {
                val cells = p.tableCells ?: continue
                for ((idx, cell) in cells.withIndex()) {
                    if (idx < maxCols) {
                        val len = cell.sumOf { it.text.length }.toFloat().coerceAtLeast(1f)
                        colMaxLen[idx] = maxOf(colMaxLen[idx], len)
                    }
                }
            }
            colMaxLen
        }

        for (p in tableBuffer) {
            items.add(DocItem.TblRow(
                cells = p.tableCells ?: emptyList(),
                colWeights = colWeights,
                cellAligns = p.tableCellAligns,
                cellBgs = p.tableCellBgs,
                cellVMerge = p.tableCellVMerge,
                cellGridSpans = p.tableCellGridSpans,
                cellVAligns = p.tableCellVAligns
            ))
        }
        tableBuffer.clear()
    }

    for (para in paragraphs) {
        when {
            para.imageData != null -> {
                flushTable()
                items.add(DocItem.Img(para.imageData))
            }
            para.isTable && para.tableCells != null -> tableBuffer.add(para)
            else -> {
                flushTable()
                items.add(DocItem.Para(para))
            }
        }
    }
    flushTable()
    return items
}

// ======================== Main screen ========================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    filePath: String,
    fileName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var docItems by remember { mutableStateOf<List<DocItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Pre-load saved position (fast DB read finishes before slow document parsing)
    var savedItemIndex by remember { mutableStateOf(0) }
    var savedItemOffset by remember { mutableStateOf(0) }
    var savedZoomScale by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            // Read saved position first (< 1ms)
            val pos = ReadingPositionManager.get(filePath)
            if (pos != null) {
                savedItemIndex = pos.position
                savedItemOffset = pos.positionExtra.toInt()
            }
            val zoom = ReadingPositionManager.get("zoom:$filePath")
            if (zoom != null && zoom.position > 0) {
                savedZoomScale = (zoom.position.toFloat() / 100f).coerceIn(0.5f, 3f)
            }
            // Then parse document (slow)
            try {
                val file = File(filePath)
                val result = DocumentParser.parse(file)
                if (result.isEmpty()) {
                    error = context.getString(R.string.document_read_error)
                } else {
                    docItems = buildDocItems(result)
                }
            } catch (e: Throwable) {
                val msg = e.message ?: ""
                val isEncrypted = e.javaClass.simpleName.contains("Encrypted", ignoreCase = true) ||
                    msg.contains("encrypted", ignoreCase = true) ||
                    msg.contains("password", ignoreCase = true)
                error = if (isEncrypted) context.getString(R.string.doc_encrypted_not_supported)
                    else msg.ifEmpty { context.getString(R.string.document_read_error) }
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                error != null -> Text(
                    text = error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )
                docItems != null -> {
                    val items = docItems!!
                    var textScale by remember { mutableFloatStateOf(savedZoomScale) }
                    val listState = rememberLazyListState(
                        initialFirstVisibleItemIndex = savedItemIndex.coerceIn(0, items.lastIndex),
                        initialFirstVisibleItemScrollOffset = savedItemOffset
                    )

                    // Save reading position + zoom (debounced)
                    LaunchedEffect(filePath) {
                        snapshotFlow {
                            Triple(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, textScale)
                        }.collectLatest { (index, offset, zoom) ->
                            delay(1000)
                            withContext(Dispatchers.IO) {
                                ReadingPositionManager.save(filePath, index, offset.toLong())
                                ReadingPositionManager.save("zoom:$filePath", (zoom * 100).toInt())
                            }
                        }
                    }

                    // Save on exit
                    DisposableEffect(filePath) {
                        onDispose {
                            ReadingPositionManager.saveAsync(
                                filePath,
                                listState.firstVisibleItemIndex,
                                listState.firstVisibleItemScrollOffset.toLong()
                            )
                            ReadingPositionManager.saveAsync("zoom:$filePath", (textScale * 100).toInt())
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    do {
                                        val event = awaitPointerEvent()
                                        if (event.changes.size >= 2) {
                                            val zoom = event.calculateZoom()
                                            textScale = (textScale * zoom).coerceIn(0.5f, 3f)
                                            event.changes.forEach { c -> c.consume() }
                                        }
                                    } while (event.changes.any { c -> c.pressed })
                                }
                            }
                    ) {
                        SelectionContainer {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                items(items.size) { index ->
                                    when (val item = items[index]) {
                                        is DocItem.Para -> RichParagraph(item.paragraph, textScale)
                                        is DocItem.Img -> EmbeddedImage(item.data)
                                        is DocItem.TblRow -> CompactTableRow(
                                            cells = item.cells,
                                            colWeights = item.colWeights,
                                            cellAligns = item.cellAligns,
                                            cellBgs = item.cellBgs,
                                            cellVMerge = item.cellVMerge,
                                            cellGridSpans = item.cellGridSpans,
                                            cellVAligns = item.cellVAligns,
                                            textScale = textScale
                                        )
                                    }
                                }
                                item { Spacer(Modifier.height(32.dp)) }
                            }
                        }

                    }
                }
            }
        }
    }
}

// ======================== Embedded image ========================

@Composable
private fun EmbeddedImage(data: ByteArray) {
    val bitmap = remember(data) {
        try { BitmapFactory.decodeByteArray(data, 0, data.size) } catch (_: Exception) { null }
    }
    if (bitmap != null) {
        val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .padding(vertical = 4.dp),
            contentScale = ContentScale.FillWidth
        )
    }
}

// ======================== Rich paragraph ========================

@Composable
private fun RichParagraph(paragraph: DocParagraph, textScale: Float) {
    val context = LocalContext.current
    val baseStyle = when (paragraph.headingLevel) {
        1 -> MaterialTheme.typography.headlineMedium
        2 -> MaterialTheme.typography.headlineSmall
        3 -> MaterialTheme.typography.titleLarge
        in 4..6 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.bodyMedium
    }
    val scaledFontSize = (baseStyle.fontSize.value * textScale).sp
    val lineH = if (paragraph.lineSpacingMultiplier > 0f)
        (scaledFontSize.value * paragraph.lineSpacingMultiplier).sp
    else scaledFontSize * 1.2f
    val style = baseStyle.copy(
        color = Color.Black,
        fontSize = scaledFontSize,
        lineHeight = lineH
    )
    val isHeading = paragraph.headingLevel > 0

    val textAlign = when (paragraph.alignment) {
        ParaAlignment.CENTER -> TextAlign.Center
        ParaAlignment.RIGHT -> TextAlign.End
        ParaAlignment.JUSTIFY -> TextAlign.Justify
        ParaAlignment.LEFT -> TextAlign.Start
    }

    val bulletPrefix = if (paragraph.listBullet != null) paragraph.listBullet + " " else ""
    val hasLinks = paragraph.spans.any { it.hyperlink != null }

    val annotated = buildAnnotatedString {
        if (bulletPrefix.isNotEmpty()) append(bulletPrefix)
        for (span in paragraph.spans) {
            val weight = when {
                span.bold || isHeading -> FontWeight.Bold
                else -> FontWeight.Normal
            }
            val fontStyle = if (span.italic) FontStyle.Italic else FontStyle.Normal

            val decorations = mutableListOf<TextDecoration>()
            if (span.underline) decorations += TextDecoration.Underline
            if (span.strikethrough) decorations += TextDecoration.LineThrough
            val decoration = if (decorations.isNotEmpty()) TextDecoration.combine(decorations) else TextDecoration.None

            val baseFontSize = if (span.fontSize > 0f) span.fontSize else baseStyle.fontSize.value
            val fontSize = (baseFontSize * textScale).sp
            val textColor = if (span.textColor != 0L) Color(span.textColor.toInt()) else Color.Unspecified
            val bgColor = if (span.highlightColor != 0L) Color(span.highlightColor.toInt()) else Color.Unspecified

            val baselineShift = when (span.verticalAlign) {
                VerticalAlign.SUPERSCRIPT -> BaselineShift.Superscript
                VerticalAlign.SUBSCRIPT -> BaselineShift.Subscript
                VerticalAlign.NORMAL -> BaselineShift.None
            }
            val subSupSize = when (span.verticalAlign) {
                VerticalAlign.SUPERSCRIPT, VerticalAlign.SUBSCRIPT -> (fontSize.value * 0.7f).sp
                VerticalAlign.NORMAL -> fontSize
            }

            if (span.hyperlink != null) pushStringAnnotation(tag = "URL", annotation = span.hyperlink)

            withStyle(
                SpanStyle(
                    fontWeight = weight,
                    fontStyle = fontStyle,
                    textDecoration = decoration,
                    fontSize = subSupSize,
                    color = textColor,
                    background = bgColor,
                    baselineShift = baselineShift,
                    fontFamily = mapFontFamily(span.fontFamily)
                )
            ) {
                // Replace tabs with spaces (tab stops approximation)
                append(span.text.replace("\t", "        "))
            }

            if (span.hyperlink != null) pop()
        }
    }

    val hasMeaningfulContent = annotated.isNotBlank()
    val hasSpacing = paragraph.spacingBeforeDp > 4 || paragraph.spacingAfterDp > 4
    if (hasMeaningfulContent || hasSpacing) {
        val topPad = if (isHeading) (12 * textScale).dp
            else if (paragraph.spacingBeforeDp > 0) (paragraph.spacingBeforeDp * textScale).dp
            else 2.dp
        val bottomPad = if (paragraph.spacingAfterDp > 0) (paragraph.spacingAfterDp * textScale).dp else 2.dp

        val indentStart = (paragraph.indentLeft * textScale).dp
        val bgMod = if (paragraph.backgroundColor != 0L) {
            Modifier.background(Color(paragraph.backgroundColor.toInt()))
        } else Modifier
        val modifier = Modifier
            .fillMaxWidth()
            .then(bgMod)
            .padding(start = indentStart, top = topPad, bottom = bottomPad)

        if (!hasMeaningfulContent) {
            // Empty spacer paragraph — just vertical space
            Spacer(modifier)
        } else if (hasLinks) {
            ClickableText(
                text = annotated,
                style = style.copy(textAlign = textAlign),
                modifier = modifier,
                onClick = { offset ->
                    annotated.getStringAnnotations("URL", offset, offset)
                        .firstOrNull()?.let { annotation ->
                            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))) }
                            catch (_: Exception) { }
                        }
                }
            )
        } else {
            Text(text = annotated, style = style, textAlign = textAlign, modifier = modifier)
        }

        if (isHeading) Spacer(Modifier.height((4 * textScale).dp))

        if (paragraph.hasBorderBottom) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF808080)))
        }
    }
}

// ======================== Compact table row ========================

@Composable
private fun CompactTableRow(
    cells: List<List<DocSpan>>,
    colWeights: FloatArray,
    cellAligns: List<ParaAlignment>? = null,
    cellBgs: List<Long>? = null,
    cellVMerge: List<Boolean>? = null,
    cellGridSpans: List<Int>? = null,
    cellVAligns: List<String>? = null,
    textScale: Float
) {
    val context = LocalContext.current
    val borderColor = Color(0xFFBDBDBD)
    val cellFontSize = (11f * textScale).sp

    Row(modifier = Modifier.fillMaxWidth()) {
        var logicalCol = 0
        for (i in cells.indices) {
            val gridSpan = cellGridSpans?.getOrNull(i) ?: 1
            val isVMerged = cellVMerge?.getOrNull(i) == true
            val cellSpans = if (isVMerged) emptyList() else cells[i]

            // Combine weights for spanned columns
            val combinedWeight = (logicalCol until (logicalCol + gridSpan).coerceAtMost(colWeights.size))
                .sumOf { colWeights.getOrElse(it) { 1f }.toDouble() }.toFloat().coerceAtLeast(1f)

            val cellAlign = cellAligns?.getOrNull(i) ?: ParaAlignment.LEFT
            val textAlign = when (cellAlign) {
                ParaAlignment.CENTER -> TextAlign.Center
                ParaAlignment.RIGHT -> TextAlign.End
                ParaAlignment.JUSTIFY -> TextAlign.Justify
                ParaAlignment.LEFT -> TextAlign.Start
            }

            val vAlign = cellVAligns?.getOrNull(i) ?: "top"
            val boxAlign = when (vAlign) {
                "center" -> Alignment.CenterStart
                "bottom" -> Alignment.BottomStart
                else -> Alignment.TopStart
            }

            val annotated = buildAnnotatedString {
                for (span in cellSpans) {
                    val decorations = mutableListOf<TextDecoration>()
                    if (span.underline) decorations += TextDecoration.Underline
                    if (span.strikethrough) decorations += TextDecoration.LineThrough
                    val decoration = if (decorations.isNotEmpty()) TextDecoration.combine(decorations) else TextDecoration.None

                    val textColor = if (span.textColor != 0L) Color(span.textColor.toInt()) else Color.Unspecified
                    val bgColor = if (span.highlightColor != 0L) Color(span.highlightColor.toInt()) else Color.Unspecified

                    if (span.hyperlink != null) pushStringAnnotation(tag = "URL", annotation = span.hyperlink)

                    withStyle(
                        SpanStyle(
                            fontWeight = if (span.bold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (span.italic) FontStyle.Italic else FontStyle.Normal,
                            textDecoration = decoration,
                            fontSize = if (span.fontSize > 0f) (span.fontSize * textScale).sp else cellFontSize,
                            color = textColor,
                            background = bgColor,
                            fontFamily = mapFontFamily(span.fontFamily)
                        )
                    ) {
                        append(span.text)
                    }

                    if (span.hyperlink != null) pop()
                }
            }

            val hasLinks = cellSpans.any { it.hyperlink != null }
            val cellStyle = MaterialTheme.typography.bodySmall.copy(
                color = Color.Black,
                fontSize = cellFontSize,
                lineHeight = (cellFontSize.value * 1.2f).sp,
                textAlign = textAlign
            )

            val cellBgColor = cellBgs?.getOrNull(i)?.takeIf { it != 0L }
            Box(
                contentAlignment = boxAlign,
                modifier = Modifier
                    .weight(combinedWeight)
                    .border(0.5.dp, borderColor)
                    .then(
                        if (cellBgColor != null) Modifier.background(Color(cellBgColor.toInt()))
                        else Modifier
                    )
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            ) {
                if (hasLinks) {
                    ClickableText(
                        text = annotated,
                        style = cellStyle,
                        onClick = { offset ->
                            annotated.getStringAnnotations("URL", offset, offset)
                                .firstOrNull()?.let { annotation ->
                                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))) }
                                    catch (_: Exception) { }
                                }
                        }
                    )
                } else {
                    Text(text = annotated, style = cellStyle)
                }
            }
            logicalCol += gridSpan
        }
        // Fill remaining logical columns if row has fewer cells
        while (logicalCol < colWeights.size) {
            Box(modifier = Modifier.weight(colWeights.getOrElse(logicalCol) { 1f }).border(0.5.dp, borderColor).padding(3.dp)) {}
            logicalCol++
        }
    }
}

private fun mapFontFamily(name: String?): FontFamily? = when {
    name == null -> null
    name.contains("Times", ignoreCase = true) -> FontFamily.Serif
    name.contains("Courier", ignoreCase = true) ||
        name.contains("Consolas", ignoreCase = true) -> FontFamily.Monospace
    name.contains("Arial", ignoreCase = true) ||
        name.contains("Calibri", ignoreCase = true) ||
        name.contains("Helvetica", ignoreCase = true) -> FontFamily.SansSerif
    else -> null
}
