package com.vamp.haron.presentation.document

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material.icons.filled.LibraryBooks
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
import kotlinx.coroutines.launch
import com.vamp.haron.R
import com.vamp.haron.data.reading.ReadingPositionManager
import com.vamp.haron.domain.model.TransferHolder
import com.vamp.haron.common.util.DocParagraph
import com.vamp.haron.common.util.DocSpan
import com.vamp.haron.common.util.DocumentParser
import com.vamp.haron.common.util.ParaAlignment
import com.vamp.haron.common.util.VerticalAlign
import androidx.compose.foundation.gestures.detectTapGestures
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
        val cellVAligns: List<String>? = null,
        val hasBorders: Boolean = true,
        val cellBorders: List<Int>? = null  // per cell border bitmask: 1=left 2=right 4=top 8=bottom
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

        val hasBorders = tableBuffer.firstOrNull()?.tableHasBorders ?: true

        for (p in tableBuffer) {
            items.add(DocItem.TblRow(
                cells = p.tableCells ?: emptyList(),
                colWeights = colWeights,
                cellAligns = p.tableCellAligns,
                cellBgs = p.tableCellBgs,
                cellVMerge = p.tableCellVMerge,
                cellGridSpans = p.tableCellGridSpans,
                cellVAligns = p.tableCellVAligns,
                hasBorders = hasBorders,
                cellBorders = p.tableCellBorders
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
            para.isTable && para.tableCells != null -> {
                // Flush when grid structure changes (different <w:tbl> elements)
                if (tableBuffer.isNotEmpty() &&
                    tableBuffer.last().tableColWidths != para.tableColWidths) {
                    flushTable()
                }
                tableBuffer.add(para)
            }
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
    onBack: () -> Unit,
    onNavigateToLibrary: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var docItems by remember { mutableStateOf<List<DocItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Hide VoiceFab in reader, show on tap
    var micVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { TransferHolder.voiceFabVisible.value = false }
    DisposableEffect(Unit) {
        onDispose {
            TransferHolder.voiceFabVisible.value = true
            TransferHolder.voiceFabPinned.value = false
        }
    }
    LaunchedEffect(micVisible) {
        TransferHolder.voiceFabVisible.value = micVisible
        if (micVisible) {
            delay(3000)
            if (!TransferHolder.voiceFabPinned.value) micVisible = false
        }
    }

    // Selection key: reset to destroy SelectionContainer (clears selection + ActionMode)
    var selectionKey by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val listener = ClipboardManager.OnPrimaryClipChangedListener { selectionKey++ }
        clipboard.addPrimaryClipChangedListener(listener)
        onDispose { clipboard.removePrimaryClipChangedListener(listener) }
    }
    BackHandler { selectionKey++; onBack() }

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

    // Reader theme: 0=Auto, 1=Light, 2=Sepia, 3=Dark
    var readerTheme by remember { mutableIntStateOf(0) }
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val bgColor = when (readerTheme) {
        1 -> Color(0xFFFAFAFA)
        2 -> Color(0xFFF5E6CC)
        3 -> Color(0xFF1A1A1A)
        else -> if (isDark) MaterialTheme.colorScheme.surface else Color.White
    }
    val textColor = when (readerTheme) {
        1 -> Color(0xFF212121)
        2 -> Color(0xFF3E2723)
        3 -> Color(0xFFCCCCCC)
        else -> MaterialTheme.colorScheme.onSurface
    }

    var showControls by remember { mutableStateOf(false) }
    val navBarInsets = androidx.compose.foundation.layout.WindowInsets.navigationBars
        .asPaddingValues()
    val statusBarInsets = androidx.compose.foundation.layout.WindowInsets.statusBars
        .asPaddingValues()

    when {
        isLoading -> Box(Modifier.fillMaxSize().background(bgColor), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        error != null -> Box(Modifier.fillMaxSize().background(bgColor), contentAlignment = Alignment.Center) {
            Text(error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        }
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

            // Progress
            val totalItems = items.size.coerceAtLeast(1)
            val progress = (listState.firstVisibleItemIndex.toFloat() / totalItems).coerceIn(0f, 1f)
            val scope = androidx.compose.runtime.rememberCoroutineScope()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .padding(top = 4.dp)
                    .pointerInput(Unit) {
                        val slop = viewConfiguration.touchSlop
                        val edgePx = 30.dp.toPx()
                        val swipeThreshold = 80.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val wasConsumed = down.isConsumed
                            val startPos = down.position
                            val isFromLeftEdge = startPos.x < edgePx
                            var wasPinch = false
                            var wasDrag = false
                            var totalDx = 0f
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.size >= 2) {
                                    wasPinch = true
                                    val zoom = event.calculateZoom()
                                    textScale = (textScale * zoom).coerceIn(0.5f, 3f)
                                    event.changes.forEach { c -> c.consume() }
                                } else if (!wasDrag) {
                                    val pos = event.changes.firstOrNull()?.position
                                    if (pos != null) {
                                        val dist = kotlin.math.hypot(
                                            (pos.x - startPos.x).toDouble(),
                                            (pos.y - startPos.y).toDouble()
                                        )
                                        if (dist > slop) wasDrag = true
                                        totalDx = pos.x - startPos.x
                                    }
                                }
                            } while (event.changes.any { c -> c.pressed })
                            if (isFromLeftEdge && totalDx > swipeThreshold) {
                                onBack()
                            } else if (!wasPinch && !wasDrag && !wasConsumed) {
                                showControls = !showControls
                                micVisible = showControls
                            }
                        }
                    }
            ) {
                // Content
                key(selectionKey) { SelectionContainer {
                    LazyColumn(
                        state = listState,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 12.dp, end = 12.dp,
                            top = 0.dp,
                            bottom = 4.dp + navBarInsets.calculateBottomPadding()
                        ),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(items.size) { index ->
                            when (val item = items[index]) {
                                is DocItem.Para -> RichParagraph(item.paragraph, textScale, textColor, bgColor)
                                is DocItem.Img -> EmbeddedImage(item.data)
                                is DocItem.TblRow -> CompactTableRow(
                                    cells = item.cells,
                                    colWeights = item.colWeights,
                                    cellAligns = item.cellAligns,
                                    cellBgs = item.cellBgs,
                                    cellVMerge = item.cellVMerge,
                                    cellGridSpans = item.cellGridSpans,
                                    cellVAligns = item.cellVAligns,
                                    hasBorders = item.hasBorders,
                                    cellBorders = item.cellBorders,
                                    textScale = textScale,
                                    textColor = textColor,
                                    themeBgColor = bgColor
                                )
                            }
                        }
                    }
                } }

                // Overlay controls — shown on tap
                androidx.compose.animation.AnimatedVisibility(
                    visible = showControls,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    // Top bar: back + filename
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bgColor.copy(alpha = 0.9f))
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = textColor)
                        }
                        Text(
                            text = fileName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall,
                            color = textColor,
                            modifier = Modifier.weight(1f)
                        )
                        if (onNavigateToLibrary != null) {
                            IconButton(onClick = onNavigateToLibrary) {
                                Icon(Icons.Filled.LibraryBooks, stringResource(R.string.navbar_action_library), tint = textColor)
                            }
                        }
                    }
                }

                // Bottom: themes + progress — shown on tap
                androidx.compose.animation.AnimatedVisibility(
                    visible = showControls,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bgColor.copy(alpha = 0.9f))
                            .padding(bottom = navBarInsets.calculateBottomPadding())
                    ) {
                        // Theme switcher
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val labels = listOf("A", "☀", "S", "🌙")
                                val themeColors = listOf(
                                    if (isDark) MaterialTheme.colorScheme.surface else Color.White,
                                    Color(0xFFFAFAFA),
                                    Color(0xFFF5E6CC),
                                    Color(0xFF1A1A1A)
                                )
                                themeColors.forEachIndexed { i, c ->
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .padding(3.dp)
                                            .background(c, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                                            .border(
                                                width = if (i == readerTheme) 2.dp else 1.dp,
                                                color = if (i == readerTheme) MaterialTheme.colorScheme.primary
                                                        else textColor.copy(alpha = 0.3f),
                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                                            )
                                            .then(Modifier.pointerInput(i) {
                                                detectTapGestures { readerTheme = i }
                                            }),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(labels[i], fontSize = 16.sp, color = if (i == 3) Color.White else Color.Black)
                                    }
                                }
                            }
                        }
                        // Progress slider
                        androidx.compose.material3.Slider(
                            value = progress,
                            onValueChange = { newVal ->
                                scope.launch {
                                    val targetIndex = (newVal * totalItems).toInt().coerceIn(0, items.lastIndex)
                                    listState.scrollToItem(targetIndex)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
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
private fun RichParagraph(paragraph: DocParagraph, textScale: Float, textColor: Color = Color.Black, themeBgColor: Color = Color.White) {
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
        color = textColor,
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
            // Smart contrast: handle both text color and background color from document
            val themeBgLum = themeBgColor.red * 0.299f + themeBgColor.green * 0.587f + themeBgColor.blue * 0.114f

            // (logging removed)

            // 1. Check document background (highlight) — ignore if it merges with theme background
            val rawBg = if (span.highlightColor != 0L) Color(span.highlightColor.toInt()) else null
            val bgColor = if (rawBg != null) {
                val bgLum = rawBg.red * 0.299f + rawBg.green * 0.587f + rawBg.blue * 0.114f
                if (kotlin.math.abs(bgLum - themeBgLum) > 0.4f) {
                    // Clashes with theme — invert
                    Color(1f - rawBg.red, 1f - rawBg.green, 1f - rawBg.blue, rawBg.alpha)
                } else rawBg
            } else Color.Unspecified

            // 2. Check text color — compare against effective background (document bg if visible, else theme bg)
            val effectiveBgForContrast = if (bgColor != Color.Unspecified) bgColor else themeBgColor
            val effectiveBgLum = effectiveBgForContrast.red * 0.299f + effectiveBgForContrast.green * 0.587f + effectiveBgForContrast.blue * 0.114f
            val spanColor = if (span.textColor != 0L) {
                val c = Color(span.textColor.toInt())
                val cLum = c.red * 0.299f + c.green * 0.587f + c.blue * 0.114f
                if (kotlin.math.abs(cLum - effectiveBgLum) > 0.25f) {
                    c // good contrast — keep original
                } else {
                    // Bad contrast — invert the color (mirror light↔dark)
                    Color(1f - c.red, 1f - c.green, 1f - c.blue, c.alpha)
                }
            } else Color.Unspecified

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
                    color = spanColor,
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
            val paraBg = Color(paragraph.backgroundColor.toInt())
            val paraBgLum = paraBg.red * 0.299f + paraBg.green * 0.587f + paraBg.blue * 0.114f
            val tBgLum = themeBgColor.red * 0.299f + themeBgColor.green * 0.587f + themeBgColor.blue * 0.114f
            val effectiveParaBg = if (kotlin.math.abs(paraBgLum - tBgLum) > 0.4f) {
                Color(1f - paraBg.red, 1f - paraBg.green, 1f - paraBg.blue, paraBg.alpha)
            } else paraBg
            Modifier.background(effectiveParaBg)
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
    hasBorders: Boolean = true,
    cellBorders: List<Int>? = null,
    textScale: Float,
    textColor: Color = Color.Black,
    themeBgColor: Color = Color.White
) {
    val context = LocalContext.current
    val themeBgLum = themeBgColor.red * 0.299f + themeBgColor.green * 0.587f + themeBgColor.blue * 0.114f
    val borderColor = if (themeBgLum < 0.5f) Color(0xFF555555) else Color(0xFFBDBDBD)
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

                    // Smart contrast for table cells (same logic as RichParagraph)
                    val cellThemeBgLum = themeBgColor.red * 0.299f + themeBgColor.green * 0.587f + themeBgColor.blue * 0.114f
                    val rawCellBg = if (span.highlightColor != 0L) Color(span.highlightColor.toInt()) else null
                    val bgColor = if (rawCellBg != null) {
                        val bgLum = rawCellBg.red * 0.299f + rawCellBg.green * 0.587f + rawCellBg.blue * 0.114f
                        if (kotlin.math.abs(bgLum - cellThemeBgLum) > 0.4f) {
                            // Clashes with theme — invert
                            Color(1f - rawCellBg.red, 1f - rawCellBg.green, 1f - rawCellBg.blue, rawCellBg.alpha)
                        } else rawCellBg
                    } else Color.Unspecified
                    val effectiveCellBg = if (bgColor != Color.Unspecified) bgColor else themeBgColor
                    val effectiveCellBgLum = effectiveCellBg.red * 0.299f + effectiveCellBg.green * 0.587f + effectiveCellBg.blue * 0.114f
                    val themeTextColor = textColor // outer textColor (theme color)
                    val spanTextColor = if (span.textColor != 0L) {
                        val c = Color(span.textColor.toInt())
                        val cLum = c.red * 0.299f + c.green * 0.587f + c.blue * 0.114f
                        if (kotlin.math.abs(cLum - effectiveCellBgLum) > 0.25f) c
                        else Color(1f - c.red, 1f - c.green, 1f - c.blue, c.alpha)
                    } else themeTextColor // no color from doc → use theme text color

                    if (span.hyperlink != null) pushStringAnnotation(tag = "URL", annotation = span.hyperlink)

                    withStyle(
                        SpanStyle(
                            fontWeight = if (span.bold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (span.italic) FontStyle.Italic else FontStyle.Normal,
                            textDecoration = decoration,
                            fontSize = if (span.fontSize > 0f) (span.fontSize * textScale).sp else cellFontSize,
                            color = spanTextColor,
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
                color = textColor,
                fontSize = cellFontSize,
                lineHeight = (cellFontSize.value * 1.2f).sp,
                textAlign = textAlign
            )

            val rawCellBgColor = cellBgs?.getOrNull(i)?.takeIf { it != 0L }
            // Smart contrast: invert cell background if it clashes with theme
            val cellBgColor = if (rawCellBgColor != null) {
                val cb = Color(rawCellBgColor.toInt())
                val cbLum = cb.red * 0.299f + cb.green * 0.587f + cb.blue * 0.114f
                if (kotlin.math.abs(cbLum - themeBgLum) > 0.4f) {
                    // Clashes — invert the background color
                    Color(1f - cb.red, 1f - cb.green, 1f - cb.blue, cb.alpha)
                } else cb
            } else null
            // Effective cell background: explicit cellBg (inverted if needed), or theme bg for tables
            val effectiveCellBg = cellBgColor ?: themeBgColor
            Box(
                contentAlignment = boxAlign,
                modifier = Modifier
                    .weight(combinedWeight)
                    .background(effectiveCellBg)
                    .then(
                        when {
                            !hasBorders -> Modifier
                            cellBorders != null -> {
                                val mask = cellBorders.getOrNull(i) ?: 15
                                if (mask == 15) Modifier.border(0.5.dp, borderColor)
                                else if (mask == 0) Modifier
                                else Modifier.drawBehind {
                                    val sw = 0.5.dp.toPx()
                                    if (mask and 1 != 0) drawLine(borderColor, Offset(0f, 0f), Offset(0f, size.height), sw)
                                    if (mask and 2 != 0) drawLine(borderColor, Offset(size.width, 0f), Offset(size.width, size.height), sw)
                                    if (mask and 4 != 0) drawLine(borderColor, Offset(0f, 0f), Offset(size.width, 0f), sw)
                                    if (mask and 8 != 0) drawLine(borderColor, Offset(0f, size.height), Offset(size.width, size.height), sw)
                                }
                            }
                            else -> Modifier.border(0.5.dp, borderColor)
                        }
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
            Box(modifier = Modifier.weight(colWeights.getOrElse(logicalCol) { 1f }).then(if (hasBorders) Modifier.border(0.5.dp, borderColor) else Modifier).padding(3.dp)) {}
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
