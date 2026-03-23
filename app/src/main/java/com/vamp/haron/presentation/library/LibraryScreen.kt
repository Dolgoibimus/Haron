package com.vamp.haron.presentation.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.unit.sp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamp.haron.R
import com.vamp.haron.data.db.entity.BookEntity
import com.vamp.haron.presentation.explorer.components.QuickPreviewDialog
import java.io.File

/** Item in the grid */
private sealed interface GridItem {
    data class AuthorHeader(val name: String) : GridItem
    data class SeriesHeader(val name: String) : GridItem
    data class FolderHeader(val folderName: String, val folderPath: String) : GridItem
    data class Book(val book: BookEntity, val badge: String) : GridItem
    data object Spacer : GridItem
}

/** Build grid items: Author → Series (if 2+) → Covers with badges */
private fun buildGridItems(books: List<BookEntity>, columns: Int): List<GridItem> {
    if (books.isEmpty()) return emptyList()

    // Group by author
    val byAuthor = books.groupBy { it.author.ifBlank { "?" } }
        .toSortedMap(String.CASE_INSENSITIVE_ORDER)

    val result = mutableListOf<GridItem>()

    for ((author, authorBooks) in byAuthor) {
        // Pad previous row
        padRow(result, columns)
        result.add(GridItem.AuthorHeader(author))

        // Group by series
        val bySeries = authorBooks.groupBy { it.series ?: "" }
        val multiBookSeries = bySeries.filter { it.key.isNotBlank() && it.value.size >= 2 }
        val singleOrNoSeries = authorBooks.filter { book ->
            val series = book.series ?: ""
            series.isBlank() || (bySeries[series]?.size ?: 0) < 2
        }

        // Single/no-series books together
        if (singleOrNoSeries.isNotEmpty()) {
            val sorted = singleOrNoSeries.sortedBy { it.title.lowercase() }
            for (book in sorted) {
                val badge = book.seriesNumber?.toString() ?: "—"
                result.add(GridItem.Book(book, badge))
            }
        }

        // Multi-book series (each as separate section)
        for ((series, seriesBooks) in multiBookSeries.toSortedMap(String.CASE_INSENSITIVE_ORDER)) {
            padRow(result, columns)
            result.add(GridItem.SeriesHeader(series))
            val sorted = seriesBooks.sortedWith(
                compareBy<BookEntity> { it.seriesNumber ?: Int.MAX_VALUE }
                    .thenBy { it.title.lowercase() }
            )
            for (book in sorted) {
                val badge = book.seriesNumber?.toString() ?: "—"
                result.add(GridItem.Book(book, badge))
            }
        }
    }
    return result
}

private fun padRow(result: MutableList<GridItem>, columns: Int) {
    if (columns <= 1 || result.isEmpty()) return
    // Count only Book and Spacer items in current row (headers span full width)
    val itemsInRow = result.size % columns
    if (itemsInRow > 0) {
        repeat(columns - itemsInRow) { result.add(GridItem.Spacer) }
    }
}

