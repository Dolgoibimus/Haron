package com.vamp.haron.presentation.terminal

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamp.haron.data.terminal.AnsiParser
import com.vamp.haron.data.terminal.ParsedLine
import com.vamp.haron.data.terminal.SshConnectionParams
import com.vamp.haron.data.terminal.SshCredentialStore
import com.vamp.haron.data.terminal.SshSessionManager
import com.vamp.haron.data.terminal.TabCompletionEngine
import android.content.SharedPreferences
import com.jcraft.jsch.JSchException
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject

data class TerminalLine(
    val text: String,
    val isCommand: Boolean = false,
    val isError: Boolean = false,
    val parsed: ParsedLine? = null
)

data class TerminalState(
    val lines: List<TerminalLine> = emptyList(),
    val currentDir: String = Environment.getExternalStorageDirectory().absolutePath,
    val isRunning: Boolean = false,
    val rawMode: Boolean = false, // true when interactive app (vi, nano) is running
    val commandHistory: List<String> = emptyList(),
    val historyIndex: Int = -1,
    val completions: List<String> = emptyList(),
    val bufferSize: Int = 2000,
    val timeoutSec: Int = 30,
    // SSH
    val sshMode: Boolean = false,
    val sshUser: String = "",
    val sshHost: String = "",
    val sshPort: Int = 22,
    val sshConnecting: Boolean = false,
    val showPasswordDialog: Boolean = false,
    val pendingSshUser: String = "",
    val pendingSshHost: String = "",
    val pendingSshPort: Int = 22
)

