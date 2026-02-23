package com.vamp.haron.domain.usecase

import com.vamp.haron.domain.repository.IndexProgress
import com.vamp.haron.domain.repository.SearchRepository
import javax.inject.Inject

class IndexFilesUseCase @Inject constructor(
    private val searchRepository: SearchRepository
) {
    suspend operator fun invoke(onProgress: (IndexProgress) -> Unit = {}) {
        searchRepository.indexAllFiles(onProgress)
    }
}
