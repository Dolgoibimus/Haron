package com.vamp.haron.data.cast

import android.content.Context
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.domain.model.CastDevice
import com.vamp.haron.domain.model.CastType
import com.vamp.haron.domain.model.RemoteInputEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.DatagramPacket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discovers and controls DLNA/UPnP media renderers on the local network.
 * Uses SSDP multicast for device discovery and SOAP/XML for AVTransport control
 * (SetAVTransportURI, Play, Pause, Stop, Seek, GetPositionInfo).
 * Maintains a list of discovered renderers and tracks current playback state.
 */
@Singleton
class DlnaManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private data class DlnaRenderer(
        val udn: String,
        val friendlyName: String,
        val controlUrl: String,
        val renderingControlUrl: String?
    )

    private val renderers = mutableMapOf<String, DlnaRenderer>()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private val _mediaIsPlaying = MutableStateFlow(false)
    val mediaIsPlaying: StateFlow<Boolean> = _mediaIsPlaying.asStateFlow()

    private val _mediaPositionMs = MutableStateFlow(0L)
    val mediaPositionMs: StateFlow<Long> = _mediaPositionMs.asStateFlow()

    private val _mediaDurationMs = MutableStateFlow(0L)
    val mediaDurationMs: StateFlow<Long> = _mediaDurationMs.asStateFlow()

    private val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var connectedDeviceId: String? = null
        private set
    private var pollingJob: Job? = null
    private var userStopped = false

    // --- Discovery ---

    fun discoverDevices(): Flow<List<CastDevice>> = flow {
        renderers.clear()
        val discovered = mutableMapOf<String, CastDevice>()

        EcosystemLogger.d(HaronConstants.TAG, "DLNA: starting SSDP discovery (M-SEARCH MediaRenderer:1)")

        try {
            val ssdpAddress = InetAddress.getByName(SSDP_ADDRESS)
            val socket = MulticastSocket(0)
            socket.soTimeout = SSDP_TIMEOUT_MS

            // Also send to ssdp:all for broader discovery
            val targets = listOf(MEDIA_RENDERER_ST, "ssdp:all")
            for (st in targets) {
                val searchMessage = buildString {
                    append("M-SEARCH * HTTP/1.1\r\n")
                    append("HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n")
                    append("MAN: \"ssdp:discover\"\r\n")
                    append("MX: $SSDP_MX\r\n")
                    append("ST: $st\r\n")
                    append("\r\n")
                }
                val data = searchMessage.toByteArray()
                val packet = DatagramPacket(data, data.size, ssdpAddress, SSDP_PORT)
                socket.send(packet)
                EcosystemLogger.d(HaronConstants.TAG, "DLNA: sent M-SEARCH ST=$st")
            }

            val buf = ByteArray(4096)
            val deadline = System.currentTimeMillis() + SSDP_TIMEOUT_MS
            var responseCount = 0

            while (System.currentTimeMillis() < deadline) {
                try {
                    val recv = DatagramPacket(buf, buf.size)
                    socket.receive(recv)
                    val response = String(recv.data, 0, recv.length)
                    responseCount++
                    val from = recv.address?.hostAddress ?: "?"
                    EcosystemLogger.d(HaronConstants.TAG, "DLNA: SSDP response #$responseCount from $from")

                    val location = parseLocationHeader(response)
                    if (location == null) {
                        // Log the ST header to see what device type responded
                        val stLine = response.lines().find { it.startsWith("ST:", ignoreCase = true) }
                        EcosystemLogger.d(HaronConstants.TAG, "DLNA: no LOCATION in response, ${stLine ?: "no ST"}")
                        continue
                    }

                    EcosystemLogger.d(HaronConstants.TAG, "DLNA: LOCATION=$location")

                    val renderer = fetchDeviceDescription(location)
                    if (renderer == null) {
                        EcosystemLogger.d(HaronConstants.TAG, "DLNA: not a MediaRenderer or parse failed: $location")
                        continue
                    }

                    if (renderer.udn !in renderers) {
                        renderers[renderer.udn] = renderer
                        discovered[renderer.udn] = CastDevice(
                            id = renderer.udn,
                            name = renderer.friendlyName,
                            type = CastType.DLNA
                        )
                        EcosystemLogger.d(HaronConstants.TAG, "DLNA: found renderer '${renderer.friendlyName}' (${renderer.udn}), controlURL=${renderer.controlUrl}")
                        emit(discovered.values.toList())
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    break
                }
            }

            socket.close()
            EcosystemLogger.d(HaronConstants.TAG, "DLNA: discovery done. $responseCount responses, ${discovered.size} renderers found")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "DLNA discovery error: ${e.message}")
        }

        if (discovered.isEmpty()) {
            EcosystemLogger.d(HaronConstants.TAG, "DLNA: no renderers found")
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    // --- Cast ---

    suspend fun castMedia(
        deviceId: String,
        url: String,
        mimeType: String,
        title: String
    ) = withContext(Dispatchers.IO) {
        val renderer = renderers[deviceId] ?: return@withContext
        userStopped = false

        val didl = buildDidlMetadata(title, mimeType, url)
        val setUriParams = mapOf(
            "InstanceID" to "0",
            "CurrentURI" to url,
            "CurrentURIMetaData" to escapeXml(didl)
        )

        val setResult = sendSoapAction(
            renderer.controlUrl,
            AV_TRANSPORT_SERVICE,
            "SetAVTransportURI",
            setUriParams
        )
        if (setResult == null) {
            EcosystemLogger.e(HaronConstants.TAG, "DLNA SetAVTransportURI failed")
            return@withContext
        }

        val playResult = sendSoapAction(
            renderer.controlUrl,
            AV_TRANSPORT_SERVICE,
            "Play",
            mapOf("InstanceID" to "0", "Speed" to "1")
        )
        if (playResult == null) {
            EcosystemLogger.e(HaronConstants.TAG, "DLNA Play failed")
            return@withContext
        }

        connectedDeviceId = deviceId
        _isConnected.value = true
        _connectedDeviceName.value = renderer.friendlyName
        _mediaIsPlaying.value = true

        EcosystemLogger.d(HaronConstants.TAG, "DLNA cast started: ${renderer.friendlyName}")
        startPolling(renderer)
    }

    // --- Playback control ---

    fun sendRemoteInput(event: RemoteInputEvent) {
        val renderer = connectedDeviceId?.let { renderers[it] } ?: return
        when (event) {
            is RemoteInputEvent.PlayPause -> {
                val action = if (_mediaIsPlaying.value) "Pause" else "Play"
                val params = if (action == "Play") {
                    mapOf("InstanceID" to "0", "Speed" to "1")
                } else {
                    mapOf("InstanceID" to "0")
                }
                sendSoapAsync(renderer.controlUrl, AV_TRANSPORT_SERVICE, action, params)
            }

            is RemoteInputEvent.SeekTo -> {
                val time = msToTime(event.positionMs)
                sendSoapAsync(
                    renderer.controlUrl,
                    AV_TRANSPORT_SERVICE,
                    "Seek",
                    mapOf("InstanceID" to "0", "Unit" to "REL_TIME", "Target" to time)
                )
            }

            is RemoteInputEvent.VolumeChange -> {
                val rcUrl = renderer.renderingControlUrl ?: return
                val currentVol = 50 // default; real value would require GetVolume
                val newVol = (currentVol + (event.delta * 100).toInt()).coerceIn(0, 100)
                sendSoapAsync(
                    rcUrl,
                    RENDERING_CONTROL_SERVICE,
                    "SetVolume",
                    mapOf(
                        "InstanceID" to "0",
                        "Channel" to "Master",
                        "DesiredVolume" to newVol.toString()
                    )
                )
            }

            is RemoteInputEvent.Next, is RemoteInputEvent.Prev -> {
                // DLNA has no queue concept — ignore
            }
            // TV remote events handled via WebSocket, not DLNA
            is RemoteInputEvent.MouseMove,
            is RemoteInputEvent.MouseClick,
            is RemoteInputEvent.Scroll,
            is RemoteInputEvent.KeyPress,
            is RemoteInputEvent.TextInput,
            is RemoteInputEvent.ClearAll -> { /* no-op */ }
        }
    }

    fun disconnect() {
        val renderer = connectedDeviceId?.let { renderers[it] } ?: return
        userStopped = true
        sendSoapAsync(
            renderer.controlUrl,
            AV_TRANSPORT_SERVICE,
            "Stop",
            mapOf("InstanceID" to "0")
        )
        clearState()
    }

    private fun clearState() {
        pollingJob?.cancel()
        pollingJob = null
        connectedDeviceId = null
        _isConnected.value = false
        _connectedDeviceName.value = null
        _mediaIsPlaying.value = false
        _mediaPositionMs.value = 0L
        _mediaDurationMs.value = 0L
    }

    // --- Polling ---

    private fun startPolling(renderer: DlnaRenderer) {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            var consecutiveErrors = 0
            while (isActive) {
                delay(3000)
                try {
                    pollTransportInfo(renderer)
                    pollPositionInfo(renderer)
                    consecutiveErrors = 0
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    consecutiveErrors++
                    if (consecutiveErrors >= 10) {
                        EcosystemLogger.d(HaronConstants.TAG, "DLNA polling stopped: 10 consecutive errors")
                        clearState()
                        break
                    }
                }
            }
        }
    }

    private fun pollTransportInfo(renderer: DlnaRenderer) {
        val response = sendSoapAction(
            renderer.controlUrl,
            AV_TRANSPORT_SERVICE,
            "GetTransportInfo",
            mapOf("InstanceID" to "0")
        ) ?: return

        val state = extractXmlValue(response, "CurrentTransportState")
        when (state) {
            "PLAYING" -> _mediaIsPlaying.value = true
            "PAUSED_PLAYBACK" -> _mediaIsPlaying.value = false
            "STOPPED", "NO_MEDIA_PRESENT" -> {
                if (!userStopped) {
                    clearState()
                }
            }
        }
    }

    private fun pollPositionInfo(renderer: DlnaRenderer) {
        val response = sendSoapAction(
            renderer.controlUrl,
            AV_TRANSPORT_SERVICE,
            "GetPositionInfo",
            mapOf("InstanceID" to "0")
        ) ?: return

        val relTime = extractXmlValue(response, "RelTime")
        val duration = extractXmlValue(response, "TrackDuration")

        if (relTime != null && relTime != "NOT_IMPLEMENTED") {
            _mediaPositionMs.value = parseTimeToMs(relTime)
        }
        if (duration != null && duration != "NOT_IMPLEMENTED") {
            _mediaDurationMs.value = parseTimeToMs(duration)
        }
    }

    // --- SOAP ---

    private fun sendSoapAction(
        controlUrl: String,
        serviceType: String,
        action: String,
        params: Map<String, String>
    ): String? {
        return try {
            val envelope = buildSoapEnvelope(serviceType, action, params)
            val url = URL(controlUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            conn.setRequestProperty("SOAPAction", "\"$serviceType#$action\"")
            conn.doOutput = true
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            conn.outputStream.use { it.write(envelope.toByteArray()) }

            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                val err = try {
                    conn.errorStream?.bufferedReader()?.readText()
                } catch (_: Exception) { null }
                EcosystemLogger.e(HaronConstants.TAG, "SOAP $action failed ($code): $err")
                null
            }
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "SOAP $action error: ${e.message}")
            null
        }
    }

    private fun sendSoapAsync(
        controlUrl: String,
        serviceType: String,
        action: String,
        params: Map<String, String>
    ) {
        scope.launch {
            sendSoapAction(controlUrl, serviceType, action, params)
        }
    }

    private fun buildSoapEnvelope(
        serviceType: String,
        action: String,
        params: Map<String, String>
    ): String {
        val paramsXml = params.entries.joinToString("") { (k, v) -> "<$k>$v</$k>" }
        return """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
<s:Body>
<u:$action xmlns:u="$serviceType">
$paramsXml
</u:$action>
</s:Body>
</s:Envelope>"""
    }

    private fun buildDidlMetadata(title: String, mimeType: String, url: String): String {
        val upnpClass = when {
            mimeType.startsWith("video") -> "object.item.videoItem"
            mimeType.startsWith("audio") -> "object.item.audioItem.musicTrack"
            mimeType.startsWith("image") -> "object.item.imageItem"
            else -> "object.item"
        }
        return """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
<item id="0" parentID="-1" restricted="1">
<dc:title>${escapeXml(title)}</dc:title>
<upnp:class>$upnpClass</upnp:class>
<res protocolInfo="http-get:*:$mimeType:*">$url</res>
</item>
</DIDL-Lite>"""
    }

    // --- XML / Device description ---

    private fun fetchDeviceDescription(locationUrl: String): DlnaRenderer? {
        return try {
            val url = URL(locationUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val xml = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val baseUrl = "${url.protocol}://${url.host}:${url.port}"
            val renderer = parseDeviceXml(xml, baseUrl)
            if (renderer == null) {
                // Log device type for debugging
                val nameMatch = Regex("<friendlyName>([^<]+)</friendlyName>").find(xml)
                val typeMatch = Regex("<deviceType>([^<]+)</deviceType>").find(xml)
                EcosystemLogger.d(HaronConstants.TAG, "DLNA: device '${nameMatch?.groupValues?.get(1) ?: "?"}' type=${typeMatch?.groupValues?.get(1) ?: "?"} — not a renderer")
            }
            renderer
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "DLNA fetch description error: ${e.message}")
            null
        }
    }

    private fun parseDeviceXml(xml: String, baseUrl: String): DlnaRenderer? {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var friendlyName: String? = null
        var udn: String? = null
        var avTransportControlUrl: String? = null
        var renderingControlUrl: String? = null

        var currentServiceType: String? = null
        var currentControlUrl: String? = null
        var inService = false
        var tagName: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    tagName = parser.name
                    if (tagName == "service") inService = true
                }

                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim() ?: ""
                    if (text.isNotEmpty()) {
                        when (tagName) {
                            "friendlyName" -> if (friendlyName == null) friendlyName = text
                            "UDN" -> if (udn == null) udn = text
                            "serviceType" -> if (inService) currentServiceType = text
                            "controlURL" -> if (inService) currentControlUrl = text
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (parser.name == "service") {
                        if (currentServiceType?.contains("AVTransport") == true && currentControlUrl != null) {
                            avTransportControlUrl = makeAbsoluteUrl(baseUrl, currentControlUrl!!)
                        }
                        if (currentServiceType?.contains("RenderingControl") == true && currentControlUrl != null) {
                            renderingControlUrl = makeAbsoluteUrl(baseUrl, currentControlUrl!!)
                        }
                        inService = false
                        currentServiceType = null
                        currentControlUrl = null
                    }
                    tagName = null
                }
            }
            event = parser.next()
        }

        if (friendlyName == null || udn == null || avTransportControlUrl == null) return null

        return DlnaRenderer(
            udn = udn,
            friendlyName = friendlyName,
            controlUrl = avTransportControlUrl,
            renderingControlUrl = renderingControlUrl
        )
    }

    private fun extractXmlValue(xml: String, tagName: String): String? {
        // Simple extraction — works for flat SOAP responses
        val start = xml.indexOf("<$tagName")
        if (start < 0) return null
        val gtPos = xml.indexOf('>', start)
        if (gtPos < 0) return null
        val end = xml.indexOf("</$tagName>", gtPos)
        if (end < 0) return null
        return xml.substring(gtPos + 1, end).trim()
    }

    // --- Utils ---

    private fun parseLocationHeader(response: String): String? {
        for (line in response.lines()) {
            if (line.startsWith("LOCATION:", ignoreCase = true)) {
                return line.substringAfter(":").trim()
            }
        }
        return null
    }

    private fun makeAbsoluteUrl(baseUrl: String, path: String): String {
        return if (path.startsWith("http://") || path.startsWith("https://")) {
            path
        } else {
            baseUrl.trimEnd('/') + "/" + path.trimStart('/')
        }
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun parseTimeToMs(hhmmss: String): Long {
        val parts = hhmmss.split(":")
        if (parts.size != 3) return 0L
        return try {
            val h = parts[0].toLong()
            val m = parts[1].toLong()
            val s = parts[2].toDouble()
            (h * 3600_000 + m * 60_000 + (s * 1000).toLong())
        } catch (_: Exception) {
            0L
        }
    }

    private fun msToTime(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return "%02d:%02d:%02d".format(h, m, s)
    }

    companion object {
        private const val SSDP_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val SSDP_MX = 3
        private const val SSDP_TIMEOUT_MS = 4000
        private const val MEDIA_RENDERER_ST = "urn:schemas-upnp-org:device:MediaRenderer:1"
        private const val AV_TRANSPORT_SERVICE = "urn:schemas-upnp-org:service:AVTransport:1"
        private const val RENDERING_CONTROL_SERVICE = "urn:schemas-upnp-org:service:RenderingControl:1"
    }
}
