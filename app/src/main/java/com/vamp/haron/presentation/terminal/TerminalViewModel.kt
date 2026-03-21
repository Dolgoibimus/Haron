package com.vamp.haron.presentation.terminal

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamp.haron.data.terminal.SshConnectionParams
import com.vamp.haron.data.terminal.SshCredential
import com.vamp.haron.data.terminal.SshCredentialStore
import com.vamp.haron.data.terminal.SshSessionManager
import android.content.SharedPreferences
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
import javax.inject.Inject

data class TerminalState(
    val currentDir: String = Environment.getExternalStorageDirectory().absolutePath,
    val history: List<String> = emptyList(),
    val historyIndex: Int = -1,
    // SSH
    val sshConnected: Boolean = false,
    val sshConnecting: Boolean = false,
    val sshUser: String = "",
    val sshHost: String = "",
    val showSshDialog: Boolean = false,
    val pendingSshUser: String = "",
    val pendingSshHost: String = "",
    val pendingSshPort: Int = 22
)

/**
 * Terminal — Termux-style single window.
 * Shell: local PTY (ShellSession).
 * SSH: JSch with push I/O (setInputStream/setOutputStream). Triggered by SSH button.
 * Input always goes directly to active session (shell or SSH).
 */
@HiltViewModel
class TerminalViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val sshCredentialStore: SshCredentialStore
) : ViewModel() {

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("terminal_history", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(TerminalState(history = loadHistory()))
    val state: StateFlow<TerminalState> = _state.asStateFlow()

    // Shell PTY
    private val shellSession = com.vamp.haron.data.terminal.ShellSession()
    private var shellReaderJob: Job? = null

    // SSH via JSch
    private val sshManager = SshSessionManager()
    private var sshReaderJob: Job? = null

    // Single buffer — both shell and SSH write here
    val buffer = com.vamp.haron.data.terminal.TerminalBuffer(rows = 40, cols = 80)

    private val isSsh: Boolean get() = _state.value.sshConnected

    companion object {
        private const val MAX_HISTORY = 200
        private const val KEY_HISTORY = "cmd_history_shell"
    }

    init {
        startShell()
    }

    private fun startShell() {
        viewModelScope.launch {
            try {
                shellSession.start(_state.value.currentDir)
                shellReaderJob = launch {
                    shellSession.outputFlow.collect { chunk ->
                        if (!isSsh) {
                            buffer.processOutput(chunk)
                            logPty(chunk)
                        }
                    }
                }
                EcosystemLogger.d(HaronConstants.TAG, "TerminalVM: shell started")
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "TerminalVM: shell start failed — ${e.message}")
            }
        }
    }

    // --- Input: routed to active session ---

    fun sendChar(char: Char) {
        EcosystemLogger.d(HaronConstants.TAG, "INPUT> char='$char' isSsh=$isSsh")
        if (isSsh) viewModelScope.launch { sshManager.sendRaw(char.toString()) }
        else shellSession.sendRaw(char.toString().toByteArray(Charsets.UTF_8))
    }

    fun sendEnter() {
        EcosystemLogger.d(HaronConstants.TAG, "INPUT> ENTER isSsh=$isSsh")
        if (isSsh) viewModelScope.launch { sshManager.sendRaw("\r") }
        else shellSession.sendRaw(byteArrayOf(0x0D))
    }

    fun sendBackspace() {
        EcosystemLogger.d(HaronConstants.TAG, "INPUT> BACKSPACE isSsh=$isSsh")
        if (isSsh) viewModelScope.launch { sshManager.sendRaw("\u007F") }
        else shellSession.sendRaw(byteArrayOf(0x7F))
    }

    fun sendRaw(data: String) {
        EcosystemLogger.d(HaronConstants.TAG, "INPUT> raw='${data.take(20)}' isSsh=$isSsh")
        if (isSsh) viewModelScope.launch { sshManager.sendRaw(data) }
        else shellSession.sendRaw(data.toByteArray(Charsets.UTF_8))
    }

    fun sendInterrupt() {
        EcosystemLogger.d(HaronConstants.TAG, "INPUT> INTERRUPT isSsh=$isSsh")
        if (isSsh) viewModelScope.launch { sshManager.sendRaw("\u0003") }
        else shellSession.sendInterrupt()
    }

    fun resizePty(rows: Int, cols: Int) {
        if (shellSession.isAlive) shellSession.resize(rows, cols)
        // SSH resize disabled — setPtySize breaks JSch PipedInputStream
    }

    // --- SSH ---

    fun showSshDialog() {
        _state.update { it.copy(showSshDialog = true) }
    }

    fun dismissSshDialog() {
        _state.update { it.copy(showSshDialog = false) }
    }

    fun getSavedPassword(user: String, host: String, port: Int): String {
        return sshCredentialStore.load(user, host, port)?.password ?: ""
    }

    fun connectSsh(user: String, host: String, port: Int, password: String, savePassword: Boolean) {
        _state.update { it.copy(showSshDialog = false, sshConnecting = true) }
        buffer.processOutput("\r\nConnecting to $user@$host:$port...\r\n")

        if (savePassword && password.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                sshCredentialStore.save(SshCredential(user, host, port, password))
            }
        }

        viewModelScope.launch {
            try {
                // Set PTY size to match actual buffer so cursor positioning is correct
                sshManager.initialCols = buffer.cols
                sshManager.initialRows = buffer.rows
                sshManager.connect(SshConnectionParams(user, host, port, password))

                // Start reading loop in IO dispatcher (same pool as JSch — required for available())
                launch(Dispatchers.IO) {
                    sshManager.startReading()
                }

                // Collect SSH output → same buffer
                sshReaderJob = launch {
                    sshManager.outputFlow.collect { chunk ->
                        buffer.processOutput(chunk)
                        logPty(chunk)
                    }
                }

                _state.update {
                    it.copy(
                        sshConnected = true,
                        sshConnecting = false,
                        sshUser = user,
                        sshHost = host
                    )
                }
                EcosystemLogger.d(HaronConstants.TAG, "TerminalVM: SSH connected to $user@$host:$port")
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "TerminalVM: SSH failed — ${e.message}")
                _state.update { it.copy(sshConnecting = false) }
                val msg = when {
                    e.message?.contains("Auth") == true -> "Authentication failed"
                    e.message?.contains("timeout", true) == true -> "Connection timed out"
                    e.message?.contains("refuse", true) == true -> "Connection refused"
                    else -> "SSH error: ${e.message}"
                }
                buffer.processOutput("$msg\r\n")
            }
        }
    }

    fun disconnectSsh() {
        sshReaderJob?.cancel()
        sshReaderJob = null
        sshManager.disconnect()
        _state.update {
            it.copy(sshConnected = false, sshConnecting = false, sshUser = "", sshHost = "")
        }
        buffer.processOutput("\r\n[SSH disconnected]\r\n")
        EcosystemLogger.d(HaronConstants.TAG, "TerminalVM: SSH disconnected")
    }

    // --- History ---

    private fun loadHistory(): List<String> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) { emptyList() }
    }

    fun historyUp(): String? {
        val history = _state.value.history
        if (history.isEmpty()) return null
        val newIndex = if (_state.value.historyIndex < 0) history.lastIndex
        else (_state.value.historyIndex - 1).coerceAtLeast(0)
        _state.update { it.copy(historyIndex = newIndex) }
        return history[newIndex]
    }

    fun historyDown(): String? {
        val history = _state.value.history
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

    // --- Util ---

    private fun logPty(chunk: String) {
        // Log raw with escape codes visible for debugging
        val escaped = chunk.take(300).replace("\u001B", "⟨ESC⟩").replace("\r", "⟨CR⟩").replace("\n", "⟨LF⟩")
        if (escaped.isNotBlank()) {
            EcosystemLogger.d(HaronConstants.TAG, "PTY> $escaped")
        }
    }

    override fun onCleared() {
        super.onCleared()
        shellReaderJob?.cancel()
        shellSession.stop()
        sshReaderJob?.cancel()
        sshManager.disconnect()
    }
}
