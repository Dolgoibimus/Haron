package com.vamp.haron.presentation.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vamp.haron.R
import com.vamp.haron.common.util.PdfMatch
import com.vamp.haron.data.reading.ReadingPositionManager
import com.vamp.haron.common.util.PdfTextPositionExtractor
import com.vamp.haron.domain.model.SearchNavigationHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.vamp.haron.presentation.common.PasswordDialog
import com.tom_roush.pdfbox.pdmodel.PDDocument
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

private const val MAX_PAGE_WIDTH = 2048
private const val CACHE_SIZE = 5

private val DOCUMENT_EXTENSIONS = setOf("doc", "docx", "odt", "rtf", "fb2")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    filePath: String,
    fileName: String,
    onBack: () -> Unit
) {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    val isFb2Zip = fileName.lowercase().endsWith(".fb2.zip")
    val isDocumentMode = extension in DOCUMENT_EXTENSIONS || isFb2Zip

    if (isDocumentMode) {
        val ext = if (isFb2Zip) "fb2.zip" else extension
        DocumentReaderContent(filePath, fileName, ext, onBack)
    } else {
        PdfReaderContent(filePath, fileName, onBack)
    }
}

// --- Document reader (DOC/DOCX/ODT/RTF) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentReaderContent(
    filePath: String,
    fileName: String,
    extension: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val highlightQuery = remember { SearchNavigationHolder.highlightQuery }
    var documentText by remember { mutableStateOf<String?>(null) }
    var lineCount by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var currentMatchIndex by remember { mutableIntStateOf(0) }
    var textLayoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    var containerHeight by remember { mutableIntStateOf(0) }
    var savedScrollPos by remember { mutableIntStateOf(0) }

    // Zoom state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        if (newScale > 1f) {
            val maxOff = (newScale - 1f) * 500f
            offsetX = (offsetX + panChange.x).coerceIn(-maxOff, maxOff)
            offsetY = (offsetY + panChange.y).coerceIn(-maxOff, maxOff)
        } else {
            offsetX = 0f
            offsetY = 0f
        }
        scale = newScale
    }

    // Extract text + restore zoom + saved scroll position
    LaunchedEffect(filePath) {
        data class DocLoadResult(
            val text: String? = null,
            val err: String? = null,
            val zoomValue: Int = 0,
            val scrollPos: Int = 0
        )
        val result = withContext(Dispatchers.IO) {
            // Read saved position first (fast)
            val posSaved = if (highlightQuery.isNullOrBlank()) ReadingPositionManager.get(filePath) else null
            val zoomSaved = ReadingPositionManager.get("zoom:$filePath")
            // Then parse document (slow)
            try {
                val text = extractDocumentText(context, filePath, extension)
                DocLoadResult(
                    text = text,
                    zoomValue = zoomSaved?.position ?: 0,
                    scrollPos = posSaved?.position ?: 0
                )
            } catch (e: Exception) {
                val msg = e.message ?: ""
                val isEncrypted = e.javaClass.simpleName.contains("Encrypted", ignoreCase = true) ||
                    msg.contains("encrypted", ignoreCase = true) ||
                    msg.contains("password", ignoreCase = true)
                val errMsg = if (isEncrypted) context.getString(R.string.doc_encrypted_not_supported)
                    else msg.ifEmpty { context.getString(R.string.read_document_failed) }
                DocLoadResult(err = errMsg)
            }
        }
        if (result.text != null) {
            savedScrollPos = result.scrollPos
            documentText = result.text
            lineCount = result.text.lines().size
            if (result.zoomValue > 100) {
                scale = (result.zoomValue.toFloat() / 100f).coerceIn(1f, 5f)
            }
        } else {
            error = result.err
        }
        isLoading = false
    }

    if (error != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            }
        }
        return
    }

    val matchIndices = remember(documentText, highlightQuery) {
        if (highlightQuery.isNullOrBlank() || documentText == null) emptyList()
        else {
            val indices = mutableListOf<Int>()
            val lower = documentText!!.lowercase()
            val lq = highlightQuery.lowercase()
            var pos = 0
            while (pos < lower.length) {
                val idx = lower.indexOf(lq, pos)
                if (idx < 0) break
                indices.add(idx)
                pos = idx + lq.length
            }
            indices
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = fileName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (matchIndices.isNotEmpty()) {
                        Text(
                            text = "${currentMatchIndex + 1}/${matchIndices.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = {
                            currentMatchIndex = if (currentMatchIndex > 0) currentMatchIndex - 1 else matchIndices.size - 1
                        }) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = {
                            currentMatchIndex = if (currentMatchIndex < matchIndices.size - 1) currentMatchIndex + 1 else 0
                        }) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isLoading) stringResource(R.string.loading) else stringResource(R.string.lines_count, lineCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val text = documentText ?: return@Scaffold
        val docScrollState = rememberScrollState(initial = savedScrollPos)

        // Save reading position + zoom (debounced)
        LaunchedEffect(filePath) {
            snapshotFlow { docScrollState.value to scale }.collectLatest { (scroll, zoom) ->
                delay(1000)
                withContext(Dispatchers.IO) {
                    ReadingPositionManager.save(filePath, scroll)
                    ReadingPositionManager.save("zoom:$filePath", (zoom * 100).toInt())
                }
            }
        }

        // Save on exit
        DisposableEffect(filePath) {
            onDispose {
                ReadingPositionManager.saveAsync(filePath, docScrollState.value)
                ReadingPositionManager.saveAsync("zoom:$filePath", (scale * 100).toInt())
            }
        }

        // Scroll to match using exact pixel position from TextLayoutResult
        LaunchedEffect(currentMatchIndex, matchIndices, textLayoutResult, containerHeight) {
            val layout = textLayoutResult ?: return@LaunchedEffect
            if (matchIndices.isNotEmpty() && currentMatchIndex in matchIndices.indices && containerHeight > 0) {
                val pos = matchIndices[currentMatchIndex]
                val line = layout.getLineForOffset(pos)
                val lineTop = layout.getLineTop(line).toInt()
                val scrollTarget = (lineTop - containerHeight / 2).coerceIn(0, docScrollState.maxValue)
                docScrollState.animateScrollTo(scrollTarget)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.1f) {
                                scale = 1f; offsetX = 0f; offsetY = 0f
                            } else {
                                scale = 2f
                            }
                        }
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { containerHeight = it.height }
                    .transformable(state = transformableState)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
            ) {
                val highlightColor = MaterialTheme.colorScheme.primary
                val currentHighlightBg = MaterialTheme.colorScheme.primaryContainer
                val annotatedText = remember(text, highlightQuery, currentMatchIndex) {
                    if (highlightQuery.isNullOrBlank()) AnnotatedString(text)
                    else buildHighlightedText(text, highlightQuery, highlightColor, matchIndices, currentMatchIndex, currentHighlightBg)
                }
                Text(
                    text = annotatedText,
                    onTextLayout = { textLayoutResult = it },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(docScrollState)
                        .padding(16.dp)
                )
            }
        }
    }
}

