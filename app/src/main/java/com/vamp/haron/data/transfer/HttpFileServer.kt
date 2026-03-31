package com.vamp.haron.data.transfer

import android.content.Context
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import com.vamp.haron.data.cast.RemoteInputChannel
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

data class HttpDownloadEvent(
    val fileIndex: Int,
    val fileName: String,
    val fileSize: Long
)

/**
 * Embedded HTTP server for sharing files over the local network.
 * Built on SimpleHttpServer (raw sockets, replaces Ktor CIO ~3 MB).
 * Serves static files, directory listings, PDF page previews, HLS streams,
 * cloud/FTP proxy, and WebSocket for remote input events.
 */
@Singleton
class HttpFileServer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remoteInputChannel: RemoteInputChannel,
    private val ftpClientManager: com.vamp.haron.data.ftp.FtpClientManager,
    private val sftpClientManager: com.vamp.haron.data.sftp.SftpClientManager
) {
    private var server: SimpleHttpServer? = null
    private var sharedFiles: List<File> = emptyList()
    var actualPort: Int = 0
        private set

    private val _downloadEvents = MutableSharedFlow<HttpDownloadEvent>(extraBufferCapacity = 64)
    val downloadEvents: SharedFlow<HttpDownloadEvent> = _downloadEvents.asSharedFlow()

    // Extended cast data
    private var slideshowFiles: List<File> = emptyList()
    private var slideshowIntervalSec: Int = 5
    private var pdfFile: File? = null
    @Suppress("unused")
    private var fileInfoHtml: String? = null

    // Cloud streaming proxy
    data class CloudStreamConfig(
        val fileId: String,
        val accountId: String,
        val fileName: String,
        val fileSize: Long
    ) {
        val providerScheme: String get() = accountId.substringBefore(':')
    }
    private val cloudStreams = mutableMapOf<String, CloudStreamConfig>()
    var cloudTokenProvider: ((String) -> String?)? = null

    // Torrent streaming proxy
    var torrentStreamRepo: com.vamp.haron.domain.repository.TorrentStreamRepository? = null

    // HLS
    @Volatile private var hlsDir: File? = null

    private var wsScope: CoroutineScope? = null

    suspend fun start(files: List<File>): Int {
        stop()
        sharedFiles = files

        val port = findAvailablePort()
        actualPort = port
        EcosystemLogger.d(HaronConstants.TAG, "HttpFileServer.start: port=$port, files=${files.size}, " +
                "fileNames=${files.take(3).map { it.name }}")

        logAllNetworkInterfaces()

        val httpServer = SimpleHttpServer(port)
        setupRoutes(httpServer)
        setupWebSocket(httpServer)

        try {
            httpServer.start()
            server = httpServer
            val serverUrl = getServerUrl()
            EcosystemLogger.d(HaronConstants.TAG, "HTTP server started on port $port, url=$serverUrl")
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "HTTP server FAILED to start on port $port: ${e::class.simpleName}: ${e.message}")
            throw e
        }
        return port
    }

    fun stop() {
        val wasPort = actualPort
        server?.stop()
        server = null
        wsScope?.cancel()
        wsScope = null
        actualPort = 0
        sharedFiles = emptyList()
        hlsDir = null
        EcosystemLogger.d(HaronConstants.TAG, "HTTP server stopped (was on port $wasPort)")
    }

    fun isRunning(): Boolean = server?.isRunning == true

    private fun setupRoutes(srv: SimpleHttpServer) {
        // --- HTML download page ---
        srv.addRoute("GET", "/") { req, resp ->
            EcosystemLogger.d(HaronConstants.TAG, "HTTP request: GET / from ${req.headers["host"]}")
            resp.respondHtml(buildHtmlPage(sharedFiles))
        }

        // --- File download ---
        srv.addRoute("GET", "/download/{index}") { req, resp ->
            val idx = req.param("index")?.toIntOrNull()
            if (idx == null || idx !in sharedFiles.indices) {
                resp.respondText("File not found", statusCode = 404); return@addRoute
            }
            val file = sharedFiles[idx]
            if (!file.exists()) { resp.respondText("File not found", statusCode = 404); return@addRoute }

            val encodedName = URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")
            val range = SimpleHttpServer.parseRange(req.header("range"), file.length())

            resp.respondStream(
                contentType = "application/octet-stream",
                totalSize = file.length(),
                rangeStart = range?.first ?: -1,
                rangeEnd = range?.second ?: -1,
                extraHeaders = mapOf("Content-Disposition" to "attachment; filename*=UTF-8''$encodedName")
            ) { output ->
                file.inputStream().use { input ->
                    if (range != null) input.skip(range.first)
                    val limit = if (range != null) range.second - range.first + 1 else file.length()
                    copyStream(input, output, limit)
                }
            }
            _downloadEvents.tryEmit(HttpDownloadEvent(idx, file.name, file.length()))
        }

        // --- File streaming ---
        srv.addRoute("GET", "/stream/{index}") { req, resp ->
            val idx = req.param("index")?.toIntOrNull()
            if (idx == null || idx !in sharedFiles.indices) {
                resp.respondText("File not found", statusCode = 404); return@addRoute
            }
            val file = sharedFiles[idx]
            if (!file.exists()) { resp.respondText("File not found", statusCode = 404); return@addRoute }

            val mime = guessMimeType(file.extension)
            val range = SimpleHttpServer.parseRange(req.header("range"), file.length())

            resp.respondStream(
                contentType = mime,
                totalSize = file.length(),
                rangeStart = range?.first ?: -1,
                rangeEnd = range?.second ?: -1
            ) { output ->
                file.inputStream().use { input ->
                    if (range != null) input.skip(range.first)
                    val limit = if (range != null) range.second - range.first + 1 else file.length()
                    copyStream(input, output, limit)
                }
            }
            _downloadEvents.tryEmit(HttpDownloadEvent(idx, file.name, file.length()))
        }

        // --- JSON file list ---
        srv.addRoute("GET", "/api/files") { _, resp ->
            val json = sharedFiles.mapIndexed { index, file ->
                """{"index":$index,"name":"${escapeJson(file.name)}","size":${file.length()}}"""
            }.joinToString(",", "[", "]")
            resp.respondJson(json)
        }

        // --- Torrent streaming proxy ---
        srv.addRoute("GET", "/torrent-stream") { req, resp ->
            val repo = torrentStreamRepo
            if (repo == null || repo.streamFileSize <= 0) {
                resp.respondText("No active torrent stream", statusCode = 404); return@addRoute
            }
            val fileSize = repo.streamFileSize
            val filePath = repo.streamFilePath
            val file = File(filePath)

            val rangeHeader = req.header("range")
            val range = SimpleHttpServer.parseRange(rangeHeader, fileSize)
            val rangeStart = range?.first ?: 0L
            val rangeEnd = range?.second ?: (fileSize - 1)
            val contentLength = rangeEnd - rangeStart + 1

            // Wait for first needed piece before responding
            val firstNeededPiece = repo.pieceIndexForOffset(rangeStart)
            if (!repo.havePiece(firstNeededPiece)) {
                val ok = repo.waitForPiece(firstNeededPiece, 120_000) // 2 min timeout for slow torrents
                if (!ok) {
                    EcosystemLogger.e(HaronConstants.TAG, "Torrent HTTP: timeout waiting for piece $firstNeededPiece")
                    resp.respondText("Buffering timeout", statusCode = 503); return@addRoute
                }
            }

            val ext = filePath.substringAfterLast('.', "").lowercase()
            val contentType = when (ext) {
                "mp4", "m4v" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "avi" -> "video/x-msvideo"
                "webm" -> "video/webm"
                else -> "application/octet-stream"
            }

            resp.respondStream(
                contentType = contentType,
                totalSize = fileSize,
                rangeStart = if (range != null) rangeStart else -1,
                rangeEnd = if (range != null) rangeEnd else -1,
                extraHeaders = mapOf("Accept-Ranges" to "bytes")
            ) { output ->
                streamTorrentData(repo, file, rangeStart, contentLength, output)
            }
        }

        // --- Cloud streaming proxy ---
        srv.addRoute("GET", "/cloud/stream/{streamId}") { req, resp ->
            val streamId = req.param("streamId")
            val config = streamId?.let { cloudStreams[it] }
            if (config == null) { resp.respondText("Stream not found", statusCode = 404); return@addRoute }

            try {
                val freshToken = cloudTokenProvider?.invoke(config.accountId)
                if (freshToken == null) {
                    resp.respondText("No access token", statusCode = 401); return@addRoute
                }

                val downloadUrl = when (config.providerScheme) {
                    "gdrive" -> "https://www.googleapis.com/drive/v3/files/${config.fileId}?alt=media"
                    "yandex" -> getYandexDownloadUrl(config.fileId, freshToken)
                        ?: run { resp.respondText("Failed to get Yandex URL", statusCode = 502); return@addRoute }
                    "dropbox" -> getDropboxTemporaryLink(config.fileId, freshToken)
                        ?: run { resp.respondText("Failed to get Dropbox link", statusCode = 502); return@addRoute }
                    else -> { resp.respondText("Unsupported provider", statusCode = 400); return@addRoute }
                }

                val needsAuth = config.providerScheme !in listOf("dropbox", "yandex")
                val url = java.net.URL(downloadUrl)
                val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "GET"
                    if (needsAuth) {
                        val authPrefix = if (config.providerScheme == "yandex") "OAuth" else "Bearer"
                        setRequestProperty("Authorization", "$authPrefix $freshToken")
                    }
                    instanceFollowRedirects = true
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    req.header("range")?.let { setRequestProperty("Range", it) }
                }

                val responseCode = conn.responseCode
                if (responseCode !in 200..299 && responseCode != 206) {
                    conn.disconnect()
                    resp.respondText("Stream error: HTTP $responseCode", statusCode = responseCode)
                    return@addRoute
                }

                val contentType = conn.getHeaderField("Content-Type") ?: "application/octet-stream"
                val contentLength = conn.getHeaderField("Content-Length")?.toLongOrNull() ?: -1
                val contentRange = conn.getHeaderField("Content-Range")

                val extraHeaders = mutableMapOf(
                    "Access-Control-Allow-Origin" to "*",
                    "Accept-Ranges" to "bytes"
                )
                if (contentRange != null) extraHeaders["Content-Range"] = contentRange

                resp.respondStream(
                    contentType = contentType,
                    totalSize = contentLength,
                    extraHeaders = extraHeaders
                ) { output ->
                    try {
                        conn.inputStream.use { input ->
                            val buffer = ByteArray(524288)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                output.flush()
                            }
                        }
                    } finally {
                        conn.disconnect()
                    }
                }
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "Cloud stream error ($streamId): ${e.message}")
                resp.respondText("Stream error: ${e.message}", statusCode = 500)
            }
        }

        // --- HLS ---
        srv.addRoute("GET", "/hls/{filename}") { req, resp ->
            val dir = hlsDir
            val name = req.param("filename")
            if (dir == null || name == null) { resp.respondText("Not found", statusCode = 404); return@addRoute }
            val file = File(dir, name)
            if (!file.exists()) { resp.respondText("Not found", statusCode = 404); return@addRoute }

            val ct = when (file.extension.lowercase()) {
                "m3u8" -> "application/vnd.apple.mpegurl"
                "ts" -> "video/mp2t"
                else -> "application/octet-stream"
            }
            resp.respondStream(
                contentType = ct,
                totalSize = file.length(),
                extraHeaders = mapOf(
                    "Access-Control-Allow-Origin" to "*",
                    "Cache-Control" to "no-cache, no-store"
                )
            ) { output ->
                file.inputStream().use { it.copyTo(output, 8192) }
            }
        }

        // --- Slideshow ---
        srv.addRoute("GET", "/slideshow") { _, resp ->
            if (slideshowFiles.isEmpty()) { resp.respondText("No slideshow", statusCode = 404); return@addRoute }
            resp.respondHtml(buildSlideshowHtml())
        }

        srv.addRoute("GET", "/slideshow/image/{index}") { req, resp ->
            val idx = req.param("index")?.toIntOrNull()
            if (idx == null || idx !in slideshowFiles.indices) { resp.respondText("Not found", statusCode = 404); return@addRoute }
            val file = slideshowFiles[idx]
            if (!file.exists()) { resp.respondText("Not found", statusCode = 404); return@addRoute }

            val mime = guessMimeType(file.extension)
            resp.respondStream(contentType = mime, totalSize = file.length()) { output ->
                file.inputStream().use { it.copyTo(output, 8192) }
            }
        }

        // --- PDF presentation ---
        srv.addRoute("GET", "/presentation/{page}") { req, resp ->
            val page = req.param("page")?.toIntOrNull()
            val pdf = pdfFile
            if (page == null || pdf == null || !pdf.exists()) { resp.respondText("Not found", statusCode = 404); return@addRoute }
            val pngBytes = renderPdfPage(pdf, page)
            if (pngBytes != null) resp.respondBytes(pngBytes, "image/png")
            else resp.respondText("Render error", statusCode = 500)
        }

        // --- FTP/SFTP proxy ---
        srv.addRoute("GET", "/ftp-proxy") { req, resp ->
            val host = req.query("host")
            val port = req.query("port")?.toIntOrNull()
            val path = req.query("path")
            val proto = req.query("proto") ?: "ftp"

            if (host == null || port == null || path == null) {
                resp.respondText("Missing params", statusCode = 400); return@addRoute
            }

            try {
                val fileSize = if (proto == "sftp") sftpClientManager.getFileSize(host, port, path)
                else ftpClientManager.getFileSize(host, port, path)

                val mimeType = guessMimeType(path.substringAfterLast('.', ""))
                val rangeHeader = req.header("range")
                val offset = if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                    rangeHeader.removePrefix("bytes=").substringBefore('-').toLongOrNull() ?: 0L
                } else 0L

                val stream = if (proto == "sftp") sftpClientManager.openInputStream(host, port, path, offset)
                else ftpClientManager.openInputStream(host, port, path, offset)

                if (stream == null) { resp.respondText("Cannot open remote file", statusCode = 404); return@addRoute }

                val (inputStream, cleanup) = stream
                try {
                    resp.respondStream(
                        contentType = mimeType,
                        totalSize = if (fileSize > 0) fileSize else -1,
                        rangeStart = if (fileSize > 0 && offset > 0) offset else -1,
                        rangeEnd = if (fileSize > 0 && offset > 0) fileSize - 1 else -1
                    ) { output ->
                        inputStream.use { it.copyTo(output, 524288) }
                    }
                } finally {
                    cleanup()
                }
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "FTP proxy error: ${e.javaClass.simpleName}: ${e.message}")
                resp.respondText("Proxy error: ${e.message}", statusCode = 500)
            }
        }

        // --- OAuth callback ---
        srv.addRoute("GET", "/oauth/callback") { req, resp ->
            val code = req.query("code")
            if (code != null) {
                com.vamp.haron.data.cloud.CloudOAuthHelper.pendingAuth.value =
                    com.vamp.haron.data.cloud.CloudOAuthHelper.PendingAuth("gdrive", code)
                resp.respondHtml("<html><body><h2>Authorization successful</h2><p>You can close this tab and return to Haron.</p></body></html>")
                EcosystemLogger.d(HaronConstants.TAG, "OAuth callback received for gdrive")
            } else {
                val error = req.query("error") ?: "unknown"
                resp.respondHtml("<html><body><h2>Authorization failed</h2><p>Error: $error</p></body></html>")
                EcosystemLogger.e(HaronConstants.TAG, "OAuth callback error: $error")
            }
        }
    }

    private fun setupWebSocket(srv: SimpleHttpServer) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        wsScope = scope

        srv.addWebSocket("/ws/remote", object : SimpleHttpServer.WsHandler {
            override fun onConnect(conn: SimpleHttpServer.WebSocketConnection) {
                EcosystemLogger.d(HaronConstants.TAG, "Remote WebSocket connected")
                // Send input events to client
                scope.launch {
                    try {
                        remoteInputChannel.events.collect { json ->
                            conn.sendText(json)
                        }
                    } catch (_: Exception) {}
                }
            }

            override fun onText(conn: SimpleHttpServer.WebSocketConnection, text: String) {
                // Client -> server messages (not used currently)
            }

            override fun onDisconnect(conn: SimpleHttpServer.WebSocketConnection) {
                EcosystemLogger.d(HaronConstants.TAG, "Remote WebSocket disconnected")
            }
        })
    }

    private fun copyStream(input: java.io.InputStream, output: java.io.OutputStream, limit: Long) {
        val buffer = ByteArray(65536)
        var remaining = limit
        while (remaining > 0) {
            val toRead = buffer.size.toLong().coerceAtMost(remaining).toInt()
            val n = input.read(buffer, 0, toRead)
            if (n == -1) break
            output.write(buffer, 0, n)
            remaining -= n
        }
        output.flush()
    }

    private fun streamTorrentData(
        repo: com.vamp.haron.domain.repository.TorrentStreamRepository,
        file: File,
        offset: Long,
        length: Long,
        output: java.io.OutputStream
    ) {
        val buffer = ByteArray(64 * 1024) // 64KB chunks
        var remaining = length
        var pos = offset

        try {
            java.io.RandomAccessFile(file, "r").use { raf ->
                raf.seek(offset)
                while (remaining > 0) {
                    // Ensure the piece containing current position is available
                    val pieceIdx = repo.pieceIndexForOffset(pos)
                    if (!repo.havePiece(pieceIdx)) {
                        val ok = kotlinx.coroutines.runBlocking { repo.waitForPiece(pieceIdx, 30_000) }
                        if (!ok) {
                            EcosystemLogger.e(HaronConstants.TAG, "Torrent HTTP: timeout at piece $pieceIdx, pos=$pos")
                            break
                        }
                    }
                    val toRead = remaining.coerceAtMost(buffer.size.toLong()).toInt()
                    val read = raf.read(buffer, 0, toRead)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    output.flush()
                    remaining -= read
                    pos += read
                }
            }
        } catch (e: Exception) {
            EcosystemLogger.d(HaronConstants.TAG, "Torrent HTTP: stream ended: ${e.message}")
        }
    }

    // --- Public API ---

    fun getServerUrl(): String? {
        val ip = getLocalIpAddress() ?: return null
        return "http://$ip:$actualPort"
    }

    fun getStreamUrl(fileIndex: Int): String? {
        val ip = getLocalIpAddress() ?: return null
        return "http://$ip:$actualPort/stream/$fileIndex"
    }

    fun getLocalIpAddress(): String? {
        try {
            EcosystemLogger.d(HaronConstants.TAG, "getLocalIpAddress: starting IP detection...")

            val ifaceIps = mutableMapOf<String, String>()
            val hotspotCandidates = mutableListOf<Pair<String, String>>()
            val niInterfaces = NetworkInterface.getNetworkInterfaces()
            if (niInterfaces != null) {
                for (intf in niInterfaces) {
                    if (!intf.isUp || intf.isLoopback) continue
                    val name = intf.name.lowercase()
                    for (addr in intf.inetAddresses) {
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            val ip = addr.hostAddress ?: continue
                            if (ip == "0.0.0.0") continue
                            ifaceIps[name] = ip
                            EcosystemLogger.d(HaronConstants.TAG, "getLocalIpAddress: iface=$name, ip=$ip")
                            val isWlanLike = name.startsWith("wlan") || name.startsWith("ap") ||
                                    name.startsWith("swlan") || name.contains("softap")
                            if (isWlanLike && ip.startsWith("192.168.")) {
                                hotspotCandidates.add(name to ip)
                            }
                        }
                    }
                }
            }

            var cmWifiIp: String? = null
            var cmWifiIface: String? = null
            var cmCgnatIp: String? = null
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            if (cm != null) {
                val allNets = cm.allNetworks
                EcosystemLogger.d(HaronConstants.TAG, "getLocalIpAddress: CM found ${allNets.size} networks")
                for (network in allNets) {
                    val caps = cm.getNetworkCapabilities(network)
                    val linkProps = cm.getLinkProperties(network)
                    val hasWifi = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
                    val hasVpn = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
                    val hasCellular = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true
                    val ifaceName = linkProps?.interfaceName
                    val addrs = linkProps?.linkAddresses?.map { it.address?.hostAddress }
                    EcosystemLogger.d(HaronConstants.TAG, "getLocalIpAddress: network=$network, iface=$ifaceName, wifi=$hasWifi, vpn=$hasVpn, cellular=$hasCellular, addrs=$addrs")
                    if (caps == null || !hasWifi || hasVpn) continue
                    if (linkProps == null) continue
                    for (la in linkProps.linkAddresses) {
                        val addr = la.address
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            val ip = addr.hostAddress
                            if (ip != null && ip != "0.0.0.0") {
                                if (isCgnatIp(ip)) {
                                    if (cmCgnatIp == null) cmCgnatIp = ip
                                } else {
                                    cmWifiIp = ip
                                    cmWifiIface = ifaceName
                                }
                            }
                        }
                    }
                }
            }
            EcosystemLogger.d(HaronConstants.TAG, "getLocalIpAddress: cmWifiIp=$cmWifiIp (iface=$cmWifiIface), cmCgnat=$cmCgnatIp, hotspotCandidates=$hotspotCandidates")

            if (hotspotCandidates.isNotEmpty() && cmWifiIp != null) {
                val hotspotOnDifferentIface = hotspotCandidates.firstOrNull { (iface, ip) ->
                    iface != cmWifiIface && ip != cmWifiIp
                }
                if (hotspotOnDifferentIface != null) {
                    EcosystemLogger.d(HaronConstants.TAG, "getLocalIpAddress: hotspot detected on ${hotspotOnDifferentIface.first}=${hotspotOnDifferentIface.second}, CM WiFi on $cmWifiIface=$cmWifiIp -> using hotspot IP")
                    return hotspotOnDifferentIface.second
                }
            }

            if (cmWifiIp == null && hotspotCandidates.isNotEmpty()) {
                val hp = hotspotCandidates.first()
                EcosystemLogger.d(HaronConstants.TAG, "getLocalIpAddress: no CM WiFi, using hotspot IP ${hp.first}=${hp.second}")
                return hp.second
            }

            if (cmWifiIp != null) {
                EcosystemLogger.d(HaronConstants.TAG, "getLocalIpAddress: using CM WiFi IP=$cmWifiIp (iface=$cmWifiIface)")
                return cmWifiIp
            }

            val bestWlanIp = ifaceIps.entries.firstOrNull { (name, ip) ->
                val isWlanLike = name.startsWith("wlan") || name.startsWith("ap") ||
                        name.startsWith("swlan") || name.contains("softap")
                isWlanLike && !isCgnatIp(ip)
            }?.value

            val otherFallback = ifaceIps.entries.firstOrNull { (name, ip) ->
                !isCgnatIp(ip) && !name.startsWith("rmnet") &&
                        !name.startsWith("ccmni") && !name.startsWith("pdp") &&
                        !name.startsWith("wlan") && !name.startsWith("ap") &&
                        !name.startsWith("swlan") && !name.contains("softap")
            }?.value

            val cgnatWlanIp = ifaceIps.entries.firstOrNull { (name, ip) ->
                val isWlanLike = name.startsWith("wlan") || name.startsWith("ap") ||
                        name.startsWith("swlan") || name.contains("softap")
                isWlanLike && isCgnatIp(ip)
            }?.value

            val result = bestWlanIp ?: otherFallback ?: cmCgnatIp ?: cgnatWlanIp
            if (result != null) {
                EcosystemLogger.d(HaronConstants.TAG, "getLocalIpAddress: fallback selected=$result (wlan=$bestWlanIp, other=$otherFallback, cmCgnat=$cmCgnatIp, wlanCgnat=$cgnatWlanIp)")
            } else {
                EcosystemLogger.e(HaronConstants.TAG, "getLocalIpAddress: no suitable IP found")
            }
            return result
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "getLocalIpAddress failed: ${e.message}")
        }
        return null
    }

    /** CGNAT range 100.64.0.0/10 */
    private fun isCgnatIp(ip: String): Boolean {
        val parts = ip.split('.').mapNotNull { it.toIntOrNull() }
        return parts.size == 4 && parts[0] == 100 && parts[1] in 64..127
    }

    private fun logAllNetworkInterfaces() {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return
            val sb = StringBuilder("=== Network Interfaces ===\n")
            for (intf in interfaces) {
                if (!intf.isUp) continue
                val addrs = intf.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .filter { !it.isLoopbackAddress }
                if (addrs.isEmpty()) continue
                sb.append("  ${intf.name} (up=${intf.isUp}, loopback=${intf.isLoopback}): ")
                sb.append(addrs.joinToString(", ") { it.hostAddress ?: "null" })
                sb.append("\n")
            }
            EcosystemLogger.d(HaronConstants.TAG, sb.toString().trimEnd())
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "logAllNetworkInterfaces error: ${e.message}")
        }
    }

    private fun findAvailablePort(): Int {
        for (port in HaronConstants.TRANSFER_PORT_START..HaronConstants.TRANSFER_PORT_END) {
            try { java.net.ServerSocket(port).use { return port } } catch (_: Exception) {}
        }
        java.net.ServerSocket(0).use { return it.localPort }
    }

    // --- Setup methods ---

    fun setupSlideshow(files: List<File>, intervalSec: Int) { slideshowFiles = files; slideshowIntervalSec = intervalSec }
    fun setupPdf(file: File) { pdfFile = file }
    fun setupFileInfo(name: String, path: String, size: String, modified: String, mimeType: String) {
        fileInfoHtml = buildFileInfoHtml(name, path, size, modified, mimeType)
    }
    fun getSlideshowUrl(): String? { val ip = getLocalIpAddress() ?: return null; return "http://$ip:$actualPort/slideshow" }
    fun getPresentationUrl(page: Int): String? { val ip = getLocalIpAddress() ?: return null; return "http://$ip:$actualPort/presentation/$page" }
    fun getFileInfoUrl(): String? { val ip = getLocalIpAddress() ?: return null; return "http://$ip:$actualPort/fileinfo" }
    fun setupCloudStream(streamId: String, config: CloudStreamConfig) { cloudStreams[streamId] = config }
    fun clearCloudStreams() { cloudStreams.clear() }
    fun getCloudStreamUrl(streamId: String): String? = "http://127.0.0.1:$actualPort/cloud/stream/$streamId"
    fun setupHls(dir: File) { hlsDir = dir }
    fun getHlsUrl(): String? { val ip = getLocalIpAddress() ?: return null; return "http://$ip:$actualPort/hls/playlist.m3u8" }

    // --- HTML builders ---

    private fun buildHtmlPage(files: List<File>): String {
        val rows = files.mapIndexed { index, file ->
            val size = formatSize(file.length())
            val icon = if (file.isDirectory) "\uD83D\uDCC1" else "\uD83D\uDCC4"
            val encodedName = URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")
            """<a class="file-card" href="/download/$index" download="$encodedName">
                <span class="file-name">$icon ${escapeHtml(file.name)}</span>
                <span class="file-size">$size</span>
            </a>"""
        }.joinToString("\n")

        return """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Haron File Transfer</title>
<style>
*{box-sizing:border-box}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;max-width:800px;margin:0 auto;padding:16px;background:#121212;color:#e0e0e0}
h1{color:#bb86fc;font-size:22px;margin:0 0 4px 0}
.info{color:#888;font-size:14px;margin:0 0 16px 0}
a.file-card{display:flex;justify-content:space-between;align-items:center;padding:14px 16px;margin-bottom:8px;background:#1e1e1e;border-radius:12px;cursor:pointer;color:#e0e0e0;text-decoration:none;-webkit-user-select:none;user-select:none;-webkit-tap-highlight-color:rgba(187,134,252,0.2)}
a.file-card:active{background:#2a2a2a}
.file-name{font-size:15px;word-break:break-word;margin-right:12px;flex:1}
.file-size{font-size:13px;color:#888;white-space:nowrap}
</style></head><body>
<h1>Haron</h1>
<p class="info">${files.size} file(s)</p>
$rows
</body></html>"""
    }

    private fun buildSlideshowHtml(): String {
        val imageUrls = slideshowFiles.indices.joinToString(",") { "'/slideshow/image/$it'" }
        return """<!DOCTYPE html>
<html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"><title>Haron Slideshow</title>
<style>body{margin:0;background:#000;display:flex;justify-content:center;align-items:center;min-height:100vh;overflow:hidden}img{max-width:100vw;max-height:100vh;object-fit:contain;transition:opacity 0.5s ease}.counter{position:fixed;bottom:16px;right:16px;color:#fff8;font:14px sans-serif}</style>
</head><body><img id="slide"/><div id="counter" class="counter"></div>
<script>var urls=[$imageUrls];var idx=0;var img=document.getElementById('slide');var counter=document.getElementById('counter');function show(){img.src=urls[idx];counter.textContent=(idx+1)+' / '+urls.length;idx=(idx+1)%urls.length;}show();setInterval(show,${slideshowIntervalSec * 1000});</script>
${buildRemoteCursorJs()}
</body></html>"""
    }

    private fun buildFileInfoHtml(name: String, path: String, size: String, modified: String, mimeType: String): String {
        return """<!DOCTYPE html>
<html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"><title>File Info — Haron</title>
<style>body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:#121212;color:#e0e0e0;display:flex;justify-content:center;align-items:center;min-height:100vh;margin:0}.card{background:#1e1e1e;border-radius:16px;padding:32px;min-width:400px;box-shadow:0 4px 24px rgba(0,0,0,0.4)}h1{color:#bb86fc;font-size:24px;margin:0 0 24px 0;word-break:break-all}.row{display:flex;justify-content:space-between;padding:8px 0;border-bottom:1px solid #333}.label{color:#888}.value{color:#e0e0e0;text-align:right;max-width:60%;word-break:break-all}</style>
</head><body><div class="card"><h1>${escapeHtml(name)}</h1>
<div class="row"><span class="label">Path</span><span class="value">${escapeHtml(path)}</span></div>
<div class="row"><span class="label">Size</span><span class="value">${escapeHtml(size)}</span></div>
<div class="row"><span class="label">Modified</span><span class="value">${escapeHtml(modified)}</span></div>
<div class="row"><span class="label">Type</span><span class="value">${escapeHtml(mimeType)}</span></div>
</div>${buildRemoteCursorJs()}</body></html>"""
    }

    private fun buildRemoteCursorJs(): String {
        val wsHost = getLocalIpAddress() ?: "localhost"
        return """<div id="haron-cursor" style="position:fixed;width:20px;height:20px;border-radius:50%;background:rgba(255,255,255,0.85);border:2px solid rgba(187,134,252,0.8);pointer-events:none;z-index:99999;display:none;transform:translate(-50%,-50%);box-shadow:0 0 8px rgba(187,134,252,0.5);transition:box-shadow 0.1s;"></div>
<script>
(function(){var cursor=document.getElementById('haron-cursor');var cx=window.innerWidth/2,cy=window.innerHeight/2;var ws=null;
function connect(){ws=new WebSocket('ws://$wsHost:$actualPort/ws/remote');ws.onopen=function(){cursor.style.display='block';cx=window.innerWidth/2;cy=window.innerHeight/2;cursor.style.left=cx+'px';cursor.style.top=cy+'px';};
ws.onmessage=function(e){try{var d=JSON.parse(e.data);if(d.type==='move'){cx=Math.max(0,Math.min(window.innerWidth,cx+d.dx));cy=Math.max(0,Math.min(window.innerHeight,cy+d.dy));cursor.style.left=cx+'px';cursor.style.top=cy+'px';}else if(d.type==='click'){cursor.style.boxShadow='0 0 16px rgba(187,134,252,1)';setTimeout(function(){cursor.style.boxShadow='0 0 8px rgba(187,134,252,0.5)';},150);var el=document.elementFromPoint(cx,cy);if(el){el.click();}}else if(d.type==='scroll'){window.scrollBy(d.dx,d.dy);}else if(d.type==='key'){document.dispatchEvent(new KeyboardEvent('keydown',{keyCode:d.keyCode,bubbles:true}));}else if(d.type==='text'){var el=document.activeElement;if(el&&(el.tagName==='INPUT'||el.tagName==='TEXTAREA'||el.isContentEditable)){el.value=(el.value||'')+d.text;el.dispatchEvent(new Event('input',{bubbles:true}));}}}catch(ex){}};
ws.onclose=function(){cursor.style.display='none';setTimeout(connect,2000);};ws.onerror=function(){ws.close();};}connect();})();
</script>"""
    }

    private fun renderPdfPage(file: File, page: Int): ByteArray? {
        return try {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            if (page < 0 || page >= renderer.pageCount) { renderer.close(); fd.close(); return null }
            val pdfPage = renderer.openPage(page)
            val scale = 2
            val bmp = Bitmap.createBitmap(pdfPage.width * scale, pdfPage.height * scale, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            pdfPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            pdfPage.close(); renderer.close(); fd.close()
            val stream = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 90, stream)
            bmp.recycle()
            stream.toByteArray()
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "PDF render error page $page: ${e.message}")
            null
        }
    }

    // --- Cloud helpers ---

    private fun getDropboxTemporaryLink(path: String, token: String): String? {
        return try {
            val url = java.net.URL("https://api.dropboxapi.com/2/files/get_temporary_link")
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true; connectTimeout = 15_000; readTimeout = 15_000
            }
            conn.outputStream.use { it.write("""{"path":"$path"}""".toByteArray()) }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                org.json.JSONObject(body).getString("link")
            } else { conn.disconnect(); null }
        } catch (e: Exception) { EcosystemLogger.e(HaronConstants.TAG, "Dropbox temp link error: ${e.message}"); null }
    }

    private fun getYandexDownloadUrl(path: String, token: String): String? {
        return try {
            val diskPath = if (path.startsWith("disk:")) path else "disk:/$path"
            val encodedPath = java.net.URLEncoder.encode(diskPath, "UTF-8")
            val url = java.net.URL("https://cloud-api.yandex.net/v1/disk/resources/download?path=$encodedPath")
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "OAuth $token")
                connectTimeout = 15_000; readTimeout = 15_000
            }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                org.json.JSONObject(body).getString("href")
            } else { conn.disconnect(); null }
        } catch (e: Exception) { EcosystemLogger.e(HaronConstants.TAG, "Yandex URL error: ${e.message}"); null }
    }

    // --- Utilities ---

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
        else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
    }

    private fun escapeHtml(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun escapeJson(text: String) = text.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun guessMimeType(ext: String): String = when (ext.lowercase()) {
        "mp4", "m4v" -> "video/mp4"; "avi" -> "video/x-msvideo"; "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"; "mov" -> "video/quicktime"
        "mp3" -> "audio/mpeg"; "flac" -> "audio/flac"; "ogg" -> "audio/ogg"
        "wav" -> "audio/wav"; "aac", "m4a" -> "audio/mp4"
        "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "gif" -> "image/gif"; "webp" -> "image/webp"
        "pdf" -> "application/pdf"; "txt" -> "text/plain"; "html", "htm" -> "text/html"
        "json" -> "application/json"; "zip" -> "application/zip"
        else -> "application/octet-stream"
    }
}
