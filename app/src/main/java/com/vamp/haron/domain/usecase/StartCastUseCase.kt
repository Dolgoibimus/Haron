package com.vamp.haron.domain.usecase

import com.vamp.haron.domain.model.CastDevice
import com.vamp.haron.domain.repository.CastRepository
import javax.inject.Inject

class StartCastUseCase @Inject constructor(
    private val castRepository: CastRepository
) {
    suspend operator fun invoke(device: CastDevice, mediaUrl: String, mimeType: String) {
        castRepository.castMedia(device, mediaUrl, mimeType)
    }

    fun disconnect() {
        castRepository.disconnect()
    }
}