private fun buildHighlightedText(
    text: String,
    query: String,
    color: Color,
    matchIndices: List<Int> = emptyList(),
    currentMatchIndex: Int = -1,
    currentBg: Color = Color.Transparent
) = buildAnnotatedString {
    val lower = text.lowercase()
    val lowerQuery = query.lowercase()
    var pos = 0
    var matchNum = 0
    while (pos < text.length) {
        val idx = lower.indexOf(lowerQuery, pos)
        if (idx < 0) {
            append(text.substring(pos))
            break
        }
        append(text.substring(pos, idx))
        val isCurrent = matchNum == currentMatchIndex
        withStyle(SpanStyle(
            fontWeight = FontWeight.Bold,
            color = color,
            background = if (isCurrent) currentBg else Color.Transparent
        )) {
            append(text.substring(idx, idx + query.length))
        }
        matchNum++
        pos = idx + query.length
    }
}

// --- Text extraction helpers ---

private fun extractDocumentText(context: Context, filePath: String, extension: String): String {
    return when (extension) {
        "docx" -> extractDocx(context, filePath)
        "odt" -> extractOdt(context, filePath)
        "doc" -> extractDoc(context, filePath)
        "rtf" -> extractRtf(context, filePath)
        "fb2" -> extractFb2(context, filePath)
        "fb2.zip" -> extractFb2FromZip(context, filePath)
        else -> throw IllegalArgumentException("Неподдерживаемый формат: $extension")
    }
}

