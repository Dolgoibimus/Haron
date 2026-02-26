package com.vamp.haron.domain.repository

import com.vamp.haron.domain.model.StegoDetectResult
import com.vamp.haron.domain.model.StegoProgress
import com.vamp.haron.domain.model.StegoResult
import kotlinx.coroutines.flow.Flow

interface SteganographyRepository {
    fun hidePayload(carrierPath: String, payloadPath: String, outputPath: String): Flow<StegoProgress>
    suspend fun hidePayloadComplete(carrierPath: String, payloadPath: String, outputPath: String): StegoResult
    suspend fun detectHiddenData(filePath: String): StegoDetectResult
    suspend fun extractPayload(filePath: String, outputDir: String): StegoResult
}
