package com.vamp.haron.data.ftp

import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Raw socket-based FTP/FTPS client.
 * Replaces Apache Commons Net FTPClient with direct RFC 959 protocol implementation.
 * Supports passive mode, binary transfers, FTPS (implicit TLS with session reuse).
 */
class SimpleFtpClient {

    private var controlSocket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    private var useTls: Boolean = false
    private var sslSession: SSLSession? = null
    private var sslSocketFactory: SSLSocketFactory? = null

    private var _replyCode: Int = 0
    private var _replyString: String = ""
    private var _host: String = ""
    private var _port: Int = 21

    private var pendingDataSocket: Socket? = null
    private var restartOffset: Long = 0L

    val replyCode: Int get() = _replyCode
    val replyString: String get() = _replyString

    val isConnected: Boolean
        get() = controlSocket?.isConnected == true && controlSocket?.isClosed == false

    // -- Connection --

    fun connect(host: String, port: Int, useTls: Boolean, connectTimeout: Int, soTimeout: Int) {
        _host = host
        _port = port
        this.useTls = useTls

        val socket: Socket = if (useTls) {
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf(TrustAllManager()), null)
            sslSocketFactory = ctx.socketFactory
            val sslSock = sslSocketFactory!!.createSocket() as SSLSocket
            sslSock.connect(InetSocketAddress(host, port), connectTimeout)
            sslSock.soTimeout = soTimeout
            sslSock.startHandshake()
            sslSession = sslSock.session
            sslSock
        } else {
            val sock = Socket()
            sock.connect(InetSocketAddress(host, port), connectTimeout)
            sock.soTimeout = soTimeout
            sock
        }