private fun extractDocx(context: Context, filePath: String): String {
    val file = resolveFile(context, filePath, "docx")
    val isTemp = filePath.startsWith("content://")
    try {
        ZipFile(file).use { zip ->
            val xmlEntry = zip.getEntry("word/document.xml")
                ?: throw IllegalStateException("Не удалось прочитать документ")
            val xml = zip.getInputStream(xmlEntry).bufferedReader().readText()
            val raw = xml.split("</w:p>").map { para ->
                Regex("<w:t[^>]*>([^<]*)</w:t>").findAll(para)
                    .map { it.groupValues[1] }
                    .joinToString("")
            }.filter { it.isNotBlank() }.joinToString("\n")
            return decodeXmlEntities(raw)
        }
    } finally {
        if (isTemp) file.delete()
    }
}

private fun extractOdt(context: Context, filePath: String): String {
    val file = resolveFile(context, filePath, "odt")
    val isTemp = filePath.startsWith("content://")
    try {
        ZipFile(file).use { zip ->
            val xmlEntry = zip.getEntry("content.xml")
                ?: throw IllegalStateException("Не удалось прочитать документ")
            val xml = zip.getInputStream(xmlEntry).bufferedReader().readText()
            val raw = xml.split("</text:p>").map { para ->
                para.replace(Regex("<[^>]+>"), "")
            }.filter { it.isNotBlank() }.joinToString("\n")
            return decodeXmlEntities(raw)
        }
    } finally {
        if (isTemp) file.delete()
    }
}

private fun extractDoc(context: Context, filePath: String): String {
    val stream = if (filePath.startsWith("content://")) {
        context.contentResolver.openInputStream(Uri.parse(filePath))
            ?: throw IllegalStateException("Не удалось открыть файл")
    } else {
        File(filePath).inputStream()
    }
    return stream.use { input ->
        val doc = HWPFDocument(input)
        val extractor = WordExtractor(doc)
        val text = extractor.text ?: ""
        extractor.close()
        doc.close()
        text
    }
}

