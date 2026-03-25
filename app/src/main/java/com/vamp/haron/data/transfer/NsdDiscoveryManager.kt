package com.vamp.haron.data.transfer

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.provider.Settings
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.domain.model.DiscoveredDevice
import com.vamp.haron.domain.model.TransferProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discovers nearby Haron instances on the local network using Android NSD (mDNS/DNS-SD).
 * Registers the current device as a discoverable service (_haron._tcp.)
 * and monitors for other Haron services to enable peer-to-peer file transfers.
 * Emits discovered devices as a reactive Flow.
 */
@Singleton
class NsdDiscoveryManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var registeredServiceName: String? = null

    fun discoverServices(): Flow<List<DiscoveredDevice>> = callbackFlow {
        val manager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: run {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        nsdManager = manager

        val found = mutableMapOf<String, DiscoveredDevice>()
        // Map service name → device id for proper removal
        val nameToId = mutableMapOf<String, String>()

        // Emit initial empty list so combine() doesn't block waiting for first emission
        trySend(emptyList())

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                EcosystemLogger.d(HaronConstants.TAG, "NSD discovery started for $serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                // Skip own service — only by exact registered name match
                val ownName = registeredServiceName
                val svcName = serviceInfo.serviceName
                if (ownName != null && svcName == ownName) {
                    EcosystemLogger.d(HaronConstants.TAG, "NSD skipping own service: $svcName")
                    return
                }
                EcosystemLogger.d(HaronConstants.TAG, "NSD service found: ${serviceInfo.serviceName}")
                manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        EcosystemLogger.e(HaronConstants.TAG, "NSD resolve failed: $errorCode")
                    }

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val host = info.host?.hostAddress ?: return
                        val port = info.port
                        val device = DiscoveredDevice(
                            id = "${host}:${port}",
                            name = info.serviceName,
                            address = host,
                            supportedProtocols = setOf(TransferProtocol.HTTP),
                            isHaron = true,
                            port = port
                        )
                        found[device.id] = device
                        nameToId[info.serviceName] = device.id
                        trySend(found.values.toList())
                    }
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                // NSD often fires false "lost" events — only remove if we tracked this name
                val deviceId = nameToId.remove(serviceInfo.serviceName)
                if (deviceId != null) {
                    found.remove(deviceId)
                    trySend(found.values.toList())
                }
                EcosystemLogger.d(HaronConstants.TAG, "NSD service lost: ${serviceInfo.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                EcosystemLogger.d(HaronConstants.TAG, "NSD discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                EcosystemLogger.e(HaronConstants.TAG, "NSD start failed: $errorCode")
                trySend(emptyList())
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                EcosystemLogger.e(HaronConstants.TAG, "NSD stop failed: $errorCode")
            }
        }
        discoveryListener = listener

        manager.discoverServices(
            HaronConstants.NSD_SERVICE_TYPE,
            NsdManager.PROTOCOL_DNS_SD,
            listener
        )

        awaitClose {
            stopDiscovery()
        }
    }

    fun stopDiscovery() {
        try {
            discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        } catch (_: Exception) { }
        discoveryListener = null
    }

    fun registerService(port: Int) {
        val manager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        nsdManager = manager

        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        val shortId = androidId.takeLast(8)
        val desiredName = "Haron-${android.os.Build.MODEL}-$shortId"
        // Set name before async registration so discovery filter works immediately
        registeredServiceName = desiredName

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = desiredName
            serviceType = HaronConstants.NSD_SERVICE_TYPE
            setPort(port)
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                EcosystemLogger.e(HaronConstants.TAG, "NSD registration failed: $errorCode")
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                EcosystemLogger.e(HaronConstants.TAG, "NSD unregistration failed: $errorCode")
            }

            override fun onServiceRegistered(info: NsdServiceInfo) {
                registeredServiceName = info.serviceName
                EcosystemLogger.d(HaronConstants.TAG, "NSD service registered: ${info.serviceName}")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                EcosystemLogger.d(HaronConstants.TAG, "NSD service unregistered: ${info.serviceName}")
            }
        }
        registrationListener = listener

        manager.registerService(
            serviceInfo,
            NsdManager.PROTOCOL_DNS_SD,
            listener
        )
    }

    fun unregisterService() {
        try {
            registrationListener?.let { nsdManager?.unregisterService(it) }
        } catch (_: Exception) { }
        registrationListener = null
        registeredServiceName = null
    }
}
