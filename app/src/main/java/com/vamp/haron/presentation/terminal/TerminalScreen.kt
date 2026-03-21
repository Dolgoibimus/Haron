package com.vamp.haron.presentation.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamp.haron.R

@Composable
fun TerminalScreen(
    onBack: () -> Unit,
    onNavigateToPath: ((String) -> Unit)? = null,
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var fontSizeSp by remember { mutableFloatStateOf(8f) }

    val bgColor = Color(0xFF1E1E1E)
    val textColor = Color(0xFFD4D4D4)
    val accentColor = Color(0xFF4EC9B0)
    val sshColor = Color(0xFF569CD6)
    val errorColor = Color(0xFFF44747)

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val keyboardPadding = (imeBottom - navBottom).coerceAtLeast(0.dp)
    val halfStatusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() / 2

    // SSH dialog
    if (state.showSshDialog) {
        SshConnectDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.dismissSshDialog() }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(top = halfStatusBar, bottom = keyboardPadding)
    ) {
        // Header: back + title + SSH button/status
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
                    color = sshColor
                )
            }

            Spacer(Modifier.weight(1f))

            if (state.sshConnected) {
                // Show connected host + disconnect button
                Text(
                    text = "${state.sshUser}@${state.sshHost}",
                    color = sshColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
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
            } else {
                // SSH connect button
                Text(
                    text = "SSH",
                    color = sshColor,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(sshColor.copy(alpha = 0.15f))
                        .clickable { viewModel.showSshDialog() }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }

        // Terminal canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }
        ) {
            TerminalGrid(
                buffer = viewModel.buffer,
                fontSizeSp = fontSizeSp,
                onFontSizeChanged = { fontSizeSp = it },
                onSizeCalculated = { rows, cols -> viewModel.resizePty(rows, cols) },
                modifier = Modifier.fillMaxSize()
            )
        }

        // History list — slides in above quick panel
        var showHistory by remember { mutableStateOf(false) }

        androidx.compose.animation.AnimatedVisibility(
            visible = showHistory && state.history.isNotEmpty(),
            enter = androidx.compose.animation.slideInVertically { it },
            exit = androidx.compose.animation.slideOutVertically { it }
        ) {
            val scrollState = rememberScrollState()
            LaunchedEffect(Unit) {
                scrollState.scrollTo(scrollState.maxValue)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 250.dp)
                    .background(Color(0xFF252526))
                    .verticalScroll(scrollState)
            ) {
                state.history.forEach { cmd ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showHistory = false
                                viewModel.insertCommand(cmd)
                            }
                            .padding(vertical = 2.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = cmd,
                            color = Color(0xFFD4D4D4),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            modifier = Modifier.weight(1f).padding(vertical = 4.dp)
                        )
                        Text(
                            text = "✕",
                            color = Color(0xFF888888),
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clickable { viewModel.removeFromHistory(cmd) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Quick keys panel
        QuickKeysPanel(
            onSendRaw = { viewModel.sendRaw(it) },
            onTab = { viewModel.sendRaw("\t") },
            onCtrlC = { viewModel.sendInterrupt() },
            onArrowUp = { viewModel.sendRaw("\u001B[A") },
            onArrowUpLong = { showHistory = !showHistory },
            onArrowDown = { viewModel.sendRaw("\u001B[B") }
        )

        // Hidden TextField — captures keyboard, sends to PTY/SSH
        // Pending backspace: keyboard may shorten text on Enter tap — wait to see if
        // keyboardActions fires (= Enter), otherwise it was a real backspace
        var hiddenText by remember { mutableStateOf(TextFieldValue("x ", TextRange(2))) }
        var pendingBackspace by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        BasicTextField(
            value = hiddenText,
            onValueChange = { newValue ->
                val oldLen = hiddenText.text.length
                val newLen = newValue.text.length
                when {
                    newLen < oldLen -> {
                        // Could be backspace OR keyboard modifying text before Enter
                        pendingBackspace = true
                        scope.launch {
                            delay(150L)
                            if (pendingBackspace) {
                                viewModel.sendBackspace()
                                pendingBackspace = false
                            }
                        }
                    }
                    newLen > oldLen -> {
                        val added = newValue.text.substring(oldLen)
                        for (ch in added) {
                            if (ch == '\n') viewModel.sendEnter()
                            else viewModel.sendChar(ch)
                        }
                    }
                }
                hiddenText = TextFieldValue("x ", TextRange(2))
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .focusRequester(focusRequester),
            textStyle = TextStyle(fontSize = 1.sp, color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = true,
                keyboardType = KeyboardType.Text
            ),
            keyboardActions = KeyboardActions(
                onSend = { pendingBackspace = false; viewModel.sendEnter() },
                onDone = { pendingBackspace = false; viewModel.sendEnter() },
                onGo = { pendingBackspace = false; viewModel.sendEnter() }
            )
        )

        // Request focus + show keyboard on start and after SSH dialog closes
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
        LaunchedEffect(state.sshConnected, state.showSshDialog) {
            if (!state.showSshDialog) {
                kotlinx.coroutines.delay(300) // wait for dialog animation
                focusRequester.requestFocus()
                keyboardController?.show()
            }
        }
    }
}

// --- SSH Connect Dialog ---

@Composable
private fun SshConnectDialog(
    viewModel: TerminalViewModel,
    onDismiss: () -> Unit
) {
    var host by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("root") }
    var port by remember { mutableStateOf("22") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var savePassword by remember { mutableStateOf(true) }

    // Load saved password when host/user/port change
    LaunchedEffect(host, user, port) {
        if (host.isNotBlank() && user.isNotBlank()) {
            val saved = viewModel.getSavedPassword(user, host, port.toIntOrNull() ?: 22)
            if (saved.isNotBlank()) password = saved
        }
    }

    val dialogBg = Color(0xFF2D2D2D)
    val textColor = Color(0xFFD4D4D4)
    val sshColor = Color(0xFF569CD6)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogBg,
        titleContentColor = textColor,
        title = {
            Text("SSH", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Host", color = textColor.copy(alpha = 0.7f)) },
                    singleLine = true,
                    colors = sshFieldColors(textColor, sshColor),
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("192.168.1.1", color = textColor.copy(alpha = 0.3f)) }
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it },
                        label = { Text("User", color = textColor.copy(alpha = 0.7f)) },
                        singleLine = true,
                        colors = sshFieldColors(textColor, sshColor),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter { c -> c.isDigit() } },
                        label = { Text("Port", color = textColor.copy(alpha = 0.7f)) },
                        singleLine = true,
                        colors = sshFieldColors(textColor, sshColor),
                        modifier = Modifier.width(80.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.ssh_password_label), color = textColor.copy(alpha = 0.7f)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null,
                                tint = textColor.copy(alpha = 0.6f)
                            )
                        }
                    },
                    colors = sshFieldColors(textColor, sshColor),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (host.isNotBlank() && password.isNotBlank()) {
                                viewModel.connectSsh(user, host, port.toIntOrNull() ?: 22, password, savePassword)
                            }
                        }
                    )
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { savePassword = !savePassword }
                ) {
                    Checkbox(
                        checked = savePassword,
                        onCheckedChange = { savePassword = it },
                        colors = CheckboxDefaults.colors(checkedColor = sshColor, uncheckedColor = Color(0xFF888888))
                    )
                    Text(stringResource(R.string.ssh_save_password), color = textColor, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.connectSsh(user, host, port.toIntOrNull() ?: 22, password, savePassword) },
                enabled = host.isNotBlank() && password.isNotBlank()
            ) {
                Text(
                    stringResource(R.string.ssh_connect),
                    color = if (host.isNotBlank() && password.isNotBlank()) sshColor else Color(0xFF555555)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = textColor.copy(alpha = 0.7f))
            }
        }
    )
}