private fun extractRtf(context: Context, filePath: String): String {
    val raw = if (filePath.startsWith("content://")) {
        context.contentResolver.openInputStream(Uri.parse(filePath))
            ?.bufferedReader()?.readText()
            ?: throw IllegalStateException("Не удалось открыть файл")
    } else {
        File(filePath).readText()
    }
    return raw
        .replace(Regex("\\\\[a-z]+[\\d]*\\s?"), " ")
        .replace(Regex("\\\\[{}\\\\]"), "")
        .replace(Regex("[{}]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun extractFb2(context: Context, filePath: String): String {
    val raw = if (filePath.startsWith("content://")) {
        context.contentResolver.openInputStream(Uri.parse(filePath))
            ?.bufferedReader()?.readText()
            ?: throw IllegalStateException("Не удалось открыть файл")
    } else {
        File(filePath).readText()
    }

    val sb = StringBuilder()

    // Extract <body> content (skip <description>, <binary>)
    val bodyMatch = Regex("<body[^>]*>(.*)</body>", RegexOption.DOT_MATCHES_ALL).find(raw)
    val body = bodyMatch?.groupValues?.get(1) ?: raw

    // Process sections and paragraphs
    // Replace <title> blocks with formatted title text
    var text = body
        // <empty-line/> → blank line
        .replace(Regex("<empty-line\\s*/?>"), "\n")
        // <title> → extract inner <p> tags as title lines
        .replace(Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)) { match ->
            val inner = match.groupValues[1]
            val titleLines = Regex("<p>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
                .findAll(inner)
                .map { it.groupValues[1].replace(Regex("<[^>]+>"), "").trim() }
                .filter { it.isNotBlank() }
                .joinToString("\n")
            "\n\n━━━━━━━━━━━━━━━━━━━━\n$titleLines\n━━━━━━━━━━━━━━━━━━━━\n\n"
        }
        // <subtitle> → centered-like text
        .replace(Regex("<subtitle>(.*?)</subtitle>", RegexOption.DOT_MATCHES_ALL)) { match ->
            val inner = match.groupValues[1].replace(Regex("<[^>]+>"), "").trim()
            "\n— $inner —\n\n"
        }
        // <epigraph> → italic-like indented block
        .replace(Regex("<epigraph>(.*?)</epigraph>", RegexOption.DOT_MATCHES_ALL)) { match ->
            val inner = match.groupValues[1]
            val lines = Regex("<p>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
                .findAll(inner)
                .map { "        " + it.groupValues[1].replace(Regex("<[^>]+>"), "").trim() }
                .joinToString("\n")
            "\n$lines\n\n"
        }
        // <poem> → extract <stanza> → <v> lines
        .replace(Regex("<poem>(.*?)</poem>", RegexOption.DOT_MATCHES_ALL)) { match ->
            val inner = match.groupValues[1]
            val verses = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL)
                .findAll(inner)
                .map { "    " + it.groupValues[1].replace(Regex("<[^>]+>"), "").trim() }
                .joinToString("\n")
            "\n$verses\n\n"
        }
        // <p> → paragraph with indent
        .replace(Regex("<p>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)) { match ->
            val inner = match.groupValues[1].replace(Regex("<[^>]+>"), "").trim()
            if (inner.isNotBlank()) "    $inner\n" else "\n"
        }
        // <section> open/close → section break
        .replace(Regex("<section[^>]*>"), "\n")
        .replace("</section>", "\n")

    // Strip all remaining XML tags
    text = text.replace(Regex("<[^>]+>"), "")
    // Decode XML entities
    text = text
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#160;", " ")
        .replace(Regex("&#(\\d+);")) { match ->
            val code = match.groupValues[1].toIntOrNull()
            if (code != null) code.toChar().toString() else ""
        }
    // Collapse excessive blank lines (max 2)
    text = text.replace(Regex("\n{4,}"), "\n\n\n")

    return text.trim()
}

private fun extractFb2FromZip(context: Context, filePath: String): String {
    val file = resolveFile(context, filePath, "fb2.zip")
    val isTemp = filePath.startsWith("content://")
    try {
        ZipFile(file).use { zip ->
            val fb2Entry = zip.entries().toList().firstOrNull { entry ->
                entry.name.lowercase().endsWith(".fb2")
            } ?: throw IllegalStateException("FB2 file not found in archive")
            val raw = zip.getInputStream(fb2Entry).bufferedReader().readText()
            return extractFb2Content(raw)
        }
    } finally {
        if (isTemp) file.delete()
    }
}

private fun extractFb2Content(raw: String): String {
    val bodyMatch = Regex("<body[^>]*>(.*)</body>", RegexOption.DOT_MATCHES_ALL).find(raw)
    val body = bodyMatch?.groupValues?.get(1) ?: raw
    var text = body
        .replace(Regex("<empty-line\\s*/?>"), "\n")
        .replace(Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)) { match ->
            val inner = match.groupValues[1]
            val titleLines = Regex("<p>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
                .findAll(inner)
                .map { it.groupValues[1].replace(Regex("<[^>]+>"), "").trim() }
                .filter { it.isNotBlank() }
                .joinToString("\n")
            "\n\n━━━━━━━━━━━━━━━━━━━━\n$titleLines\n━━━━━━━━━━━━━━━━━━━━\n\n"
        }
        .replace(Regex("<subtitle>(.*?)</subtitle>", RegexOption.DOT_MATCHES_ALL)) { match ->
            "\n— ${match.groupValues[1].replace(Regex("<[^>]+>"), "").trim()} —\n\n"
        }
        .replace(Regex("<epigraph>(.*?)</epigraph>", RegexOption.DOT_MATCHES_ALL)) { match ->
            val lines = Regex("<p>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
                .findAll(match.groupValues[1])
                .map { "        " + it.groupValues[1].replace(Regex("<[^>]+>"), "").trim() }
                .joinToString("\n")
            "\n$lines\n\n"
        }
        .replace(Regex("<poem>(.*?)</poem>", RegexOption.DOT_MATCHES_ALL)) { match ->
            val verses = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL)
                .findAll(match.groupValues[1])
                .map { "    " + it.groupValues[1].replace(Regex("<[^>]+>"), "").trim() }
                .joinToString("\n")
            "\n$verses\n\n"
        }
        .replace(Regex("<p>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)) { match ->
            val inner = match.groupValues[1].replace(Regex("<[^>]+>"), "").trim()
            if (inner.isNotBlank()) "    $inner\n" else "\n"
        }
        .replace(Regex("<section[^>]*>"), "\n")
        .replace("</section>", "\n")
    text = text.replace(Regex("<[^>]+>"), "")
    text = text
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#160;", " ")
        .replace(Regex("&#(\\d+);")) { match ->
            val code = match.groupValues[1].toIntOrNull()
            if (code != null) code.toChar().toString() else ""
        }
    text = text.replace(Regex("\n{4,}"), "\n\n\n")
    return text.trim()
}

private fun decodeXmlEntities(text: String): String = text
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&apos;", "'")
    .replace("&#160;", " ")
    .replace(Regex("&#(\\d+);")) { match ->
        val code = match.groupValues[1].toIntOrNull()
        if (code != null) code.toChar().toString() else ""
    }

private fun resolveFile(context: Context, filePath: String, extension: String): File {
    return if (filePath.startsWith("content://")) {
        val tempFile = File(context.cacheDir, "doc_reader_${System.currentTimeMillis()}.$extension")
        context.contentResolver.openInputStream(Uri.parse(filePath))?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Не удалось открыть файл")
        tempFile
    } else {
        File(filePath)
    }
}

// --- PDF reader (existing logic) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfReaderContent(
    filePath: String,
    fileName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var currentPage by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var showGoToDialog by remember { mutableStateOf(false) }
    val pageCache = remember { mutableStateMapOf<Int, Bitmap>() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // PDF password state
    var isEncrypted by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var pdfPassword by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var decryptedTempFile by remember { mutableStateOf<File?>(null) }

    // Highlight state
    val highlightQuery = remember { SearchNavigationHolder.highlightQuery }
    var allMatches by remember { mutableStateOf<List<PdfMatch>>(emptyList()) }
    var currentMatchIndex by remember { mutableIntStateOf(0) }
    var isExtractingText by remember { mutableStateOf(false) }
    val pageScaleMap = remember { mutableStateMapOf<Int, Float>() }
    val matchesByPage = remember(allMatches) { allMatches.groupBy { it.pageIndex } }

    // Zoom state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        if (newScale > 1f) {
            val maxOff = (newScale - 1f) * 500f
            offsetX = (offsetX + panChange.x).coerceIn(-maxOff, maxOff)
            offsetY = (offsetY + panChange.y).coerceIn(-maxOff, maxOff)
        } else {
            offsetX = 0f
            offsetY = 0f
        }
        scale = newScale
    }

    // Open PDF
    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            try {
                val descriptor = if (filePath.startsWith("content://")) {
                    context.contentResolver.openFileDescriptor(Uri.parse(filePath), "r")
                } else {
                    ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_ONLY)
                }
                if (descriptor != null) {
                    pfd = descriptor
                    val r = PdfRenderer(descriptor)
                    renderer = r
                    pageCount = r.pageCount
                } else {
                    error = context.getString(R.string.pdf_open_failed)
                }
            } catch (e: SecurityException) {
                isEncrypted = true
                showPasswordDialog = true
            } catch (e: Exception) {
                val msg = e.message ?: ""
                if (msg.contains("password", ignoreCase = true) || msg.contains("encrypted", ignoreCase = true)) {
                    isEncrypted = true
                    showPasswordDialog = true
                } else {
                    error = msg.ifEmpty { context.getString(R.string.pdf_open_error) }
                }
            }
        }
    }

    // Decrypt PDF with password
    LaunchedEffect(pdfPassword) {
        val pw = pdfPassword ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val sourceFile = if (filePath.startsWith("content://")) {
                    val tmp = File(context.cacheDir, "pdf_src_${System.currentTimeMillis()}.pdf")
                    context.contentResolver.openInputStream(Uri.parse(filePath))?.use { inp ->
                        tmp.outputStream().use { out -> inp.copyTo(out) }
                    }
                    tmp
                } else {
                    File(filePath)
                }
                val doc = PDDocument.load(sourceFile, pw)
                doc.isAllSecurityToBeRemoved = true
                val tempOut = File(context.cacheDir, "pdf_decrypted_${System.currentTimeMillis()}.pdf")
                doc.save(tempOut)
                doc.close()
                if (filePath.startsWith("content://")) sourceFile.delete()

                val descriptor = ParcelFileDescriptor.open(tempOut, ParcelFileDescriptor.MODE_READ_ONLY)
                pfd = descriptor
                val r = PdfRenderer(descriptor)
                renderer = r
                pageCount = r.pageCount
                decryptedTempFile = tempOut
                isEncrypted = false
                passwordError = null
                error = null
            } catch (e: Exception) {
                val msg = e.message ?: ""
                if (msg.contains("password", ignoreCase = true) || msg.contains("decrypt", ignoreCase = true) ||
                    e is java.io.IOException) {
                    passwordError = context.getString(R.string.wrong_password)
                    showPasswordDialog = true
                    pdfPassword = null
                } else {
                    error = msg.ifEmpty { context.getString(R.string.pdf_open_error) }
                }
            }
        }
    }

    // Restore reading position + zoom
    LaunchedEffect(filePath, pageCount) {
        if (pageCount <= 0) return@LaunchedEffect
        val zoomSaved = withContext(Dispatchers.IO) { ReadingPositionManager.get("zoom:$filePath") }
        if (zoomSaved != null && zoomSaved.position > 100) {
            scale = (zoomSaved.position.toFloat() / 100f).coerceIn(1f, 5f)
        }
        if (!highlightQuery.isNullOrBlank()) return@LaunchedEffect
        val saved = withContext(Dispatchers.IO) { ReadingPositionManager.get(filePath) }
        if (saved != null && saved.position in 1 until pageCount) {
            listState.scrollToItem(saved.position)
        }
    }

    // Track current page
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
            currentPage = index
        }
    }

    // Save reading position + zoom (debounced)
    LaunchedEffect(filePath, pageCount) {
        if (pageCount <= 0) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to scale }.collectLatest { (page, zoom) ->
            delay(1000)
            withContext(Dispatchers.IO) {
                ReadingPositionManager.save(filePath, page)
                ReadingPositionManager.save("zoom:$filePath", (zoom * 100).toInt())
            }
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            ReadingPositionManager.saveAsync(filePath, currentPage)
            ReadingPositionManager.saveAsync("zoom:$filePath", (scale * 100).toInt())
            pageCache.values.forEach { if (!it.isRecycled) it.recycle() }
            pageCache.clear()
            renderer?.close()
            pfd?.close()
            decryptedTempFile?.delete()
        }
    }

    // Extract text matches for highlight
    LaunchedEffect(highlightQuery, pageCount) {
        if (highlightQuery.isNullOrBlank() || pageCount <= 0) return@LaunchedEffect
        isExtractingText = true
        withContext(Dispatchers.IO) {
            try {
                val file = if (filePath.startsWith("content://")) {
                    val tmp = File(context.cacheDir, "pdf_highlight_${System.currentTimeMillis()}.pdf")
                    context.contentResolver.openInputStream(Uri.parse(filePath))?.use { inp ->
                        tmp.outputStream().use { out -> inp.copyTo(out) }
                    }
                    tmp
                } else {
                    File(filePath)
                }
                val extractor = PdfTextPositionExtractor(context)
                val matches = extractor.findMatches(file, highlightQuery, pageCount)
                allMatches = matches
                if (filePath.startsWith("content://")) file.delete()
            } catch (_: Exception) {
                allMatches = emptyList()
            }
        }
        isExtractingText = false
    }

    // Auto-scroll to first match
    LaunchedEffect(allMatches) {
        if (allMatches.isNotEmpty()) {
            listState.animateScrollToItem(allMatches[0].pageIndex)
        }
    }

    // Navigate between matches
    LaunchedEffect(currentMatchIndex) {
        if (allMatches.isNotEmpty() && currentMatchIndex in allMatches.indices) {
            val targetPage = allMatches[currentMatchIndex].pageIndex
            listState.animateScrollToItem(targetPage)
        }
    }

    if (showPasswordDialog) {
        PasswordDialog(
            fileName = fileName,
            errorMessage = passwordError,
            onConfirm = { pw ->
                showPasswordDialog = false
                pdfPassword = pw
            },
            onDismiss = {
                showPasswordDialog = false
                onBack()
            }
        )
    }

    if (isEncrypted && !showPasswordDialog && pdfPassword == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.pdf_encrypted), color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            }
        }
        return
    }

    if (error != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = fileName.ifEmpty { "PDF" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (isExtractingText) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    if (allMatches.isNotEmpty()) {
                        Text(
                            text = "${currentMatchIndex + 1}/${allMatches.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = {
                            currentMatchIndex = if (currentMatchIndex > 0) currentMatchIndex - 1 else allMatches.size - 1
                        }) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = {
                            currentMatchIndex = if (currentMatchIndex < allMatches.size - 1) currentMatchIndex + 1 else 0
                        }) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { showGoToDialog = true }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (pageCount > 0) stringResource(R.string.page_format, currentPage + 1, pageCount) else stringResource(R.string.loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    ) { padding ->
        val r = renderer
        if (r == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.1f) {
                                scale = 1f; offsetX = 0f; offsetY = 0f
                            } else {
                                scale = 2f
                            }
                        }
                    )
                }
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(state = transformableState)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
            ) {
                items(pageCount) { pageIndex ->
                    PdfPageItem(
                        renderer = r,
                        pageIndex = pageIndex,
                        pageCache = pageCache,
                        currentPage = currentPage,
                        matchesOnPage = matchesByPage[pageIndex] ?: emptyList(),
                        currentGlobalMatch = allMatches.getOrNull(currentMatchIndex),
                        pageScaleMap = pageScaleMap
                    )
                }
            }
        }
    }

    // Go-to-page dialog
    if (showGoToDialog) {
        GoToPageDialog(
            currentPage = currentPage + 1,
            totalPages = pageCount,
            onDismiss = { showGoToDialog = false },
            onGoTo = { page ->
                showGoToDialog = false
                val target = (page - 1).coerceIn(0, pageCount - 1)
                coroutineScope.launch {
                    listState.animateScrollToItem(target)
                }
            }
        )
    }
}

