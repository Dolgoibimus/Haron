package com.vamp.haron.presentation.terminal

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamp.haron.data.terminal.AnsiParser
import com.vamp.haron.data.terminal.ParsedLine
import com.vamp.haron.data.terminal.TabCompletionEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject

data class TerminalLine(
    val text: String,
    val isCommand: Boolean = false,
    val isError: Boolean = false,
    val parsed: ParsedLine? = null
)

data class TerminalState(
    val lines: List<TerminalLine> = listOf(
        TerminalLine("Haron Terminal v2.0 — ANSI color support", isCommand = false),
        TerminalLine("Type 'help' for available commands.", isCommand = false)
    ),
    val currentDir: String = Environment.getExternalStorageDirectory().absolutePath,
    val isRunning: Boolean = false,
    val commandHistory: List<String> = emptyList(),
    val historyIndex: Int = -1,
    val completions: List<String> = emptyList(),
    val bufferSize: Int = 2000,
    val timeoutSec: Int = 30
)

@HiltViewModel
class TerminalViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(TerminalState())
    val state: StateFlow<TerminalState> = _state.asStateFlow()

    private val ansiParser = AnsiParser()
    private val tabEngine = TabCompletionEngine()

    companion object {
        private const val MAX_HISTORY = 200
    }

    fun executeCommand(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return

        // Clear completions
        _state.update { it.copy(completions = emptyList()) }

        // Add to history
        val newHistory = (_state.value.commandHistory + trimmed).takeLast(MAX_HISTORY)

        // Show prompt + command
        val prompt = "\$ $trimmed"
        appendLine(TerminalLine(prompt, isCommand = true))
        _state.update {
            it.copy(
                isRunning = true,
                commandHistory = newHistory,
                historyIndex = -1
            )
        }

        viewModelScope.launch {
            val result = processCommand(trimmed)
            _state.update { it.copy(isRunning = false) }
            for (line in result) {
                appendLine(line)
            }
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
            "exit" -> listOf(TerminalLine("Use the back button to exit."))
            else -> executeExternal(input)
        }
    }

    private fun showHelp(): List<TerminalLine> {
        return listOf(
            TerminalLine("Built-in commands:"),
            TerminalLine("  cd <dir>    — Change directory"),
            TerminalLine("  pwd         — Print working directory"),
            TerminalLine("  clear       — Clear screen"),
            TerminalLine("  help        — Show this help"),
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
        return if (dir.isDirectory) {
            _state.update { it.copy(currentDir = dir.canonicalPath) }
            emptyList()
        } else {
            listOf(TerminalLine("cd: $target: No such directory", isError = true))
        }
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

                val output = withTimeoutOrNull(timeoutMs) {
                    process.inputStream.bufferedReader().readText()
                }

                if (output == null) {
                    process.destroyForcibly()
                    return@withContext listOf(
                        TerminalLine(
                            "Command timed out (${_state.value.timeoutSec}s)",
                            isError = true
                        )
                    )
                }

                val exitCode = process.waitFor()
                ansiParser.reset()
                val lines = output.lines()
                    .take(maxLines)
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
}
