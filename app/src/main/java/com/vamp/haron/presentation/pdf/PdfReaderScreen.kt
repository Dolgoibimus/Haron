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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vamp.haron.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

private const val MAX_PAGE_WIDTH = 2048
private const val CACHE_SIZE = 5

private val DOCUMENT_EXTENSIONS = setOf("doc", "docx", "odt", "rtf")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    filePath: String,
    fileName: String,
    onBack: () -> Unit
) {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    val isDocumentMode = extension in DOCUMENT_EXTENSIONS

    if (isDocumentMode) {
        DocumentReaderContent(filePath, fileName, extension, onBack)
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
    var documentText by remember { mutableStateOf<String?>(null) }
    var lineCount by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

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

    // Extract text
    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            try {
                val text = extractDocumentText(context, filePath, extension)
                documentText = text
                lineCount = text.lines().size
            } catch (e: Exception) {
                error = e.message ?: context.getString(R.string.read_document_failed)
            }
            isLoading = false
        }
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
                        text = fileName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
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
                    .transformable(state = transformableState)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                )
            }
        }
    }
}

// --- Text extraction helpers ---

private fun extractDocumentText(context: Context, filePath: String, extension: String): String {
    return when (extension) {
        "docx" -> extractDocx(context, filePath)
        "odt" -> extractOdt(context, filePath)
        "doc" -> extractDoc(context, filePath)
        "rtf" -> extractRtf(context, filePath)
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
            return xml.split("</w:p>").map { para ->
                Regex("<w:t[^>]*>([^<]*)</w:t>").findAll(para)
                    .map { it.groupValues[1] }
                    .joinToString("")
            }.filter { it.isNotBlank() }.joinToString("\n")
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
            return xml.split("</text:p>").map { para ->
                para.replace(Regex("<[^>]+>"), "")
            }.filter { it.isNotBlank() }.joinToString("\n")
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
            } catch (e: Exception) {
                error = e.message ?: context.getString(R.string.pdf_open_error)
            }
        }
    }

    // Track current page
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
            currentPage = index
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            pageCache.values.forEach { it.recycle() }
            pageCache.clear()
            renderer?.close()
            pfd?.close()
        }
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
                        currentPage = currentPage
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
    currentPage: Int
) {
    var bitmap by remember(pageIndex) { mutableStateOf(pageCache[pageIndex]) }
    var isLoading by remember(pageIndex) { mutableStateOf(bitmap == null) }

    LaunchedEffect(pageIndex) {
        if (bitmap != null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val page = renderer.openPage(pageIndex)
                val scaleF = MAX_PAGE_WIDTH.toFloat() / page.width.coerceAtLeast(1)
                val w = (page.width * scaleF).toInt().coerceAtLeast(1)
                val h = (page.height * scaleF).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                // Evict old cache entries
                if (pageCache.size >= CACHE_SIZE) {
                    val keysToRemove = pageCache.keys.filter { key ->
                        key < currentPage - 2 || key > currentPage + 2
                    }
                    for (key in keysToRemove) {
                        pageCache.remove(key)?.recycle()
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
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 2.dp)
        )
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