@HiltViewModel
class TerminalViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val sshCredentialStore: SshCredentialStore
) : ViewModel() {

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("terminal_history", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        TerminalState(commandHistory = loadHistory())
    )
    val state: StateFlow<TerminalState> = _state.asStateFlow()

    private val ansiParser = AnsiParser()
    private val tabEngine = TabCompletionEngine()

    // Persistent shell
    private val shellSession = com.vamp.haron.data.terminal.ShellSession()
    private var shellReaderJob: Job? = null
    private val shellLineBuffer = StringBuilder()

    // Terminal grid buffer for full-screen apps (vi, nano, htop)
    val terminalBuffer = com.vamp.haron.data.terminal.TerminalBuffer(rows = 40, cols = 80)

    // SSH
    private val sshManager = SshSessionManager()
    private var sshReaderJob: Job? = null
    private val sshLineBuffer = StringBuilder()

    companion object {
        private const val MAX_HISTORY = 200
        private const val KEY_HISTORY = "cmd_history"
    }

    init {
        startShellSession()
    }

    private fun startShellSession() {
        viewModelScope.launch {
            try {
                shellSession.start(_state.value.currentDir)
                startShellReader()
                EcosystemLogger.d(HaronConstants.TAG, "TerminalVM: persistent shell started")
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "TerminalVM: shell start failed — ${e.message}")
                appendLine(TerminalLine("Failed to start shell: ${e.message}", isError = true))
            }
        }
    }

    private fun startShellReader() {
        shellReaderJob?.cancel()
        shellReaderJob = viewModelScope.launch {
            shellSession.outputFlow.collect { chunk ->
                // Feed raw output to terminal buffer (handles all ANSI: cursor, erase, colors)
                terminalBuffer.processOutput(chunk)

                // Detect exit from interactive app: prompt reappears
                if (_state.value.rawMode) {
                    // Strip ANSI codes for prompt detection
                    val clean = chunk.replace(Regex("\u001B\\[[0-9;?]*[a-zA-Z]"), "").trimEnd()
                    val lastLine = clean.lines().lastOrNull { it.isNotBlank() } ?: ""
                    EcosystemLogger.d(HaronConstants.TAG, "TerminalVM: raw check lastLine='${lastLine.takeLast(20)}' clean_end='${clean.takeLast(10)}'")
                    if (lastLine.endsWith("$") || lastLine.endsWith("#") || lastLine.endsWith(">")
                        || lastLine.endsWith("$ ") || lastLine.endsWith("# ") || lastLine.endsWith("> ")) {
                        _state.update { it.copy(rawMode = false) }
                        EcosystemLogger.d(HaronConstants.TAG, "TerminalVM: raw mode OFF (prompt detected)")
                    }
                }
            }
        }
    }

    private fun loadHistory(): List<String> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) { emptyList() }
    }

    private fun saveHistory(history: List<String>) {
        val arr = JSONArray(history)
        prefs.edit().putString(KEY_HISTORY, arr.toString()).commit()
    }

    fun executeCommand(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return
        EcosystemLogger.d(HaronConstants.TAG, "TerminalVM: execute cmd=${trimmed.take(80)}, sshMode=${_state.value.sshMode}")

        // Clear completions
        _state.update { it.copy(completions = emptyList()) }

        // Don't save to history in raw mode (vi/nano commands)
        if (!_state.value.rawMode) {
            val newHistory = (_state.value.commandHistory + trimmed).takeLast(MAX_HISTORY)
            saveHistory(newHistory)
            _state.update {
                it.copy(
                    commandHistory = newHistory,
                    historyIndex = -1
                )
            }
        }

        if (_state.value.sshMode) {
            executeSshCommand(trimmed)
            return
        }

        // Built-in commands that DON'T go to shell
        val cmd = trimmed.split(" ").firstOrNull()?.lowercase() ?: ""
        when (cmd) {
            "help" -> {
                for (line in showHelp()) {
                    terminalBuffer.processOutput(line.text + "\r\n")
                }
                return
            }
            "clear", "cls" -> {
                terminalBuffer.processOutput("\u001B[2J\u001B[H") // ANSI clear screen + cursor home
                return
            }
            "ssh" -> {
                val parts = parseCommandLine(trimmed)
                parseSshCommand(parts.drop(1))
                return
            }
        }

        // Detect interactive apps → switch to raw mode
        val interactiveApps = setOf("vi", "vim", "nano", "htop", "top", "less", "more", "man")
        if (cmd in interactiveApps) {
            _state.update { it.copy(rawMode = true) }
        }

        // Everything else → send directly to persistent shell as raw text
        // Shell handles echo, cd, vi, export, pipes — everything
        if (shellSession.isAlive) {
            shellSession.sendCommand(trimmed)
        } else {
            terminalBuffer.processOutput("[Shell restarting...]\r\n")
            startShellSession()
            viewModelScope.launch {
                kotlinx.coroutines.delay(500)
                if (shellSession.isAlive) {
                    shellSession.sendCommand(trimmed)
                } else {
                    terminalBuffer.processOutput("[Shell failed to restart]\r\n")
                }
            }
        }
    }

    private fun executeSshCommand(input: String) {
        val lower = input.lowercase().trim()
        when (lower) {
            "clear", "cls" -> {
                clearScreen()
                return
            }
            "exit", "disconnect" -> {
                disconnectSsh()
                return
            }
        }
        // Send command through SSH — no local prompt, PTY echoes
        viewModelScope.launch {
            sshManager.sendCommand(input)
        }
    }

    fun sendSshRaw(data: String) {
        viewModelScope.launch {
            sshManager.sendRaw(data)
        }
    }

    /** Send a single character directly to PTY (raw mode for vi/nano) */
    fun sendChar(char: Char) {
        shellSession.sendRaw(char.toString().toByteArray(Charsets.UTF_8))
    }

    /** Send Enter in raw mode */
    fun sendEnter() {
        shellSession.sendRaw(byteArrayOf(0x0D)) // CR
    }

    /** Exit raw mode (called when interactive app exits) */
    fun exitRawMode() {
        _state.update { it.copy(rawMode = false) }
    }

    /** Resize PTY to match screen dimensions */
    fun resizePty(rows: Int, cols: Int) {
        if (shellSession.isAlive) {
            shellSession.resize(rows, cols)
        }
    }

    /** Send Ctrl+C to the active session (shell or SSH) */
    fun sendInterrupt() {
        if (_state.value.sshMode) {
            viewModelScope.launch { sshManager.sendRaw("\u0003") }
        } else {
            shellSession.sendInterrupt()
        }
        EcosystemLogger.d(HaronConstants.TAG, "TerminalVM: Ctrl+C sent")
    }

    /** Send raw data to the active session */
    fun sendRaw(data: String) {
        if (_state.value.sshMode) {
            viewModelScope.launch { sshManager.sendRaw(data) }
        } else {
            shellSession.sendRaw(data.toByteArray(Charsets.UTF_8))
        }
    }



    fun requestCompletion(currentInput: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val completions = tabEngine.complete(currentInput, _state.value.currentDir)
            _state.update { it.copy(completions = completions) }
        }
    }

    fun clearCompletions() {
        _state.update { it.copy(completions = emptyList()) }
    }

    fun historyUp(): String? {
        val history = _state.value.commandHistory
        if (history.isEmpty()) return null
        val newIndex = if (_state.value.historyIndex < 0) {
            history.lastIndex
        } else {
            (_state.value.historyIndex - 1).coerceAtLeast(0)
        }
        _state.update { it.copy(historyIndex = newIndex) }
        return history[newIndex]
    }

    fun historyDown(): String? {
        val history = _state.value.commandHistory
        if (history.isEmpty()) return null
        val idx = _state.value.historyIndex
        if (idx < 0) return null
        val newIndex = idx + 1
        return if (newIndex > history.lastIndex) {
            _state.update { it.copy(historyIndex = -1) }
            ""
        } else {
            _state.update { it.copy(historyIndex = newIndex) }
            history[newIndex]
        }
    }

    fun clearScreen() {
        _state.update { it.copy(lines = emptyList()) }
    }

    fun setBufferSize(size: Int) {
        _state.update { it.copy(bufferSize = size.coerceIn(100, 10000)) }
    }

    fun setTimeoutSec(sec: Int) {
        _state.update { it.copy(timeoutSec = sec.coerceIn(5, 300)) }
    }

    private fun appendLine(line: TerminalLine) {
        _state.update { s ->
            val newLines = (s.lines + line).takeLast(s.bufferSize)
            s.copy(lines = newLines)
        }
    }

    private suspend fun processCommand(input: String): List<TerminalLine> {
        val parts = parseCommandLine(input)
        if (parts.isEmpty()) return emptyList()

        val cmd = parts[0].lowercase()
        val args = parts.drop(1)

        return when (cmd) {
            "help" -> showHelp()
            "cd" -> changeDirectory(args)
            "pwd" -> listOf(TerminalLine(_state.value.currentDir))
            "clear", "cls" -> {
                clearScreen(); emptyList()
            }
            "ssh" -> {
                parseSshCommand(args)
                emptyList()
            }
            "exit" -> listOf(TerminalLine("Use the back button to exit."))
            else -> executeExternal(input)
        }
    }

    // --- SSH ---

    private fun parseSshCommand(args: List<String>) {
        if (args.isEmpty()) {
            appendLine(TerminalLine("Usage: ssh user@host [-p port]", isError = true))
            _state.update { it.copy(isRunning = false) }
            return
        }

        var userHost = ""
        var port = 22

        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "-p" -> {
                    if (i + 1 < args.size) {
                        port = args[i + 1].toIntOrNull() ?: 22
                        i += 2
                    } else {
                        i++
                    }
                }
                else -> {
                    if (userHost.isEmpty()) userHost = args[i]
                    i++
                }
            }
        }

        if (!userHost.contains("@")) {
            appendLine(TerminalLine("Usage: ssh user@host [-p port]", isError = true))
            _state.update { it.copy(isRunning = false) }
            return
        }

        val user = userHost.substringBefore("@")
        val host = userHost.substringAfter("@")

        if (user.isBlank() || host.isBlank()) {
            appendLine(TerminalLine("Usage: ssh user@host [-p port]", isError = true))
            _state.update { it.copy(isRunning = false) }
            return
        }

        _state.update {
            it.copy(
                isRunning = false,
                showPasswordDialog = true,
                pendingSshUser = user,
                pendingSshHost = host,
                pendingSshPort = port
            )
        }
    }

    fun getSavedPassword(user: String, host: String, port: Int): String {
        return sshCredentialStore.load(user, host, port)?.password ?: ""
    }

    fun connectSsh(password: String, savePassword: Boolean) {
        val user = _state.value.pendingSshUser
        val host = _state.value.pendingSshHost
        val port = _state.value.pendingSshPort

        EcosystemLogger.d(HaronConstants.TAG, "TerminalVM: SSH connect $user@$host:$port")

        _state.update {
            it.copy(
                showPasswordDialog = false,
                sshConnecting = true
            )
        }

        appendLine(TerminalLine("Connecting to $user@$host:$port...", isCommand = true))

        viewModelScope.launch {
            try {
                sshManager.connect(SshConnectionParams(user, host, port, password))

                if (savePassword) {
                    withContext(Dispatchers.IO) {
                        sshCredentialStore.save(
                            com.vamp.haron.data.terminal.SshCredential(user, host, port, password)
                        )
                    }
                }

                sshLineBuffer.clear()
                _state.update {
                    it.copy(
                        sshMode = true,
                        sshUser = user,
                        sshHost = host,
                        sshPort = port,
                        sshConnecting = false
                    )
                }

                EcosystemLogger.d(HaronConstants.TAG, "TerminalVM: SSH connected to $user@$host:$port")
                appendLine(TerminalLine("Connected to $user@$host. Type 'exit' to disconnect.", isCommand = true))
                startSshReader()
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "TerminalVM: SSH connect failed $user@$host:$port — ${e.message}")
                _state.update {
                    it.copy(
                        sshConnecting = false,
                        pendingSshUser = "",
                        pendingSshHost = "",
                        pendingSshPort = 22
                    )
                }
                val message = when (e) {
                    is JSchException -> {
                        when {
                            e.message?.contains("Auth") == true -> "Authentication failed"
                            e.message?.contains("timeout", ignoreCase = true) == true -> "Connection timed out"
                            e.message?.contains("reject", ignoreCase = true) == true -> "Connection refused"
                            else -> "SSH error: ${e.message}"
                        }
                    }
                    is UnknownHostException -> "Unknown host: $host"
                    is ConnectException -> "Connection refused: $host:$port"
                    is SocketTimeoutException -> "Connection timed out"
                    is NoRouteToHostException -> "No route to host: $host"
                    else -> "Connection failed: ${e.message}"
                }
                appendLine(TerminalLine(message, isError = true))
            }
        }
    }

    private fun startSshReader() {
        sshReaderJob?.cancel()
        sshReaderJob = viewModelScope.launch {
            launch(Dispatchers.IO) {
                sshManager.startReading()
            }

            sshManager.outputFlow.collect { chunk ->
                // Split chunk into lines, handling partial lines
                sshLineBuffer.append(chunk)
                val content = sshLineBuffer.toString()
                val lines = content.split('\n')

                // All complete lines
                for (i in 0 until lines.size - 1) {
                    val line = lines[i].trimEnd('\r')
                    ansiParser.reset()
                    val parsed = ansiParser.parseLine(line)
                    appendLine(TerminalLine(text = line, parsed = parsed))
                }

                // Keep the last partial line in buffer
                sshLineBuffer.clear()
                sshLineBuffer.append(lines.last())

                // If buffer ends with something meaningful (prompt), flush it
                val remaining = sshLineBuffer.toString()
                if (remaining.isNotEmpty() && (remaining.endsWith("$ ") || remaining.endsWith("# ") || remaining.endsWith("> "))) {
                    ansiParser.reset()
                    val parsed = ansiParser.parseLine(remaining)
                    appendLine(TerminalLine(text = remaining, parsed = parsed))
                    sshLineBuffer.clear()
                }
            }
        }

        // Monitor connection state
        viewModelScope.launch {
            // Wait until reader finishes (connection closed)
            sshReaderJob?.join()
            if (_state.value.sshMode) {
                disconnectSsh(showMessage = true)
            }
        }
    }

    fun disconnectSsh(showMessage: Boolean = true) {
        EcosystemLogger.d(HaronConstants.TAG, "TerminalVM: SSH disconnect ${_state.value.sshUser}@${_state.value.sshHost}")
        sshReaderJob?.cancel()
        sshReaderJob = null
        sshManager.disconnect()
        sshLineBuffer.clear()

        if (showMessage) {
            appendLine(TerminalLine("[SSH disconnected]", isCommand = true))
        }

        _state.update {
            it.copy(
                sshMode = false,
                sshUser = "",
                sshHost = "",
                sshPort = 22,
                sshConnecting = false,
                pendingSshUser = "",
                pendingSshHost = "",
                pendingSshPort = 22
            )
        }
    }

    fun cancelSshPasswordDialog() {
        _state.update {
            it.copy(
                showPasswordDialog = false,
                pendingSshUser = "",
                pendingSshHost = "",
                pendingSshPort = 22
            )
        }
        appendLine(TerminalLine("SSH connection cancelled.", isCommand = true))
    }

    // --- Help ---

    private fun showHelp(): List<TerminalLine> {
        return listOf(
            TerminalLine("Built-in commands:"),
            TerminalLine("  cd <dir>    — Change directory"),
            TerminalLine("  pwd         — Print working directory"),
            TerminalLine("  clear       — Clear screen"),
            TerminalLine("  help        — Show this help"),
            TerminalLine(""),
            TerminalLine("SSH remote access:"),
            TerminalLine("  ssh user@host          — Connect to remote server"),
            TerminalLine("  ssh user@host -p 2222  — Connect on custom port"),
            TerminalLine("  exit / disconnect      — Close SSH session"),
            TerminalLine(""),
            TerminalLine("System commands (via sh):"),
            TerminalLine("  ls, cat, cp, mv, rm, mkdir, rmdir,"),
            TerminalLine("  grep, find, wc, head, tail, sort,"),
            TerminalLine("  chmod, touch, echo, df, du, ping,"),
            TerminalLine("  whoami, date, uname, id, ps, top"),
            TerminalLine(""),
            TerminalLine("ANSI colors: try 'ls --color=always'"),
            TerminalLine("Tab completion: type partial path and tap Tab"),
            TerminalLine("Clickable paths in output — tap to navigate"),
            TerminalLine("Pipe (|) and redirect (>, >>) supported."),
            TerminalLine("Quick symbols panel above keyboard.")
        )
    }

    // --- Local commands ---

    private fun changeDirectory(args: List<String>): List<TerminalLine> {
        val target = when {
            args.isEmpty() -> Environment.getExternalStorageDirectory().absolutePath
            args[0] == "~" -> Environment.getExternalStorageDirectory().absolutePath
            args[0] == "-" -> Environment.getExternalStorageDirectory().absolutePath
            args[0] == ".." -> File(_state.value.currentDir).parent
                ?: _state.value.currentDir
            args[0].startsWith("/") -> args[0]
            else -> File(_state.value.currentDir, args[0]).canonicalPath
        }
        val dir = File(target)
        if (!dir.isDirectory) {
            return listOf(TerminalLine("cd: $target: No such directory", isError = true))
        }
        if (!matchesCaseSensitive(dir)) {
            return listOf(TerminalLine("cd: $target: No such directory (case mismatch)", isError = true))
        }
        _state.update { it.copy(currentDir = dir.canonicalPath) }
        return emptyList()
    }

    private fun matchesCaseSensitive(target: File): Boolean {
        var current = target.canonicalFile
        while (current.parent != null) {
            val parent = current.parentFile ?: return true
            val actualNames = parent.list() ?: return true
            if (current.name !in actualNames) return false
            current = parent
        }
        return true
    }

    private suspend fun executeExternal(command: String): List<TerminalLine> {
        val timeoutMs = _state.value.timeoutSec * 1000L
        val maxLines = _state.value.bufferSize

        return withContext(Dispatchers.IO) {
            try {
                val pb = ProcessBuilder("sh", "-c", command)
                pb.directory(File(_state.value.currentDir))
                pb.redirectErrorStream(true)
                pb.environment()["HOME"] = Environment.getExternalStorageDirectory().absolutePath
                pb.environment()["TERM"] = "xterm-256color"

                val process = pb.start()
                val reader = process.inputStream.bufferedReader()
                val collectedLines = mutableListOf<String>()
                val deadline = System.currentTimeMillis() + timeoutMs

                val readerThread = Thread {
                    try {
                        reader.forEachLine { line ->
                            if (Thread.currentThread().isInterrupted) return@forEachLine
                            synchronized(collectedLines) {
                                if (collectedLines.size < maxLines) collectedLines.add(line)
                            }
                        }
                    } catch (_: Exception) { }
                }
                readerThread.start()
                readerThread.join(timeoutMs)

                if (readerThread.isAlive) {
                    readerThread.interrupt()
                    process.destroyForcibly()
                    ansiParser.reset()
                    val partial = synchronized(collectedLines) { collectedLines.toList() }
                    val parsed = partial.map { rawLine ->
                        TerminalLine(
                            text = rawLine,
                            parsed = ansiParser.parseLine(rawLine)
                        )
                    }
                    return@withContext parsed + TerminalLine(
                        "Command timed out (${_state.value.timeoutSec}s) — partial output above",
                        isError = true
                    )
                }

                val exitCode = process.waitFor()
                ansiParser.reset()
                val lines = synchronized(collectedLines) { collectedLines.toList() }
                    .map { rawLine ->
                        val parsed = ansiParser.parseLine(rawLine)
                        TerminalLine(
                            text = rawLine,
                            isError = exitCode != 0,
                            parsed = parsed
                        )
                    }

                if (exitCode != 0 && lines.isEmpty()) {
                    listOf(TerminalLine("Exit code: $exitCode", isError = true))
                } else {
                    lines
                }
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "TerminalVM: external cmd error — ${e.message}")
                listOf(TerminalLine("Error: ${e.message}", isError = true))
            }
        }
    }

    private fun parseCommandLine(input: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var inQuote = false
        var quoteChar = ' '

        for (char in input) {
            when {
                inQuote -> {
                    if (char == quoteChar) inQuote = false
                    else current.append(char)
                }
                char == '"' || char == '\'' -> {
                    inQuote = true
                    quoteChar = char
                }
                char == ' ' -> {
                    if (current.isNotEmpty()) {
                        parts.add(current.toString())
                        current.clear()
                    }
                }
                else -> current.append(char)
            }
        }
        if (current.isNotEmpty()) parts.add(current.toString())
        return parts
    }

    override fun onCleared() {
        super.onCleared()
        shellReaderJob?.cancel()
        shellSession.stop()
        sshReaderJob?.cancel()
        sshManager.disconnect()
    }
}
