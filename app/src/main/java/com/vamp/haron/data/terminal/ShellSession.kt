package com.vamp.haron.data.terminal

import android.os.Environment
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Persistent shell session with real PTY.
 * Ctrl+C, vi, nano, htop — everything works.
 * Output streamed via SharedFlow (same pattern as SshSessionManager).
 */
class ShellSession {

    private var masterFd: Int = -1
    private var pid: Int = -1
    private var inputStream: FileInputStream? = null
    private var outputStream: FileOutputStream? = null
    private var readerThread: Thread? = null
    private var isRunning = false

    private val _outputFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val outputFlow: SharedFlow<String> = _outputFlow.asSharedFlow()

    val isAlive: Boolean
        get() = isRunning && pid > 0

    /**
     * Start a persistent shell with real PTY.
     */
    suspend fun start(
        workingDir: String = Environment.getExternalStorageDirectory().absolutePath,
        rows: Int = 40,
        cols: Int = 120
    ) {
        if (isRunning) return

        withContext(Dispatchers.IO) {
            try {
                val result = PtyNative.createSubprocess("sh", workingDir, rows, cols)
                    ?: throw RuntimeException("Failed to create PTY subprocess")

                pid = result[0]
                masterFd = result[1]

                // Create Java streams from file descriptor
                val fd = createFileDescriptor(masterFd)
                inputStream = FileInputStream(fd)
                outputStream = FileOutputStream(fd)
                isRunning = true

                EcosystemLogger.d(HaronConstants.TAG, "ShellSession: PTY started, pid=$pid, fd=$masterFd, dir=$workingDir")

                // Start reading output
                readerThread = Thread {
                    readOutput()
                }.apply {
                    name = "ShellSession-pty-reader"
                    isDaemon = true
                    start()
                }
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "ShellSession: PTY start failed — ${e.message}")
                isRunning = false
                throw e
            }
        }
    }

    /**
     * Send a command to the shell. Newline appended automatically.
     */
    fun sendCommand(command: String) {
        val out = outputStream ?: return
        try {
            out.write("$command\n".toByteArray(Charsets.UTF_8))
            out.flush()
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "ShellSession: send failed — ${e.message}")
        }
    }

    /**
     * Send raw bytes (e.g. Ctrl+C, arrow keys).
     */
    fun sendRaw(data: ByteArray) {
        val out = outputStream ?: return
        try {
            out.write(data)
            out.flush()
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "ShellSession: sendRaw failed — ${e.message}")
        }
    }

    /**
     * Send Ctrl+C (SIGINT to foreground process group via PTY).
     * With real PTY, this is handled by terminal line discipline — proper SIGINT.
     */
    fun sendInterrupt() {
        sendRaw(byteArrayOf(0x03)) // ETX → PTY converts to SIGINT
        EcosystemLogger.d(HaronConstants.TAG, "ShellSession: Ctrl+C via PTY")
    }

    fun sendEof() = sendRaw(byteArrayOf(0x04))
    fun sendSuspend() = sendRaw(byteArrayOf(0x1A))

    /**
     * Resize the terminal.
     */
    fun resize(rows: Int, cols: Int) {
        if (masterFd >= 0) {
            PtyNative.setWindowSize(masterFd, rows, cols)
            EcosystemLogger.d(HaronConstants.TAG, "ShellSession: resized to ${cols}x${rows}")
        }
    }

    /**
     * Stop the shell session.
     */
    fun stop() {
        EcosystemLogger.d(HaronConstants.TAG, "ShellSession: stopping, pid=$pid")
        isRunning = false
        readerThread?.interrupt()
        readerThread = null

        try { outputStream?.close() } catch (_: Exception) {}
        try { inputStream?.close() } catch (_: Exception) {}
        outputStream = null
        inputStream = null

        if (pid > 0) {
            try {
                PtyNative.sendSignal(pid, PtyNative.SIGTERM)
                // Give it a moment, then force kill
                Thread.sleep(100)
                PtyNative.sendSignal(pid, PtyNative.SIGKILL)
            } catch (_: Exception) {}
            pid = -1
        }
        masterFd = -1
    }

    /**
     * Read PTY output and emit to flow.
     */
    private fun readOutput() {
        val buffer = ByteArray(4096)
        val stream = inputStream ?: return
        try {
            while (isRunning && !Thread.currentThread().isInterrupted) {
                val bytesRead = stream.read(buffer)
                if (bytesRead > 0) {
                    val text = String(buffer, 0, bytesRead, Charsets.UTF_8)
                    _outputFlow.tryEmit(text)
                } else if (bytesRead < 0) {
                    // EOF — process exited
                    EcosystemLogger.d(HaronConstants.TAG, "ShellSession: PTY EOF, process exited")
                    break
                }
            }
        } catch (_: InterruptedException) {
            // Normal shutdown
        } catch (e: Exception) {
            if (isRunning) {
                EcosystemLogger.e(HaronConstants.TAG, "ShellSession: reader error — ${e.message}")
                _outputFlow.tryEmit("\r\n[Shell session ended: ${e.message}]\r\n")
            }
        } finally {
            isRunning = false
        }
    }

    /**
     * Create a FileDescriptor from a native fd int.
     * Uses reflection since FileDescriptor(int) is not public.
     */
    private fun createFileDescriptor(fd: Int): FileDescriptor {
        val fileDescriptor = FileDescriptor()
        val field = FileDescriptor::class.java.getDeclaredField("descriptor")
        field.isAccessible = true
        field.setInt(fileDescriptor, fd)
        return fileDescriptor
    }
}
