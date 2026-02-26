package com.vamp.haron.presentation.explorer.state

import androidx.compose.ui.geometry.Offset
import com.vamp.haron.data.network.NetworkDevice

sealed interface QuickSendState {
    data object Idle : QuickSendState

    /** Sender is dragging a file towards device circles */
    data class DraggingToDevice(
        val filePath: String,
        val fileName: String,
        /** Initial long-press position — circles are anchored here */
        val anchorOffset: Offset,
        /** Current finger position — file preview follows this */
        val dragOffset: Offset,
        val haronDevices: List<NetworkDevice>
    ) : QuickSendState

    /** File is being sent */
    data class Sending(val deviceName: String) : QuickSendState
}
