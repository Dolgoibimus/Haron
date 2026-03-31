package com.vamp.haron.data.transfer

import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight HTTP server on raw sockets.
 * Replaces Ktor CIO (~3 МБ). Supports:
 * - GET / HEAD routing with path parameters and query strings
 * - Range requests (RFC 7233) for video/audio seeking
 * - WebSocket (RFC 6455) for TV remote
 * - CORS headers
 * - Concurrent connections via thread pool
 */
class SimpleHttpServer(private val port: Int) {
    private val TAG = "Haron/HttpSrv"

    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private val executor = Executors.newCachedThreadPool()

    // Routes
    private val getRoutes = mutableListOf<Route>()
    private val wsRoutes = mutableMapOf<String, WsHandler>()

    val isRunning: Boolean get() = running.get()

    fun addRoute(method: String, pathPattern: String, handler: suspend (HttpRequest, HttpResponse) -> Unit) {
        getRoutes.add(Route(method.uppercase(), pathPattern, handler))
    }

    fun addWebSocket(path: String, handler: WsHandler) {
        wsRoutes[path] = handler
    }

    fun start() {
        stop()
        val ss = ServerSocket(port, 50)
        serverSocket = ss
        running.set(true)
        EcosystemLogger.d(TAG, "Listening on port $port")
        executor.submit { acceptLoop(ss) }
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }

    private fun acceptLoop(ss: ServerSocket) {
        while (running.get()) {
            try {
                val client = ss.accept()
                executor.submit { handleClient(client) }
            } catch (_: Exception) {
                if (!running.get()) break
            }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 30_000
            val input = BufferedInputStream(socket.getInputStream())
            val output = socket.getOutputStream()

            val request = parseRequest(input) ?: return
            EcosystemLogger.d(TAG, "${request.method} ${request.path} from ${socket.remoteSocketAddress}")

            // WebSocket upgrade?
            val wsKey = request.headers["sec-websocket-key"]
            if (wsKey != null && request.headers["upgrade"]?.lowercase() == "websocket") {
                val handler = wsRoutes[request.path]
                if (handler != null) {
                    handleWebSocketUpgrade(socket, input, output, wsKey, handler)
                    return // WebSocket takes over the connection
                }
            }

            // HEAD → find GET route
            val method = if (request.method == "HEAD") "GET" else request.method
            val matchedRoute = findRoute(method, request.path)

            if (matchedRoute == null) {
                sendError(output, 404, "Not Found")
                return
            }

            val (route, params) = matchedRoute
            request.pathParams = params

            val response = HttpResponse(output, request.method == "HEAD")

            // Run handler in a coroutine-like fashion (blocking, since each client is on its own thread)
            kotlinx.coroutines.runBlocking {
                route.handler(request, response)
            }

            if (!response.sent) {
                sendError(output, 500, "No response sent")
            }

        } catch (_: Exception) {
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun parseRequest(input: BufferedInputStream): HttpRequest? {
        val requestLine = readLine(input) ?: return null
        val parts = requestLine.split(" ", limit = 3)
        if (parts.size < 2) return null

        val method = parts[0].uppercase()
        val fullPath = parts[1]
        val queryIdx = fullPath.indexOf('?')
        val path = if (queryIdx >= 0) fullPath.substring(0, queryIdx) else fullPath
        val queryString = if (queryIdx >= 0) fullPath.substring(queryIdx + 1) else ""

        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val colonIdx = line.indexOf(':')
            if (colonIdx > 0) {
                val key = line.substring(0, colonIdx).trim().lowercase()
                val value = line.substring(colonIdx + 1).trim()
                headers[key] = value
            }
        }

        val queryParams = parseQueryParams(queryString)

        return HttpRequest(method, path, headers, queryParams)
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isEmpty()) return emptyMap()
        return query.split("&").mapNotNull {
            val eq = it.indexOf('=')
            if (eq > 0) {
                val key = java.net.URLDecoder.decode(it.substring(0, eq), "UTF-8")
                val value = java.net.URLDecoder.decode(it.substring(eq + 1), "UTF-8")
                key to value
            } else null
        }.toMap()
    }

    private fun findRoute(method: String, path: String): Pair<Route, Map<String, String>>? {
        for (route in getRoutes) {
            if (route.method != method) continue
            val params = matchPath(route.pathPattern, path)
            if (params != null) return route to params
        }
        return null
    }

    /**
     * Match path against pattern like "/download/{index}" or "/cloud/stream/{streamId}".
     * Returns path parameters map or null if no match.
     */
    private fun matchPath(pattern: String, path: String): Map<String, String>? {
        val patternParts = pattern.split("/")
        val pathParts = path.split("/")
        if (patternParts.size != pathParts.size) return null

        val params = mutableMapOf<String, String>()
        for (i in patternParts.indices) {
            val pp = patternParts[i]
            val tp = pathParts[i]
            if (pp.startsWith("{") && pp.endsWith("}")) {
                params[pp.substring(1, pp.length - 1)] = java.net.URLDecoder.decode(tp, "UTF-8")
            } else if (pp != tp) {
                return null
            }
        }
        return params
    }

