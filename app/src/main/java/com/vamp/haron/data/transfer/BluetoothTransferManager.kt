package com.vamp.haron.data.transfer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.domain.model.DiscoveredDevice
import com.vamp.haron.domain.model.TransferProtocol
import com.vamp.haron.domain.model.TransferProgressInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.RandomAccessFile
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothTransferManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var receiver: BroadcastReceiver? = null
    private var btServerSocket: BluetoothServerSocket? = null
    @Volatile
    private var btListening = false

    private val bluetoothAdapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    val isAvailable: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun discoverDevices(): Flow<List<DiscoveredDevice>> = callbackFlow {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val found = mutableMapOf<String, DiscoveredDevice>()

        // Add already-bonded (paired) devices first
        adapter.bondedDevices?.forEach { device ->
            val discovered = device.toDiscoveredDevice()
            found[discovered.id] = discovered
        }

        // Emit bonded devices immediately so combine() doesn't block
        trySend(found.values.toList())
        EcosystemLogger.d(HaronConstants.TAG, "Bluetooth bonded devices: ${found.size}")

        val btReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        device?.let {
                            val discovered = it.toDiscoveredDevice()
                            found[discovered.id] = discovered
                            trySend(found.values.toList())
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        EcosystemLogger.d(HaronConstants.TAG, "Bluetooth discovery finished, found: ${found.size}")
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(btReceiver, filter)
        receiver = btReceiver

        adapter.startDiscovery()
        EcosystemLogger.d(HaronConstants.TAG, "Bluetooth discovery started")

        awaitClose {
            stopDiscovery()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        bluetoothAdapter?.cancelDiscovery()
        try {
            receiver?.let { context.unregisterReceiver(it) }
        } catch (_: Exception) { }
        receiver = null
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.toDiscoveredDevice(): DiscoveredDevice {
        return DiscoveredDevice(
            id = address,
            name = name ?: address,
            address = address,
            supportedProtocols = setOf(TransferProtocol.BLUETOOTH),
            isHaron = false
        )
    }

    /**
     * Send files over Bluetooth RFCOMM socket.
     * ~2 MB/s typical speed.
     */
    @SuppressLint("MissingPermission")
    fun sendFiles(
        deviceAddress: String,
        files: List<File>
    ): Flow<TransferProgressInfo> = flow {
        val adapter = bluetoothAdapter ?: throw Exception("Bluetooth not available")
        adapter.cancelDiscovery()

        val device = adapter.getRemoteDevice(deviceAddress)
        val socket = try {
            val s = device.createRfcommSocketToServiceRecord(HARON_UUID)
            s.connect()
            s
        } catch (e: java.io.IOException) {
            EcosystemLogger.w(HaronConstants.TAG, "Standard RFCOMM failed, trying fallback: ${e.message}")
            // Reflection fallback for Samsung/LG/Huawei devices
            try {
                val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                val fallback = method.invoke(device, 1) as android.bluetooth.BluetoothSocket
                fallback.connect()
                fallback
            } catch (e2: Exception) {
                throw java.io.IOException(
                    "Bluetooth connection failed. Make sure the target device has Haron in receive mode. (${e.message})",
                    e2
                )
            }
        }

        try {
            val output = DataOutputStream(socket.outputStream)

            val totalBytes = files.sumOf { it.length() }
            var transferred = 0L
            val startTime = System.currentTimeMillis()

            // Send request
            val request = TransferProtocolNegotiator.buildRequest(files, "BLUETOOTH")
            output.writeUTF(request)
            output.flush()

            // Wait for ACCEPT/DECLINE with timeout (handshake — same as TCP path)
            val reader = BufferedReader(InputStreamReader(socket.inputStream))
            val waitStart = System.currentTimeMillis()
            while (socket.inputStream.available() == 0) {
                if (System.currentTimeMillis() - waitStart > 10_000) {
                    throw java.io.IOException(
                        "No response from receiver (timeout). Make sure the target device has Haron open in receive mode."
                    )
                }
                Thread.sleep(200)
            }
            val response = reader.readLine()
                ?: throw java.io.IOException(
                    "No response from receiver. Make sure the target device has Haron open in receive mode."
                )
            val responseType = TransferProtocolNegotiator.parseType(response)
            if (responseType == TransferProtocolNegotiator.TYPE_DECLINE) {
                val reason = TransferProtocolNegotiator.parseDecline(response)
                throw java.io.IOException("Transfer declined: $reason")
            }

            EcosystemLogger.d(HaronConstants.TAG, "BT handshake OK, sending ${files.size} files")

            // Send files sequentially
            files.forEachIndexed { index, file ->
                val header = TransferProtocolNegotiator.buildFileHeader(file.name, file.length(), index)
                output.writeUTF(header)
                output.flush()

                file.inputStream().use { input ->
                    val buffer = ByteArray(4096) // Smaller buffer for BT
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
        } finally {
            try { socket.close() } catch (_: Exception) { }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Start listening for incoming Bluetooth RFCOMM connections.
     * Returns a Flow that emits incoming transfer requests.
     */
    @SuppressLint("MissingPermission")
    fun startListening(): Flow<IncomingTransferRequest> = callbackFlow {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            close()
            return@callbackFlow
        }

        val serverSocket = adapter.listenUsingRfcommWithServiceRecord("Haron Transfer", HARON_UUID)
        btServerSocket = serverSocket
        btListening = true

        EcosystemLogger.d(HaronConstants.TAG, "Bluetooth RFCOMM server started")

        while (btListening && isActive) {
            try {
                // accept() blocks, but we set btListening=false and close serverSocket to break out
                val socket = serverSocket.accept(2000) ?: continue

                EcosystemLogger.d(HaronConstants.TAG, "BT incoming connection from ${socket.remoteDevice?.address}")

                val input = DataInputStream(socket.inputStream)
                val requestJson = input.readUTF()
                val type = TransferProtocolNegotiator.parseType(requestJson)

                if (type == TransferProtocolNegotiator.TYPE_REQUEST) {
                    val requestData = TransferProtocolNegotiator.parseRequest(requestJson)
                    val deviceName = socket.remoteDevice?.name ?: socket.remoteDevice?.address ?: "Unknown"
                    // Wrap BluetoothSocket into a standard Socket-compatible IncomingTransferRequest
                    // For BT we create a wrapper using piped streams — simpler: just handle inline
                    // Actually we need a different approach for BT receive since it uses BluetoothSocket
                    // For now, receive directly here
                    val request = IncomingTransferRequest(
                        deviceName = deviceName,
                        files = requestData.files,
                        protocol = requestData.protocol,
                        socket = java.net.Socket(), // Placeholder — BT uses its own socket
                        isBluetooth = true
                    )
                    trySend(request)

                    // Store BT socket for accept/decline
                    pendingBtSocket = socket
                    pendingBtInput = input
                } else {
                    socket.close()
                }
            } catch (_: java.io.IOException) {
                if (!btListening) break
                // Timeout or transient error, continue
            }
        }

        close()
        awaitClose {
            stopListening()
        }
    }.flowOn(Dispatchers.IO)

    @Volatile
    private var pendingBtSocket: android.bluetooth.BluetoothSocket? = null
    private var pendingBtInput: DataInputStream? = null

    /**
     * Accept incoming BT transfer and receive files.
     */
    fun acceptBtTransfer(files: List<TransferProtocolNegotiator.FileInfo>): Flow<TransferProgressInfo> = flow {
        val socket = pendingBtSocket ?: throw IllegalStateException("No pending BT request")
        val input = pendingBtInput ?: throw IllegalStateException("No pending BT input")
        pendingBtSocket = null
        pendingBtInput = null

        try {
            val output = PrintWriter(socket.outputStream, true)
            output.println(TransferProtocolNegotiator.buildAccept(0))

            val totalBytes = files.sumOf { it.size }
            var transferred = 0L
            val startTime = System.currentTimeMillis()
            val saveDir = getReceiveDir()

            var fileIndex = 0
            while (fileIndex < files.size) {
                val headerJson = input.readUTF()
                val headerType = TransferProtocolNegotiator.parseType(headerJson)

                if (headerType == TransferProtocolNegotiator.TYPE_COMPLETE) break
                if (headerType != TransferProtocolNegotiator.TYPE_FILE_HEADER) continue

                val header = TransferProtocolNegotiator.parseFileHeader(headerJson)
                val destFile = getUniqueFile(saveDir, header.name)
                val offset = header.offset
                var remaining = header.size - offset

                if (offset > 0 && destFile.exists() && destFile.length() == offset) {
                    RandomAccessFile(destFile, "rw").use { raf ->
                        raf.seek(offset)
                        val buffer = ByteArray(4096)
                        while (remaining > 0) {
                            val toRead = minOf(remaining.toInt(), buffer.size)
                            val read = input.read(buffer, 0, toRead)
                            if (read == -1) break
                            raf.write(buffer, 0, read)
                            remaining -= read
                            transferred += read
                            emitProgress(transferred, totalBytes, fileIndex, files.size, header.name, startTime)?.let { emit(it) }
                        }
                    }
                } else {
                    destFile.outputStream().use { fos ->
                        val buffer = ByteArray(4096)
                        while (remaining > 0) {
                            val toRead = minOf(remaining.toInt(), buffer.size)
                            val read = input.read(buffer, 0, toRead)
                            if (read == -1) break
                            fos.write(buffer, 0, read)
                            remaining -= read
                            transferred += read
                            emitProgress(transferred, totalBytes, fileIndex, files.size, header.name, startTime)?.let { emit(it) }
                        }
                    }
                }

                EcosystemLogger.d(HaronConstants.TAG, "BT received file: ${header.name} → ${destFile.absolutePath}")
                fileIndex++
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
        } finally {
            try { socket.close() } catch (_: Exception) { }
        }
    }.flowOn(Dispatchers.IO)

    fun declineBtTransfer(reason: String = "User declined") {
        val socket = pendingBtSocket ?: return
        pendingBtSocket = null
        pendingBtInput = null
        try {
            val output = PrintWriter(socket.outputStream, true)
            output.println(TransferProtocolNegotiator.buildDecline(reason))
            socket.close()
        } catch (_: Exception) { }
    }

    @SuppressLint("MissingPermission")
    fun stopListening() {
        btListening = false
        try {
            btServerSocket?.close()
        } catch (_: Exception) { }
        btServerSocket = null
        pendingBtSocket?.let { try { it.close() } catch (_: Exception) { } }
        pendingBtSocket = null
        pendingBtInput = null
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

    /**
     * Send files via standard Android Bluetooth OPP (Object Push Profile).
     * Works with ANY Bluetooth device — not limited to Haron.
     * Launches system Bluetooth share UI.
     */
    @SuppressLint("MissingPermission")
    fun sendViaSystemBluetooth(files: List<File>) {
        bluetoothAdapter?.cancelDiscovery()

        val uris = ArrayList(files.map { file ->
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        })

        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = getMimeType(files[0].name)
                putExtra(Intent.EXTRA_STREAM, uris[0])
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }

        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            // Direct to system Bluetooth app
            intent.setPackage("com.android.bluetooth")
            context.startActivity(intent)
        } catch (e: Exception) {
            EcosystemLogger.w(HaronConstants.TAG, "System BT app not found, using chooser: ${e.message}")
            // Fallback: share chooser
            intent.setPackage(null)
            intent.component = null
            context.startActivity(Intent.createChooser(intent, "Bluetooth").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    private fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }

    companion object {
        val HARON_UUID: UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    }
}
