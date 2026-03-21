package com.vamp.haron.data.terminal

import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
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

/**
 * SSH session using JSch.
 * Uses getInputStream()/getOutputStream() with a dedicated reader thread.
 * setPtySize() is NOT called — it breaks JSch's internal PipedInputStream.
 */
class SshSessionManager {

    private var session: Session? = null
    private var channel: ChannelShell? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private val _outputFlow = MutableSharedFlow<String>(extraBufferCapacity = 256)
    val outputFlow: SharedFlow<String> = _outputFlow.asSharedFlow()

    val isConnected: Boolean
        get() = channel?.isConnected == true && session?.isConnected == true

    /** Terminal size to use when opening channel */
    var initialCols: Int = 80
    var initialRows: Int = 40

    suspend fun connect(params: SshConnectionParams) = withContext(Dispatchers.IO) {
        EcosystemLogger.d(HaronConstants.TAG, "SshSessionManager: connecting to ${params.user}@${params.host}:${params.port}")
        try {
            disconnect()
            val jsch = JSch()
            val sess = jsch.getSession(params.user, params.host, params.port)
            sess.setPassword(params.password)
            sess.setConfig("StrictHostKeyChecking", "no")
            sess.timeout = 15_000
            sess.setServerAliveInterval(30_000)
            sess.setServerAliveCountMax(3)
            sess.connect(15_000)

            EcosystemLogger.d(HaronConstants.TAG, "SshSessionManager: session established, serverVersion=${sess.serverVersion}")

            val ch = sess.openChannel("shell") as ChannelShell
            ch.setPtyType("xterm-256color", initialCols, initialRows, 0, 0)

            // Get streams BEFORE connect — required by JSch
            inputStream = ch.inputStream
            outputStream = ch.outputStream
            ch.connect(15_000)

            session = sess
            channel = ch

            EcosystemLogger.i(HaronConstants.TAG, "SshSessionManager: connected to ${params.host}:${params.port}, shell channel opened")
        } catch (e: Throwable) {
            EcosystemLogger.e(HaronConstants.TAG, "SshSessionManager: connect failed to ${params.host}: ${e.message}")
            throw e
        }
    }

    /**
     * Read SSH output using available() + Thread.sleep in coroutine context.
     * MUST run in Dispatchers.IO — same thread pool as JSch uses internally.
     * This is the ONLY approach that works with JSch PipedInputStream.
     */
    suspend fun startReading() = withContext(Dispatchers.IO) {
        val stream = inputStream ?: return@withContext
        EcosystemLogger.d(HaronConstants.TAG, "SshSessionManager: reading loop started")
        val buf = ByteArray(4096)
        try {
            while (isActive && isConnected) {
                val available = stream.available()
                if (available > 0) {
                    val n = stream.read(buf, 0, minOf(available, buf.size))
                    if (n > 0) {
                        _outputFlow.emit(String(buf, 0, n, Charsets.UTF_8))
                    }
                } else {
                    if (channel?.isClosed == true) {
                        EcosystemLogger.d(HaronConstants.TAG, "SshSessionManager: channel closed")
                        _outputFlow.emit("\r\n[Connection closed]")
                        break
                    }
                    Thread.sleep(50)
                }
            }
        } catch (e: Throwable) {
            EcosystemLogger.e(HaronConstants.TAG, "SshSessionManager: reading error: ${e.message}")
            if (isConnected) {
                _outputFlow.tryEmit("\r\n[Connection lost]")
            }
        }
    }

    suspend fun sendRaw(data: String) = withContext(Dispatchers.IO) {
        val os = outputStream
        if (os == null) {
            EcosystemLogger.e(HaronConstants.TAG, "SshSessionManager: sendRaw — outputStream is null!")
            return@withContext
        }
        try {
            os.write(data.toByteArray(Charsets.UTF_8))
            os.flush()
        } catch (e: Throwable) {
            EcosystemLogger.e(HaronConstants.TAG, "SshSessionManager: sendRaw failed: ${e.message}")
        }
    }

    // setPtySize NOT implemented — it breaks JSch PipedInputStream

    fun disconnect() {
        EcosystemLogger.d(HaronConstants.TAG, "SshSessionManager: disconnecting")
        try { channel?.disconnect() } catch (_: Exception) {}
        try { session?.disconnect() } catch (_: Exception) {}
        channel = null
        session = null
        outputStream = null
        inputStream = null
        EcosystemLogger.d(HaronConstants.TAG, "SshSessionManager: disconnected")
    }
}
