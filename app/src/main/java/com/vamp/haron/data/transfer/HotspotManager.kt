package com.vamp.haron.data.transfer

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import com.vamp.core.logger.EcosystemLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class HotspotInfo(
    val ssid: String,
    val password: String,
    val ip: String
)

@Singleton
class HotspotManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null
    val isActive: Boolean get() = reservation != null

    /**
     * Start a local-only hotspot and return SSID + password + IP.
     * Returns null if hotspot could not be started.
     */
    suspend fun start(): HotspotInfo? {
        if (reservation != null) {
            return getInfoFromReservation(reservation!!)
        }

        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null

        val result = tryStartHotspot(wifiManager)

        if (result == null) {
            return null
        }

        // Wait for the network interface to get an IP
        if (result.ip.isEmpty()) {
            val ip = waitForHotspotIp()
            if (ip != null) return result.copy(ip = ip)
        }
        return result
    }

    fun stop() {
        try {
            reservation?.close()
        } catch (e: Exception) {
            EcosystemLogger.w(TAG, "Hotspot stop error: ${e.message}")
        }
        reservation = null
    }

    private suspend fun tryStartHotspot(wifiManager: WifiManager): HotspotInfo? {
        return withTimeoutOrNull(10_000L) {
            suspendCancellableCoroutine { cont ->
                try {
                    wifiManager.startLocalOnlyHotspot(
                        object : WifiManager.LocalOnlyHotspotCallback() {
                            override fun onStarted(res: WifiManager.LocalOnlyHotspotReservation) {
                                reservation = res
                                val info = getInfoFromReservation(res)
                                EcosystemLogger.d(TAG, "Hotspot started: ${info?.ssid}")
                                if (cont.isActive) cont.resume(info)
                            }

                            override fun onStopped() {
                                EcosystemLogger.d(TAG, "Hotspot stopped by system")
                                reservation = null
                                if (cont.isActive) cont.resume(null)
                            }

                            override fun onFailed(reason: Int) {
                                EcosystemLogger.w(TAG, "Hotspot failed: reason=$reason")
                                if (cont.isActive) cont.resume(null)
                            }
                        },
                        null
                    )
                } catch (e: Exception) {
                    EcosystemLogger.w(TAG, "startLocalOnlyHotspot error: ${e.message}")
                    if (cont.isActive) cont.resume(null)
                }

                cont.invokeOnCancellation {
                    try { reservation?.close() } catch (_: Exception) {}
                    reservation = null
                }
            }
        }
    }

    @Suppress("deprecation")
    private fun getInfoFromReservation(res: WifiManager.LocalOnlyHotspotReservation): HotspotInfo? {
        var ssid: String
        var password: String

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val config = res.softApConfiguration
                ssid = config.ssid ?: return null
                password = config.passphrase ?: ""
            } catch (e: Exception) {
                val config = res.wifiConfiguration ?: return null
                ssid = config.SSID?.removeSurrounding("\"") ?: return null
                password = config.preSharedKey?.removeSurrounding("\"") ?: ""
            }
        } else {
            val config = res.wifiConfiguration ?: return null
            ssid = config.SSID?.removeSurrounding("\"") ?: return null
            password = config.preSharedKey?.removeSurrounding("\"") ?: ""
        }

        val ip = getHotspotIp() ?: ""
        return HotspotInfo(ssid = ssid, password = password, ip = ip)
    }

    private fun getHotspotIp(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return null
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                val name = iface.name.lowercase()
                if (name.startsWith("swlan") || name.startsWith("ap") ||
                    name.contains("softap") || name.startsWith("wlan")) {
                    for (addr in iface.inetAddresses) {
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            val ip = addr.hostAddress ?: continue
                            if (ip.startsWith("192.168.")) return ip
                        }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun waitForHotspotIp(): String? {
        repeat(20) {
            val ip = getHotspotIp()
            if (ip != null) return ip
            delay(200)
        }
        return null
    }

    companion object {
        private const val TAG = "HotspotManager"
    }
}
