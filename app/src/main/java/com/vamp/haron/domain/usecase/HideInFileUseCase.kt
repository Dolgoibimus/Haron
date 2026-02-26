package com.vamp.haron.domain.usecase

import com.vamp.haron.domain.model.StegoResult
import com.vamp.haron.domain.repository.SteganographyRepository
import javax.inject.Inject

class HideInFileUseCase @Inject constructor(
    private val repository: SteganographyRepository
) {
    suspend operator fun invoke(
        carrierPath: String,
        payloadPath: String,
        outputPath: String
    ): StegoResult {
        return repository.hidePayloadComplete(carrierPath, payloadPath, outputPath)
    }
}
