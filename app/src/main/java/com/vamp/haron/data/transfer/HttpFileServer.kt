package com.vamp.haron.data.transfer

import android.content.Context
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HttpFileServer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var engine: ApplicationEngine? = null
    private var sharedFiles: List<File> = emptyList()
    var actualPort: Int = 0
        private set

    // Extended cast data
    private var slideshowFiles: List<File> = emptyList()
    private var slideshowIntervalSec: Int = 5
    private var pdfFile: File? = null
    private var fileInfoHtml: String? = null

    suspend fun start(files: List<File>): Int {
        stop()
        sharedFiles = files

        val port = findAvailablePort()
        actualPort = port

        engine = embeddedServer(CIO, host = "0.0.0.0", port = port) {
            install(PartialContent)
            install(AutoHeadResponse)
            routing {
                get("/") {
                    call.respondText(buildHtmlPage(sharedFiles), ContentType.Text.Html)
                }
                get("/download/{index}") {
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
                }
                get("/stream/{index}") {
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
                    call.respondFile(file)
                }

                // JSON API for Haron-to-Haron QR download
                get("/api/files") {
                    val json = sharedFiles.mapIndexed { index, file ->
                        """{"index":$index,"name":"${escapeJson(file.name)}","size":${file.length()}}"""
                    }.joinToString(",", "[", "]")
                    call.respondText(json, ContentType.Application.Json)
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

                get("/fileinfo") {
                    val html = fileInfoHtml
                    if (html != null) {
                        call.respondText(html, ContentType.Text.Html)
                    } else {
                        call.respondText("No info", status = HttpStatusCode.NotFound)
                    }
                }
            }
        }

        engine?.start(wait = false)
        EcosystemLogger.d(HaronConstants.TAG, "HTTP server started on port $port")
        return port
    }

    fun stop() {
        engine?.stop(1000, 2000)
        engine = null
        sharedFiles = emptyList()
        EcosystemLogger.d(HaronConstants.TAG, "HTTP server stopped")
    }

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
            // 1) ConnectivityManager — find specifically WiFi network (not mobile data!)
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                    as? android.net.ConnectivityManager
            if (cm != null) {
                for (network in cm.allNetworks) {
                    val caps = cm.getNetworkCapabilities(network) ?: continue
                    if (!caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) continue
                    val linkProps = cm.getLinkProperties(network) ?: continue
                    for (la in linkProps.linkAddresses) {
                        val addr = la.address
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            val ip = addr.hostAddress
                            if (ip != null && ip != "0.0.0.0") {
                                EcosystemLogger.d(HaronConstants.TAG, "IP from WiFi network: $ip")
                                return ip
                            }
                        }
                    }
                }
                EcosystemLogger.w(HaronConstants.TAG, "No WiFi network found via ConnectivityManager")
            }

            // 2) Fallback: NetworkInterface, prefer wlan*
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            var fallback: String? = null
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val name = intf.name.lowercase()
                for (addr in intf.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        if (name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("swlan")) {
                            EcosystemLogger.d(HaronConstants.TAG, "IP from interface $name: $ip")
                            return ip
                        }
                        // Skip mobile data interfaces
                        if (fallback == null && !name.startsWith("rmnet") && !name.startsWith("ccmni") && !name.startsWith("pdp")) {
                            fallback = ip
                        }
                    }
                }
            }
            if (fallback != null) {
                EcosystemLogger.w(HaronConstants.TAG, "IP fallback (non-WiFi): $fallback")
            } else {
                EcosystemLogger.e(HaronConstants.TAG, "No suitable IP found. Connect to WiFi.")
            }
            return fallback
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "Failed to get IP: ${e.message}")
        }
        return null
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
            val jsName = escapeHtml(file.name).replace("'", "\\'")
            """<div class="file-card" onclick="dl('/stream/$index','$jsName')">
                <span class="file-name">$icon ${escapeHtml(file.name)}</span>
                <span class="file-size">$size</span>
            </div>"""
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
  .file-card { display: flex; justify-content: space-between; align-items: center;
               padding: 14px 16px; margin-bottom: 8px; background: #1e1e1e;
               border-radius: 12px; cursor: pointer; color: #e0e0e0;
               -webkit-user-select: none; user-select: none;
               -webkit-tap-highlight-color: rgba(187,134,252,0.2); }
  .file-card:active { background: #2a2a2a; }
  .file-card.loading { opacity: 0.5; pointer-events: none; }
  .file-name { font-size: 15px; word-break: break-word; margin-right: 12px; flex: 1; }
  .file-size { font-size: 13px; color: #888; white-space: nowrap; }
</style>
</head>
<body>
<h1>Haron</h1>
<p class="info">${'$'}{files.size} file(s)</p>
$rows
<script>
function dl(url, name) {
  event.currentTarget.classList.add('loading');
  fetch(url).then(function(r){return r.blob()}).then(function(b){
    var a = document.createElement('a');
    a.href = URL.createObjectURL(b);
    a.download = name;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(a.href);
    document.querySelector('.loading')&&document.querySelector('.loading').classList.remove('loading');
  }).catch(function(){
    document.querySelector('.loading')&&document.querySelector('.loading').classList.remove('loading');
    alert('Download failed');
  });
}
</script>
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
</body></html>"""
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