/** Build items grouped by parent folder: FolderHeader → books sorted by name */
private fun buildFolderGroupedItems(books: List<BookEntity>, columns: Int): List<GridItem> {
    if (books.isEmpty()) return emptyList()
    val byFolder = books.groupBy { File(it.filePath).parent ?: "/" }
        .toSortedMap(String.CASE_INSENSITIVE_ORDER)
    val result = mutableListOf<GridItem>()
    for ((folderPath, folderBooks) in byFolder) {
        padRow(result, columns)
        val folderName = File(folderPath).name.ifEmpty { folderPath }
        result.add(GridItem.FolderHeader(folderName, folderPath))
        val sorted = folderBooks.sortedBy { it.title.lowercase() }
        for (book in sorted) {
            result.add(GridItem.Book(book, book.format.uppercase()))
        }
    }
    padRow(result, columns)
    return result
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onBack: () -> Unit,
    onOpenReader: (String, String) -> Unit,
    onOpenPdfReader: (String, String) -> Unit = { fp, fn -> onOpenReader(fp, fn) },
    onOpenSettings: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val covers by viewModel.coverState.collectAsState()
    val allBooks by viewModel.books.collectAsState()

    val gridColumns = state.gridColumnsByTab[state.selectedTab] ?: 3

    // Filter books by tab + build sorted grid items
    val gridItems = remember(allBooks, state.selectedTab, gridColumns) {
        val filtered = when (state.selectedTab) {
            0 -> allBooks.filter { it.format in listOf("fb2", "fb2.zip", "epub") }
            1 -> allBooks.filter { it.format == "pdf" }
            else -> allBooks.filter { it.format !in listOf("fb2", "fb2.zip", "epub", "pdf") }
        }
        when (state.selectedTab) {
            0 -> buildGridItems(filtered, gridColumns)
            else -> buildFolderGroupedItems(filtered, gridColumns)
        }
    }
    val books = remember(gridItems) { gridItems.filterIsInstance<GridItem.Book>().map { it.book } }

    // Navigation events
    LaunchedEffect(Unit) {
        viewModel.openReader.collect { book ->
            val name = File(book.filePath).name
            if (book.format == "pdf") {
                onOpenPdfReader(book.filePath, name)
            } else {
                onOpenReader(book.filePath, name)
            }
        }
    }

    // Book info dialog
    var infoBook by remember { mutableStateOf<BookEntity?>(null) }
    infoBook?.let { book ->
        BookInfoDialog(
            book = book,
            viewModel = viewModel,
            covers = covers,
            onDismiss = { infoBook = null },
            onRead = { viewModel.openBook(book) }
        )
    }

    // First launch dialog
    if (state.isFirstLaunch) {
        FirstLaunchDialog(
            onScanAll = { viewModel.onFirstLaunchScanAll() },
            onPickFolders = { viewModel.onFirstLaunchPickFolders() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.library_title),
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(24.dp))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.rescan() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh), modifier = Modifier.size(24.dp))
                    }
                    IconButton(onClick = { onOpenSettings() }) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings), modifier = Modifier.size(24.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                expandedHeight = 36.dp
            )
        },
        bottomBar = {
            TabRow(selectedTabIndex = state.selectedTab) {
                Tab(
                    selected = state.selectedTab == 0,
                    onClick = { viewModel.setTab(0) },
                    text = { Text("FB2 / EPUB", style = MaterialTheme.typography.labelMedium) }
                )
                Tab(
                    selected = state.selectedTab == 1,
                    onClick = { viewModel.setTab(1) },
                    text = { Text("PDF", style = MaterialTheme.typography.labelMedium) }
                )
                Tab(
                    selected = state.selectedTab == 2,
                    onClick = { viewModel.setTab(2) },
                    text = { Text(stringResource(R.string.library_tab_other), style = MaterialTheme.typography.labelMedium) }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Scan progress
            state.scanProgress?.let { progress ->
                LinearProgressIndicator(
                    progress = { if (progress.total > 0) progress.scanned.toFloat() / progress.total else 0f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "${progress.scanned}/${progress.total} ${progress.currentFile}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }

            if (books.isEmpty() && state.scanProgress == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.library_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.selectedTab == 0) {
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { viewModel.onFirstLaunchScanAll() }) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.library_scan))
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                var cumulativeZoom = 1f
                                var isPinching = false
                                do {
                                    val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                    if (event.changes.size >= 2) {
                                        isPinching = true
                                        // Consume all to cancel long press on children
                                        event.changes.forEach { it.consume() }
                                        val zoom = event.calculateZoom()
                                        cumulativeZoom *= zoom
                                        if (cumulativeZoom < 0.75f) {
                                            viewModel.adjustGridColumns(1)
                                            cumulativeZoom = 1f
                                        } else if (cumulativeZoom > 1.33f) {
                                            viewModel.adjustGridColumns(-1)
                                            cumulativeZoom = 1f
                                        }
                                    } else if (isPinching) {
                                        // Still consume after pinch started
                                        event.changes.forEach { it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                ) {
                    if (gridColumns == 1) {
                        // List mode
                        androidx.compose.foundation.lazy.LazyColumn(
                            contentPadding = PaddingValues(2.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(gridItems.size) { index ->
                                when (val item = gridItems[index]) {
                                    is GridItem.AuthorHeader -> {
                                        Text(
                                            item.name,
                                            fontSize = 20.sp,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                        )
                                    }
                                    is GridItem.SeriesHeader -> {
                                        Text(
                                            item.name,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 2.dp)
                                        )
                                    }
                                    is GridItem.FolderHeader -> {
                                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                            Text(
                                                item.folderName,
                                                fontSize = 18.sp,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                item.folderPath,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    is GridItem.Book -> BookListItem(
                                        book = item.book,
                                        badge = item.badge,
                                        viewModel = viewModel,
                                        covers = covers,
                                        onClick = { viewModel.openBook(item.book) }
                                    )
                                    is GridItem.Spacer -> {}
                                }
                            }
                        }
                    } else {
                        // Grid mode (3-6 columns)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(gridColumns),
                            contentPadding = PaddingValues(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            for (item in gridItems) {
                                when (item) {
                                    is GridItem.AuthorHeader -> {
                                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                            Text(
                                                item.name,
                                                fontSize = 20.sp,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    is GridItem.SeriesHeader -> {
                                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                            Text(
                                                item.name,
                                                fontSize = 16.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 1.dp)
                                            )
                                        }
                                    }
                                    is GridItem.FolderHeader -> {
                                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                                                Text(
                                                    item.folderName,
                                                    fontSize = 18.sp,
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    item.folderPath,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    is GridItem.Book -> item {
                                        BookCoverItem(
                                            book = item.book,
                                            badge = item.badge,
                                            viewModel = viewModel,
                                            covers = covers,
                                            onClick = { viewModel.openBook(item.book) },
                                            onInfoClick = { infoBook = item.book }
                                        )
                                    }
                                    is GridItem.Spacer -> item {
                                        Spacer(Modifier.height(1.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** List mode item: cover + title + badge */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookListItem(
    book: BookEntity,
    badge: String,
    viewModel: LibraryViewModel,
    covers: Map<String, android.graphics.Bitmap?> = emptyMap(),
    onClick: () -> Unit
) {
    val coverBitmap = covers[book.filePath]
    LaunchedEffect(book.filePath) {
        viewModel.loadCoverIfNeeded(book.filePath, book.format)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(4.dp)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp, 60.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                val bmp = coverBitmap
                if (bmp != null) {
                    Image(bitmap = bmp.asImageBitmap(), contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Text(book.format.uppercase(), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
                // Badge
                Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), RoundedCornerShape(bottomStart = 4.dp))
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                book.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Grid mode item: cover only + badge in top-right, info in top-left */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCoverItem(
    book: BookEntity,
    badge: String,
    viewModel: LibraryViewModel,
    covers: Map<String, android.graphics.Bitmap?> = emptyMap(),
    onClick: () -> Unit,
    onInfoClick: () -> Unit = {}
) {
    val coverBitmap = covers[book.filePath]
    LaunchedEffect(book.filePath) {
        viewModel.loadCoverIfNeeded(book.filePath, book.format)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.67f)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val bmp = coverBitmap
        if (bmp != null) {
            Image(bitmap = bmp.asImageBitmap(), contentDescription = book.title,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Book, contentDescription = null, modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                Text(book.format.uppercase(), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }

        // Info button top-left
        Icon(
            Icons.Filled.Info,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(30.dp)
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(bottomEnd = 6.dp))
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) { onInfoClick() }
                .padding(2.dp),
            tint = Color.White.copy(alpha = 0.8f)
        )

        // Badge top-right
        Text(
            badge,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), RoundedCornerShape(bottomStart = 6.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        )

        // Progress bar bottom
        if (book.progress > 0f) {
            LinearProgressIndicator(
                progress = { book.progress },
                modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun BookInfoDialog(
    book: BookEntity,
    viewModel: LibraryViewModel,
    covers: Map<String, android.graphics.Bitmap?> = emptyMap(),
    onDismiss: () -> Unit,
    onRead: () -> Unit
) {
    val coverBitmap = covers[book.filePath]
    LaunchedEffect(book.filePath) {
        viewModel.loadCoverIfNeeded(book.filePath, book.format)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(book.title.ifBlank { File(book.filePath).name }, fontSize = 16.sp) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                val bmp = coverBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .clip(RoundedCornerShape(4.dp))
                            .align(Alignment.CenterHorizontally)
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (book.author.isNotBlank()) {
                    InfoRow("Автор", book.author)
                }
                if (book.series != null) {
                    InfoRow("Серия", "${book.series}${book.seriesNumber?.let { " #$it" } ?: ""}")
                }
                InfoRow("Формат", book.format.uppercase())
                InfoRow("Размер", formatFileSize(book.fileSize))
                if (book.language != null) {
                    InfoRow("Язык", book.language)
                }
                if (book.annotation != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Аннотация",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        book.annotation,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    book.filePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
            }
        },
        confirmButton = {
            Button(onClick = { onDismiss(); onRead() }) {
                Text(stringResource(R.string.library_read))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            "$label: ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatFileSize(size: Long): String {
    return when {
        size >= 1_048_576 -> "%.1f MB".format(size / 1_048_576.0)
        size >= 1024 -> "%.0f KB".format(size / 1024.0)
        else -> "$size B"
    }
}

@Composable
private fun FirstLaunchDialog(
    onScanAll: () -> Unit,
    onPickFolders: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.library_first_launch_title)) },
        text = {
            Text(stringResource(R.string.library_first_launch_text))
        },
        confirmButton = {
            Button(onClick = onScanAll) {
                Text(stringResource(R.string.library_scan_all))
            }
        },
        dismissButton = {
            TextButton(onClick = onPickFolders) {
                Text(stringResource(R.string.library_pick_folders))
            }
        }
    )
}