    private fun sendError(output: OutputStream, code: Int, message: String) {
        val body = message.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $code $message\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(body)
        output.flush()
    }

    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return if (buf.size() > 0) buf.toString(Charsets.UTF_8.name()) else null
            if (b == '\n'.code) {
                val s = buf.toString(Charsets.UTF_8.name())
                return if (s.endsWith('\r')) s.dropLast(1) else s
            }
            buf.write(b)
        }
    }

    // ─── WebSocket (RFC 6455) ───

    private fun handleWebSocketUpgrade(
        socket: Socket,
        input: BufferedInputStream,
        output: OutputStream,
        key: String,
        handler: WsHandler
    ) {
        // Handshake
        val accept = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-1").digest(
                (key + "258EAFA5-E914-47DA-95CA-5AB5CF11CE85").toByteArray()
            )
        )
        val response = "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: $accept\r\n\r\n"
        output.write(response.toByteArray())
        output.flush()

        val wsConn = WebSocketConnection(socket, input, output)
        try {
            handler.onConnect(wsConn)

            // Read frames loop
            while (running.get() && !socket.isClosed) {
                val frame = wsConn.readFrame() ?: break
                when (frame.opcode) {
                    0x01 -> handler.onText(wsConn, String(frame.payload, Charsets.UTF_8)) // text
                    0x02 -> handler.onBinary(wsConn, frame.payload) // binary
                    0x08 -> { wsConn.close(); break } // close
                    0x09 -> wsConn.sendFrame(0x0A, frame.payload) // ping → pong
                    0x0A -> {} // pong — ignore
                }
            }
        } catch (_: Exception) {
        } finally {
            handler.onDisconnect(wsConn)
            try { socket.close() } catch (_: Exception) {}
        }
    }

    // ─── Data classes ───

    data class Route(
        val method: String,
        val pathPattern: String,
        val handler: suspend (HttpRequest, HttpResponse) -> Unit
    )

    data class HttpRequest(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val queryParams: Map<String, String>,
        var pathParams: Map<String, String> = emptyMap()
    ) {
        fun param(name: String): String? = pathParams[name]
        fun query(name: String): String? = queryParams[name]
        fun header(name: String): String? = headers[name.lowercase()]
    }

    class HttpResponse(private val output: OutputStream, private val headOnly: Boolean) {
        var sent = false
            private set

        fun respond(statusCode: Int, statusText: String, headers: Map<String, String>, body: ByteArray?) {
            if (sent) return
            sent = true
            val sb = StringBuilder("HTTP/1.1 $statusCode $statusText\r\n")
            sb.append("Connection: close\r\n")
            for ((k, v) in headers) sb.append("$k: $v\r\n")
            if (body != null && !headers.containsKey("Content-Length")) {
                sb.append("Content-Length: ${body.size}\r\n")
            }
            sb.append("\r\n")
            output.write(sb.toString().toByteArray(Charsets.UTF_8))
            if (!headOnly && body != null) output.write(body)
            output.flush()
        }

        fun respondText(text: String, contentType: String = "text/plain", statusCode: Int = 200) {
            val body = text.toByteArray(Charsets.UTF_8)
            respond(statusCode, statusText(statusCode), mapOf(
                "Content-Type" to "$contentType; charset=utf-8",
                "Content-Length" to body.size.toString()
            ), body)
        }

        fun respondHtml(html: String, statusCode: Int = 200) = respondText(html, "text/html", statusCode)
        fun respondJson(json: String, statusCode: Int = 200) = respondText(json, "application/json", statusCode)

        fun respondBytes(bytes: ByteArray, contentType: String, statusCode: Int = 200, extraHeaders: Map<String, String> = emptyMap()) {
            val headers = mutableMapOf(
                "Content-Type" to contentType,
                "Content-Length" to bytes.size.toString()
            )
            headers.putAll(extraHeaders)
            respond(statusCode, statusText(statusCode), headers, bytes)
        }

        /**
         * Stream response — sends headers first, then writes body via callback.
         * Supports Range requests when rangeStart/rangeEnd/totalSize are provided.
         */
        fun respondStream(
            contentType: String,
            totalSize: Long = -1,
            rangeStart: Long = -1,
            rangeEnd: Long = -1,
            extraHeaders: Map<String, String> = emptyMap(),
            writer: (OutputStream) -> Unit
        ) {
            if (sent) return
            sent = true

            val isPartial = rangeStart >= 0 && totalSize > 0
            val statusCode = if (isPartial) 206 else 200
            val contentLength = if (isPartial) (rangeEnd - rangeStart + 1) else totalSize

            val sb = StringBuilder("HTTP/1.1 $statusCode ${statusText(statusCode)}\r\n")
            sb.append("Connection: close\r\n")
            sb.append("Content-Type: $contentType\r\n")
            if (contentLength > 0) sb.append("Content-Length: $contentLength\r\n")
            if (totalSize > 0) sb.append("Accept-Ranges: bytes\r\n")
            if (isPartial) sb.append("Content-Range: bytes $rangeStart-$rangeEnd/$totalSize\r\n")
            for ((k, v) in extraHeaders) sb.append("$k: $v\r\n")
            sb.append("\r\n")
            output.write(sb.toString().toByteArray(Charsets.UTF_8))
            output.flush()

            if (!headOnly) {
                writer(output)
                output.flush()
            }
        }

        private fun statusText(code: Int): String = when (code) {
            200 -> "OK"; 206 -> "Partial Content"
            301 -> "Moved Permanently"; 302 -> "Found"
            400 -> "Bad Request"; 401 -> "Unauthorized"
            404 -> "Not Found"; 500 -> "Internal Server Error"
            502 -> "Bad Gateway"
            else -> "Unknown"
        }
    }

    // ─── WebSocket frame ───

    data class WsFrame(val opcode: Int, val payload: ByteArray)

    class WebSocketConnection(
        private val socket: Socket,
        private val input: BufferedInputStream,
        private val output: OutputStream
    ) : Closeable {

        fun readFrame(): WsFrame? {
            val b0 = input.read()
            if (b0 == -1) return null
            val b1 = input.read()
            if (b1 == -1) return null

            val opcode = b0 and 0x0F
            val masked = (b1 and 0x80) != 0
            var payloadLen = (b1 and 0x7F).toLong()

            if (payloadLen == 126L) {
                val h = input.read()
                val l = input.read()
                payloadLen = ((h shl 8) or l).toLong()
            } else if (payloadLen == 127L) {
                var len = 0L
                for (i in 0 until 8) len = (len shl 8) or (input.read().toLong() and 0xFF)
                payloadLen = len
            }

            val mask = if (masked) {
                val m = ByteArray(4)
                input.read(m)
                m
            } else null

            val payload = ByteArray(payloadLen.toInt())
            var read = 0
            while (read < payload.size) {
                val n = input.read(payload, read, payload.size - read)
                if (n == -1) return null
                read += n
            }

            if (mask != null) {
                for (i in payload.indices) payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
            }

            return WsFrame(opcode, payload)
        }

        @Synchronized
        fun sendFrame(opcode: Int, payload: ByteArray) {
            val header = ByteArrayOutputStream()
            header.write(0x80 or opcode) // FIN + opcode
            if (payload.size < 126) {
                header.write(payload.size) // no mask for server→client
            } else if (payload.size < 65536) {
                header.write(126)
                header.write((payload.size shr 8) and 0xFF)
                header.write(payload.size and 0xFF)
            } else {
                header.write(127)
                for (i in 7 downTo 0) header.write((payload.size.toLong() shr (i * 8)).toInt() and 0xFF)
            }
            output.write(header.toByteArray())
            output.write(payload)
            output.flush()
        }

        fun sendText(text: String) = sendFrame(0x01, text.toByteArray(Charsets.UTF_8))

        override fun close() {
            try { sendFrame(0x08, ByteArray(0)) } catch (_: Exception) {}
            try { socket.close() } catch (_: Exception) {}
        }
    }

    interface WsHandler {
        fun onConnect(conn: WebSocketConnection)
        fun onText(conn: WebSocketConnection, text: String)
        fun onBinary(conn: WebSocketConnection, data: ByteArray) {}
        fun onDisconnect(conn: WebSocketConnection)
    }

    companion object {
        /**
         * Parse Range header "bytes=START-" or "bytes=START-END".
         * Returns (start, end) or null.
         */
        fun parseRange(rangeHeader: String?, fileSize: Long): Pair<Long, Long>? {
            if (rangeHeader == null || !rangeHeader.startsWith("bytes=")) return null
            val spec = rangeHeader.removePrefix("bytes=")
            val dashIdx = spec.indexOf('-')
            if (dashIdx < 0) return null
            val startStr = spec.substring(0, dashIdx).trim()
            val endStr = spec.substring(dashIdx + 1).trim()
            val start = startStr.toLongOrNull() ?: return null
            val end = if (endStr.isNotEmpty()) endStr.toLongOrNull() ?: (fileSize - 1) else (fileSize - 1)
            if (start < 0 || start >= fileSize) return null
            return start to end.coerceAtMost(fileSize - 1)
        }
    }
}
