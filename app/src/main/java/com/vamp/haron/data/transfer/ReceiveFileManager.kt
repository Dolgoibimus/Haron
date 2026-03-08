package com.vamp.haron.data.transfer

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.domain.model.TransferProgressInfo
import com.vamp.haron.data.datastore.HaronPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.DataInputStream
import java.io.File
import java.io.PrintWriter
import java.io.RandomAccessFile
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Incoming transfer request — emitted when a sender connects.
 */
data class DropRequestInfo(
    val deviceName: String,
    val deviceAddress: String,
    val devicePort: Int
)

data class IncomingTransferRequest(
    val deviceName: String,
    val files: List<TransferProtocolNegotiator.FileInfo>,
    val protocol: String,
    internal val socket: Socket,
    val isBluetooth: Boolean = false
)

/**
 * Pending Quick Send from untrusted device — UI shows dialog, response completes the deferred.
 */
data class QuickSendPending(
    val senderName: String,
    val fileCount: Int,
    val response: CompletableDeferred<Boolean> = CompletableDeferred()
)

@Singleton
class ReceiveFileManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val nsdDiscoveryManager: NsdDiscoveryManager,
    private val preferences: HaronPreferences,
    private val bluetoothTransferManager: BluetoothTransferManager
) {
    /** Own CoroutineScope — server lives independently of any Activity/ViewModel */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var serverSocket: ServerSocket? = null
    @Volatile
    private var listening = false
    private var serverJob: Job? = null
    private var btJob: Job? = null

    private var pendingRequest: IncomingTransferRequest? = null

    private val _dropRequests = MutableSharedFlow<DropRequestInfo>(extraBufferCapacity = 1)
    val dropRequests: SharedFlow<DropRequestInfo> = _dropRequests.asSharedFlow()

    private val _quickReceiveCompleted = MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** Emits the directory path where files were saved after a Quick Send receive */
    val quickReceiveCompleted: SharedFlow<String> = _quickReceiveCompleted.asSharedFlow()

    /** Shared incoming requests — collected by TransferViewModel */
    private val _incomingRequests = MutableSharedFlow<IncomingTransferRequest>(extraBufferCapacity = 1)
    val incomingRequests: SharedFlow<IncomingTransferRequest> = _incomingRequests.asSharedFlow()

    /** Emits file count after each completed receive (quick or manual) for today's counter */
    private val _receiveCompleted = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val receiveCompleted: SharedFlow<Int> = _receiveCompleted.asSharedFlow()

    /** Emits sender name only when a trusted (friend) device sends files — for the overlay circle */
    private val _friendReceived = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val friendReceived: SharedFlow<String> = _friendReceived.asSharedFlow()

    /** Emits when untrusted Quick Send needs confirmation — UI shows dialog */
    private val _quickSendPending = MutableSharedFlow<QuickSendPending>(extraBufferCapacity = 1)
    val quickSendPending: SharedFlow<QuickSendPending> = _quickSendPending.asSharedFlow()

    /** Emits progress during Quick Send receive — collected by ExplorerViewModel */
    private val _quickReceiveProgress = MutableSharedFlow<TransferProgressInfo?>(extraBufferCapacity = 64)
    val quickReceiveProgress: SharedFlow<TransferProgressInfo?> = _quickReceiveProgress.asSharedFlow()

    val actualPort: Int get() = serverSocket?.localPort ?: 0

    /**
     * Ensure the TCP server is running. Idempotent — safe to call multiple times.
     * Server runs in its own CoroutineScope, independent of caller lifecycle.
     */
    fun ensureListening() {
        if (listening && serverJob?.isActive == true && serverSocket != null && !serverSocket!!.isClosed) {
            EcosystemLogger.d(HaronConstants.TAG, "ReceiveFileManager already listening on port ${serverSocket!!.localPort}")
            return
        }

        // Clean up any stale state
        stopListening()

        val server = findAvailablePort()
        serverSocket = server
        listening = true

        // Register NSD so other devices can find us
        nsdDiscoveryManager.registerService(server.localPort)
        EcosystemLogger.d(HaronConstants.TAG, "ReceiveFileManager listening on port ${server.localPort}")

        serverJob = scope.launch {
            while (listening && isActive) {
                try {
                    server.soTimeout = 2000 // Check cancellation every 2s
                    val socket = try {
                        server.accept()
                    } catch (_: java.net.SocketTimeoutException) {
                        continue
                    }

                    EcosystemLogger.d(HaronConstants.TAG, "Incoming connection from ${socket.remoteSocketAddress}")

                    // Read the REQUEST message
                    val input = DataInputStream(socket.getInputStream())
                    val requestJson = input.readUTF()
                    val type = TransferProtocolNegotiator.parseType(requestJson)

                    if (type == TransferProtocolNegotiator.TYPE_REQUEST) {
                        val requestData = TransferProtocolNegotiator.parseRequest(requestJson)
                        val deviceName = socket.inetAddress?.hostAddress ?: "Unknown"
                        val request = IncomingTransferRequest(
                            deviceName = deviceName,
                            files = requestData.files,
                            protocol = requestData.protocol,
                            socket = socket
                        )
                        pendingRequest = request
                        _incomingRequests.tryEmit(request)
                    } else if (type == TransferProtocolNegotiator.TYPE_QUICK_SEND) {
                        val senderName = TransferProtocolNegotiator.parseQuickSendSender(requestJson)
                        if (senderName != null && preferences.isDeviceTrusted(senderName)) {
                            // Trusted (friend): auto-accept
                            handleQuickSend(socket, requestJson)
                        } else {
                            // Untrusted: confirm via dialog, but keep socket on IO thread
                            val requestData = TransferProtocolNegotiator.parseRequest(requestJson)
                            val deviceName = senderName ?: socket.inetAddress?.hostAddress ?: "Unknown"
                            val pending = QuickSendPending(
                                senderName = deviceName,
                                fileCount = requestData.files.size
                            )
                            _quickSendPending.tryEmit(pending)
                            // Wait for user decision (30 sec timeout)
                            val accepted = withTimeoutOrNull(30_000L) {
                                pending.response.await()
                            } ?: false
                            if (accepted) {
                                handleQuickSend(socket, requestJson)
                            } else {
                                try {
                                    val output = PrintWriter(socket.getOutputStream(), true)
                                    output.println(TransferProtocolNegotiator.buildDecline("User declined"))
                                } catch (_: Exception) { }
                                socket.close()
                            }
                        }
                    } else if (type == TransferProtocolNegotiator.TYPE_DROP_REQUEST) {
                        val dropData = TransferProtocolNegotiator.parseDropRequest(requestJson)
                        val address = socket.inetAddress?.hostAddress ?: "Unknown"
                        _dropRequests.tryEmit(
                            DropRequestInfo(
                                deviceName = dropData.senderName,
                                deviceAddress = address,
                                devicePort = dropData.senderPort
                            )
                        )
                        EcosystemLogger.d(HaronConstants.TAG, "DROP_REQUEST from ${dropData.senderName} ($address:${dropData.senderPort})")
                        socket.close()
                    } else {
                        socket.close()
                    }
                } catch (_: SocketException) {
                    break // Server socket closed
                } catch (e: Exception) {
                    EcosystemLogger.e(HaronConstants.TAG, "ReceiveFileManager error: ${e.message}")
                }
            }
            EcosystemLogger.d(HaronConstants.TAG, "ReceiveFileManager server loop ended")
        }

        // Start Bluetooth RFCOMM listener alongside TCP
        btJob = scope.launch {
            try {
                bluetoothTransferManager.startListening().collect { btRequest ->
                    pendingRequest = btRequest
                    _incomingRequests.tryEmit(btRequest)
                }
            } catch (e: Exception) {
                if (listening) {
                    EcosystemLogger.e(HaronConstants.TAG, "BT listener error: ${e.message}")
                }
            }
        }
    }

    /**
     * Accept the pending incoming transfer. Returns a Flow of progress.
     */
    fun acceptTransfer(): Flow<TransferProgressInfo> = flow {
        val request = pendingRequest ?: throw IllegalStateException("No pending request")
        pendingRequest = null

        // Bluetooth path — delegate to BluetoothTransferManager
        if (request.isBluetooth) {
            emitAll(bluetoothTransferManager.acceptBtTransfer(request.files))
            _receiveCompleted.tryEmit(request.files.size)
            return@flow
        }

        // TCP path
        val socket = request.socket
        try {
            val output = PrintWriter(socket.getOutputStream(), true)
            val input = DataInputStream(socket.getInputStream())

            // Send ACCEPT
            output.println(TransferProtocolNegotiator.buildAccept(actualPort))

            val totalBytes = request.files.sumOf { it.size }
            var transferred = 0L
            val startTime = System.currentTimeMillis()
            val saveDir = getReceiveDir()
            val savedFiles = mutableListOf<File>()

            // Receive files
            var fileIndex = 0
            while (fileIndex < request.files.size) {
                val headerJson = input.readUTF()
                val headerType = TransferProtocolNegotiator.parseType(headerJson)

                if (headerType == TransferProtocolNegotiator.TYPE_COMPLETE) {
                    break
                }

                if (headerType != TransferProtocolNegotiator.TYPE_FILE_HEADER) {
                    continue
                }

                val header = TransferProtocolNegotiator.parseFileHeader(headerJson)
                val destFile = getUniqueFile(saveDir, header.name)
                val offset = header.offset
                var remaining = header.size - offset

                if (offset > 0 && destFile.exists() && destFile.length() == offset) {
                    // Resume: append to existing partial file
                    RandomAccessFile(destFile, "rw").use { raf ->
                        raf.seek(offset)
                        val buffer = ByteArray(HaronConstants.TRANSFER_BUFFER_SIZE)
                        while (remaining > 0) {
                            val toRead = minOf(remaining.toInt(), buffer.size)
                            val read = input.read(buffer, 0, toRead)
                            if (read == -1) break
                            raf.write(buffer, 0, read)
                            remaining -= read
                            transferred += read
                            emitProgress(transferred, totalBytes, fileIndex, request.files.size, header.name, startTime)?.let { emit(it) }
                        }
                    }
                } else {
                    // Normal receive
                    destFile.outputStream().use { fos ->
                        val buffer = ByteArray(HaronConstants.TRANSFER_BUFFER_SIZE)
                        while (remaining > 0) {
                            val toRead = minOf(remaining.toInt(), buffer.size)
                            val read = input.read(buffer, 0, toRead)
                            if (read == -1) break
                            fos.write(buffer, 0, read)
                            remaining -= read
                            transferred += read
                            emitProgress(transferred, totalBytes, fileIndex, request.files.size, header.name, startTime)?.let { emit(it) }
                        }
                    }
                }

                savedFiles.add(destFile)
                EcosystemLogger.d(HaronConstants.TAG, "Received file: ${header.name} → ${destFile.absolutePath}")
                fileIndex++
            }

            // Final progress
            emit(
                TransferProgressInfo(
                    bytesTransferred = totalBytes,
                    totalBytes = totalBytes,
                    currentFileIndex = request.files.size,
                    totalFiles = request.files.size,
                    currentFileName = "",
                    speedBytesPerSec = 0,
                    etaSeconds = 0
                )
            )
            _receiveCompleted.tryEmit(fileIndex)
            _quickReceiveCompleted.tryEmit(saveDir.absolutePath)
            scanFiles(savedFiles)
        } finally {
            try { socket.close() } catch (_: Exception) { }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Decline the pending incoming transfer.
     */
    fun declineTransfer(reason: String = "User declined") {
        val request = pendingRequest ?: return
        pendingRequest = null
        if (request.isBluetooth) {
            bluetoothTransferManager.declineBtTransfer(reason)
            return
        }
        try {
            val output = PrintWriter(request.socket.getOutputStream(), true)
            output.println(TransferProtocolNegotiator.buildDecline(reason))
            request.socket.close()
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "Decline error: ${e.message}")
        }
    }

    fun stopListening() {
        listening = false
        serverJob?.cancel()
        serverJob = null
        btJob?.cancel()
        btJob = null
        bluetoothTransferManager.stopListening()
        nsdDiscoveryManager.unregisterService()
        try {
            serverSocket?.close()
        } catch (_: Exception) { }
        serverSocket = null
        pendingRequest?.let {
            try { it.socket.close() } catch (_: Exception) { }
        }
        pendingRequest = null
        EcosystemLogger.d(HaronConstants.TAG, "ReceiveFileManager stopped")
    }

    /**
     * Handle QUICK_SEND: auto-accept and receive files immediately.
     * No user confirmation needed — sender initiated quick send.
     */
    private fun handleQuickSend(socket: Socket, requestJson: String) {
        try {
            val senderName = TransferProtocolNegotiator.parseQuickSendSender(requestJson)
            val requestData = TransferProtocolNegotiator.parseRequest(requestJson)
            val output = PrintWriter(socket.getOutputStream(), true)
            val input = DataInputStream(socket.getInputStream())

            // Auto-accept
            output.println(TransferProtocolNegotiator.buildAccept(actualPort))

            val totalBytes = requestData.files.sumOf { it.size }
            val saveDir = getReceiveDir()
            val savedFiles = mutableListOf<File>()
            var transferred = 0L
            val startTime = System.currentTimeMillis()
            var lastProgressTime = 0L

            // Receive files
            var fileIndex = 0
            while (fileIndex < requestData.files.size) {
                val headerJson = input.readUTF()
                val headerType = TransferProtocolNegotiator.parseType(headerJson)

                if (headerType == TransferProtocolNegotiator.TYPE_COMPLETE) break
                if (headerType != TransferProtocolNegotiator.TYPE_FILE_HEADER) continue

                val header = TransferProtocolNegotiator.parseFileHeader(headerJson)
                val destFile = getUniqueFile(saveDir, header.name)
                var remaining = header.size

                destFile.outputStream().use { fos ->
                    val buffer = ByteArray(HaronConstants.TRANSFER_BUFFER_SIZE)
                    while (remaining > 0) {
                        val toRead = minOf(remaining.toInt(), buffer.size)
                        val read = input.read(buffer, 0, toRead)
                        if (read == -1) break
                        fos.write(buffer, 0, read)
                        remaining -= read
                        transferred += read
                        val now = System.currentTimeMillis()
                        if (now - lastProgressTime >= 100 || transferred == totalBytes) {
                            lastProgressTime = now
                            emitProgress(transferred, totalBytes, fileIndex, requestData.files.size, header.name, startTime)?.let {
                                _quickReceiveProgress.tryEmit(it)
                            }
                        }
                    }
                }

                savedFiles.add(destFile)
                EcosystemLogger.d(HaronConstants.TAG, "Quick received: ${header.name} → ${destFile.absolutePath}")
                fileIndex++
            }

            // Signal completion (null = done)
            _quickReceiveProgress.tryEmit(null)

            EcosystemLogger.d(HaronConstants.TAG, "Quick send complete: $fileIndex files received from $senderName")
            _quickReceiveCompleted.tryEmit(saveDir.absolutePath)
            scanFiles(savedFiles)
            _receiveCompleted.tryEmit(fileIndex)
            // Notify overlay only for trusted (friend) devices
            if (senderName != null && preferences.isDeviceTrusted(senderName)) {
                val displayName = preferences.getDeviceAlias(senderName) ?: senderName
                _friendReceived.tryEmit(displayName)
            }
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "handleQuickSend error: ${e.message}")
            _quickReceiveProgress.tryEmit(null)
        } finally {
            try { socket.close() } catch (_: Exception) { }
        }
    }

    private fun scanFiles(files: List<File>) {
        if (files.isEmpty()) return
        val paths = files.map { it.absolutePath }.toTypedArray()
        MediaScannerConnection.scanFile(context, paths, null, null)
    }

    private fun getReceiveDir(): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Haron"
        )
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getUniqueFile(dir: File, name: String): File {
        var file = File(dir, name)
        if (!file.exists()) return file

        val dotIndex = name.lastIndexOf('.')
        val baseName = if (dotIndex > 0) name.substring(0, dotIndex) else name
        val ext = if (dotIndex > 0) name.substring(dotIndex) else ""
        var counter = 1
        while (file.exists()) {
            file = File(dir, "${baseName}_($counter)$ext")
            counter++
        }
        return file
    }

    private fun findAvailablePort(): ServerSocket {
        for (port in HaronConstants.TRANSFER_PORT_START..HaronConstants.TRANSFER_PORT_END) {
            try {
                return ServerSocket(port)
            } catch (_: Exception) { }
        }
        return ServerSocket(0)
    }

    private fun emitProgress(
        transferred: Long,
        totalBytes: Long,
        fileIndex: Int,
        totalFiles: Int,
        fileName: String,
        startTime: Long
    ): TransferProgressInfo? {
        val elapsed = System.currentTimeMillis() - startTime
        val speed = if (elapsed > 0) (transferred * 1000 / elapsed) else 0
        val eta = if (speed > 0) ((totalBytes - transferred) / speed) else 0
        return TransferProgressInfo(
            bytesTransferred = transferred,
            totalBytes = totalBytes,
            currentFileIndex = fileIndex,
            totalFiles = totalFiles,
            currentFileName = fileName,
            speedBytesPerSec = speed,
            etaSeconds = eta
        )
    }
}
