package com.vamp.haron.data.repository

import android.content.Context
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.data.transfer.BluetoothTransferManager
import com.vamp.haron.data.transfer.HttpFileServer
import com.vamp.haron.data.transfer.IncomingTransferRequest
import com.vamp.haron.data.transfer.NsdDiscoveryManager
import com.vamp.haron.data.transfer.QuickSendPending
import com.vamp.haron.data.transfer.ReceiveFileManager
import com.vamp.haron.data.transfer.TransferProtocolNegotiator
import com.vamp.haron.data.transfer.WifiDirectManager
import com.vamp.haron.domain.model.DiscoveredDevice
import com.vamp.haron.domain.model.TransferProgressInfo
import com.vamp.haron.domain.model.TransferProtocol
import com.vamp.haron.domain.repository.TransferRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.retryWhen
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wifiDirectManager: WifiDirectManager,
    private val bluetoothTransferManager: BluetoothTransferManager,
    private val nsdDiscoveryManager: NsdDiscoveryManager,
    private val httpFileServer: HttpFileServer,
    private val receiveFileManager: ReceiveFileManager
) : TransferRepository {

    private val retryDelays = HaronConstants.TRANSFER_RETRY_DELAYS
        .split(",")
        .mapNotNull { it.trim().toLongOrNull() }

    @Volatile
    private var cancelled = false

    override fun discoverDevices(): Flow<List<DiscoveredDevice>> {
        return combine(
            wifiDirectManager.discoverPeers(),
            nsdDiscoveryManager.discoverServices()
        ) { wifiDevices, nsdDevices ->
            mergeDevices(wifiDevices, nsdDevices)
        }
    }

    override fun stopDiscovery() {
        wifiDirectManager.stopDiscovery()
        nsdDiscoveryManager.stopDiscovery()
    }

    override suspend fun sendFiles(
        files: List<File>,
        device: DiscoveredDevice,
        protocol: TransferProtocol
    ): Flow<TransferProgressInfo> {
        cancelled = false
        // For Haron devices (discovered via NSD) — send directly to their TCP port
        val sendFlow = if (device.isHaron && device.port > 0) {
            sendViaTcp(files, device)
        } else when (protocol) {
            TransferProtocol.WIFI_DIRECT -> sendViaWifiDirect(files, device)
            TransferProtocol.HTTP -> sendViaHttp(files)
            TransferProtocol.BLUETOOTH -> sendViaBluetooth(files, device)
        }
        return sendFlow.retryWhen { cause, attempt ->
            if (cancelled || cause !is IOException) return@retryWhen false
            val delayIndex = attempt.toInt().coerceAtMost(retryDelays.size - 1)
            if (attempt >= HaronConstants.TRANSFER_MAX_RETRIES) return@retryWhen false
            EcosystemLogger.w(
                HaronConstants.TAG,
                "Transfer retry ${attempt + 1}/${HaronConstants.TRANSFER_MAX_RETRIES}: ${cause.message}"
            )
            delay(retryDelays[delayIndex])
            true
        }.catch { e ->
            EcosystemLogger.e(HaronConstants.TAG, "Transfer failed: ${e.message}")
            emit(
                TransferProgressInfo(
                    totalFiles = files.size,
                    currentFileName = "Error: ${e.message}"
                )
            )
        }
    }

    override fun cancelTransfer() {
        cancelled = true
        httpFileServer.stop()
    }

    override fun getLocalIpAddress(): String? {
        return httpFileServer.getLocalIpAddress()
    }

    // --- Receiving ---

    override fun startReceiving(): Flow<IncomingTransferRequest> {
        receiveFileManager.ensureListening()
        return receiveFileManager.incomingRequests
    }

    override fun stopReceiving() {
        // Don't stop global listener — it's managed by ReceiveFileManager's own scope
    }

    override fun acceptTransfer(): Flow<TransferProgressInfo> {
        return receiveFileManager.acceptTransfer()
    }

    override fun declineTransfer() {
        receiveFileManager.declineTransfer()
    }

    override fun getReceivePort(): Int {
        return receiveFileManager.actualPort
    }

    override val incomingRequests: SharedFlow<IncomingTransferRequest>
        get() = receiveFileManager.incomingRequests

    override val receiveCompleted: SharedFlow<Int>
        get() = receiveFileManager.receiveCompleted

    override val friendReceived: SharedFlow<String>
        get() = receiveFileManager.friendReceived

    override val quickSendPending: SharedFlow<QuickSendPending>
        get() = receiveFileManager.quickSendPending

    // --- Private sending methods ---

    private fun sendViaWifiDirect(
        files: List<File>,
        device: DiscoveredDevice
    ): Flow<TransferProgressInfo> = flow {
        val info = wifiDirectManager.connectAndWait(device.address)
        EcosystemLogger.d(
            HaronConstants.TAG,
            "P2P connected: groupOwner=${info.isGroupOwner}, ownerAddr=${info.groupOwnerAddress?.hostAddress}"
        )
        emitAll(wifiDirectManager.sendFiles(info, files))
    }.flowOn(Dispatchers.IO)

    private fun sendViaHttp(files: List<File>): Flow<TransferProgressInfo> = flow {
        val port = httpFileServer.start(files)
        val url = httpFileServer.getServerUrl()
        EcosystemLogger.d(HaronConstants.TAG, "HTTP server ready at $url")

        emit(
            TransferProgressInfo(
                totalFiles = files.size,
                currentFileName = url ?: "Server started on port $port"
            )
        )
    }

    private fun sendViaBluetooth(
        files: List<File>,
        device: DiscoveredDevice
    ): Flow<TransferProgressInfo> = flow {
        // Standard Android Bluetooth OPP — works with any device
        bluetoothTransferManager.sendViaSystemBluetooth(files)
        val totalBytes = files.sumOf { it.length() }
        emit(
            TransferProgressInfo(
                bytesTransferred = totalBytes,
                totalBytes = totalBytes,
                totalFiles = files.size,
                currentFileIndex = files.size,
                currentFileName = ""
            )
        )
    }.flowOn(Dispatchers.IO)

    /**
     * Send files directly to another Haron device via TCP socket.
     * Connects to the receiver's ReceiveFileManager TCP port (discovered via NSD).
     */
    private fun sendViaTcp(
        files: List<File>,
        device: DiscoveredDevice
    ): Flow<TransferProgressInfo> = flow {
        EcosystemLogger.d(HaronConstants.TAG, "TCP direct send to ${device.address}:${device.port}")

        val socket = connectWithPortScan(device.address, device.port)

        socket.use { sock ->
            val output = DataOutputStream(sock.getOutputStream())
            val reader = BufferedReader(InputStreamReader(sock.getInputStream()))

            val totalBytes = files.sumOf { it.length() }
            var transferred = 0L
            val startTime = System.currentTimeMillis()

            // Send REQUEST
            val request = TransferProtocolNegotiator.buildRequest(files, "TCP")
            output.writeUTF(request)
            output.flush()

            // Wait for ACCEPT
            val response = reader.readLine() ?: throw IOException("No response from receiver")
            val type = TransferProtocolNegotiator.parseType(response)
            if (type == TransferProtocolNegotiator.TYPE_DECLINE) {
                val reason = TransferProtocolNegotiator.parseDecline(response)
                throw IOException("Transfer declined: $reason")
            }

            // Send files
            files.forEachIndexed { index, file ->
                val header = TransferProtocolNegotiator.buildFileHeader(file.name, file.length(), index)
                output.writeUTF(header)
                output.flush()

                file.inputStream().use { input ->
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
                                etaSeconds = eta
                            )
                        )
                    }
                }
                output.flush()
            }

            // Send COMPLETE
            output.writeUTF(TransferProtocolNegotiator.buildComplete())
            output.flush()

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
        }
    }.flowOn(Dispatchers.IO)

    /** Find WiFi Network for socket binding (avoids mobile data routing) */
    private fun findWifiNetwork(): android.net.Network? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? android.net.ConnectivityManager ?: return null
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) {
                return network
            }
        }
        return null
    }

    /** Try discovered port first, then scan 8080-8090 on ECONNREFUSED */
    private fun connectWithPortScan(address: String, preferredPort: Int): Socket {
        val wifiNetwork = findWifiNetwork()
        val portsToTry = buildList {
            add(preferredPort)
            for (p in HaronConstants.TRANSFER_PORT_START..HaronConstants.TRANSFER_PORT_END) {
                if (p != preferredPort) add(p)
            }
        }
        var lastError: Exception? = null
        for (tryPort in portsToTry) {
            try {
                val sock = Socket()
                // Bind to WiFi to avoid routing through mobile data
                wifiNetwork?.bindSocket(sock)
                sock.connect(InetSocketAddress(address, tryPort), 3_000)
                if (tryPort != preferredPort) {
                    EcosystemLogger.d(HaronConstants.TAG, "Port scan: connected on $tryPort (NSD reported $preferredPort)")
                }
                return sock
            } catch (e: java.net.ConnectException) {
                lastError = e
            } catch (e: java.net.SocketTimeoutException) {
                lastError = e
            }
        }
        throw lastError ?: IOException("Cannot connect to $address")
    }

    private fun mergeDevices(
        wifi: List<DiscoveredDevice>,
        nsd: List<DiscoveredDevice>
    ): List<DiscoveredDevice> {
        val merged = mutableMapOf<String, DiscoveredDevice>()

        nsd.forEach { device ->
            merged[device.address] = device
        }

        wifi.forEach { device ->
            val existing = merged[device.address]
            if (existing != null) {
                merged[device.address] = existing.copy(
                    supportedProtocols = existing.supportedProtocols + device.supportedProtocols
                )
            } else {
                merged[device.id] = device
            }
        }

        return merged.values.toList()
    }
}
