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
import androidx.compose.runtime.mutableIntStateOf
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
    var shellInput by remember { mutableStateOf(TextFieldValue("")) }
    var sshInput by remember { mutableStateOf(TextFieldValue("")) }
    var inputValue by remember { mutableStateOf(TextFieldValue("")) }
    val listState = rememberLazyListState()
    var selectedTab by remember { mutableIntStateOf(0) } // 0=Shell, 1=SSH, 2=AI

    // Sync inputValue with per-tab storage on tab switch + reset history index
    LaunchedEffect(selectedTab) {
        inputValue = if (selectedTab == 1) sshInput else shellInput
        viewModel.resetHistoryIndex()
    }
    // Save back to per-tab storage
    LaunchedEffect(inputValue) {
        if (selectedTab == 1) sshInput = inputValue else shellInput = inputValue
    }

    // Sync tab with SSH mode
    LaunchedEffect(state.sshMode) {
        if (state.sshMode && selectedTab != 1) selectedTab = 1
        if (!state.sshMode && selectedTab == 1) selectedTab = 0
    }

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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor)
                    .padding(top = halfStatusBar)
            ) {
                // Header row: back + title + SSH status
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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
                    }

                    if (state.sshMode) {
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

                // Tabs: Shell | SSH | AI
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf("Shell", "SSH", "AI").forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        Text(
                            text = title,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) commandColor else textColor.copy(alpha = 0.5f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(if (isSelected) Color(0xFF2D2D2D) else Color.Transparent)
                                .clickable {
                                    when (index) {
                                        0 -> {
                                            if (state.sshMode) viewModel.disconnectSsh()
                                            selectedTab = 0
                                        }
                                        1 -> selectedTab = 1
                                        2 -> selectedTab = 2
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (selectedTab == 2) return@Scaffold // AI tab — no input bar yet

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
                            // Special keys → send raw to shell, don't insert in text field
                            if (symbol.firstOrNull()?.code?.let { it < 0x20 || it == 0x1B } == true) {
                                viewModel.sendRaw(symbol)
                            } else {
                                val t = inputValue.text + symbol
                                inputValue = TextFieldValue(t, TextRange(t.length))
                            }
                        },
                        onTab = {
                            viewModel.requestCompletion(inputValue.text)
                        },
                        onCtrlC = {
                            viewModel.sendInterrupt()
                            viewModel.clearCompletions()
                            inputValue = TextFieldValue("")
                        }
                    )
                }

                // Input row
                val inputBlocked = state.sshConnecting // persistent shell is always ready

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
                            onValueChange = { newValue ->
                                if (!inputBlocked) {
                                    if (state.rawMode) {
                                        // Raw mode: send each new character directly to PTY
                                        if (newValue.text.length < inputValue.text.length || (inputValue.text == " " && newValue.text.isEmpty())) {
                                            // Backspace was pressed
                                            viewModel.sendRaw("\u007F") // DEL character
                                        } else {
                                            // Filter out the sentinel space
                                            val oldClean = inputValue.text.trimStart()
                                            val newClean = newValue.text.trimStart()
                                            val added = if (newClean.length > oldClean.length) newClean.removePrefix(oldClean) else newValue.text.removePrefix(inputValue.text)
                                            if (added.isNotEmpty()) {
                                                for (ch in added) {
                                                    if (ch == '\n') {
                                                        viewModel.sendEnter()
                                                    } else {
                                                        viewModel.sendChar(ch)
                                                    }
                                                }
                                            }
                                        }
                                        // Keep a single space so Backspace can be detected
                                        inputValue = TextFieldValue(" ", TextRange(1))
                                    } else {
                                        inputValue = newValue
                                        viewModel.clearCompletions()
                                    }
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
                                    if (state.rawMode) {
                                        // In raw mode, Enter → send CR to PTY
                                        viewModel.sendEnter()
                                    } else if (inputValue.text.isNotBlank() && !inputBlocked) {
                                        viewModel.executeCommand(inputValue.text)
                                        inputValue = TextFieldValue("")
                                    }
                                },
                                onDone = {
                                    if (state.rawMode) {
                                        viewModel.sendEnter()
                                    }
                                },
                                onGo = {
                                    if (state.rawMode) {
                                        viewModel.sendEnter()
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
        when (selectedTab) {
            0 -> {
                TerminalGrid(
                    buffer = viewModel.shellBuffer,
                    onSizeCalculated = { rows, cols -> viewModel.resizePty(rows, cols) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }
            1 -> {
                TerminalGrid(
                    buffer = viewModel.sshBuffer,
                    onSizeCalculated = { rows, cols -> viewModel.resizePty(rows, cols) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }
            2 -> {
                // AI tab — placeholder
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(bgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Claude AI",
                            color = commandColor,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Coming soon",
                            color = textColor.copy(alpha = 0.5f),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
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

private data class PanelBtn(val label: String, val color: Color, val action: () -> Unit)

@Composable
private fun QuickSymbolsPanel(
    bgColor: Color,
    textColor: Color,
    onSymbol: (String) -> Unit,
    onTab: () -> Unit,
    onCtrlC: () -> Unit
) {
    val ctrlColor = Color(0xFFF44747)
    val specialColor = Color(0xFF4EC9B0)
    val arrowColor = Color(0xFFDCDCAA)
    val escColor = Color(0xFFCE9178)

    val buttons = listOf(
        PanelBtn("Esc", escColor) { onSymbol("\u001B") },
        PanelBtn("Enter", specialColor) { onSymbol("\r") },
        PanelBtn("Tab", specialColor) { onTab() },
        PanelBtn("^C", ctrlColor) { onCtrlC() },
        PanelBtn("^D", ctrlColor) { onSymbol("\u0004") },
        PanelBtn("^Z", ctrlColor) { onSymbol("\u001A") },
        PanelBtn("↑", arrowColor) { onSymbol("\u001B[A") },
        PanelBtn("↓", arrowColor) { onSymbol("\u001B[B") },
        PanelBtn("←", arrowColor) { onSymbol("\u001B[D") },
        PanelBtn("→", arrowColor) { onSymbol("\u001B[C") },
    )
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
