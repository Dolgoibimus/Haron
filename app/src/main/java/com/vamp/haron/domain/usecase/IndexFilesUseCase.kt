package com.vamp.haron.domain.usecase

import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.domain.repository.IndexProgress
import com.vamp.haron.domain.repository.SearchRepository
import javax.inject.Inject

class IndexFilesUseCase @Inject constructor(
    private val searchRepository: SearchRepository
) {
    suspend operator fun invoke(onProgress: (IndexProgress) -> Unit = {}) {
        EcosystemLogger.d(HaronConstants.TAG, "IndexFilesUseCase: starting file indexing")
        try {
            var lastProgress: IndexProgress? = null
            searchRepository.indexAllFiles { progress ->
                lastProgress = progress
                onProgress(progress)
            }
            EcosystemLogger.d(HaronConstants.TAG, "IndexFilesUseCase: indexing complete, processed=${lastProgress?.processedFiles ?: 0}")
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "IndexFilesUseCase: indexing failed — ${e.message}")
            throw e
        }
    }
}
