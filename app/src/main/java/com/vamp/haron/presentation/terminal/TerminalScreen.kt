package com.vamp.haron.presentation.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardTab
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamp.haron.R
import com.vamp.haron.data.terminal.PathDetector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    onBack: () -> Unit,
    onNavigateToPath: ((String) -> Unit)? = null,
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val bgColor = Color(0xFF1E1E1E)
    val textColor = Color(0xFFD4D4D4)
    val commandColor = Color(0xFF4EC9B0)
    val errorColor = Color(0xFFF44747)
    val linkColor = Color(0xFF569CD6)
    val monoStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp
    )

    // Auto-scroll to bottom
    LaunchedEffect(state.lines.size) {
        if (state.lines.isNotEmpty()) {
            listState.animateScrollToItem(state.lines.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.terminal_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = bgColor,
                    titleContentColor = textColor,
                    navigationIconContentColor = textColor
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(bgColor)
                .imePadding()
        ) {
            // Output area
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                items(state.lines, key = null) { line ->
                    if (line.parsed != null && line.parsed.spans.any { it.fg != null || it.bold || it.italic }) {
                        // ANSI-styled line with clickable paths
                        val annotated = buildStyledText(line, textColor, errorColor, commandColor, linkColor)
                        Text(
                            text = annotated,
                            style = monoStyle,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    } else {
                        // Plain line with path detection
                        val plainText = line.parsed?.plainText ?: line.text
                        val paths = PathDetector.detectPaths(plainText)
                        if (paths.isNotEmpty() && !line.isCommand) {
                            val annotated = buildAnnotatedString {
                                var lastEnd = 0
                                val baseColor = when {
                                    line.isError -> errorColor
                                    line.isCommand -> commandColor
                                    else -> textColor
                                }
                                for (p in paths) {
                                    if (p.startIndex > lastEnd) {
                                        withStyle(SpanStyle(color = baseColor)) {
                                            append(plainText.substring(lastEnd, p.startIndex))
                                        }
                                    }
                                    withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                                        append(p.path)
                                    }
                                    lastEnd = p.endIndex
                                }
                                if (lastEnd < plainText.length) {
                                    withStyle(SpanStyle(color = baseColor)) {
                                        append(plainText.substring(lastEnd))
                                    }
                                }
                            }
                            Text(
                                text = annotated,
                                style = monoStyle,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        } else {
                            val color = when {
                                line.isError -> errorColor
                                line.isCommand -> commandColor
                                else -> textColor
                            }
                            Text(
                                text = plainText,
                                style = monoStyle,
                                color = color,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            // Completion chips
            if (state.completions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2D2D2D))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    state.completions.forEach { completion ->
                        Text(
                            text = completion,
                            style = monoStyle.copy(fontSize = 12.sp),
                            color = commandColor,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF383838))
                                .clickable {
                                    val parts = inputText.split(" ").toMutableList()
                                    if (parts.isNotEmpty()) {
                                        parts[parts.lastIndex] = completion
                                    }
                                    inputText = parts.joinToString(" ")
                                    viewModel.clearCompletions()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Quick symbols panel
            QuickSymbolsPanel(
                bgColor = Color(0xFF2D2D2D),
                textColor = textColor,
                onSymbol = { symbol ->
                    inputText += symbol
                },
                onTab = {
                    viewModel.requestCompletion(inputText)
                },
                onCtrlC = {
                    // Ctrl+C — just show ^C
                    viewModel.clearCompletions()
                    inputText = ""
                }
            )

            // Input row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF252526)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.currentDir.substringAfterLast('/') + " $",
                    style = monoStyle,
                    color = commandColor,
                    modifier = Modifier.padding(start = 8.dp)
                )

                TextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        viewModel.clearCompletions()
                    },
                    modifier = Modifier.weight(1f),
                    textStyle = monoStyle.copy(color = textColor),
                    singleLine = true,
                    placeholder = {
                        Text(
                            stringResource(R.string.terminal_input_hint),
                            style = monoStyle,
                            color = textColor.copy(alpha = 0.4f)
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank()) {
                                viewModel.executeCommand(inputText)
                                inputText = ""
                            }
                        }
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = commandColor
                    ),
                    enabled = !state.isRunning
                )

                IconButton(
                    onClick = {
                        val cmd = viewModel.historyUp()
                        if (cmd != null) inputText = cmd
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        stringResource(R.string.terminal_history_up),
                        tint = textColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = {
                        val cmd = viewModel.historyDown()
                        if (cmd != null) inputText = cmd
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        stringResource(R.string.terminal_history_down),
                        tint = textColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.executeCommand(inputText)
                            inputText = ""
                        }
                    },
                    modifier = Modifier.size(36.dp),
                    enabled = !state.isRunning && inputText.isNotBlank()
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        stringResource(R.string.terminal_send),
                        tint = if (!state.isRunning && inputText.isNotBlank()) commandColor
                        else textColor.copy(alpha = 0.3f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickSymbolsPanel(
    bgColor: Color,
    textColor: Color,
    onSymbol: (String) -> Unit,
    onTab: () -> Unit,
    onCtrlC: () -> Unit
) {
    val symbols = listOf("~", "/", "|", ">", "<", "&", ";", "\"", "'", ".", "-", "_")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tab button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF383838))
                .clickable { onTab() }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Tab",
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                color = Color(0xFF4EC9B0)
            )
        }

        // Ctrl+C button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF383838))
                .clickable { onCtrlC() }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "^C",
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                color = Color(0xFFF44747)
            )
        }

        // Symbol buttons
        symbols.forEach { sym ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF383838))
                    .clickable { onSymbol(sym) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = sym,
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    color = textColor
                )
            }
        }
    }
}

private fun buildStyledText(
    line: TerminalLine,
    defaultColor: Color,
    errorColor: Color,
    commandColor: Color,
    linkColor: Color
): AnnotatedString {
    val parsed = line.parsed ?: return AnnotatedString(line.text)
    val baseColor = when {
        line.isError -> errorColor
        line.isCommand -> commandColor
        else -> defaultColor
    }

    return buildAnnotatedString {
        for (span in parsed.spans) {
            val fg = span.fg ?: baseColor
            val style = SpanStyle(
                color = fg,
                background = span.bg ?: Color.Unspecified,
                fontWeight = if (span.bold) FontWeight.Bold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
                textDecoration = if (span.underline) TextDecoration.Underline else null
            )
            withStyle(style) {
                append(span.text)
            }
        }
    }
}
