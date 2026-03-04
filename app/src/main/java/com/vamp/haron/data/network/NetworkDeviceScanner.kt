package com.vamp.haron.data.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.provider.Settings
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discovers network devices:
 * 1. Haron instances via NSD (_haron._tcp.)
 * 2. SMB shares via NSD (_smb._tcp.) + subnet scan on port 445
 */
@Singleton
class NetworkDeviceScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _devices = MutableStateFlow<List<NetworkDevice>>(emptyList())
    val devices: StateFlow<List<NetworkDevice>> = _devices.asStateFlow()

    private var nsdManager: NsdManager? = null
    private var haronListener: NsdManager.DiscoveryListener? = null
    private var smbListener: NsdManager.DiscoveryListener? = null
    private var scanning = false

    private val found = ConcurrentHashMap<String, NetworkDevice>()

    fun startDiscovery() {
        if (scanning) return
        scanning = true
        found.clear()
        _devices.value = emptyList()

        val manager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        nsdManager = manager

        // 1. Discover Haron instances
        startNsdDiscovery(manager, HaronConstants.NSD_SERVICE_TYPE, NetworkDeviceType.HARON)

        // 2. Discover SMB shares via mDNS
        startNsdDiscovery(manager, SMB_SERVICE_TYPE, NetworkDeviceType.SMB)

        // 3. Subnet scan for SMB (port 445) — catches devices without mDNS
        scope.launch {
            delay(1000) // Give NSD a head start
            scanSubnetForSmb()
        }
    }

    fun stopDiscovery() {
        scanning = false
        try { haronListener?.let { nsdManager?.stopServiceDiscovery(it) } } catch (_: Exception) {}
        try { smbListener?.let { nsdManager?.stopServiceDiscovery(it) } } catch (_: Exception) {}
        haronListener = null
        smbListener = null
    }

    fun refreshDevices() {
        stopDiscovery()
        startDiscovery()
    }

    private fun startNsdDiscovery(
        manager: NsdManager,
        serviceType: String,
        deviceType: NetworkDeviceType
    ) {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {
                EcosystemLogger.d(TAG, "NSD discovery started: $type")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                // Skip own Haron service by matching our unique service name
                if (deviceType == NetworkDeviceType.HARON) {
                    val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
                    val ownPrefix = "Haron-${android.os.Build.MODEL}-${androidId.takeLast(8)}"
                    val svcName = serviceInfo.serviceName
                    // NSD may append " (N)" suffix, so use startsWith
                    if (svcName.startsWith(ownPrefix)) return
                }
                manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        EcosystemLogger.w(TAG, "NSD resolve failed: $errorCode for ${info.serviceName}")
                    }

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val host = info.host?.hostAddress ?: return
                        val port = info.port
                        val device = NetworkDevice(
                            id = "${deviceType.name}_${host}:${port}",
                            name = info.serviceName,
                            address = host,
                            port = port,
                            type = deviceType
                        )
                        found[device.id] = device
                        _devices.value = found.values.toList()
                    }
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val prefix = "${deviceType.name}_"
                found.keys.removeAll { it.startsWith(prefix) && it.contains(serviceInfo.serviceName) }
                _devices.value = found.values.toList()
            }

            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, errorCode: Int) {
                EcosystemLogger.w(TAG, "NSD discovery failed for $type: $errorCode")
            }
            override fun onStopDiscoveryFailed(type: String, errorCode: Int) {}
        }

        when (deviceType) {
            NetworkDeviceType.HARON -> haronListener = listener
            NetworkDeviceType.SMB -> smbListener = listener
        }

        try {
            manager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            EcosystemLogger.w(TAG, "Failed to start NSD for $serviceType: ${e.message}")
        }
    }

    /**
     * Scan local subnet for SMB shares (port 445).
     * Quick scan: parallel probes with 300ms timeout.
     * Skips VPN/CGNAT subnets (100.64.0.0/10) to avoid scanning Tailscale etc.
     */
    private suspend fun scanSubnetForSmb() {
        val localIp = getLocalIpAddress() ?: return
        if (isCgnatAddress(localIp)) {
            EcosystemLogger.d(TAG, "Skipping SMB subnet scan — CGNAT/VPN address: $localIp")
            return
        }
        val subnet = localIp.substringBeforeLast('.')
        if (subnet == localIp) return // No valid subnet

        EcosystemLogger.d(TAG, "Scanning subnet $subnet.* for SMB")

        // Scan in batches to avoid too many concurrent connections
        val batchSize = 20
        for (start in 1..254 step batchSize) {
            if (!scanning) return
            val end = (start + batchSize - 1).coerceAtMost(254)
            val jobs = (start..end).map { i ->
                scope.launch {
                    if (!isActive || !scanning) return@launch
                    val ip = "$subnet.$i"
                    if (ip == localIp) return@launch
                    // Skip already discovered
                    if (found.values.any { it.address == ip }) return@launch

                    if (probePort(ip, SMB_PORT)) {
                        val hostname = try {
                            InetAddress.getByName(ip).canonicalHostName.let {
                                if (it == ip) "SMB ($ip)" else it
                            }
                        } catch (_: Exception) { "SMB ($ip)" }

                        val device = NetworkDevice(
                            id = "SMB_SCAN_${ip}:${SMB_PORT}",
                            name = hostname,
                            address = ip,
                            port = SMB_PORT,
                            type = NetworkDeviceType.SMB
                        )
                        found[device.id] = device
                        _devices.value = found.values.toList()
                    }
                }
            }
            jobs.forEach { it.join() }
        }
        EcosystemLogger.d(TAG, "Subnet scan complete, found ${found.size} devices")
    }

    private fun probePort(ip: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), PROBE_TIMEOUT_MS)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Returns local IPv4 address, preferring Wi-Fi (wlan) over VPN/other interfaces.
     */
    private fun getLocalIpAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return null
            val candidates = interfaces
                .filter { it.isUp && !it.isLoopback }
                .flatMap { iface ->
                    iface.inetAddresses.toList()
                        .filterIsInstance<java.net.Inet4Address>()
                        .filter { !it.isLoopbackAddress }
                        .map { iface.name to it.hostAddress }
                }
            // Prefer wlan (Wi-Fi) over tun/tailscale/other
            candidates.firstOrNull { it.first.startsWith("wlan") }?.second
                ?: candidates.firstOrNull { !isCgnatAddress(it.second ?: "") }?.second
                ?: candidates.firstOrNull()?.second
        } catch (_: Exception) {
            null
        }
    }

    /** CGNAT range 100.64.0.0/10 — used by Tailscale, carrier NAT, etc. */
    private fun isCgnatAddress(ip: String): Boolean {
        val parts = ip.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return false
        return parts[0] == 100 && parts[1] in 64..127
    }

    companion object {
        private const val TAG = "NetworkScanner"
        private const val SMB_SERVICE_TYPE = "_smb._tcp."
        private const val SMB_PORT = 445
        private const val PROBE_TIMEOUT_MS = 300
    }
}
