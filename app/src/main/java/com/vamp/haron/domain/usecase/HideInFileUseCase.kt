package com.vamp.haron.domain.usecase

import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
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
        EcosystemLogger.d(HaronConstants.TAG, "HideInFileUseCase: hiding payload in carrier, output=$outputPath")
        return try {
            val result = repository.hidePayloadComplete(carrierPath, payloadPath, outputPath)
            when (result) {
                is StegoResult.Hidden -> EcosystemLogger.d(HaronConstants.TAG, "HideInFileUseCase: hide complete, size=${result.payloadSize}")
                is StegoResult.Error -> EcosystemLogger.w(HaronConstants.TAG, "HideInFileUseCase: hide failed — ${result.message}")
                else -> {}
            }
            result
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "HideInFileUseCase: error — ${e.message}")
            throw e
        }
    }
}
