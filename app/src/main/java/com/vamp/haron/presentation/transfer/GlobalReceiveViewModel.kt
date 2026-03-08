package com.vamp.haron.presentation.transfer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.data.transfer.QuickSendPending
import com.vamp.haron.domain.repository.TransferRepository
import com.vamp.haron.service.TransferService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Global ViewModel for handling incoming transfer requests from any screen.
 * Lives in MainActivity so it works regardless of current navigation destination.
 */
@HiltViewModel
class GlobalReceiveViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val transferRepository: TransferRepository
) : ViewModel() {

    data class IncomingInfo(
        val deviceName: String,
        val fileCount: Int
    )

    private val _incoming = MutableStateFlow<IncomingInfo?>(null)
    val incoming: StateFlow<IncomingInfo?> = _incoming.asStateFlow()

    private val _receiving = MutableStateFlow(false)
    val receiving: StateFlow<Boolean> = _receiving.asStateFlow()

    /** Pending Quick Send confirmation — socket stays on IO, we just respond */
    private var pendingQuickSend: QuickSendPending? = null

    init {
        // Regular transfer requests (TYPE_REQUEST)
        viewModelScope.launch {
            transferRepository.incomingRequests.collect { request ->
                EcosystemLogger.d(HaronConstants.TAG, "GlobalReceiveVM: incoming request from ${request.deviceName}, files=${request.files.size}")
                pendingQuickSend = null
                _incoming.value = IncomingInfo(
                    deviceName = request.deviceName,
                    fileCount = request.files.size
                )
            }
        }

        // Quick Send from untrusted devices — socket stays on IO thread
        viewModelScope.launch {
            transferRepository.quickSendPending.collect { pending ->
                EcosystemLogger.d(HaronConstants.TAG, "GlobalReceiveVM: quickSend pending from ${pending.senderName}, files=${pending.fileCount}")
                pendingQuickSend = pending
                _incoming.value = IncomingInfo(
                    deviceName = pending.senderName,
                    fileCount = pending.fileCount
                )
            }
        }
    }

    fun accept() {
        val qs = pendingQuickSend
        if (qs != null) {
            EcosystemLogger.d(HaronConstants.TAG, "GlobalReceiveVM: accept quickSend from ${qs.senderName}")
            // Quick Send: just complete the deferred — file receive happens on IO thread
            qs.response.complete(true)
            pendingQuickSend = null
            _incoming.value = null
            return
        }

        EcosystemLogger.d(HaronConstants.TAG, "GlobalReceiveVM: accept regular transfer")
        // Regular transfer: accept via flow
        _incoming.value = null
        _receiving.value = true
        TransferService.startServer(appContext)

        viewModelScope.launch {
            transferRepository.acceptTransfer()
                .catch { e ->
                    EcosystemLogger.e(HaronConstants.TAG, "GlobalReceiveVM: transfer error — ${e.message}")
                    _receiving.value = false
                    TransferService.stopServer(appContext)
                }
                .collect { progress ->
                    TransferService.updateProgress(progress)
                    if (progress.bytesTransferred >= progress.totalBytes && progress.totalBytes > 0) {
                        _receiving.value = false
                        TransferService.stopServer(appContext)
                    }
                }
        }
    }

    fun decline() {
        val qs = pendingQuickSend
        if (qs != null) {
            EcosystemLogger.d(HaronConstants.TAG, "GlobalReceiveVM: decline quickSend from ${qs.senderName}")
            // Quick Send: complete with false — decline happens on IO thread
            qs.response.complete(false)
            pendingQuickSend = null
            _incoming.value = null
            return
        }

        EcosystemLogger.d(HaronConstants.TAG, "GlobalReceiveVM: decline regular transfer")
        _incoming.value = null
        transferRepository.declineTransfer()
    }
}
