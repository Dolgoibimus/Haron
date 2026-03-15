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
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class WifiDirectManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var manager: WifiP2pManager? = null
    private var p2pChannel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null

    /** P2P connection info — updated when CONNECTION_CHANGED fires */
    private val _p2pInfo = MutableStateFlow<WifiP2pInfo?>(null)

    /** true = we initiated the connect (sender side) */
    @Volatile
    private var isInitiator = false

    /** P2P receive server job (receiver side) */
    private var p2pServerJob: Job? = null
    private var p2pServerSocket: ServerSocket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Incoming P2P sockets — ReceiveFileManager subscribes to this */
    private val _incomingP2pSocket = MutableSharedFlow<Socket>(extraBufferCapacity = 4)
    val incomingP2pSocket: SharedFlow<Socket> = _incomingP2pSocket.asSharedFlow()

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
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        wifiManager.requestConnectionInfo(wifiChannel) { info ->
                            EcosystemLogger.d(
                                HaronConstants.TAG,
                                "P2P CONNECTION_CHANGED: groupFormed=${info?.groupFormed}, " +
                                        "isGroupOwner=${info?.isGroupOwner}, " +
                                        "ownerAddr=${info?.groupOwnerAddress?.hostAddress}, " +
                                        "isInitiator=$isInitiator"
                            )
                            _p2pInfo.value = info
                            // Receiver side: if P2P group formed and we did NOT initiate → start P2P server
                            if (info != null && info.groupFormed && !isInitiator) {
                                startP2pReceiveServer(info)
                            }
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

    /**
     * Connect to a Wi-Fi Direct peer and wait for P2P group to form.
     * Returns [WifiP2pInfo] with real IP addresses for socket communication.
     *
     * @param address MAC address of the peer device
     * @param timeoutMs maximum time to wait for group formation
     * @throws IOException if connect fails or times out
     */
    @SuppressLint("MissingPermission")
    suspend fun connectAndWait(address: String, timeoutMs: Long = 30_000): WifiP2pInfo {
        ensureInitialized()
        val mgr = manager ?: throw IOException("Wi-Fi P2P not available")
        val channel = p2pChannel ?: throw IOException("Wi-Fi P2P channel not initialized")

        isInitiator = true
        _p2pInfo.value = null // Reset — wait for fresh CONNECTION_CHANGED

        EcosystemLogger.d(HaronConstants.TAG, "P2P connectAndWait: connecting to $address")

        // Step 1: initiate connect()
        val connectResult = suspendCancellableCoroutine { cont: CancellableContinuation<Boolean> ->
            val config = WifiP2pConfig().apply {
                deviceAddress = address
                groupOwnerIntent = 0 // Sender prefers to be client (not group owner)
            }
            mgr.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    EcosystemLogger.d(HaronConstants.TAG, "P2P connect() onSuccess (negotiation started)")
                    cont.resume(true)
                }

                override fun onFailure(reason: Int) {
                    EcosystemLogger.e(HaronConstants.TAG, "P2P connect() onFailure: reason=$reason")
                    cont.resume(false)
                }
            })
        }

        if (!connectResult) {
            isInitiator = false
            throw IOException("Wi-Fi Direct connect failed")
        }

        // Step 2: wait for CONNECTION_CHANGED to deliver groupFormed=true
        val info = withTimeoutOrNull(timeoutMs) {
            _p2pInfo
                .filterNotNull()
                .filter { it.groupFormed }
                .first()
        }

        if (info == null) {
            isInitiator = false
            throw IOException("Wi-Fi Direct: P2P group not formed within ${timeoutMs}ms")
        }

        EcosystemLogger.d(
            HaronConstants.TAG,
            "P2P group formed: isGroupOwner=${info.isGroupOwner}, " +
                    "ownerAddr=${info.groupOwnerAddress?.hostAddress}"
        )
        return info
    }

    /**
     * Start a P2P receive server on the receiver side (called when CONNECTION_CHANGED fires
     * and we are NOT the initiator). Group owner listens on ServerSocket; non-owner connects
     * to group owner. Accepted sockets are emitted via [incomingP2pSocket] for ReceiveFileManager.
     */
    private fun startP2pReceiveServer(info: WifiP2pInfo) {
        // Avoid duplicate servers
        if (p2pServerJob?.isActive == true) {
            EcosystemLogger.d(HaronConstants.TAG, "P2P receive server already running")
            return
        }

        p2pServerJob = scope.launch {
            try {
                if (info.isGroupOwner) {
                    // We are group owner → listen for sender
                    EcosystemLogger.d(HaronConstants.TAG, "P2P receiver: starting ServerSocket on port $TRANSFER_PORT (group owner)")
                    val server = ServerSocket(TRANSFER_PORT)
                    p2pServerSocket = server
                    server.soTimeout = 60_000 // Wait up to 60s for sender to connect
                    try {
                        while (isActive) {
                            val socket = try {
                                server.accept()
                            } catch (_: java.net.SocketTimeoutException) {
                                EcosystemLogger.d(HaronConstants.TAG, "P2P ServerSocket accept timeout, stopping")
                                break
                            }
                            EcosystemLogger.d(HaronConstants.TAG, "P2P receiver: accepted connection from ${socket.remoteSocketAddress}")
                            _incomingP2pSocket.tryEmit(socket)
                        }
                    } finally {
                        try { server.close() } catch (_: Exception) { }
                        p2pServerSocket = null
                    }
                } else {
                    // We are NOT group owner → connect to group owner
                    val ownerAddr = info.groupOwnerAddress?.hostAddress
                        ?: throw IOException("P2P group owner address is null")
                    EcosystemLogger.d(HaronConstants.TAG, "P2P receiver: connecting to group owner $ownerAddr:$TRANSFER_PORT")
                    val socket = Socket()
                    socket.connect(InetSocketAddress(ownerAddr, TRANSFER_PORT), 15_000)
                    EcosystemLogger.d(HaronConstants.TAG, "P2P receiver: connected to group owner")
                    _incomingP2pSocket.tryEmit(socket)
                }
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "P2P receive server error: ${e.message}")
            }
        }
    }

    /**
     * Send files through a direct socket connection after Wi-Fi Direct P2P group is formed.
     * Group owner hosts a ServerSocket; client connects to it.
     *
     * @param info P2P connection info from [connectAndWait]
     * @param files list of files to send
     * @param resumeFromByte if > 0, resume transfer from this byte offset
     */
    fun sendFiles(
        info: WifiP2pInfo,
        files: List<File>,
        resumeFromByte: Long = 0L
    ): Flow<TransferProgressInfo> = flow {
        val totalBytes = files.sumOf { it.length() }
        var transferred = resumeFromByte
        val startTime = System.currentTimeMillis()

        val socket: Socket = if (info.isGroupOwner) {
            // We are group owner → listen for receiver to connect
            EcosystemLogger.d(HaronConstants.TAG, "P2P sender (group owner): listening on port $TRANSFER_PORT")
            ServerSocket(TRANSFER_PORT).use { server ->
                server.soTimeout = 30_000
                server.accept()
            }
        } else {
            // We are client → connect to group owner
            val ownerAddr = info.groupOwnerAddress?.hostAddress
                ?: throw IOException("P2P group owner address is null")
            EcosystemLogger.d(HaronConstants.TAG, "P2P sender (client): connecting to $ownerAddr:$TRANSFER_PORT")
            Socket().also {
                it.connect(InetSocketAddress(ownerAddr, TRANSFER_PORT), 15_000)
            }
        }

        EcosystemLogger.d(HaronConstants.TAG, "P2P sender: socket established, sending ${files.size} files ($totalBytes bytes)")

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

            EcosystemLogger.d(HaronConstants.TAG, "P2P sender: transfer accepted, starting file send")

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

    fun disconnect() {
        isInitiator = false
        _p2pInfo.value = null
        p2pServerJob?.cancel()
        p2pServerJob = null
        try { p2pServerSocket?.close() } catch (_: Exception) { }
        p2pServerSocket = null
        manager?.removeGroup(p2pChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                EcosystemLogger.d(HaronConstants.TAG, "P2P group removed")
            }
            override fun onFailure(reason: Int) {
                EcosystemLogger.d(HaronConstants.TAG, "P2P group remove failed: $reason")
            }
        })
    }

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
