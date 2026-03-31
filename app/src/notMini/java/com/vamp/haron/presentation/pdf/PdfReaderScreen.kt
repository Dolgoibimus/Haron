package com.vamp.haron.presentation.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.unit.sp
import com.vamp.haron.R
import com.vamp.haron.common.util.swipeBackFromLeft
import com.vamp.haron.common.util.PdfMatch
import com.vamp.haron.data.reading.ReadingPositionManager
import com.vamp.haron.domain.model.TransferHolder
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    filePath: String,
    fileName: String,
    onBack: () -> Unit,
    onNavigateToLibrary: (() -> Unit)? = null
) {
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

    PdfReaderContent(filePath, fileName, onBack, onTap = { micVisible = !micVisible }, onNavigateToLibrary = onNavigateToLibrary)
}

// --- PDF reader (existing logic) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfReaderContent(
    filePath: String,
    fileName: String,
    onBack: () -> Unit,
    onTap: () -> Unit = {},
    onNavigateToLibrary: (() -> Unit)? = null
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
    var lastDoubleTapCheck by remember { mutableLongStateOf(0L) }

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
                    if (onNavigateToLibrary != null) {
                        IconButton(onClick = onNavigateToLibrary) {
                            Icon(Icons.Filled.LibraryBooks, contentDescription = stringResource(R.string.navbar_action_library))
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
                    .swipeBackFromLeft(onBack = onBack)
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
                .swipeBackFromLeft(onBack = onBack)
                .padding(padding)
                .pointerInput(Unit) {
                    val slop = viewConfiguration.touchSlop
                    val edgePx = 30.dp.toPx()
                    val swipeThreshold = 80.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startPos = down.position
                        val isFromLeftEdge = startPos.x < edgePx
                        var wasDrag = false
                        var totalDx = 0f
                        var lastTapTime = 0L
                        do {
                            val event = awaitPointerEvent()
                            val pos = event.changes.firstOrNull()?.position
                            if (pos != null) {
                                val dist = kotlin.math.hypot(
                                    (pos.x - startPos.x).toDouble(),
                                    (pos.y - startPos.y).toDouble()
                                )
                                if (dist > slop) wasDrag = true
                                totalDx = pos.x - startPos.x
                            }
                        } while (event.changes.any { c -> c.pressed })
                        if (isFromLeftEdge && totalDx > swipeThreshold) {
                            onBack()
                        } else if (!wasDrag) {
                            // Check double-tap
                            val now = System.currentTimeMillis()
                            if (now - lastDoubleTapCheck < 300) {
                                if (scale > 1.1f) {
                                    scale = 1f; offsetX = 0f; offsetY = 0f
                                } else {
                                    scale = 2f
                                }
                                lastDoubleTapCheck = 0L
                            } else {
                                lastDoubleTapCheck = now
                                onTap()
                            }
                        }
                    }
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
