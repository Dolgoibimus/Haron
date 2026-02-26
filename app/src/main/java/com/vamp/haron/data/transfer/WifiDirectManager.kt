package com.vamp.haron.data.transfer

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.domain.model.DiscoveredDevice
import com.vamp.haron.domain.model.TransferProgressInfo
import com.vamp.haron.domain.model.TransferProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiDirectManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var manager: WifiP2pManager? = null
    private var p2pChannel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null

    private fun ensureInitialized() {
        if (manager == null) {
            manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
            p2pChannel = manager?.initialize(context, context.mainLooper, null)
        }
    }

    @SuppressLint("MissingPermission")
    fun discoverPeers(): Flow<List<DiscoveredDevice>> = callbackFlow {
        ensureInitialized()
        val wifiManager = manager ?: run {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val wifiChannel = p2pChannel ?: run {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        // Emit initial empty list so combine() doesn't block waiting for peers
        trySend(emptyList())

        val peerReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        wifiManager.requestPeers(wifiChannel) { peers: WifiP2pDeviceList? ->
                            val devices = peers?.deviceList?.map { it.toDiscoveredDevice() } ?: emptyList()
                            trySend(devices)
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        context.registerReceiver(peerReceiver, filter)
        receiver = peerReceiver

        wifiManager.discoverPeers(wifiChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                EcosystemLogger.d(HaronConstants.TAG, "Wi-Fi Direct discovery started")
            }

            override fun onFailure(reason: Int) {
                EcosystemLogger.e(HaronConstants.TAG, "Wi-Fi Direct discovery failed: $reason")
                trySend(emptyList())
            }
        })

        awaitClose {
            stopDiscovery()
        }
    }

    fun stopDiscovery() {
        try {
            receiver?.let { context.unregisterReceiver(it) }
        } catch (_: Exception) { }
        receiver = null
        manager?.stopPeerDiscovery(p2pChannel, null)
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String, callback: (WifiP2pInfo?) -> Unit) {
        ensureInitialized()
        val config = WifiP2pConfig().apply {
            deviceAddress = address
        }
        manager?.connect(p2pChannel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                EcosystemLogger.d(HaronConstants.TAG, "Wi-Fi Direct connecting to $address")
                manager?.requestConnectionInfo(p2pChannel) { info ->
                    callback(info)
                }
            }

            override fun onFailure(reason: Int) {
                EcosystemLogger.e(HaronConstants.TAG, "Wi-Fi Direct connect failed: $reason")
                callback(null)
            }
        })
    }

    fun disconnect() {
        manager?.removeGroup(p2pChannel, null)
    }

    /**
     * Send files through a direct socket connection after Wi-Fi Direct is established.
     * Group owner hosts a ServerSocket; client connects to it.
     * @param resumeFromByte if > 0, resume transfer from this byte offset (used after retry)
     */
    fun sendFiles(
        hostAddress: String,
        isGroupOwner: Boolean,
        files: List<File>,
        port: Int = TRANSFER_PORT,
        resumeFromByte: Long = 0L
    ): Flow<TransferProgressInfo> = flow {
        val totalBytes = files.sumOf { it.length() }
        var transferred = resumeFromByte
        val startTime = System.currentTimeMillis()

        val socket: Socket = if (isGroupOwner) {
            ServerSocket(port).use { server ->
                server.soTimeout = 30_000
                server.accept()
            }
        } else {
            Socket().also {
                it.connect(InetSocketAddress(hostAddress, port), 15_000)
            }
        }

        socket.use { sock ->
            val output = DataOutputStream(sock.getOutputStream())
            val reader = BufferedReader(InputStreamReader(sock.getInputStream()))

            // Send request
            val request = TransferProtocolNegotiator.buildRequest(files, "WIFI_DIRECT")
            output.writeUTF(request)
            output.flush()

            // Wait for accept
            val response = reader.readLine() ?: throw Exception("No response from receiver")
            val type = TransferProtocolNegotiator.parseType(response)
            if (type == TransferProtocolNegotiator.TYPE_DECLINE) {
                val reason = TransferProtocolNegotiator.parseDecline(response)
                throw Exception("Transfer declined: $reason")
            }

            // Determine which file and offset to start from (for resume)
            var startFileIndex = 0
            var fileOffset = 0L
            if (resumeFromByte > 0) {
                var cumulative = 0L
                for (i in files.indices) {
                    val fLen = files[i].length()
                    if (cumulative + fLen > resumeFromByte) {
                        startFileIndex = i
                        fileOffset = resumeFromByte - cumulative
                        break
                    }
                    cumulative += fLen
                    if (cumulative >= resumeFromByte) {
                        startFileIndex = i + 1
                        fileOffset = 0
                        break
                    }
                }
            }

            // Send files
            files.forEachIndexed { index, file ->
                if (index < startFileIndex) return@forEachIndexed

                val offset = if (index == startFileIndex) fileOffset else 0L
                val header = TransferProtocolNegotiator.buildFileHeader(file.name, file.length(), index, offset)
                output.writeUTF(header)
                output.flush()

                file.inputStream().use { input ->
                    if (offset > 0) input.skip(offset)
                    val buffer = ByteArray(HaronConstants.TRANSFER_BUFFER_SIZE)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        transferred += read
                        val elapsed = System.currentTimeMillis() - startTime
                        val speed = if (elapsed > 0) (transferred * 1000 / elapsed) else 0
                        val eta = if (speed > 0) ((totalBytes - transferred) / speed) else 0
                        emit(
                            TransferProgressInfo(
                                bytesTransferred = transferred,
                                totalBytes = totalBytes,
                                currentFileIndex = index,
                                totalFiles = files.size,
                                currentFileName = file.name,
                                speedBytesPerSec = speed,
                                etaSeconds = eta,
                                resumeOffset = transferred
                            )
                        )
                    }
                }
                output.flush()
            }

            // Send complete
            output.writeUTF(TransferProtocolNegotiator.buildComplete())
            output.flush()
        }

        emit(
            TransferProgressInfo(
                bytesTransferred = totalBytes,
                totalBytes = totalBytes,
                currentFileIndex = files.size,
                totalFiles = files.size,
                currentFileName = "",
                speedBytesPerSec = 0,
                etaSeconds = 0
            )
        )
    }.flowOn(Dispatchers.IO)

    private fun WifiP2pDevice.toDiscoveredDevice(): DiscoveredDevice {
        return DiscoveredDevice(
            id = deviceAddress,
            name = deviceName.ifEmpty { deviceAddress },
            address = deviceAddress,
            supportedProtocols = setOf(TransferProtocol.WIFI_DIRECT),
            isHaron = deviceName.contains("Haron", ignoreCase = true)
        )
    }

    companion object {
        const val TRANSFER_PORT = 8988
    }
}
