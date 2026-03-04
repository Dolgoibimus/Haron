package com.vamp.haron.data.terminal

import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

data class SshConnectionParams(
    val user: String,
    val host: String,
    val port: Int = 22,
    val password: String
)

class SshSessionManager {

    private var session: Session? = null
    private var channel: ChannelShell? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null

    private val _outputFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val outputFlow: SharedFlow<String> = _outputFlow.asSharedFlow()

    val isConnected: Boolean
        get() = channel?.isConnected == true && session?.isConnected == true

    suspend fun connect(params: SshConnectionParams) = withContext(Dispatchers.IO) {
        disconnect()
        val jsch = JSch()
        val sess = jsch.getSession(params.user, params.host, params.port)
        sess.setPassword(params.password)
        sess.setConfig("StrictHostKeyChecking", "no")
        sess.timeout = 15_000
        sess.connect(15_000)

        val ch = sess.openChannel("shell") as ChannelShell
        ch.setPtyType("xterm-256color", 120, 40, 0, 0)

        inputStream = ch.inputStream
        outputStream = ch.outputStream
        ch.connect(15_000)

        session = sess
        channel = ch
    }

    suspend fun startReading() = withContext(Dispatchers.IO) {
        val stream = inputStream ?: return@withContext
        val buffer = ByteArray(4096)
        try {
            while (isActive && isConnected) {
                val available = stream.available()
                if (available > 0) {
                    val read = stream.read(buffer, 0, minOf(available, buffer.size))
                    if (read > 0) {
                        val chunk = String(buffer, 0, read, Charsets.UTF_8)
                        _outputFlow.emit(chunk)
                    }
                } else {
                    // Check if channel closed
                    if (channel?.isClosed == true) {
                        val exitStatus = channel?.exitStatus ?: -1
                        _outputFlow.emit("\n[Connection closed, exit status: $exitStatus]")
                        break
                    }
                    Thread.sleep(50)
                }
            }
        } catch (_: Exception) {
            if (isConnected) {
                _outputFlow.tryEmit("\n[Connection lost]")
            }
        }
    }

    suspend fun sendCommand(command: String) = withContext(Dispatchers.IO) {
        try {
            outputStream?.let { os ->
                os.write("$command\r".toByteArray(Charsets.UTF_8))
                os.flush()
            }
        } catch (_: Exception) {
            _outputFlow.tryEmit("\n[Failed to send command]")
        }
    }

    suspend fun sendRaw(data: String) = withContext(Dispatchers.IO) {
        try {
            outputStream?.let { os ->
                os.write(data.toByteArray(Charsets.UTF_8))
                os.flush()
            }
        } catch (_: Exception) {
            _outputFlow.tryEmit("\n[Failed to send data]")
        }
    }

    fun disconnect() {
        try {
            channel?.disconnect()
        } catch (_: Exception) {}
        try {
            session?.disconnect()
        } catch (_: Exception) {}
        channel = null
        session = null
        outputStream = null
        inputStream = null
    }
}
