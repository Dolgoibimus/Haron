package com.vamp.haron.domain.repository

import com.vamp.haron.domain.model.CastDevice
import com.vamp.haron.domain.model.RemoteInputEvent
import kotlinx.coroutines.flow.Flow

interface CastRepository {
    fun discoverCastDevices(): Flow<List<CastDevice>>
    suspend fun castMedia(device: CastDevice, mediaUrl: String, mimeType: String)
    fun sendRemoteInput(event: RemoteInputEvent)
    fun disconnect()
    fun isConnected(): Boolean
}