@Composable
private fun PdfPageItem(
    renderer: PdfRenderer,
    pageIndex: Int,
    pageCache: MutableMap<Int, Bitmap>,
    currentPage: Int,
    matchesOnPage: List<PdfMatch>,
    currentGlobalMatch: PdfMatch?,
    pageScaleMap: MutableMap<Int, Float>
) {
    var bitmap by remember(pageIndex) { mutableStateOf(pageCache[pageIndex]) }
    var isLoading by remember(pageIndex) { mutableStateOf(bitmap == null) }

    LaunchedEffect(pageIndex) {
        if (bitmap != null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val page = renderer.openPage(pageIndex)
                val scaleF = MAX_PAGE_WIDTH.toFloat() / page.width.coerceAtLeast(1)
                pageScaleMap[pageIndex] = scaleF
                val w = (page.width * scaleF).toInt().coerceAtLeast(1)
                val h = (page.height * scaleF).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                // Evict old cache entries (no recycle — composables may still reference them)
                if (pageCache.size >= CACHE_SIZE) {
                    val keysToRemove = pageCache.keys.filter { key ->
                        key < currentPage - 2 || key > currentPage + 2
                    }
                    for (key in keysToRemove) {
                        pageCache.remove(key)
                    }
                }
                pageCache[pageIndex] = bmp
                bitmap = bmp
            } catch (_: Exception) {
                // Page render failed
            }
        }
        isLoading = false
    }

    val bmp = bitmap
    if (bmp != null && !bmp.isRecycled) {
        val highlightColor = Color(0x66FFEB3B) // yellow 40% alpha
        val currentHighlightColor = Color(0x99FF9800) // orange 60% alpha — current match

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 2.dp)
        ) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth()
            )
            if (matchesOnPage.isNotEmpty()) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val displayScale = size.width / bmp.width.toFloat()
                    val scaleF = pageScaleMap[pageIndex] ?: 1f
                    val totalScale = scaleF * displayScale
                    for (match in matchesOnPage) {
                        val isCurrent = match == currentGlobalMatch
                        val color = if (isCurrent) currentHighlightColor else highlightColor
                        for (rect in match.rects) {
                            drawRect(
                                color = color,
                                topLeft = Offset(rect.left * totalScale, rect.top * totalScale),
                                size = Size(
                                    (rect.right - rect.left) * totalScale,
                                    (rect.bottom - rect.top) * totalScale
                                )
                            )
                        }
                    }
                }
            }
        }
    } else if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun GoToPageDialog(
    currentPage: Int,
    totalPages: Int,
    onDismiss: () -> Unit,
    onGoTo: (Int) -> Unit
) {
    var pageInput by remember { mutableStateOf(currentPage.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.go_to_page)) },
        text = {
            Column {
                Text(stringResource(R.string.enter_page_number, totalPages))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { pageInput = it.filter { c -> c.isDigit() } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val page = pageInput.toIntOrNull()
                            if (page != null && page in 1..totalPages) {
                                onGoTo(page)
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val page = pageInput.toIntOrNull()
                    if (page != null && page in 1..totalPages) {
                        onGoTo(page)
                    }
                },
                enabled = pageInput.toIntOrNull()?.let { it in 1..totalPages } == true
            ) {
                Text(stringResource(R.string.go))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
