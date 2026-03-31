package com.vamp.haron.presentation.editor

import android.net.Uri
import android.graphics.Typeface
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.draw.clipToBounds
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.vamp.haron.R
import com.vamp.haron.common.util.swipeBackFromLeft
import com.vamp.haron.data.reading.ReadingPositionManager
import com.vamp.haron.domain.model.SearchNavigationHolder
import com.vamp.haron.domain.model.TransferHolder
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.event.ContentChangeEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val MAX_FILE_SIZE = 1024 * 1024 // 1 MB
private const val DISPLAY_CHUNK_SIZE = 4096 // max chars per LazyColumn item
private const val MAX_EDIT_SIZE = 256 * 1024 // max chars for edit mode (BasicTextField limit)
private const val MAX_UNDO_STACK = 50
private const val UNDO_DEBOUNCE_MS = 500L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(
    filePath: String,
    fileName: String,
    cloudUri: String? = null,
    otherPanelPath: String? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val highlightQuery = remember { SearchNavigationHolder.highlightQuery }
    val isDarkTheme = isSystemInDarkTheme()

    var isEditMode by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Whether file is too large for BasicTextField (use Sora Editor instead)
    val useSoraEditor = remember { mutableStateOf(false) }
    // Whether the original file had no newlines (need to strip inserted \n on save)
    var soraInsertedNewlines by remember { mutableStateOf(false) }
    // Reference to Sora CodeEditor instance for large files
    var soraEditorRef by remember { mutableStateOf<CodeEditor?>(null) }
    // Reactive state for Sora undo/redo (plain method calls don't trigger recomposition)
    var soraCanUndo by remember { mutableStateOf(false) }
    var soraCanRedo by remember { mutableStateOf(false) }

    // VoiceFab: visible in view mode, hidden in edit mode
    LaunchedEffect(isEditMode) {
        TransferHolder.voiceFabVisible.value = !isEditMode
        if (isEditMode && !useSoraEditor.value) {
            // Small delay to let recomposition apply readOnly=false before requesting focus
            delay(100)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
        if (!isEditMode) {
            // Hide keyboard (works for both Compose BasicTextField and Sora Editor View)
            keyboardController?.hide()
            val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val view = soraEditorRef ?: (context as? android.app.Activity)?.currentFocus
            if (view != null) {
                imm?.hideSoftInputFromWindow(view.windowToken, 0)
            }
        }
    }
    DisposableEffect(Unit) { onDispose { TransferHolder.voiceFabVisible.value = true } }

    // Diagnostic logging for cloud text editing
    LaunchedEffect(Unit) {
        com.vamp.core.logger.EcosystemLogger.d(
            com.vamp.haron.common.constants.HaronConstants.TAG,
            "TextEditorScreen: filePath=$filePath, fileName=$fileName, cloudUri=$cloudUri"
        )
    }

    // === State: raw text for view mode, TextFieldValue only for edit mode ===
    var rawText by remember { mutableStateOf("") }
    var textLines by remember { mutableStateOf(emptyList<String>()) } // display chunks for LazyColumn
    var totalLineCount by remember { mutableIntStateOf(1) } // actual \n count for status bar
    var savedText by remember { mutableStateOf("") }
    var savedCursorPos by remember { mutableIntStateOf(0) }
    // TextFieldValue created ONLY when entering edit mode (avoids 600KB on main thread)
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var editModeInitialized by remember { mutableStateOf(false) }

    var isModified by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isTruncated by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showCloudSaveDialog by remember { mutableStateOf(false) }
    var savedScrollTarget by remember { mutableIntStateOf(0) }

    // TextFieldValue is initialized in the Edit button onClick (not in LaunchedEffect)
    // to avoid empty first frame and to check file size before entering edit mode

    // Search match navigation (uses rawText, not textFieldValue)
    val matchIndices = remember(rawText, highlightQuery) {
        if (highlightQuery.isNullOrBlank() || rawText.isEmpty()) emptyList()
        else {
            val indices = mutableListOf<Int>()
            val lower = rawText.lowercase()
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

    // Cursor line / total lines (lightweight: use textLines.size, savedCursorPos in view mode)
    val cursorLine = if (isEditMode && editModeInitialized) {
        remember(textFieldValue.selection) {
            val text = textFieldValue.text
            val pos = textFieldValue.selection.start.coerceIn(0, text.length)
            text.substring(0, pos).count { it == '\n' } + 1
        }
    } else {
        remember(savedCursorPos, rawText) {
            if (rawText.isEmpty()) 1
            else rawText.substring(0, savedCursorPos.coerceIn(0, rawText.length)).count { it == '\n' } + 1
        }
    }

    val totalLines = totalLineCount

    // Load file
    LaunchedEffect(filePath) {
        data class LoadResult(
            val content: String? = null,
            val chunks: List<String> = emptyList(),
            val lineCount: Int = 1,
            val error: String? = null,
            val truncated: Boolean = false,
            val cursorPos: Int = 0,
            val scrollTarget: Int = 0,
            val zoomSp: Float = 0f
        )
        val t0 = System.currentTimeMillis()
        val result = withContext(Dispatchers.IO) {
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
                // Split into display chunks on IO thread (handles single-line files)
                val splitLines = content.split('\n')
                val lineCount = splitLines.size
                val chunks = buildList {
                    for (line in splitLines) {
                        if (line.length <= DISPLAY_CHUNK_SIZE) {
                            add(line)
                        } else {
                            var i = 0
                            while (i < line.length) {
                                add(line.substring(i, minOf(i + DISPLAY_CHUNK_SIZE, line.length)))
                                i += DISPLAY_CHUNK_SIZE
                            }
                        }
                    }
                    if (isEmpty()) add("")
                }
                val saved = ReadingPositionManager.get(filePath)
                val cursor = if (!highlightQuery.isNullOrBlank()) 0
                    else saved?.position?.coerceIn(0, content.length) ?: 0
                val scroll = if (highlightQuery.isNullOrBlank()) saved?.positionExtra?.toInt() ?: 0 else 0
                val zoomSaved = ReadingPositionManager.get("zoom:$filePath")
                val zoom = if (zoomSaved != null && zoomSaved.position > 0)
                    (zoomSaved.position.toFloat() / 100f).coerceIn(8f, 32f) else 0f
                LoadResult(content = content, chunks = chunks, lineCount = lineCount, truncated = isTruncated, cursorPos = cursor, scrollTarget = scroll, zoomSp = zoom)
            } catch (e: Exception) {
                LoadResult(error = e.message ?: context.getString(R.string.read_file_error))
            }
        }
        val t1 = System.currentTimeMillis()
        if (result.content != null) {
            rawText = result.content
            textLines = result.chunks
            totalLineCount = result.lineCount
            savedCursorPos = result.cursorPos
            savedScrollTarget = result.scrollTarget
            if (result.zoomSp > 0f) fontSizeSp = result.zoomSp
            savedText = result.content
            isLoading = false
            com.vamp.core.logger.EcosystemLogger.d(
                com.vamp.haron.common.constants.HaronConstants.TAG,
                "TextEditor: loaded ${result.content.length} chars, ${result.lineCount} lines, ${result.chunks.size} chunks in ${t1 - t0}ms"
            )
        } else {
            loadError = result.error
            isLoading = false
        }
    }

    fun saveFile() {
        // Capture content on main thread BEFORE switching to IO (Sora Editor is a View)
        val content = if (isEditMode && useSoraEditor.value) {
            val soraText = soraEditorRef?.text?.toString() ?: rawText
            if (soraInsertedNewlines) soraText.replace("\n", "") else soraText
        } else if (isEditMode) {
            textFieldValue.text
        } else rawText
        com.vamp.core.logger.EcosystemLogger.d(
            com.vamp.haron.common.constants.HaronConstants.TAG,
            "TextEditor saveFile: capturing ${content.length} chars (isEdit=$isEditMode, sora=${useSoraEditor.value})"
        )
        scope.launch(Dispatchers.IO) {
            try {
                @Suppress("NAME_SHADOWING")
                val content = content
                if (filePath.startsWith("content://")) {
                    val uri = Uri.parse(filePath)
                    context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                        stream.write(content.toByteArray(Charsets.UTF_8))
                    }
                } else {
                    File(filePath).writeText(content, Charsets.UTF_8)
                }
                rawText = content
                textLines = splitTextToChunks(content)
                totalLineCount = content.count { it == '\n' } + 1
                savedText = content
                isModified = false
                com.vamp.core.logger.EcosystemLogger.d(
                    com.vamp.haron.common.constants.HaronConstants.TAG,
                    "TextEditor saveFile: saved ${content.length} chars to $filePath"
                )
            } catch (e: Exception) {
                com.vamp.core.logger.EcosystemLogger.e(
                    com.vamp.haron.common.constants.HaronConstants.TAG,
                    "TextEditor saveFile error: ${e.message}"
                )
            }
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
        if (isEditMode && isModified) {
            showExitDialog = true
        } else if (isEditMode) {
            isEditMode = false
        } else {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        if (isEditMode && isModified) showExitDialog = true
                        else if (isEditMode) isEditMode = false
                        else onBack()
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
                            if (isEditMode) {
                                val pos = matchIndices[newIdx]
                                textFieldValue = textFieldValue.copy(selection = TextRange(pos, pos + (highlightQuery?.length ?: 0)))
                            }
                        }) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = {
                            val newIdx = if (currentMatchIndex < matchIndices.size - 1) currentMatchIndex + 1 else 0
                            currentMatchIndex = newIdx
                            if (isEditMode) {
                                val pos = matchIndices[newIdx]
                                textFieldValue = textFieldValue.copy(selection = TextRange(pos, pos + (highlightQuery?.length ?: 0)))
                            }
                        }) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (isEditMode) {
                        if (useSoraEditor.value) {
                            // Sora Editor has built-in undo/redo
                            IconButton(
                                onClick = {
                                    soraEditorRef?.undo()
                                    soraCanUndo = soraEditorRef?.canUndo() == true
                                    soraCanRedo = soraEditorRef?.canRedo() == true
                                },
                                enabled = soraCanUndo
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = stringResource(R.string.undo),
                                    tint = if (soraCanUndo)
                                        MaterialTheme.colorScheme.onSurface
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                            }
                            IconButton(
                                onClick = {
                                    soraEditorRef?.redo()
                                    soraCanUndo = soraEditorRef?.canUndo() == true
                                    soraCanRedo = soraEditorRef?.canRedo() == true
                                },
                                enabled = soraCanRedo
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Redo,
                                    contentDescription = stringResource(R.string.redo),
                                    tint = if (soraCanRedo)
                                        MaterialTheme.colorScheme.onSurface
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                            }
                        } else {
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
                        }
                        IconButton(
                            onClick = {
                                if (cloudUri != null) {
                                    saveFile()
                                    showCloudSaveDialog = true
                                } else {
                                    saveFile()
                                    isEditMode = false
                                }
                            }
                        ) {
                            Icon(
                                Icons.Filled.Save,
                                contentDescription = stringResource(R.string.save),
                                tint = if (isModified)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        IconButton(onClick = {
                            useSoraEditor.value = true
                            isEditMode = true
                        }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.edit),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    // Theme picker button
                    var showThemePicker by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showThemePicker = true }) {
                            Icon(
                                Icons.Filled.Palette,
                                contentDescription = stringResource(R.string.editor_theme),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        DropdownMenu(
                            expanded = showThemePicker,
                            onDismissRequest = { showThemePicker = false }
                        ) {
                            SyntaxHighlightHelper.themes.forEach { theme ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = theme.displayName,
                                            color = if (theme.id == SyntaxHighlightHelper.currentThemeId)
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (theme.isDark) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    onClick = {
                                        showThemePicker = false
                                        soraEditorRef?.let { editor ->
                                            SyntaxHighlightHelper.switchTheme(editor, theme.id)
                                        }
                                    }
                                )
                            }
                        }
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
                .swipeBackFromLeft(onBack = {
                    if (isEditMode && isModified) showExitDialog = true
                    else if (isEditMode) isEditMode = false
                    else onBack()
                })
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
                    val monoStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSizeSp.sp,
                        lineHeight = (fontSizeSp * 1.54f).sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val highlightColor = MaterialTheme.colorScheme.primary

                    // Pinch-to-zoom modifier (shared between modes)
                    val pinchZoomModifier = Modifier.pointerInput(Unit) {
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

                    val usesSora = isEditMode && useSoraEditor.value
                    // Always use Sora for view mode (syntax highlighting + line numbers)
                    val useSoraReadOnly = !isEditMode

                    if (usesSora || useSoraReadOnly) {
                        // === Sora Editor (edit mode or read-only view with syntax highlighting) ===
                        val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
                        val bgColor = MaterialTheme.colorScheme.surface.toArgb()
                        val cursorColor = MaterialTheme.colorScheme.primary.toArgb()
                        val lineNumColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
                        val selectionColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f).toArgb()

                        DisposableEffect(filePath) {
                            onDispose {
                                val editor = soraEditorRef
                                if (editor != null) {
                                    val cursor = editor.cursor
                                    // Save line-based position (approximate char offset)
                                    val lines = editor.text
                                    var charPos = 0
                                    for (i in 0 until cursor.leftLine.coerceAtMost(lines.lineCount - 1)) {
                                        charPos += lines.getColumnCount(i) + 1 // +1 for \n
                                    }
                                    charPos += cursor.leftColumn
                                    ReadingPositionManager.saveAsync(filePath, charPos, 0L)
                                    ReadingPositionManager.saveAsync("zoom:$filePath", (fontSizeSp * 100).toInt())
                                    editor.release()
                                    soraEditorRef = null
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(start = 10.dp, end = 10.dp)
                                .clipToBounds()
                        ) {
                        AndroidView(
                            factory = { ctx ->
                                CodeEditor(ctx).apply {
                                    typefaceText = Typeface.MONOSPACE
                                    setTextSize(fontSizeSp)
                                    setWordwrap(true)
                                    isLineNumberEnabled = true
                                    isScalable = true
                                    isEditable = isEditMode
                                    setDividerWidth(0f)
                                    // Plain colors first (fast), TextMate applied async after
                                    val scheme = EditorColorScheme()
                                    scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, bgColor)
                                    scheme.setColor(EditorColorScheme.TEXT_NORMAL, textColor)
                                    scheme.setColor(EditorColorScheme.LINE_NUMBER, lineNumColor)
                                    scheme.setColor(EditorColorScheme.SELECTION_INSERT, cursorColor)
                                    scheme.setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, selectionColor)
                                    scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, bgColor)
                                    scheme.setColor(EditorColorScheme.CURRENT_LINE, bgColor)
                                    scheme.setColor(EditorColorScheme.LINE_DIVIDER, bgColor)
                                    setColorScheme(scheme)
                                    // Pre-process: break long lines for Sora (can't word-wrap 655K-char lines)
                                    val maxLineLen = 200
                                    val needsBreaking = rawText.lines().any { it.length > maxLineLen * 2 }
                                    val editorText = if (needsBreaking) {
                                        soraInsertedNewlines = true
                                        buildString {
                                            for (line in rawText.split('\n')) {
                                                if (line.length <= maxLineLen) {
                                                    append(line)
                                                    append('\n')
                                                } else {
                                                    var i = 0
                                                    while (i < line.length) {
                                                        val end = minOf(i + maxLineLen, line.length)
                                                        append(line, i, end)
                                                        append('\n')
                                                        i = end
                                                    }
                                                }
                                            }
                                        }.trimEnd('\n')
                                    } else {
                                        soraInsertedNewlines = false
                                        rawText
                                    }
                                    setText(editorText)
                                    // Track modifications
                                    subscribeAlways(ContentChangeEvent::class.java) {
                                        isModified = true
                                        soraCanUndo = canUndo()
                                        soraCanRedo = canRedo()
                                    }
                                    soraEditorRef = this
                                    com.vamp.core.logger.EcosystemLogger.d(
                                        com.vamp.haron.common.constants.HaronConstants.TAG,
                                        "TextEditor: Sora Editor initialized for ${rawText.length} chars"
                                    )
                                }
                            },
                            update = { editor ->
                                editor.isEditable = isEditMode
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        // Apply syntax highlighting async (after editor is created)
                        LaunchedEffect(soraEditorRef) {
                            val editor = soraEditorRef ?: return@LaunchedEffect
                            withContext(Dispatchers.Default) {
                                SyntaxHighlightHelper.init(context)
                                SyntaxHighlightHelper.prepareLanguage(fileName)
                            }
                            // Apply on main thread (Sora API requires it)
                            SyntaxHighlightHelper.applyPrepared(editor, isDarkTheme)
                        }
                        } // end Box
                    } else if (isEditMode) {
                        // === EDIT MODE (small file): BasicTextField with verticalScroll ===
                        val verticalScroll = rememberScrollState()
                        val density = LocalDensity.current
                        val imeBottom = WindowInsets.ime.getBottom(density)
                        val highlightTransformation = remember(highlightQuery, highlightColor) {
                            if (highlightQuery.isNullOrBlank()) VisualTransformation.None
                            else SearchHighlightTransformation(highlightQuery, highlightColor)
                        }

                        LaunchedEffect(savedScrollTarget) {
                            if (savedScrollTarget > 0) verticalScroll.scrollTo(savedScrollTarget)
                        }

                        LaunchedEffect(filePath) {
                            snapshotFlow {
                                Triple(verticalScroll.value, textFieldValue.selection.start, fontSizeSp)
                            }.collectLatest { (scroll, cursor, zoom) ->
                                delay(1000)
                                withContext(Dispatchers.IO) {
                                    ReadingPositionManager.save(filePath, cursor, scroll.toLong())
                                    ReadingPositionManager.save("zoom:$filePath", (zoom * 100).toInt())
                                }
                            }
                        }

                        DisposableEffect(filePath) {
                            onDispose {
                                ReadingPositionManager.saveAsync(filePath, textFieldValue.selection.start, verticalScroll.value.toLong())
                                ReadingPositionManager.saveAsync("zoom:$filePath", (fontSizeSp * 100).toInt())
                            }
                        }

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

                        LaunchedEffect(currentMatchIndex, matchIndices, verticalScroll.maxValue) {
                            if (matchIndices.isNotEmpty() && currentMatchIndex in matchIndices.indices && verticalScroll.maxValue > 0) {
                                val pos = matchIndices[currentMatchIndex]
                                val fraction = pos.toFloat() / rawText.length.coerceAtLeast(1)
                                val scrollTarget = (fraction * verticalScroll.maxValue - verticalScroll.viewportSize / 2f).toInt().coerceIn(0, verticalScroll.maxValue)
                                verticalScroll.animateScrollTo(scrollTarget)
                            }
                        }

                        LaunchedEffect(matchIndices) {
                            if (matchIndices.isNotEmpty()) {
                                currentMatchIndex = 0
                                val pos = matchIndices[0]
                                textFieldValue = textFieldValue.copy(selection = TextRange(pos, pos + (highlightQuery?.length ?: 0)))
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .then(pinchZoomModifier)
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
                                readOnly = false,
                                textStyle = monoStyle,
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                visualTransformation = highlightTransformation,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .padding(start = 4.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
                            )
                        }
                    } else {
                        // === VIEW MODE: LazyColumn for performance (no TextFieldValue on main thread) ===
                        val lazyListState = rememberLazyListState()

                        // Restore position from saved cursor offset (ratio-based for chunk compatibility)
                        LaunchedEffect(textLines) {
                            if (textLines.isNotEmpty() && savedCursorPos > 0 && rawText.isNotEmpty()) {
                                val ratio = savedCursorPos.toFloat() / rawText.length
                                val chunkIdx = (ratio * textLines.size).toInt()
                                    .coerceIn(0, textLines.size - 1)
                                if (chunkIdx > 0) {
                                    lazyListState.scrollToItem(chunkIdx)
                                }
                            }
                        }

                        // Save position (debounced)
                        LaunchedEffect(filePath) {
                            snapshotFlow {
                                Triple(lazyListState.firstVisibleItemIndex, lazyListState.firstVisibleItemScrollOffset, fontSizeSp)
                            }.collectLatest { (chunkIdx, offset, zoom) ->
                                delay(1000)
                                // Ratio-based char position for chunk compatibility
                                val charPos = if (textLines.isNotEmpty() && rawText.isNotEmpty()) {
                                    (chunkIdx.toFloat() / textLines.size * rawText.length).toInt()
                                } else 0
                                withContext(Dispatchers.IO) {
                                    ReadingPositionManager.save(filePath, charPos, offset.toLong())
                                    ReadingPositionManager.save("zoom:$filePath", (zoom * 100).toInt())
                                }
                            }
                        }

                        // Save on exit
                        DisposableEffect(filePath) {
                            onDispose {
                                val chunkIdx = lazyListState.firstVisibleItemIndex
                                val charPos = if (textLines.isNotEmpty() && rawText.isNotEmpty()) {
                                    (chunkIdx.toFloat() / textLines.size * rawText.length).toInt()
                                } else 0
                                ReadingPositionManager.saveAsync(filePath, charPos, lazyListState.firstVisibleItemScrollOffset.toLong())
                                ReadingPositionManager.saveAsync("zoom:$filePath", (fontSizeSp * 100).toInt())
                            }
                        }

                        // Scroll to search match (ratio-based for chunk compatibility)
                        LaunchedEffect(currentMatchIndex, matchIndices) {
                            if (matchIndices.isNotEmpty() && currentMatchIndex in matchIndices.indices && textLines.isNotEmpty() && rawText.isNotEmpty()) {
                                val charPos = matchIndices[currentMatchIndex].coerceIn(0, rawText.length)
                                val ratio = charPos.toFloat() / rawText.length
                                val chunkIdx = (ratio * textLines.size).toInt()
                                    .coerceIn(0, textLines.size - 1)
                                lazyListState.animateScrollToItem(chunkIdx)
                            }
                        }

                        // Auto-select first match (just set index, no TextFieldValue update)
                        LaunchedEffect(matchIndices) {
                            if (matchIndices.isNotEmpty()) {
                                currentMatchIndex = 0
                            }
                        }

                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .then(pinchZoomModifier)
                        ) {
                            itemsIndexed(textLines) { index, line ->
                                val annotatedLine = if (!highlightQuery.isNullOrBlank()) {
                                    buildHighlightedLine(line, highlightQuery, highlightColor)
                                } else {
                                    AnnotatedString(line)
                                }
                                Text(
                                    text = annotatedLine,
                                    style = monoStyle,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            start = 2.dp,
                                            end = 2.dp,
                                            top = if (index == 0) 8.dp else 0.dp,
                                            bottom = if (index == textLines.lastIndex) 8.dp else 0.dp
                                        )
                                )
                            }
                        }
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

    // Cloud helpers (shared between save button dialog and exit dialog)
    val uploadToCloud: () -> Unit = {
        if (cloudUri != null) {
            scope.launch {
                try {
                    val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        CloudManagerEntryPoint::class.java
                    )
                    val cm = entryPoint.cloudManager()
                    com.vamp.core.logger.EcosystemLogger.d(
                        com.vamp.haron.common.constants.HaronConstants.TAG,
                        "TextEditor cloud save: cloudUri=$cloudUri, filePath=$filePath"
                    )
                    val parsed = cm.parseCloudUri(cloudUri)
                    if (parsed != null) {
                        val (provider, cloudFileId) = parsed
                        cm.updateFileContent(parsed.accountId, cloudFileId, filePath).collect { progress ->
                            if (progress.isComplete) {
                                if (progress.error != null) {
                                    com.vamp.core.logger.EcosystemLogger.e(
                                        com.vamp.haron.common.constants.HaronConstants.TAG,
                                        "TextEditor cloud save error: ${progress.error}"
                                    )
                                    android.widget.Toast.makeText(context, progress.error, android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    android.widget.Toast.makeText(context, context.getString(com.vamp.haron.R.string.cloud_save_success), android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } else {
                        com.vamp.core.logger.EcosystemLogger.e(
                            com.vamp.haron.common.constants.HaronConstants.TAG,
                            "TextEditor cloud save: parseCloudUri returned null for '$cloudUri'"
                        )
                    }
                } catch (e: Exception) {
                    com.vamp.core.logger.EcosystemLogger.e(
                        com.vamp.haron.common.constants.HaronConstants.TAG,
                        "TextEditor cloud save exception: ${e.message}"
                    )
                    android.widget.Toast.makeText(context, "Save error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val saveToLocal: () -> Unit = {
        if (!otherPanelPath.isNullOrBlank()) {
            try {
                val targetDir = java.io.File(otherPanelPath)
                if (!targetDir.exists()) targetDir.mkdirs()
                val targetFile = java.io.File(targetDir, fileName)
                java.io.File(filePath).copyTo(targetFile, overwrite = true)
                android.widget.Toast.makeText(
                    context,
                    context.getString(com.vamp.haron.R.string.cloud_save_local_success, targetFile.absolutePath),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                com.vamp.core.logger.EcosystemLogger.d(
                    com.vamp.haron.common.constants.HaronConstants.TAG,
                    "TextEditor saveToLocal: copied to ${targetFile.absolutePath}"
                )
            } catch (e: Exception) {
                com.vamp.core.logger.EcosystemLogger.e(
                    com.vamp.haron.common.constants.HaronConstants.TAG,
                    "TextEditor saveToLocal error: ${e.message}"
                )
                android.widget.Toast.makeText(context, "Save error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            android.widget.Toast.makeText(
                context,
                context.getString(com.vamp.haron.R.string.cloud_save_no_local_panel),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Cloud save dialog (from save button)
    if (showCloudSaveDialog) {
        AlertDialog(
            onDismissRequest = { showCloudSaveDialog = false },
            title = { Text(stringResource(R.string.cloud_save_title)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            uploadToCloud()
                            showCloudSaveDialog = false
                            isEditMode = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.cloud_save_to_cloud))
                    }
                    TextButton(
                        onClick = {
                            saveToLocal()
                            showCloudSaveDialog = false
                            isEditMode = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.cloud_save_to_local))
                    }
                    TextButton(
                        onClick = {
                            uploadToCloud()
                            saveToLocal()
                            showCloudSaveDialog = false
                            isEditMode = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.cloud_save_to_both))
                    }
                }
            },
            confirmButton = { },
            dismissButton = {
                TextButton(onClick = { showCloudSaveDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Exit dialog — different for cloud vs local files
    if (showExitDialog) {
        if (cloudUri != null) {
            // Cloud file: offer cloud save, local save, both, or discard
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text(stringResource(R.string.cloud_save_title)) },
                text = {
                    Column {
                        Text(stringResource(R.string.save_before_exit))
                        Spacer(Modifier.height(16.dp))
                        TextButton(
                            onClick = {
                                saveFile()
                                uploadToCloud()
                                showExitDialog = false
                                isEditMode = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.cloud_save_to_cloud))
                        }
                        TextButton(
                            onClick = {
                                saveFile()
                                saveToLocal()
                                showExitDialog = false
                                isEditMode = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.cloud_save_to_local))
                        }
                        TextButton(
                            onClick = {
                                saveFile()
                                uploadToCloud()
                                saveToLocal()
                                showExitDialog = false
                                isEditMode = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.cloud_save_to_both))
                        }
                    }
                },
                confirmButton = { },
                dismissButton = {
                    TextButton(onClick = {
                        showExitDialog = false
                        isEditMode = false
                        isModified = false
                        rawText = savedText
                        textLines = splitTextToChunks(savedText)
                        totalLineCount = savedText.count { it == '\n' } + 1
                        if (!useSoraEditor.value) {
                            textFieldValue = TextFieldValue(savedText, TextRange(textFieldValue.selection.start.coerceIn(0, savedText.length)))
                        }
                        soraEditorRef?.release()
                        soraEditorRef = null
                    }) {
                        Text(stringResource(R.string.cloud_save_discard))
                    }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text(stringResource(R.string.unsaved_changes_title)) },
                text = { Text(stringResource(R.string.save_before_exit)) },
                confirmButton = {
                    TextButton(onClick = {
                        saveFile()
                        showExitDialog = false
                        isEditMode = false
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
                            isEditMode = false
                            isModified = false
                            rawText = savedText
                            textLines = splitTextToChunks(savedText)
                            totalLineCount = savedText.count { it == '\n' } + 1
                            // Reload original text in Sora (don't release — still used in read-only view)
                            soraEditorRef?.setText(savedText)
                            soraEditorRef?.let {
                                soraCanUndo = it.canUndo()
                                soraCanRedo = it.canRedo()
                            }
                        }) {
                            Text(stringResource(R.string.dont_save))
                        }
                    }
                }
            )
        }
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface CloudManagerEntryPoint {
    fun cloudManager(): com.vamp.haron.data.cloud.CloudManager
}

/** Split text into display chunks: keeps short lines as-is, breaks long lines into DISPLAY_CHUNK_SIZE pieces */
private fun splitTextToChunks(text: String): List<String> = buildList {
    for (line in text.split('\n')) {
        if (line.length <= DISPLAY_CHUNK_SIZE) {
            add(line)
        } else {
            var i = 0
            while (i < line.length) {
                add(line.substring(i, minOf(i + DISPLAY_CHUNK_SIZE, line.length)))
                i += DISPLAY_CHUNK_SIZE
            }
        }
    }
    if (isEmpty()) add("")
}

private fun buildHighlightedLine(
    line: String,
    query: String,
    color: androidx.compose.ui.graphics.Color
): AnnotatedString {
    val builder = AnnotatedString.Builder(line)
    val lower = line.lowercase()
    val lq = query.lowercase()
    var pos = 0
    while (pos < lower.length) {
        val idx = lower.indexOf(lq, pos)
        if (idx < 0) break
        builder.addStyle(
            SpanStyle(fontWeight = FontWeight.Bold, color = color),
            idx,
            idx + query.length
        )
        pos = idx + query.length
    }
    return builder.toAnnotatedString()
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
