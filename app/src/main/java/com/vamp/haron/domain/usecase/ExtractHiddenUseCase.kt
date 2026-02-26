package com.vamp.haron.domain.usecase

import com.vamp.haron.domain.model.StegoResult
import com.vamp.haron.domain.repository.SteganographyRepository
import javax.inject.Inject

class ExtractHiddenUseCase @Inject constructor(
    private val repository: SteganographyRepository
) {
    suspend operator fun invoke(filePath: String, outputDir: String): StegoResult {
        return repository.extractPayload(filePath, outputDir)
    }
}
