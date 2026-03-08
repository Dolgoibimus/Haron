package com.vamp.haron.domain.usecase

import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.data.db.entity.FileIndexEntity
import com.vamp.haron.domain.repository.SearchFilter
import com.vamp.haron.domain.repository.SearchRepository
import javax.inject.Inject

class SearchFilesUseCase @Inject constructor(
    private val searchRepository: SearchRepository
) {
    suspend operator fun invoke(filter: SearchFilter): List<FileIndexEntity> {
        EcosystemLogger.d(HaronConstants.TAG, "SearchFilesUseCase: query=\"${filter.query}\", category=${filter.category}")
        return try {
            val results = searchRepository.searchFiles(filter)
            EcosystemLogger.d(HaronConstants.TAG, "SearchFilesUseCase: found ${results.size} results")
            results
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "SearchFilesUseCase: search failed — ${e.message}")
            throw e
        }
    }
}
