package com.vamp.haron.domain.repository

import com.vamp.haron.data.transfer.IncomingTransferRequest
import com.vamp.haron.data.transfer.QuickSendPending
import com.vamp.haron.domain.model.DiscoveredDevice
import com.vamp.haron.domain.model.TransferProgressInfo
import com.vamp.haron.domain.model.TransferProtocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import java.io.File

interface TransferRepository {
    fun discoverDevices(): Flow<List<DiscoveredDevice>>
    fun stopDiscovery()
    suspend fun sendFiles(
        files: List<File>,
        device: DiscoveredDevice,
        protocol: TransferProtocol
    ): Flow<TransferProgressInfo>
    fun cancelTransfer()
    fun getLocalIpAddress(): String?

    // Receiving
    fun startReceiving(): Flow<IncomingTransferRequest>
    fun stopReceiving()
    fun acceptTransfer(): Flow<TransferProgressInfo>
    fun declineTransfer()
    fun getReceivePort(): Int

    /** Shared incoming requests — works even when ExplorerVM holds the listener */
    val incomingRequests: SharedFlow<IncomingTransferRequest>
    /** Emits file count after each completed receive */
    val receiveCompleted: SharedFlow<Int>
    /** Emits sender display name when a trusted friend sends files */
    val friendReceived: SharedFlow<String>
    /** Emits when untrusted Quick Send needs confirmation */
    val quickSendPending: SharedFlow<QuickSendPending>
}