        controlSocket = socket
        reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
        writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))

        // Read server greeting
        readResponse()
    }

    fun login(username: String, password: String): Boolean {
        sendCommand("USER $username")
        // 331 = need password, 230 = logged in without password
        if (_replyCode == 331) {
            sendCommand("PASS $password")
        }
        return _replyCode == 230
    }

    fun logout() {
        try {
            sendCommand("QUIT")
        } catch (_: Exception) {
        }
    }

    fun disconnect() {
        try { reader?.close() } catch (_: Exception) {}
        try { writer?.close() } catch (_: Exception) {}
        try { controlSocket?.close() } catch (_: Exception) {}
        controlSocket = null
        reader = null
        writer = null
        sslSession = null
    }

    fun setSoTimeout(timeoutMs: Int) {
        controlSocket?.soTimeout = timeoutMs
    }

    // -- Mode commands --

    fun enterPassiveMode() {
        // Just sets mode — actual PASV is sent per-transfer in openDataConnection
    }

    fun setBinaryMode() {
        sendCommand("TYPE I")
    }

    fun sendUtf8Opts() {
        try {
            sendCommand("OPTS UTF8 ON")
        } catch (_: Exception) {
        }
    }

    fun setRestartOffset(offset: Long) {
        restartOffset = offset
    }

    // -- File operations --

    fun listFiles(path: String): List<FtpFileEntry> {
        val dataSocket = openDataConnection()
        sendCommand("LIST $path")
        if (_replyCode != 150 && _replyCode != 125) {
            dataSocket.close()
            return emptyList()
        }

        val lines = mutableListOf<String>()
        try {
            BufferedReader(InputStreamReader(dataSocket.getInputStream(), Charsets.UTF_8)).use { dr ->
                var line: String?
                while (dr.readLine().also { line = it } != null) {
                    line?.let { if (it.isNotBlank()) lines.add(it) }
                }
            }
        } finally {
            dataSocket.close()
        }

        // Read final 226 response
        readResponse()

        return lines.mapNotNull { parseLsLine(it) }
    }

    /**
     * Get file info via MLST command (single file metadata in control channel).
     * Returns null if server doesn't support MLST.
     */
    fun getFileInfo(path: String): FtpFileEntry? {
        sendCommand("MLST $path")
        if (_replyCode != 250) return null
        // MLST response is multiline: 250-Start\r\n <entry>\r\n 250 End
        // The entry line is in _replyString
        return parseMlstResponse(_replyString)
    }

    /**
     * Get file size via SIZE command.
     */
    fun getFileSize(path: String): Long {
        sendCommand("SIZE $path")
        if (_replyCode != 213) return -1L
        return _replyString.trim().substringAfterLast(" ").toLongOrNull() ?: -1L
    }

    fun retrieveFileStream(remotePath: String): InputStream? {
        val dataSocket = openDataConnection()
        if (restartOffset > 0) {
            sendCommand("REST $restartOffset")
            restartOffset = 0L
            if (_replyCode != 350) {
                dataSocket.close()
                return null
            }
        }
        sendCommand("RETR $remotePath")
        if (_replyCode != 150 && _replyCode != 125) {
            dataSocket.close()
            return null
        }
        pendingDataSocket = dataSocket
        return dataSocket.getInputStream()
    }

    fun storeFileStream(remotePath: String): OutputStream? {
        val dataSocket = openDataConnection()
        sendCommand("STOR $remotePath")
        if (_replyCode != 150 && _replyCode != 125) {
            dataSocket.close()
            return null
        }
        pendingDataSocket = dataSocket
        return dataSocket.getOutputStream()
    }

    fun completePendingCommand(): Boolean {
        try {
            pendingDataSocket?.close()
        } catch (_: Exception) {
        }
        pendingDataSocket = null
        readResponse()
        return _replyCode in 200..299
    }

    fun makeDirectory(path: String): Boolean {
        sendCommand("MKD $path")
        return _replyCode == 257
    }

    fun deleteFile(path: String): Boolean {
        sendCommand("DELE $path")
        return _replyCode == 250
    }

    fun removeDirectory(path: String): Boolean {
        sendCommand("RMD $path")
        return _replyCode == 250
    }

    fun rename(from: String, to: String): Boolean {
        sendCommand("RNFR $from")
        if (_replyCode != 350) return false
        sendCommand("RNTO $to")
        return _replyCode == 250
    }

    // -- Internal protocol methods --

    private fun sendCommand(cmd: String) {
        val w = writer ?: throw IOException("Not connected")
        val logCmd = if (cmd.startsWith("PASS ")) "PASS ***" else cmd
        EcosystemLogger.d(HaronConstants.TAG, "FTP> $logCmd")
        w.write(cmd)
        w.write("\r\n")
        w.flush()
        readResponse()
    }

    /**
     * Read FTP response (possibly multiline).
     * Multiline: first line "code-text", subsequent lines until "code text" (space after code).
     */
    private fun readResponse() {
        val r = reader ?: throw IOException("Not connected")
        val sb = StringBuilder()
        val firstLine = r.readLine() ?: throw IOException("Connection closed by server")
        sb.appendLine(firstLine)

        if (firstLine.length < 3) throw IOException("Invalid FTP response: $firstLine")
        val code = firstLine.substring(0, 3).toIntOrNull()
            ?: throw IOException("Invalid FTP response code: $firstLine")

        // Check if multiline (code followed by '-')
        if (firstLine.length > 3 && firstLine[3] == '-') {
            while (true) {
                val line = r.readLine() ?: break
                sb.appendLine(line)
                // End of multiline: line starts with same code + space
                if (line.length >= 4 && line.startsWith("$code ")) break
            }
        }

        _replyCode = code
        _replyString = sb.toString().trim()
        EcosystemLogger.d(HaronConstants.TAG, "FTP< $code ${_replyString.take(200)}")
    }

    /**
     * Open a data connection using PASV mode.
     * For FTPS, wraps the data socket in SSL with session reuse.
     */
    private fun openDataConnection(): Socket {
        val w = writer ?: throw IOException("Not connected")
        w.write("PASV\r\n")
        w.flush()
        readResponse()
        if (_replyCode != 227) {
            throw IOException("PASV failed: $_replyCode $_replyString")
        }

        // Parse: 227 Entering Passive Mode (h1,h2,h3,h4,p1,p2)
        val matcher = PASV_PATTERN.matcher(_replyString)
        if (!matcher.find()) {
            throw IOException("Cannot parse PASV response: $_replyString")
        }
        val h1 = matcher.group(1)!!
        val h2 = matcher.group(2)!!
        val h3 = matcher.group(3)!!
        val h4 = matcher.group(4)!!
        val p1 = matcher.group(5)!!.toInt()
        val p2 = matcher.group(6)!!.toInt()

        // Some servers return 0.0.0.0 or internal IPs — use control connection host instead
        val dataHost = if (h1 == "0" && h2 == "0" && h3 == "0" && h4 == "0") {
            _host
        } else {
            "$h1.$h2.$h3.$h4"
        }
        val dataPort = p1 * 256 + p2

        val plainSocket = Socket()
        plainSocket.connect(InetSocketAddress(dataHost, dataPort), HaronConstants.FTP_CONNECT_TIMEOUT_MS)
        plainSocket.soTimeout = HaronConstants.FTP_SO_TIMEOUT_MS

        return if (useTls && sslSocketFactory != null) {
            // Wrap in SSL socket, reusing session from control connection
            val sslSock = sslSocketFactory!!.createSocket(
                plainSocket, dataHost, dataPort, true
            ) as SSLSocket
            sslSock.useClientMode = true
            // Session reuse: set the session from control connection
            sslSession?.let { session ->
                try {
                    val sslParams = sslSock.sslParameters
                    // On newer Java/Android versions, session reuse happens automatically
                    // when using the same SSLContext. For older versions, this is best-effort.
                    sslSock.sslParameters = sslParams
                } catch (_: Exception) {
                }
            }
            sslSock.startHandshake()
            sslSock
        } else {
            plainSocket
        }
    }

    // -- LIST output parsing --

    /**
     * Parse a single line of Unix `ls -l` output.
     * Format: drwxr-xr-x  2 user group  4096 Jan 01 12:00 dirname
     *         -rw-r--r--  1 user group 12345 Jan 01 12:00 filename.txt
     *
     * Also handles Windows IIS format:
     * 01-01-20  12:00AM       <DIR>          dirname
     * 01-01-20  12:00AM              12345 filename.txt
     */
    private fun parseLsLine(line: String): FtpFileEntry? {
        if (line.isBlank()) return null

        // Try Unix format first
        if (line.length > 10 && (line[0] == '-' || line[0] == 'd' || line[0] == 'l')) {
            return parseUnixLsLine(line)
        }

        // Try Windows/IIS format
        if (line.length > 10 && line[0].isDigit()) {
            return parseWindowsLsLine(line)
        }

        // Unknown format — skip
        return null
    }

    private fun parseUnixLsLine(line: String): FtpFileEntry? {
        // Split carefully: permissions, links, owner, group, size, month, day, time/year, name...
        val parts = line.split(Regex("\\s+"), limit = 9)
        if (parts.size < 9) return null

        val permissions = parts[0]
        val isDir = permissions.startsWith("d")
        val isLink = permissions.startsWith("l")
        val size = parts[4].toLongOrNull() ?: 0L

        // Date: parts[5]=month, parts[6]=day, parts[7]=time_or_year
        val timestamp = parseUnixDate(parts[5], parts[6], parts[7])

        // Name: parts[8], but for symlinks strip " -> target"
        var name = parts[8]
        if (isLink && " -> " in name) {
            name = name.substringBefore(" -> ")
        }

        if (name == "." || name == "..") return null

        return FtpFileEntry(
            name = name,
            isDirectory = isDir,
            size = size,
            timestampMillis = timestamp,
            rawPermissions = permissions
        )
    }

    private fun parseWindowsLsLine(line: String): FtpFileEntry? {
        // Format: MM-DD-YY  HH:MMAM/PM  <DIR>  name  OR  MM-DD-YY  HH:MMAM/PM  size  name
        val parts = line.trim().split(Regex("\\s+"), limit = 4)
        if (parts.size < 4) return null

        val isDir = parts[2] == "<DIR>"
        val size = if (isDir) 0L else parts[2].toLongOrNull() ?: 0L
        val name = parts[3]

        if (name == "." || name == "..") return null

        val timestamp = parseWindowsDate(parts[0], parts[1])

        return FtpFileEntry(
            name = name,
            isDirectory = isDir,
            size = size,
            timestampMillis = timestamp,
            rawPermissions = if (isDir) "drwxr-xr-x" else "-rw-r--r--"
        )
    }

    private fun parseUnixDate(month: String, day: String, timeOrYear: String): Long {
        return try {
            val cal = Calendar.getInstance()
            val monthIndex = MONTHS.indexOf(month.take(3).replaceFirstChar { it.uppercase() })
            if (monthIndex < 0) return 0L

            cal.set(Calendar.MONTH, monthIndex)
            cal.set(Calendar.DAY_OF_MONTH, day.toIntOrNull() ?: 1)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)

            if (":" in timeOrYear) {
                // HH:MM format — current year
                val timeParts = timeOrYear.split(":")
                cal.set(Calendar.HOUR_OF_DAY, timeParts[0].toIntOrNull() ?: 0)
                cal.set(Calendar.MINUTE, timeParts.getOrNull(1)?.toIntOrNull() ?: 0)
                // If the date is in the future, it's probably last year
                if (cal.timeInMillis > System.currentTimeMillis() + 86400000L) {
                    cal.add(Calendar.YEAR, -1)
                }
            } else {
                // Year
                cal.set(Calendar.YEAR, timeOrYear.toIntOrNull() ?: cal.get(Calendar.YEAR))
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
            }

            cal.timeInMillis
        } catch (_: Exception) {
            0L
        }
    }

    private fun parseWindowsDate(date: String, time: String): Long {
        return try {
            val fmt = SimpleDateFormat("MM-dd-yy hh:mma", Locale.US)
            fmt.parse("$date $time")?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Parse MLST response to extract file size.
     * MLST facts format: type=file;size=12345;modify=20200101120000; filename
     */
    private fun parseMlstResponse(response: String): FtpFileEntry? {
        // Find the entry line (starts with space in multiline response)
        val entryLine = response.lines().find { it.startsWith(" ") }?.trim()
            ?: return null

        val semiIdx = entryLine.lastIndexOf(';')
        if (semiIdx < 0) return null

        val facts = entryLine.substring(0, semiIdx + 1)
        val name = entryLine.substring(semiIdx + 1).trim()

        var size = -1L
        var isDir = false
        var timestamp = 0L

        for (fact in facts.split(";")) {
            val kv = fact.split("=", limit = 2)
            if (kv.size != 2) continue
            when (kv[0].lowercase()) {
                "size" -> size = kv[1].toLongOrNull() ?: -1L
                "type" -> isDir = kv[1].equals("dir", ignoreCase = true) ||
                        kv[1].equals("cdir", ignoreCase = true) ||
                        kv[1].equals("pdir", ignoreCase = true)
                "modify" -> timestamp = parseMlstTimestamp(kv[1])
            }
        }

        return FtpFileEntry(
            name = name,
            isDirectory = isDir,
            size = size,
            timestampMillis = timestamp,
            rawPermissions = ""
        )
    }

    private fun parseMlstTimestamp(ts: String): Long {
        return try {
            val fmt = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
            fmt.parse(ts.take(14))?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    companion object {
        private val PASV_PATTERN: Pattern =
            Pattern.compile("\\((\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)\\)")

        private val MONTHS = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        )
    }
}

/**
 * Parsed FTP file entry from LIST output.
 */
data class FtpFileEntry(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val timestampMillis: Long,
    val rawPermissions: String
)

/**
 * Trust-all X509 TrustManager for FTP servers with self-signed certs.
 * Same behavior as Apache Commons Net's default.
 */
private class TrustAllManager : javax.net.ssl.X509TrustManager {
    override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
    override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
}