@Composable
private fun sshFieldColors(textColor: Color, accentColor: Color) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = textColor,
    unfocusedTextColor = textColor,
    cursorColor = accentColor,
    focusedBorderColor = accentColor,
    unfocusedBorderColor = Color(0xFF555555)
)

// --- Quick Keys Panel ---

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickKeysPanel(
    onSendRaw: (String) -> Unit,
    onTab: () -> Unit,
    onCtrlC: () -> Unit,
    onArrowUp: () -> Unit,
    onArrowUpLong: () -> Unit,
    onArrowDown: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val ctrlColor = Color(0xFFF44747)
    val specialColor = Color(0xFF4EC9B0)
    val arrowColor = Color(0xFFDCDCAA)
    val escColor = Color(0xFFCE9178)
    val textColor = Color(0xFFD4D4D4)

    data class Btn(val label: String, val color: Color, val action: () -> Unit)

    val buttons = listOf(
        Btn("Esc", escColor) { onSendRaw("\u001B") },
        Btn("Tab", specialColor) { onTab() },
        Btn("^C", ctrlColor) { onCtrlC() },
        Btn("^D", ctrlColor) { onSendRaw("\u0004") },
        Btn("^Z", ctrlColor) { onSendRaw("\u001A") },
        Btn("^L", ctrlColor) { onSendRaw("\u000C") },
        Btn("Paste", specialColor) {
            val clip = clipboardManager.getText()?.text
            if (!clip.isNullOrEmpty()) {
                for (ch in clip) onSendRaw(ch.toString())
            }
        },
        // ↑ handled separately (short tap = arrow, long tap = history)
        Btn("↓", arrowColor) { onArrowDown() },
        Btn("←", arrowColor) { onSendRaw("\u001B[D") },
        Btn("→", arrowColor) { onSendRaw("\u001B[C") },
        Btn("Home", arrowColor) { onSendRaw("\u001B[H") },
        Btn("End", arrowColor) { onSendRaw("\u001B[F") },
    )

    val symbols = listOf("~", "/", "|", ">", "<", "&", ";", "\"", "'", ".", "-", "_")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2D2D2D))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Regular buttons before ↑
        buttons.take(7).forEach { btn ->
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

        // ↑ button: short tap = arrow up, long tap = toggle history list
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF383838))
                .combinedClickable(
                    onClick = { onArrowUp() },
                    onLongClick = { onArrowUpLong() }
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "↑",
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                color = arrowColor
            )
        }

        // Remaining buttons (↓, ←, →, Home, End)
        buttons.drop(7).forEach { btn ->
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
                    .clickable { onSendRaw(sym) }
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
