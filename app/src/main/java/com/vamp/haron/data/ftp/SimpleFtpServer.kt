package com.vamp.haron.data.ftp

import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight embedded FTP server (RFC 959).
 * Replaces Apache ftpserver-core (~0.5 МБ).
 * Supports: USER/PASS, SYST, FEAT, TYPE, PWD, CWD, CDUP, LIST, NLST,
 * PASV, RETR, STOR, DELE, MKD, RMD, RNFR, RNTO, SIZE, NOOP, QUIT.
 */
class SimpleFtpServer(
    private val rootDir: String,
    private val config: FtpServerConfig
) {
    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private val executor = Executors.newCachedThreadPool()
    private val sessions = ConcurrentHashMap<Int, Socket>()
    private var sessionCounter = 0

    var onConnect: ((String) -> Unit)? = null
    var onLogin: ((String, String) -> Unit)? = null
    var onDisconnect: ((String) -> Unit)? = null
    var onUpload: ((String, String) -> Unit)? = null
    var onDownload: ((String, String) -> Unit)? = null

    val isRunning: Boolean get() = running.get()

    fun start(port: Int): Int {
        stop()
        var actualPort = port
        for (attempt in 0..1) {
            try {
                val ss = ServerSocket(actualPort, 50)
                serverSocket = ss
                running.set(true)
                EcosystemLogger.d(HaronConstants.TAG, "SimpleFtpServer: listening on port $actualPort")
                executor.submit { acceptLoop(ss) }
                return actualPort
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "SimpleFtpServer: port $actualPort failed: ${e.message}")
                if (attempt == 0) actualPort = HaronConstants.FTP_SERVER_PORT_FALLBACK
            }
        }
        return -1
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        EcosystemLogger.d(HaronConstants.TAG, "SimpleFtpServer: stopping")
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        sessions.values.forEach { try { it.close() } catch (_: Exception) {} }
        sessions.clear()
    }

    private fun acceptLoop(ss: ServerSocket) {
        while (running.get()) {
            try {
                val client = ss.accept()
                val id = ++sessionCounter
                sessions[id] = client
                executor.submit { handleSession(id, client) }
            } catch (_: Exception) {
                if (!running.get()) break
            }
        }
    }

    private fun handleSession(id: Int, socket: Socket) {
        val addr = socket.remoteSocketAddress?.toString() ?: "?"
        onConnect?.invoke(addr)
        EcosystemLogger.i(HaronConstants.TAG, "FTP: connect from $addr")

        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8), true)

            val session = FtpSession(rootDir, config, writer, addr)
            writer.print("220 Haron FTP Server ready\r\n"); writer.flush()

            while (running.get() && !socket.isClosed) {
                val line = reader.readLine() ?: break
                val spaceIdx = line.indexOf(' ')
                val cmd = (if (spaceIdx > 0) line.substring(0, spaceIdx) else line).uppercase()
                val arg = if (spaceIdx > 0) line.substring(spaceIdx + 1).trim() else ""

                EcosystemLogger.d(HaronConstants.TAG, "FTP[$addr]: $cmd ${if (cmd == "PASS") "***" else arg}")

                when (cmd) {
                    "USER" -> session.handleUser(arg)
                    "PASS" -> {
                        val ok = session.handlePass(arg)
                        if (ok) onLogin?.invoke(session.username, addr)
                    }
                    "SYST" -> session.reply("215 UNIX Type: L8")
                    "FEAT" -> {
                        writer.print("211-Features:\r\n SIZE\r\n UTF8\r\n PASV\r\n211 End\r\n"); writer.flush()
                    }
                    "OPTS" -> {
                        if (arg.uppercase().startsWith("UTF8")) session.reply("200 UTF8 ON")
                        else session.reply("501 Option not recognized")
                    }
                    "TYPE" -> session.reply("200 Type set to ${arg.uppercase()}")
                    "PWD", "XPWD" -> session.handlePwd()
                    "CWD", "XCWD" -> session.handleCwd(arg)
                    "CDUP", "XCUP" -> session.handleCwd("..")
                    "LIST" -> session.handleList(false)
                    "NLST" -> session.handleList(true)
                    "PASV" -> session.handlePasv()
                    "RETR" -> {
                        session.handleRetr(arg)
                        onDownload?.invoke(session.username, arg)
                    }
                    "STOR" -> {
                        session.handleStor(arg)
                        onUpload?.invoke(session.username, arg)
                    }
                    "DELE" -> session.handleDele(arg)
                    "MKD", "XMKD" -> session.handleMkd(arg)
                    "RMD", "XRMD" -> session.handleRmd(arg)
                    "RNFR" -> session.handleRnfr(arg)
                    "RNTO" -> session.handleRnto(arg)
                    "SIZE" -> session.handleSize(arg)
                    "NOOP" -> session.reply("200 OK")
                    "QUIT" -> {
                        session.reply("221 Bye")
                        break
                    }
                    else -> session.reply("502 Command not implemented")
                }
            }
        } catch (_: Exception) {
        } finally {
            onDisconnect?.invoke(addr)
            EcosystemLogger.i(HaronConstants.TAG, "FTP: disconnect from $addr")
            sessions.remove(id)
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private class FtpSession(
        private val rootDir: String,
        private val config: FtpServerConfig,
        private val writer: PrintWriter,
        private val addr: String
    ) {
        var username = ""
        private var authenticated = false
        private var currentDir = "/" // virtual path relative to rootDir
        private var dataSocket: ServerSocket? = null
        private var renameFrom: File? = null

        fun reply(msg: String) {
            writer.print("$msg\r\n"); writer.flush()
        }

        fun handleUser(user: String) {
            username = user
            if (config.anonymousAccess && user.equals("anonymous", true)) {
                reply("331 Anonymous login, send email as password")
            } else if (user == config.username) {
                reply("331 Password required for $user")
            } else if (config.anonymousAccess) {
                // Accept any username in anonymous mode
                reply("331 Password required")
            } else {
                reply("530 Login incorrect")
            }
        }

        fun handlePass(pass: String): Boolean {
            if (config.anonymousAccess && (username.equals("anonymous", true) || config.username.isEmpty())) {
                authenticated = true
                reply("230 Anonymous login successful")
                return true
            }
            if (username == config.username && pass == config.password) {
                authenticated = true
                reply("230 Login successful")
                return true
            }
            reply("530 Login incorrect")
            return false
        }

        fun handlePwd() {
            if (!checkAuth()) return
            reply("257 \"$currentDir\" is current directory")
        }

        fun handleCwd(path: String) {
            if (!checkAuth()) return
            val newPath = resolvePath(path)
            val file = File(rootDir, newPath)
            if (file.isDirectory) {
                currentDir = newPath
                reply("250 Directory changed to $currentDir")
            } else {
                reply("550 Directory not found")
            }
        }

        fun handleList(namesOnly: Boolean) {
            if (!checkAuth()) return
            val dir = File(rootDir, currentDir)
            val files = dir.listFiles() ?: emptyArray()
            val ds = acceptDataConnection() ?: return

            try {
                val out = PrintWriter(OutputStreamWriter(ds.getOutputStream(), Charsets.UTF_8), true)
                reply("150 Opening data connection")

                if (namesOnly) {
                    for (f in files) {
                        out.print("${f.name}\r\n")
                    }
                } else {
                    val sdf = SimpleDateFormat("MMM dd HH:mm", Locale.US)
                    for (f in files) {
                        val perm = if (f.isDirectory) "drwxr-xr-x" else "-rw-r--r--"
                        val size = if (f.isDirectory) 4096L else f.length()
                        val date = sdf.format(Date(f.lastModified()))
                        out.print("$perm 1 ftp ftp %12d $date ${f.name}\r\n".format(size))
                    }
                }
                out.flush()
            } finally {
                ds.close()
            }
            reply("226 Transfer complete")
        }

        fun handlePasv() {
            if (!checkAuth()) return
            closeDataSocket()
            try {
                val ss = ServerSocket(0, 1)
                ss.soTimeout = 30_000
                dataSocket = ss
                val port = ss.localPort
                val p1 = port / 256
                val p2 = port % 256
                // Use 0,0,0,0 — client will connect to the server IP
                reply("227 Entering Passive Mode (0,0,0,0,$p1,$p2)")
            } catch (e: Exception) {
                reply("425 Can't open passive connection: ${e.message}")
            }
        }

        fun handleRetr(filename: String) {
            if (!checkAuth()) return
            val file = File(rootDir, resolvePath(filename))
            if (!file.isFile) {
                reply("550 File not found")
                return
            }
            val ds = acceptDataConnection() ?: return
            try {
                reply("150 Opening data connection for ${file.name} (${file.length()} bytes)")
                file.inputStream().use { input ->
                    ds.getOutputStream().use { output ->
                        input.copyTo(output, 65536)
                    }
                }
            } finally {
                ds.close()
            }
            reply("226 Transfer complete")
        }

        fun handleStor(filename: String) {
            if (!checkAuth()) return
            if (config.readOnly) {
                reply("550 Read-only server")
                return
            }
            val file = File(rootDir, resolvePath(filename))
            val ds = acceptDataConnection() ?: return
            try {
                reply("150 Opening data connection for upload")
                ds.getInputStream().use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output, 65536)
                    }
                }
            } finally {
                ds.close()
            }
            reply("226 Transfer complete")
        }

        fun handleDele(filename: String) {
            if (!checkAuth()) return
            if (config.readOnly) { reply("550 Read-only server"); return }
            val file = File(rootDir, resolvePath(filename))
            if (file.isFile && file.delete()) reply("250 File deleted")
            else reply("550 Delete failed")
        }

        fun handleMkd(dirname: String) {
            if (!checkAuth()) return
            if (config.readOnly) { reply("550 Read-only server"); return }
            val dir = File(rootDir, resolvePath(dirname))
            if (dir.mkdirs()) reply("257 \"${dirname}\" created")
            else reply("550 Create directory failed")
        }

        fun handleRmd(dirname: String) {
            if (!checkAuth()) return
            if (config.readOnly) { reply("550 Read-only server"); return }
            val dir = File(rootDir, resolvePath(dirname))
            if (dir.isDirectory && dir.delete()) reply("250 Directory removed")
            else reply("550 Remove directory failed")
        }

        fun handleRnfr(filename: String) {
            if (!checkAuth()) return
            if (config.readOnly) { reply("550 Read-only server"); return }
            val file = File(rootDir, resolvePath(filename))
            if (file.exists()) {
                renameFrom = file
                reply("350 Ready for RNTO")
            } else {
                reply("550 File not found")
            }
        }

        fun handleRnto(filename: String) {
            if (!checkAuth()) return
            val from = renameFrom
            if (from == null) { reply("503 RNFR required first"); return }
            renameFrom = null
            val to = File(rootDir, resolvePath(filename))
            if (from.renameTo(to)) reply("250 Rename successful")
            else reply("550 Rename failed")
        }

        fun handleSize(filename: String) {
            if (!checkAuth()) return
            val file = File(rootDir, resolvePath(filename))
            if (file.isFile) reply("213 ${file.length()}")
            else reply("550 File not found")
        }

        private fun checkAuth(): Boolean {
            if (!authenticated) { reply("530 Not logged in"); return false }
            return true
        }

        private fun resolvePath(path: String): String {
            if (path.startsWith("/")) {
                return normalizePath(path)
            }
            return normalizePath("$currentDir/$path")
        }

        private fun normalizePath(path: String): String {
            val parts = path.split("/").filter { it.isNotEmpty() && it != "." }
            val stack = mutableListOf<String>()
            for (part in parts) {
                if (part == "..") { if (stack.isNotEmpty()) stack.removeAt(stack.size - 1) }
                else stack.add(part)
            }
            return "/" + stack.joinToString("/")
        }

        private fun acceptDataConnection(): Socket? {
            val ss = dataSocket ?: run {
                reply("425 No data connection")
                return null
            }
            return try {
                val s = ss.accept()
                closeDataSocket()
                s
            } catch (e: Exception) {
                reply("425 Data connection failed: ${e.message}")
                closeDataSocket()
                null
            }
        }

        private fun closeDataSocket() {
            try { dataSocket?.close() } catch (_: Exception) {}
            dataSocket = null
        }
    }
}
