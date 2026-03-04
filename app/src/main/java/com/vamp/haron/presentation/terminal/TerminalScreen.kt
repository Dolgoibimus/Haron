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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamp.haron.R
import com.vamp.haron.data.terminal.PathDetector

@Composable
fun TerminalScreen(
    onBack: () -> Unit,
    onNavigateToPath: ((String) -> Unit)? = null,
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var inputValue by remember { mutableStateOf(TextFieldValue("")) }
    val listState = rememberLazyListState()

    val bgColor = Color(0xFF1E1E1E)
    val textColor = Color(0xFFD4D4D4)
    val commandColor = Color(0xFF4EC9B0)
    val errorColor = Color(0xFFF44747)
    val linkColor = Color(0xFF569CD6)
    val sshPromptColor = Color(0xFF569CD6)
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

    // SSH Password Dialog
    if (state.showPasswordDialog) {
        SshPasswordDialog(
            user = state.pendingSshUser,
            host = state.pendingSshHost,
            port = state.pendingSshPort,
            savedPassword = viewModel.getSavedPassword(
                state.pendingSshUser, state.pendingSshHost, state.pendingSshPort
            ),
            onConnect = { password, save ->
                viewModel.connectSsh(password, save)
            },
            onCancel = { viewModel.cancelSshPasswordDialog() }
        )
    }

    Scaffold(
        containerColor = bgColor,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            val halfStatusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() / 2
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor)
                    .padding(top = halfStatusBar)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = textColor,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onBack() }
                )
                Text(
                    text = stringResource(R.string.terminal_title),
                    color = textColor,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 15.dp)
                )

                if (state.sshConnecting) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = commandColor
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.ssh_connecting),
                        color = textColor.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (state.sshMode) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "SSH",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(sshPromptColor)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { viewModel.disconnectSsh() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Filled.LinkOff,
                            contentDescription = stringResource(R.string.ssh_disconnect),
                            tint = errorColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val keyboardPadding = (imeBottom - navBottom).coerceAtLeast(0.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = keyboardPadding)
            ) {
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
                                        val parts = inputValue.text.split(" ").toMutableList()
                                        if (parts.isNotEmpty()) {
                                            parts[parts.lastIndex] = completion
                                        }
                                        val t = parts.joinToString(" ")
                                        inputValue = TextFieldValue(t, TextRange(t.length))
                                        viewModel.clearCompletions()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Quick symbols panel — different in SSH mode
                if (state.sshMode) {
                    SshQuickPanel(
                        bgColor = Color(0xFF2D2D2D),
                        textColor = textColor,
                        onSendRaw = { data -> viewModel.sendSshRaw(data) },
                        onSymbol = { symbol ->
                            val t = inputValue.text + symbol
                            inputValue = TextFieldValue(t, TextRange(t.length))
                        }
                    )
                } else {
                    QuickSymbolsPanel(
                        bgColor = Color(0xFF2D2D2D),
                        textColor = textColor,
                        onSymbol = { symbol ->
                            val t = inputValue.text + symbol
                            inputValue = TextFieldValue(t, TextRange(t.length))
                        },
                        onTab = {
                            viewModel.requestCompletion(inputValue.text)
                        },
                        onCtrlC = {
                            viewModel.clearCompletions()
                            inputValue = TextFieldValue("")
                        }
                    )
                }

                // Input row
                val inputBlocked = state.isRunning || state.sshConnecting

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF252526)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Prompt
                    if (state.sshMode) {
                        Text(
                            text = "${state.sshUser}@${state.sshHost} $",
                            style = monoStyle,
                            color = sshPromptColor,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    } else {
                        Text(
                            text = state.currentDir.substringAfterLast('/') + " $",
                            style = monoStyle,
                            color = commandColor,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    val focusRequester = remember { FocusRequester() }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (inputValue.text.isEmpty()) {
                            Text(
                                stringResource(R.string.terminal_input_hint),
                                style = monoStyle,
                                color = textColor.copy(alpha = 0.4f)
                            )
                        }
                        BasicTextField(
                            value = inputValue,
                            onValueChange = {
                                if (!inputBlocked) {
                                    inputValue = it
                                    viewModel.clearCompletions()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            textStyle = monoStyle.copy(color = textColor),
                            singleLine = true,
                            cursorBrush = SolidColor(if (state.sshMode) sshPromptColor else commandColor),
                            enabled = !inputBlocked,
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Send,
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Password
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (inputValue.text.isNotBlank() && !inputBlocked) {
                                        viewModel.executeCommand(inputValue.text)
                                        inputValue = TextFieldValue("")
                                    }
                                }
                            )
                        )
                    }

                    // Keep focus after command finishes (not on first open)
                    var hasExecuted by remember { mutableStateOf(false) }
                    LaunchedEffect(state.isRunning) {
                        if (state.isRunning) {
                            hasExecuted = true
                        } else if (hasExecuted) {
                            focusRequester.requestFocus()
                        }
                    }

                    IconButton(
                        onClick = {
                            val cmd = viewModel.historyUp()
                            if (cmd != null) inputValue = TextFieldValue(cmd, TextRange(cmd.length))
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
                            if (cmd != null) inputValue = TextFieldValue(cmd, TextRange(cmd.length))
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
                            if (inputValue.text.isNotBlank()) {
                                viewModel.executeCommand(inputValue.text)
                                inputValue = TextFieldValue("")
                            }
                        },
                        modifier = Modifier.size(36.dp),
                        enabled = !inputBlocked && inputValue.text.isNotBlank()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            stringResource(R.string.terminal_send),
                            tint = if (!inputBlocked && inputValue.text.isNotBlank()) {
                                if (state.sshMode) sshPromptColor else commandColor
                            } else textColor.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(if (state.sshMode) sshPromptColor else commandColor)
                )
            }
        }
    ) { padding ->
        // Output area
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 8.dp)
        ) {
            items(state.lines, key = null) { line ->
                // In SSH mode, detect tappable choices (numbered lines like "1. Option")
                val sshChoice = if (state.sshMode && !line.isCommand) {
                    extractSshChoice(line.parsed?.plainText ?: line.text)
                } else null

                val tappableModifier = if (sshChoice != null) {
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(2.dp))
                        .clickable { viewModel.sendSshRaw("$sshChoice\r") }
                        .background(Color(0xFF2A2D3E))
                        .padding(vertical = 1.dp)
                } else {
                    Modifier.padding(vertical = 1.dp)
                }

                if (line.parsed != null && line.parsed.spans.any { it.fg != null || it.bold || it.italic }) {
                    val annotated = buildStyledText(line, textColor, errorColor, commandColor, linkColor)
                    Text(
                        text = annotated,
                        style = monoStyle,
                        modifier = tappableModifier
                    )
                } else {
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
                            modifier = tappableModifier
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
                            modifier = tappableModifier
                        )
                    }
                }
            }
        }
    }
}

