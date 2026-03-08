package com.vamp.haron.domain.usecase

import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.domain.model.StegoResult
import com.vamp.haron.domain.repository.SteganographyRepository
import javax.inject.Inject

class ExtractHiddenUseCase @Inject constructor(
    private val repository: SteganographyRepository
) {
    suspend operator fun invoke(filePath: String, outputDir: String): StegoResult {
        EcosystemLogger.d(HaronConstants.TAG, "ExtractHiddenUseCase: extracting from $filePath to $outputDir")
        return try {
            val result = repository.extractPayload(filePath, outputDir)
            when (result) {
                is StegoResult.Extracted -> EcosystemLogger.d(HaronConstants.TAG, "ExtractHiddenUseCase: extracted ${result.payloadName}")
                is StegoResult.Error -> EcosystemLogger.w(HaronConstants.TAG, "ExtractHiddenUseCase: extraction failed — ${result.message}")
                else -> {}
            }
            result
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "ExtractHiddenUseCase: error — ${e.message}")
            throw e
        }
    }
}
