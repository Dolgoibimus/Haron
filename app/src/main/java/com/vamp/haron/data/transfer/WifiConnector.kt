package com.vamp.haron.data.transfer

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import com.vamp.core.logger.EcosystemLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class WifiConnector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var activeCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * Connect to Wi-Fi network programmatically.
     * API 29+: WifiNetworkSpecifier (shows system confirmation dialog).
     * API 26-28: WifiManager.addNetwork() (deprecated but functional).
     * Returns true on success.
     */
    suspend fun connect(ssid: String, password: String?, timeoutMs: Long = 30_000): Boolean {
        EcosystemLogger.d(TAG, "connect: ssid=$ssid, hasPassword=${!password.isNullOrEmpty()}, timeout=$timeoutMs")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectApi29(ssid, password, timeoutMs)
        } else {
            connectLegacy(ssid, password, timeoutMs)
        }
    }

    /**
     * Disconnect from a previously requested network (API 29+).
     */
    fun disconnect() {
        val cb = activeCallback ?: return
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(cb)
            EcosystemLogger.d(TAG, "disconnect: unregistered network callback")
        } catch (e: Exception) {
            EcosystemLogger.e(TAG, "disconnect error: ${e.message}")
        }
        activeCallback = null
    }

    private suspend fun connectApi29(ssid: String, password: String?, timeoutMs: Long): Boolean {
        EcosystemLogger.d(TAG, "connectApi29: building WifiNetworkSpecifier for ssid=$ssid")
        val specifierBuilder = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)

        if (!password.isNullOrEmpty()) {
            specifierBuilder.setWpa2Passphrase(password)
        }

        val specifier = specifierBuilder.build()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(specifier)
            .build()

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Unregister previous callback if any
        disconnect()

        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        EcosystemLogger.d(TAG, "connectApi29: network available, binding process")
                        // Bind this process to the hotspot network so HTTP requests go through it
                        cm.bindProcessToNetwork(network)
                        activeCallback = this
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onUnavailable() {
                        EcosystemLogger.e(TAG, "connectApi29: network unavailable (user rejected or timeout)")
                        if (cont.isActive) cont.resume(false)
                    }

                    override fun onLost(network: Network) {
                        EcosystemLogger.d(TAG, "connectApi29: network lost")
                        cm.bindProcessToNetwork(null)
                        activeCallback = null
                    }
                }

                EcosystemLogger.d(TAG, "connectApi29: requesting network...")
                cm.requestNetwork(request, callback)

                cont.invokeOnCancellation {
                    try {
                        cm.unregisterNetworkCallback(callback)
                        cm.bindProcessToNetwork(null)
                    } catch (_: Exception) {}
                    activeCallback = null
                }
            }
        } ?: run {
            EcosystemLogger.e(TAG, "connectApi29: timeout after ${timeoutMs}ms")
            false
        }
    }

    @Suppress("deprecation")
    private suspend fun connectLegacy(ssid: String, password: String?, timeoutMs: Long): Boolean {
        EcosystemLogger.d(TAG, "connectLegacy: adding network ssid=$ssid")
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager

        if (wifiManager == null) {
            EcosystemLogger.e(TAG, "connectLegacy: WifiManager is null")
            return false
        }

        if (!wifiManager.isWifiEnabled) {
            EcosystemLogger.e(TAG, "connectLegacy: Wi-Fi is disabled")
            return false
        }

        val config = WifiConfiguration().apply {
            SSID = "\"$ssid\""
            if (!password.isNullOrEmpty()) {
                preSharedKey = "\"$password\""
            } else {
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            }
        }

        val networkId = wifiManager.addNetwork(config)
        if (networkId == -1) {
            EcosystemLogger.e(TAG, "connectLegacy: addNetwork returned -1")
            return false
        }

        val enabled = wifiManager.enableNetwork(networkId, true)
        if (!enabled) {
            EcosystemLogger.e(TAG, "connectLegacy: enableNetwork returned false")
            wifiManager.removeNetwork(networkId)
            return false
        }

        // Wait for connection
        val connected = withTimeoutOrNull(timeoutMs) {
            var attempts = 0
            while (attempts < (timeoutMs / 500).toInt()) {
                val info = wifiManager.connectionInfo
                if (info != null && info.ssid?.removeSurrounding("\"") == ssid) {
                    EcosystemLogger.d(TAG, "connectLegacy: connected to $ssid")
                    return@withTimeoutOrNull true
                }
                kotlinx.coroutines.delay(500)
                attempts++
            }
            false
        } ?: false

        if (!connected) {
            EcosystemLogger.e(TAG, "connectLegacy: connection timeout")
            wifiManager.removeNetwork(networkId)
        }

        return connected
    }

    companion object {
        private const val TAG = "WifiConnector"
    }
}