/** Extracts a choice value from interactive prompt lines. Returns the value to send, or null. */
private fun extractSshChoice(text: String): String? {
    val trimmed = text.trimStart()
    // Patterns: "1. Option", "2) Option", ">1. Option", ")1. Option"
    // Also: "[Y/n]", "(yes/no)"
    val numberMatch = Regex("""^[>)\s]*(\d+)\s*[.):]\s*\S""").find(trimmed)
    if (numberMatch != null) return numberMatch.groupValues[1]
    return null
}

@Composable
private fun SshPasswordDialog(
    user: String,
    host: String,
    port: Int,
    savedPassword: String,
    onConnect: (password: String, save: Boolean) -> Unit,
    onCancel: () -> Unit
) {
    var password by remember { mutableStateOf(savedPassword) }
    var passwordVisible by remember { mutableStateOf(false) }
    var savePassword by remember { mutableStateOf(savedPassword.isNotEmpty()) }

    val portSuffix = if (port != 22) ":$port" else ""

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Color(0xFF2D2D2D),
        titleContentColor = Color(0xFFD4D4D4),
        title = {
            Column {
                Text(
                    text = stringResource(R.string.ssh_password_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$user@$host$portSuffix",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF569CD6)
                )
            }
        },
        text = {
            Column {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = {
                        Text(
                            stringResource(R.string.ssh_password_label),
                            color = Color(0xFFD4D4D4).copy(alpha = 0.7f)
                        )
                    },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Filled.VisibilityOff
                                else Icons.Filled.Visibility,
                                contentDescription = null,
                                tint = Color(0xFFD4D4D4).copy(alpha = 0.6f)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFFD4D4D4),
                        unfocusedTextColor = Color(0xFFD4D4D4),
                        cursorColor = Color(0xFF569CD6),
                        focusedBorderColor = Color(0xFF569CD6),
                        unfocusedBorderColor = Color(0xFF555555)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                        keyboardType = KeyboardType.Password
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (password.isNotBlank()) {
                                onConnect(password, savePassword)
                            }
                        }
                    )
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { savePassword = !savePassword }
                ) {
                    Checkbox(
                        checked = savePassword,
                        onCheckedChange = { savePassword = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF569CD6),
                            uncheckedColor = Color(0xFF888888)
                        )
                    )
                    Text(
                        text = stringResource(R.string.ssh_save_password),
                        color = Color(0xFFD4D4D4),
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConnect(password, savePassword) },
                enabled = password.isNotBlank()
            ) {
                Text(
                    stringResource(R.string.ssh_connect),
                    color = if (password.isNotBlank()) Color(0xFF569CD6) else Color(0xFF555555)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel), color = Color(0xFFD4D4D4).copy(alpha = 0.7f))
            }
        }
    )
}

@Composable
private fun SshQuickPanel(
    bgColor: Color,
    textColor: Color,
    onSendRaw: (String) -> Unit,
    onSymbol: (String) -> Unit
) {
    val ctrlColor = Color(0xFFF44747)
    val specialColor = Color(0xFF4EC9B0)
    val arrowColor = Color(0xFFDCDCAA)

    data class PanelButton(
        val label: String,
        val color: Color,
        val action: () -> Unit
    )

    val buttons = listOf(
        PanelButton("Enter", specialColor) { onSendRaw("\r") },
        PanelButton("Tab", specialColor) { onSendRaw("\t") },
        PanelButton("^C", ctrlColor) { onSendRaw("\u0003") },
        PanelButton("^D", ctrlColor) { onSendRaw("\u0004") },
        PanelButton("^Z", ctrlColor) { onSendRaw("\u001A") },
        PanelButton("^L", ctrlColor) { onSendRaw("\u000C") },
        PanelButton("Esc", Color(0xFFCE9178)) { onSendRaw("\u001B") },
        PanelButton("↑", arrowColor) { onSendRaw("\u001B[A") },
        PanelButton("↓", arrowColor) { onSendRaw("\u001B[B") },
        PanelButton("→", arrowColor) { onSendRaw("\u001B[C") },
        PanelButton("←", arrowColor) { onSendRaw("\u001B[D") },
        PanelButton("Home", arrowColor) { onSendRaw("\u001B[H") },
        PanelButton("End", arrowColor) { onSendRaw("\u001B[F") },
    )

    val symbols = listOf("/", "|", ">", "<", "&", ";", "~", "\"", "'", ".", "-", "_")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        buttons.forEach { btn ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF383838))
                    .clickable { btn.action() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = btn.label,
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    color = btn.color
                )
            }
        }

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
