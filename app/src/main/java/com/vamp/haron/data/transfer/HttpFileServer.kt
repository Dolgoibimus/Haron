package com.vamp.haron.data.transfer

import android.content.Context
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import com.vamp.haron.data.cast.RemoteInputChannel
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import java.time.Duration
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
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

@Singleton
class HttpFileServer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remoteInputChannel: RemoteInputChannel
) {
    private var engine: ApplicationEngine? = null
    private var sharedFiles: List<File> = emptyList()
    var actualPort: Int = 0
        private set

    private val _downloadEvents = MutableSharedFlow<HttpDownloadEvent>(extraBufferCapacity = 64)
    val downloadEvents: SharedFlow<HttpDownloadEvent> = _downloadEvents.asSharedFlow()

    // Extended cast data
    private var slideshowFiles: List<File> = emptyList()
    private var slideshowIntervalSec: Int = 5
    private var pdfFile: File? = null
    @Suppress("unused") // kept for potential future use
    private var fileInfoHtml: String? = null

    // Cloud streaming proxy
    data class CloudStreamConfig(
        val fileId: String,
        val accountId: String, // "gdrive:alice@gmail.com"
        val fileName: String,
        val fileSize: Long
    ) {
        /** Provider scheme extracted from accountId (e.g. "gdrive") */
        val providerScheme: String get() = accountId.substringBefore(':')
    }
    private val cloudStreams = mutableMapOf<String, CloudStreamConfig>()
    /** Callback to get fresh access token by accountId */
    var cloudTokenProvider: ((String) -> String?)? = null

    // HLS (progressive transcode → Chromecast)
    @Volatile private var hlsDir: File? = null

    suspend fun start(files: List<File>): Int {
        stop()
        sharedFiles = files

        val port = findAvailablePort()
        actualPort = port
        EcosystemLogger.d(HaronConstants.TAG, "HttpFileServer.start: port=$port, files=${files.size}, " +
                "fileNames=${files.take(3).map { it.name }}")

        // Log all network interfaces for diagnostics
        logAllNetworkInterfaces()

        engine = embeddedServer(CIO, host = "0.0.0.0", port = port) {
            install(PartialContent)
            install(AutoHeadResponse)
            install(WebSockets) {
                pingPeriod = Duration.ofSeconds(15)
                timeout = Duration.ofSeconds(30)
                maxFrameSize = Long.MAX_VALUE
            }
            routing {
                // --- WebSocket for TV remote ---
                webSocket("/ws/remote") {
                    EcosystemLogger.d(HaronConstants.TAG, "Remote WebSocket connected")
                    try {
                        remoteInputChannel.events.collect { json ->
                            send(Frame.Text(json))
                        }
                    } catch (e: Exception) {
                        EcosystemLogger.d(HaronConstants.TAG, "Remote WebSocket disconnected: ${e.message}")
                    }
                }
                get("/") {
                    EcosystemLogger.d(HaronConstants.TAG, "HTTP request: GET / from ${call.request.local.remoteAddress}")
                    call.respondText(buildHtmlPage(sharedFiles), ContentType.Text.Html)
                }
                get("/download/{index}") {
                    EcosystemLogger.d(HaronConstants.TAG, "HTTP request: GET /download/${call.parameters["index"]} from ${call.request.local.remoteAddress}")
                    val idx = call.parameters["index"]?.toIntOrNull()
                    if (idx == null || idx !in sharedFiles.indices) {
                        call.respondText("File not found", status = HttpStatusCode.NotFound)
                        return@get
                    }
                    val file = sharedFiles[idx]
                    if (!file.exists()) {
                        call.respondText("File not found", status = HttpStatusCode.NotFound)
                        return@get
                    }
                    val encodedName = URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")
                    call.response.header(
                        HttpHeaders.ContentDisposition,
                        "attachment; filename*=UTF-8''$encodedName"
                    )
                    call.response.header(HttpHeaders.ContentLength, file.length().toString())
                    val fileSize = file.length()
                    call.respondOutputStream(
                        contentType = ContentType.Application.OctetStream,
                        status = HttpStatusCode.OK
                    ) {
                        withContext(Dispatchers.IO) {
                            file.inputStream().use { input ->
                                input.copyTo(this@respondOutputStream, bufferSize = 8192)
                            }
                        }
                    }
                    _downloadEvents.tryEmit(HttpDownloadEvent(idx, file.name, fileSize))
                }
                get("/stream/{index}") {
                    EcosystemLogger.d(HaronConstants.TAG, "HTTP request: GET /stream/${call.parameters["index"]} from ${call.request.local.remoteAddress}")
                    val idx = call.parameters["index"]?.toIntOrNull()
                    if (idx == null || idx !in sharedFiles.indices) {
                        call.respondText("File not found", status = HttpStatusCode.NotFound)
                        return@get
                    }
                    val file = sharedFiles[idx]
                    if (!file.exists()) {
                        call.respondText("File not found", status = HttpStatusCode.NotFound)
                        return@get
                    }
                    call.response.header(HttpHeaders.AcceptRanges, "bytes")
                    val fileSize = file.length()
                    call.respondFile(file)
                    _downloadEvents.tryEmit(HttpDownloadEvent(idx, file.name, fileSize))
                }

                // JSON API for Haron-to-Haron QR download
                get("/api/files") {
                    EcosystemLogger.d(HaronConstants.TAG, "HTTP request: GET /api/files from ${call.request.local.remoteAddress}, files=${sharedFiles.size}")
                    val json = sharedFiles.mapIndexed { index, file ->
                        """{"index":$index,"name":"${escapeJson(file.name)}","size":${file.length()}}"""
                    }.joinToString(",", "[", "]")
                    call.respondText(json, ContentType.Application.Json)
                }

                // --- Cloud streaming proxy ---
                get("/cloud/stream/{streamId}") {
                    val streamId = call.parameters["streamId"]
                    val config = streamId?.let { cloudStreams[it] }
                    if (config == null) {
                        call.respondText("Stream not found", status = HttpStatusCode.NotFound)
                        return@get
                    }

                    try {
                        // Get fresh token on each request (tokens expire after 1 hour)
                        val freshToken = cloudTokenProvider?.invoke(config.accountId)
                        if (freshToken == null) {
                            EcosystemLogger.e(HaronConstants.TAG, "Cloud stream ($streamId): no token for ${config.accountId}")
                            call.respondText("No access token", status = HttpStatusCode.Unauthorized)
                            return@get
                        }
                        EcosystemLogger.d(HaronConstants.TAG, "Cloud stream ($streamId): using token ${freshToken.take(15)}...")

                        // Resolve download URL (provider-specific)
                        val downloadUrl = when (config.providerScheme) {
                            "gdrive" -> "https://www.googleapis.com/drive/v3/files/${config.fileId}?alt=media"
                            "yandex" -> {
                                // Two-step: get temporary download URL, then stream from it
                                val tempUrl = withContext(Dispatchers.IO) {
                                    getYandexDownloadUrl(config.fileId, freshToken)
                                }
                                if (tempUrl == null) {
                                    call.respondText("Failed to get Yandex download URL", status = HttpStatusCode.BadGateway)
                                    return@get
                                }
                                tempUrl
                            }
                            "dropbox" -> {
                                // Get temporary direct link (supports GET + Range)
                                val tempLink = withContext(Dispatchers.IO) {
                                    getDropboxTemporaryLink(config.fileId, freshToken)
                                }
                                if (tempLink == null) {
                                    call.respondText("Failed to get Dropbox temp link", status = HttpStatusCode.BadGateway)
                                    return@get
                                }
                                tempLink
                            }
                            else -> {
                                call.respondText("Unsupported provider", status = HttpStatusCode.BadRequest)
                                return@get
                            }
                        }
                        // Yandex temp URL and Dropbox temp link are self-authenticated
                        val needsAuth = config.providerScheme !in listOf("dropbox", "yandex")

                        val url = java.net.URL(downloadUrl)
                        val conn = withContext(Dispatchers.IO) {
                            (url.openConnection() as java.net.HttpURLConnection).apply {
                                requestMethod = "GET"
                                if (needsAuth) {
                                    val authPrefix = if (config.providerScheme == "yandex") "OAuth" else "Bearer"
                                    setRequestProperty("Authorization", "$authPrefix $freshToken")
                                }
                                instanceFollowRedirects = true
                                connectTimeout = 15_000
                                readTimeout = 60_000
                                // Forward Range header for seeking
                                call.request.headers[HttpHeaders.Range]?.let {
                                    setRequestProperty("Range", it)
                                }
                            }
                        }

                        val responseCode = withContext(Dispatchers.IO) { conn.responseCode }
                        EcosystemLogger.d(HaronConstants.TAG, "Cloud stream ($streamId): HTTP $responseCode from ${config.providerScheme}")

                        if (responseCode !in 200..299 && responseCode != 206) {
                            val errorBody = try { withContext(Dispatchers.IO) { conn.errorStream?.bufferedReader()?.readText() } } catch (_: Exception) { null }
                            EcosystemLogger.e(HaronConstants.TAG, "Cloud stream ($streamId): HTTP $responseCode, error=$errorBody")
                            conn.disconnect()
                            call.respondText("Stream error: HTTP $responseCode", status = HttpStatusCode.fromValue(responseCode))
                            return@get
                        }

                        val contentType = conn.getHeaderField("Content-Type") ?: "application/octet-stream"
                        val contentLength = conn.getHeaderField("Content-Length")
                        val contentRange = conn.getHeaderField("Content-Range")

                        val status = if (responseCode == 206) HttpStatusCode.PartialContent else HttpStatusCode.OK
                        call.response.header(HttpHeaders.AcceptRanges, "bytes")
                        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                        contentLength?.let { call.response.header(HttpHeaders.ContentLength, it) }
                        contentRange?.let { call.response.header(HttpHeaders.ContentRange, it) }

                        call.respondOutputStream(
                            contentType = ContentType.parse(contentType),
                            status = status
                        ) {
                            withContext(Dispatchers.IO) {
                                try {
                                    conn.inputStream.use { input ->
                                        val buffer = ByteArray(65536)
                                        var bytesRead: Int
                                        while (input.read(buffer).also { bytesRead = it } != -1) {
                                            write(buffer, 0, bytesRead)
                                        }
                                    }
                                } finally {
                                    conn.disconnect()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        EcosystemLogger.e(HaronConstants.TAG, "Cloud stream error ($streamId): ${e::class.simpleName}: ${e.message}")
                        call.respondText("Stream error: ${e.message}", status = HttpStatusCode.InternalServerError)
                    }
                }

                // --- HLS (progressive transcode → Chromecast) ---
                get("/hls/{filename}") {
                    val dir = hlsDir
                    val name = call.parameters["filename"]
                    EcosystemLogger.d(HaronConstants.TAG, "HLS request: /$name, hlsDir=${dir?.absolutePath}")
                    if (dir == null || name == null) {
                        EcosystemLogger.e(HaronConstants.TAG, "HLS 404: dir=$dir, name=$name")
                        call.respondText("Not found", status = HttpStatusCode.NotFound)
                        return@get
                    }
                    val file = File(dir, name)
                    if (!file.exists()) {
                        EcosystemLogger.e(HaronConstants.TAG, "HLS 404: file not found: ${file.absolutePath}")
                        call.respondText("Not found", status = HttpStatusCode.NotFound)
                        return@get
                    }
                    val ct = when (file.extension.lowercase()) {
                        "m3u8" -> ContentType.parse("application/vnd.apple.mpegurl")
                        "ts" -> ContentType.parse("video/mp2t")
                        else -> ContentType.Application.OctetStream
                    }
                    // CORS for Chromecast JS-based HLS player
                    call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                    // Disable caching so Chromecast re-fetches playlist for new segments
                    call.response.header(HttpHeaders.CacheControl, "no-cache, no-store")
                    call.response.header(HttpHeaders.ContentLength, file.length().toString())
                    EcosystemLogger.d(HaronConstants.TAG, "HLS serving: $name (${file.length()} bytes, ct=$ct)")
                    call.respondOutputStream(contentType = ct, status = HttpStatusCode.OK) {
                        withContext(Dispatchers.IO) {
                            file.inputStream().use { it.copyTo(this@respondOutputStream, 8192) }
                        }
                    }
                }

                // --- Extended Cast endpoints ---

                get("/slideshow") {
                    if (slideshowFiles.isEmpty()) {
                        call.respondText("No slideshow", status = HttpStatusCode.NotFound)
                        return@get
                    }
                    call.respondText(buildSlideshowHtml(), ContentType.Text.Html)
                }
                get("/slideshow/image/{index}") {
                    val idx = call.parameters["index"]?.toIntOrNull()
                    if (idx == null || idx !in slideshowFiles.indices) {
                        call.respondText("Not found", status = HttpStatusCode.NotFound)
                        return@get
                    }
                    val file = slideshowFiles[idx]
                    if (!file.exists()) {
                        call.respondText("Not found", status = HttpStatusCode.NotFound)
                        return@get
                    }
                    val mime = guessMimeType(file.extension)
                    call.response.header(HttpHeaders.ContentLength, file.length().toString())
                    call.respondOutputStream(
                        contentType = ContentType.parse(mime),
                        status = HttpStatusCode.OK
                    ) {
                        withContext(Dispatchers.IO) {
                            file.inputStream().use { it.copyTo(this@respondOutputStream, 8192) }
                        }
                    }
                }

                get("/presentation/{page}") {
                    val page = call.parameters["page"]?.toIntOrNull()
                    val pdf = pdfFile
                    if (page == null || pdf == null || !pdf.exists()) {
                        call.respondText("Not found", status = HttpStatusCode.NotFound)
                        return@get
                    }
                    val pngBytes = withContext(Dispatchers.IO) { renderPdfPage(pdf, page) }
                    if (pngBytes != null) {
                        call.respondBytes(pngBytes, ContentType.Image.PNG)
                    } else {
                        call.respondText("Render error", status = HttpStatusCode.InternalServerError)
                    }
                }

                // --- OAuth loopback callback (Google Drive) ---
                get("/oauth/callback") {
                    val code = call.request.queryParameters["code"]
                    if (code != null) {
                        com.vamp.haron.data.cloud.CloudOAuthHelper.pendingAuth.value =
                            com.vamp.haron.data.cloud.CloudOAuthHelper.PendingAuth("gdrive", code)
                        call.respondText(
                            "<html><body><h2>Authorization successful</h2><p>You can close this tab and return to Haron.</p></body></html>",
                            ContentType.Text.Html
                        )
                        EcosystemLogger.d(HaronConstants.TAG, "OAuth callback received for gdrive")
                    } else {
                        val error = call.request.queryParameters["error"] ?: "unknown"
                        call.respondText(
                            "<html><body><h2>Authorization failed</h2><p>Error: $error</p></body></html>",
                            ContentType.Text.Html
                        )
                        EcosystemLogger.e(HaronConstants.TAG, "OAuth callback error: $error")
                    }
                }

            }
        }

        try {
            engine?.start(wait = false)
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
        engine?.stop(1000, 2000)
        engine = null
        actualPort = 0
        sharedFiles = emptyList()
        hlsDir = null
        EcosystemLogger.d(HaronConstants.TAG, "HTTP server stopped (was on port $wasPort)")
    }

    fun isRunning(): Boolean = engine != null

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

            // 1) Scan ALL NetworkInterfaces first — build a map of iface→IP
            val ifaceIps = mutableMapOf<String, String>() // iface name → IPv4
            val hotspotCandidates = mutableListOf<Pair<String, String>>() // (iface, ip)
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
                            // Hotspot AP interfaces typically have 192.168.x.x on wlan0/ap/swlan/softap
                            val isWlanLike = name.startsWith("wlan") || name.startsWith("ap") ||
                                    name.startsWith("swlan") || name.contains("softap")
                            if (isWlanLike && ip.startsWith("192.168.")) {
                                hotspotCandidates.add(name to ip)
                            }
                        }
                    }
                }
            }

            // 2) ConnectivityManager — find WiFi client network
            var cmWifiIp: String? = null
            var cmWifiIface: String? = null
            var cmCgnatIp: String? = null
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                    as? android.net.ConnectivityManager
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

            // 3) If hotspot is active on a DIFFERENT interface than CM WiFi → prefer hotspot IP
            //    (concurrent STA+AP: clients connect to hotspot subnet, not to upstream WiFi)
            if (hotspotCandidates.isNotEmpty() && cmWifiIp != null) {
                val hotspotOnDifferentIface = hotspotCandidates.firstOrNull { (iface, ip) ->
                    iface != cmWifiIface && ip != cmWifiIp
                }
                if (hotspotOnDifferentIface != null) {
                    EcosystemLogger.d(HaronConstants.TAG, "getLocalIpAddress: hotspot detected on ${hotspotOnDifferentIface.first}=${hotspotOnDifferentIface.second}, CM WiFi on $cmWifiIface=$cmWifiIp → using hotspot IP")
                    return hotspotOnDifferentIface.second
                }
            }

            // 4) Also check: if NO CM WiFi but hotspot exists → use hotspot IP
            if (cmWifiIp == null && hotspotCandidates.isNotEmpty()) {
                val hp = hotspotCandidates.first()
                EcosystemLogger.d(HaronConstants.TAG, "getLocalIpAddress: no CM WiFi, using hotspot IP ${hp.first}=${hp.second}")
                return hp.second
            }

            // 5) Regular case: use CM WiFi IP
            if (cmWifiIp != null) {
                EcosystemLogger.d(HaronConstants.TAG, "getLocalIpAddress: using CM WiFi IP=$cmWifiIp (iface=$cmWifiIface)")
                return cmWifiIp
            }

            // 6) Fallback: best wlan IP from NetworkInterface scan
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
            try {
                java.net.ServerSocket(port).use { return port }
            } catch (_: Exception) { }
        }
        java.net.ServerSocket(0).use { return it.localPort }
    }

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
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Haron File Transfer</title>
<style>
  * { box-sizing: border-box; }
  body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
         max-width: 800px; margin: 0 auto; padding: 16px; background: #121212; color: #e0e0e0; }
  h1 { color: #bb86fc; font-size: 22px; margin: 0 0 4px 0; }
  .info { color: #888; font-size: 14px; margin: 0 0 16px 0; }
  a.file-card { display: flex; justify-content: space-between; align-items: center;
               padding: 14px 16px; margin-bottom: 8px; background: #1e1e1e;
               border-radius: 12px; cursor: pointer; color: #e0e0e0;
               text-decoration: none;
               -webkit-user-select: none; user-select: none;
               -webkit-tap-highlight-color: rgba(187,134,252,0.2); }
  a.file-card:active { background: #2a2a2a; }
  .file-name { font-size: 15px; word-break: break-word; margin-right: 12px; flex: 1; }
  .file-size { font-size: 13px; color: #888; white-space: nowrap; }
</style>
</head>
<body>
<h1>Haron</h1>
<p class="info">${'$'}{files.size} file(s)</p>
$rows
</body>
</html>"""
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
            else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
        }
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private fun escapeJson(text: String): String {
        return text.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    // --- Extended Cast setup ---

    fun setupSlideshow(files: List<File>, intervalSec: Int) {
        slideshowFiles = files
        slideshowIntervalSec = intervalSec
    }

    fun setupPdf(file: File) {
        pdfFile = file
    }

    fun setupFileInfo(name: String, path: String, size: String, modified: String, mimeType: String) {
        fileInfoHtml = buildFileInfoHtml(name, path, size, modified, mimeType)
    }

    fun getSlideshowUrl(): String? {
        val ip = getLocalIpAddress() ?: return null
        return "http://$ip:$actualPort/slideshow"
    }

    fun getPresentationUrl(page: Int): String? {
        val ip = getLocalIpAddress() ?: return null
        return "http://$ip:$actualPort/presentation/$page"
    }

    fun getFileInfoUrl(): String? {
        val ip = getLocalIpAddress() ?: return null
        return "http://$ip:$actualPort/fileinfo"
    }

    fun setupCloudStream(streamId: String, config: CloudStreamConfig) {
        cloudStreams[streamId] = config
    }

    fun clearCloudStreams() {
        cloudStreams.clear()
    }

    fun getCloudStreamUrl(streamId: String): String? {
        return "http://127.0.0.1:$actualPort/cloud/stream/$streamId"
    }

    /** Get Dropbox temporary direct link (4 hours TTL, supports GET + Range) */
    private fun getDropboxTemporaryLink(path: String, token: String): String? {
        return try {
            val url = java.net.URL("https://api.dropboxapi.com/2/files/get_temporary_link")
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 15_000
            }
            conn.outputStream.use { it.write("""{"path":"$path"}""".toByteArray()) }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val json = org.json.JSONObject(body)
                json.getString("link")
            } else {
                val error = conn.errorStream?.bufferedReader()?.readText()
                EcosystemLogger.e(HaronConstants.TAG, "Dropbox temp link failed: ${conn.responseCode}, $error")
                conn.disconnect()
                null
            }
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "Dropbox temp link error: ${e.message}")
            null
        }
    }

    /** Yandex Disk two-step download: GET /resources/download?path=... → href (temp URL) */
    private fun getYandexDownloadUrl(path: String, token: String): String? {
        return try {
            val diskPath = if (path.startsWith("disk:")) path else "disk:/$path"
            val encodedPath = java.net.URLEncoder.encode(diskPath, "UTF-8")
            val url = java.net.URL("https://cloud-api.yandex.net/v1/disk/resources/download?path=$encodedPath")
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "OAuth $token")
                connectTimeout = 15_000
                readTimeout = 15_000
            }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val json = org.json.JSONObject(body)
                json.getString("href")
            } else {
                val error = conn.errorStream?.bufferedReader()?.readText()
                EcosystemLogger.e(HaronConstants.TAG, "Yandex download URL failed: ${conn.responseCode}, $error")
                conn.disconnect()
                null
            }
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "Yandex download URL error: ${e.message}")
            null
        }
    }

    fun setupHls(dir: File) {
        hlsDir = dir
    }

    fun getHlsUrl(): String? {
        val ip = getLocalIpAddress() ?: return null
        return "http://$ip:$actualPort/hls/playlist.m3u8"
    }

    private fun buildSlideshowHtml(): String {
        val imageUrls = slideshowFiles.indices.joinToString(",") { "'/slideshow/image/$it'" }
        return """<!DOCTYPE html>
<html><head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Haron Slideshow</title>
<style>
body { margin: 0; background: #000; display: flex; justify-content: center; align-items: center; min-height: 100vh; overflow: hidden; }
img { max-width: 100vw; max-height: 100vh; object-fit: contain; transition: opacity 0.5s ease; }
.counter { position: fixed; bottom: 16px; right: 16px; color: #fff8; font: 14px sans-serif; }
</style>
</head><body>
<img id="slide" />
<div id="counter" class="counter"></div>
<script>
var urls = [$imageUrls];
var idx = 0;
var img = document.getElementById('slide');
var counter = document.getElementById('counter');
function show() {
  img.src = urls[idx];
  counter.textContent = (idx+1) + ' / ' + urls.length;
  idx = (idx + 1) % urls.length;
}
show();
setInterval(show, ${slideshowIntervalSec * 1000});
</script>
${buildRemoteCursorJs()}
</body></html>"""
    }

    private fun renderPdfPage(file: File, page: Int): ByteArray? {
        return try {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            if (page < 0 || page >= renderer.pageCount) {
                renderer.close()
                fd.close()
                return null
            }
            val pdfPage = renderer.openPage(page)
            val scale = 2 // 2x for better quality on TV
            val bmp = Bitmap.createBitmap(
                pdfPage.width * scale, pdfPage.height * scale, Bitmap.Config.ARGB_8888
            )
            bmp.eraseColor(android.graphics.Color.WHITE)
            pdfPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            pdfPage.close()
            renderer.close()
            fd.close()

            val stream = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 90, stream)
            bmp.recycle()
            stream.toByteArray()
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "PDF render error page $page: ${e.message}")
            null
        }
    }

    private fun buildFileInfoHtml(name: String, path: String, size: String, modified: String, mimeType: String): String {
        return """<!DOCTYPE html>
<html><head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>File Info — Haron</title>
<style>
body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
       background: #121212; color: #e0e0e0; display: flex; justify-content: center;
       align-items: center; min-height: 100vh; margin: 0; }
.card { background: #1e1e1e; border-radius: 16px; padding: 32px; min-width: 400px;
        box-shadow: 0 4px 24px rgba(0,0,0,0.4); }
h1 { color: #bb86fc; font-size: 24px; margin: 0 0 24px 0; word-break: break-all; }
.row { display: flex; justify-content: space-between; padding: 8px 0;
       border-bottom: 1px solid #333; }
.label { color: #888; }
.value { color: #e0e0e0; text-align: right; max-width: 60%; word-break: break-all; }
</style>
</head><body>
<div class="card">
  <h1>${escapeHtml(name)}</h1>
  <div class="row"><span class="label">Path</span><span class="value">${escapeHtml(path)}</span></div>
  <div class="row"><span class="label">Size</span><span class="value">${escapeHtml(size)}</span></div>
  <div class="row"><span class="label">Modified</span><span class="value">${escapeHtml(modified)}</span></div>
  <div class="row"><span class="label">Type</span><span class="value">${escapeHtml(mimeType)}</span></div>
</div>
${buildRemoteCursorJs()}
</body></html>"""
    }

    private fun buildRemoteCursorJs(): String {
        val wsHost = getLocalIpAddress() ?: "localhost"
        return """<div id="haron-cursor" style="position:fixed;width:20px;height:20px;border-radius:50%;background:rgba(255,255,255,0.85);border:2px solid rgba(187,134,252,0.8);pointer-events:none;z-index:99999;display:none;transform:translate(-50%,-50%);box-shadow:0 0 8px rgba(187,134,252,0.5);transition:box-shadow 0.1s;"></div>
<script>
(function(){
  var cursor = document.getElementById('haron-cursor');
  var cx = window.innerWidth/2, cy = window.innerHeight/2;
  var ws = null;
  function connect() {
    ws = new WebSocket('ws://$wsHost:$actualPort/ws/remote');
    ws.onopen = function(){ cursor.style.display='block'; cx=window.innerWidth/2; cy=window.innerHeight/2; cursor.style.left=cx+'px'; cursor.style.top=cy+'px'; };
    ws.onmessage = function(e){
      try {
        var d = JSON.parse(e.data);
        if(d.type==='move'){
          cx=Math.max(0,Math.min(window.innerWidth,cx+d.dx));
          cy=Math.max(0,Math.min(window.innerHeight,cy+d.dy));
          cursor.style.left=cx+'px'; cursor.style.top=cy+'px';
        } else if(d.type==='click'){
          cursor.style.boxShadow='0 0 16px rgba(187,134,252,1)';
          setTimeout(function(){cursor.style.boxShadow='0 0 8px rgba(187,134,252,0.5)';},150);
          var el=document.elementFromPoint(cx,cy);
          if(el){el.click();}
        } else if(d.type==='scroll'){
          window.scrollBy(d.dx,d.dy);
        } else if(d.type==='key'){
          var evt=new KeyboardEvent('keydown',{keyCode:d.keyCode,bubbles:true});
          document.dispatchEvent(evt);
        } else if(d.type==='text'){
          var el=document.activeElement;
          if(el&&(el.tagName==='INPUT'||el.tagName==='TEXTAREA'||el.isContentEditable)){
            el.value=(el.value||'')+d.text;
            el.dispatchEvent(new Event('input',{bubbles:true}));
          }
        }
      }catch(ex){}
    };
    ws.onclose = function(){ cursor.style.display='none'; setTimeout(connect,2000); };
    ws.onerror = function(){ ws.close(); };
  }
  connect();
})();
</script>"""
    }

    private fun guessMimeType(ext: String): String {
        return when (ext.lowercase()) {
            "mp4", "m4v" -> "video/mp4"
            "avi" -> "video/x-msvideo"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "html", "htm" -> "text/html"
            "json" -> "application/json"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }
}
