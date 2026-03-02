package com.vamp.haron.data.repository

import com.vamp.haron.data.cast.DlnaManager
import com.vamp.haron.data.cast.GoogleCastManager
import com.vamp.haron.data.cast.MiracastManager
import com.vamp.haron.domain.model.CastDevice
import com.vamp.haron.domain.model.CastType
import com.vamp.haron.domain.model.RemoteInputEvent
import com.vamp.haron.domain.repository.CastRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CastRepositoryImpl @Inject constructor(
    private val googleCastManager: GoogleCastManager,
    private val miracastManager: MiracastManager,
    private val dlnaManager: DlnaManager
) : CastRepository {

    override fun discoverCastDevices(): Flow<List<CastDevice>> {
        return combine(
            miracastManager.discoverDisplays(),
            dlnaManager.discoverDevices()
        ) { miracast, dlna -> miracast + dlna }
    }

    override suspend fun castMedia(device: CastDevice, mediaUrl: String, mimeType: String) {
        when (device.type) {
            CastType.CHROMECAST -> {
                googleCastManager.castMedia(mediaUrl, mimeType, title = "")
            }
            CastType.MIRACAST -> {
                miracastManager.selectRoute(device.id)
            }
            CastType.DLNA -> {
                dlnaManager.castMedia(device.id, mediaUrl, mimeType, "")
            }
        }
    }

    override fun sendRemoteInput(event: RemoteInputEvent) {
        if (googleCastManager.isConnected.value) {
            googleCastManager.sendRemoteInput(event)
        }
        if (dlnaManager.isConnected.value) {
            dlnaManager.sendRemoteInput(event)
        }
    }

    override fun disconnect() {
        googleCastManager.disconnect()
        dlnaManager.disconnect()
    }

    override fun isConnected(): Boolean {
        return googleCastManager.isConnected.value || dlnaManager.isConnected.value
    }
}
