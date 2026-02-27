package com.vamp.haron.presentation.editor

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vamp.haron.R
import com.vamp.haron.domain.model.SearchNavigationHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val MAX_FILE_SIZE = 1024 * 1024 // 1 MB
private const val MAX_UNDO_STACK = 50
private const val UNDO_DEBOUNCE_MS = 500L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(
    filePath: String,
    fileName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val highlightQuery = remember { SearchNavigationHolder.highlightQuery }

    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var savedText by remember { mutableStateOf("") }
    var isModified by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isTruncated by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    // Search match navigation
    val matchIndices = remember(textFieldValue.text, highlightQuery) {
        if (highlightQuery.isNullOrBlank()) emptyList()
        else {
            val indices = mutableListOf<Int>()
            val lower = textFieldValue.text.lowercase()
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
    var currentMatchIndex by remember { mutableIntStateOf(0) }

    // Pinch-to-zoom font size
    var fontSizeSp by remember { mutableFloatStateOf(13f) }

    // Undo/Redo stacks
    var undoStack by remember { mutableStateOf(listOf<TextFieldValue>()) }
    var redoStack by remember { mutableStateOf(listOf<TextFieldValue>()) }
    var lastUndoTime by remember { mutableStateOf(0L) }

    // Cursor line
    val cursorLine = remember(textFieldValue.selection) {
        val text = textFieldValue.text
        val pos = textFieldValue.selection.start.coerceIn(0, text.length)
        text.substring(0, pos).count { it == '\n' } + 1
    }

    val totalLines = remember(textFieldValue.text) {
        textFieldValue.text.count { it == '\n' } + 1
    }

    // Load file
    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            try {
                val content = if (filePath.startsWith("content://")) {
                    val uri = Uri.parse(filePath)
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bytes = stream.readBytes()
                        if (bytes.size > MAX_FILE_SIZE) {
                            isTruncated = true
                            String(bytes, 0, MAX_FILE_SIZE, Charsets.UTF_8)
                        } else {
                            String(bytes, Charsets.UTF_8)
                        }
                    } ?: ""
                } else {
                    val file = File(filePath)
                    if (file.length() > MAX_FILE_SIZE) {
                        isTruncated = true
                        file.inputStream().use { stream ->
                            val bytes = ByteArray(MAX_FILE_SIZE)
                            stream.read(bytes)
                            String(bytes, Charsets.UTF_8)
                        }
                    } else {
                        file.readText(Charsets.UTF_8)
                    }
                }
                textFieldValue = TextFieldValue(content, TextRange(0))
                savedText = content
                isLoading = false
            } catch (e: Exception) {
                loadError = e.message ?: context.getString(R.string.read_file_error)
                isLoading = false
            }
        }
    }

    fun saveFile() {
        scope.launch(Dispatchers.IO) {
            try {
                val content = textFieldValue.text
                if (filePath.startsWith("content://")) {
                    val uri = Uri.parse(filePath)
                    context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                        stream.write(content.toByteArray(Charsets.UTF_8))
                    }
                } else {
                    File(filePath).writeText(content, Charsets.UTF_8)
                }
                savedText = content
                isModified = false
            } catch (_: Exception) { }
        }
    }

    fun pushUndo(value: TextFieldValue) {
        val now = System.currentTimeMillis()
        if (now - lastUndoTime > UNDO_DEBOUNCE_MS) {
            undoStack = (undoStack + value).takeLast(MAX_UNDO_STACK)
            redoStack = emptyList()
            lastUndoTime = now
        }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack = redoStack + textFieldValue
        textFieldValue = undoStack.last()
        undoStack = undoStack.dropLast(1)
        isModified = textFieldValue.text != savedText
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack = undoStack + textFieldValue
        textFieldValue = redoStack.last()
        redoStack = redoStack.dropLast(1)
        isModified = textFieldValue.text != savedText
    }

    BackHandler {
        if (isModified) {
            showExitDialog = true
        } else {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        if (isModified) showExitDialog = true else onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                title = {
                    Text(
                        text = if (isModified) "$fileName *" else fileName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                actions = {
                    if (matchIndices.isNotEmpty()) {
                        Text(
                            text = "${currentMatchIndex + 1}/${matchIndices.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = {
                            val newIdx = if (currentMatchIndex > 0) currentMatchIndex - 1 else matchIndices.size - 1
                            currentMatchIndex = newIdx
                            val pos = matchIndices[newIdx]
                            textFieldValue = textFieldValue.copy(selection = TextRange(pos, pos + (highlightQuery?.length ?: 0)))
                        }) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = {
                            val newIdx = if (currentMatchIndex < matchIndices.size - 1) currentMatchIndex + 1 else 0
                            currentMatchIndex = newIdx
                            val pos = matchIndices[newIdx]
                            textFieldValue = textFieldValue.copy(selection = TextRange(pos, pos + (highlightQuery?.length ?: 0)))
                        }) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }
                    IconButton(
                        onClick = { undo() },
                        enabled = undoStack.isNotEmpty()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
                            contentDescription = stringResource(R.string.undo),
                            tint = if (undoStack.isNotEmpty())
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                    IconButton(
                        onClick = { redo() },
                        enabled = redoStack.isNotEmpty()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Redo,
                            contentDescription = stringResource(R.string.redo),
                            tint = if (redoStack.isNotEmpty())
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                    IconButton(
                        onClick = { saveFile() },
                        enabled = isModified
                    ) {
                        Icon(
                            Icons.Filled.Save,
                            contentDescription = stringResource(R.string.save),
                            tint = if (isModified)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .imePadding()
        ) {
            when {
                isLoading -> {
                    Text(
                        text = stringResource(R.string.loading),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                loadError != null -> {
                    Text(
                        text = stringResource(R.string.error_format, loadError ?: ""),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {
                    val verticalScroll = rememberScrollState()
                    val density = LocalDensity.current
                    val imeBottom = WindowInsets.ime.getBottom(density)

                    // Scroll to cursor when keyboard appears or cursor moves
                    LaunchedEffect(imeBottom, cursorLine) {
                        if (imeBottom > 0 && verticalScroll.maxValue > 0 && totalLines > 0) {
                            val lineHeight = verticalScroll.maxValue.toFloat() / totalLines.coerceAtLeast(1)
                            val cursorY = (cursorLine - 1) * lineHeight
                            val viewportHeight = verticalScroll.viewportSize.toFloat()
                            val targetScroll = (cursorY - viewportHeight / 2).toInt()
                                .coerceIn(0, verticalScroll.maxValue)
                            verticalScroll.animateScrollTo(targetScroll)
                        }
                    }

                    // Scroll to match: proportional position
                    LaunchedEffect(currentMatchIndex, matchIndices, verticalScroll.maxValue) {
                        if (matchIndices.isNotEmpty() && currentMatchIndex in matchIndices.indices && verticalScroll.maxValue > 0) {
                            val pos = matchIndices[currentMatchIndex]
                            val fraction = pos.toFloat() / textFieldValue.text.length.coerceAtLeast(1)
                            val scrollTarget = (fraction * verticalScroll.maxValue - verticalScroll.viewportSize / 2f).toInt().coerceIn(0, verticalScroll.maxValue)
                            verticalScroll.animateScrollTo(scrollTarget)
                        }
                    }

                    // Auto-select first match on load
                    LaunchedEffect(matchIndices) {
                        if (matchIndices.isNotEmpty()) {
                            currentMatchIndex = 0
                            val pos = matchIndices[0]
                            textFieldValue = textFieldValue.copy(selection = TextRange(pos, pos + (highlightQuery?.length ?: 0)))
                        }
                    }

                    val monoStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSizeSp.sp,
                        lineHeight = (fontSizeSp * 1.54f).sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val highlightColor = MaterialTheme.colorScheme.primary
                    val highlightTransformation = remember(highlightQuery, highlightColor) {
                        if (highlightQuery.isNullOrBlank()) VisualTransformation.None
                        else SearchHighlightTransformation(highlightQuery, highlightColor)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    do {
                                        val event = awaitPointerEvent()
                                        if (event.changes.size >= 2) {
                                            val zoom = event.calculateZoom()
                                            fontSizeSp = (fontSizeSp * zoom).coerceIn(8f, 32f)
                                            event.changes.forEach { it.consume() }
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                            .verticalScroll(verticalScroll)
                    ) {
                        BasicTextField(
                            value = textFieldValue,
                            onValueChange = { newValue ->
                                if (newValue.text != textFieldValue.text) {
                                    pushUndo(textFieldValue)
                                    isModified = newValue.text != savedText
                                }
                                textFieldValue = newValue
                            },
                            textStyle = monoStyle,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            visualTransformation = highlightTransformation,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
                        )
                    }
                }
            }
            // Status bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.line_format, cursorLine),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                if (isTruncated) {
                    Text(
                        text = stringResource(R.string.file_truncated),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = if (isModified) stringResource(R.string.modified) else stringResource(R.string.saved),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isModified)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Exit dialog
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.unsaved_changes_title)) },
            text = { Text(stringResource(R.string.save_before_exit)) },
            confirmButton = {
                TextButton(onClick = {
                    saveFile()
                    showExitDialog = false
                    onBack()
                }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showExitDialog = false
                    }) {
                        Text(stringResource(R.string.stay))
                    }
                    TextButton(onClick = {
                        showExitDialog = false
                        onBack()
                    }) {
                        Text(stringResource(R.string.dont_save))
                    }
                }
            }
        )
    }
}

private class SearchHighlightTransformation(
    private val query: String,
    private val color: androidx.compose.ui.graphics.Color
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text.text)
        // Copy existing spans
        text.spanStyles.forEach { builder.addStyle(it.item, it.start, it.end) }
        // Add highlight spans
        val lower = text.text.lowercase()
        val lowerQuery = query.lowercase()
        var pos = 0
        while (pos < lower.length) {
            val idx = lower.indexOf(lowerQuery, pos)
            if (idx < 0) break
            builder.addStyle(
                SpanStyle(fontWeight = FontWeight.Bold, color = color),
                idx,
                idx + query.length
            )
            pos = idx + query.length
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}
