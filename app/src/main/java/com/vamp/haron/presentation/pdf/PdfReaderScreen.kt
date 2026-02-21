package com.vamp.haron.presentation.pdf

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val MAX_PAGE_WIDTH = 2048
private const val CACHE_SIZE = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
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
                    error = "Не удалось открыть PDF"
                }
            } catch (e: Exception) {
                error = e.message ?: "Ошибка открытия PDF"
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
                TextButton(onClick = onBack) { Text("Назад") }
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
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
                    text = if (pageCount > 0) "Страница ${currentPage + 1} / $pageCount" else "Загрузка…",
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
            contentDescription = "Страница ${pageIndex + 1}",
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
        title = { Text("Перейти к странице") },
        text = {
            Column {
                Text("Введите номер страницы (1 — $totalPages):")
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
                Text("Перейти")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
