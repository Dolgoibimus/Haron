package com.vamp.haron.domain.usecase

import com.vamp.haron.data.db.entity.FileIndexEntity
import com.vamp.haron.domain.repository.SearchFilter
import com.vamp.haron.domain.repository.SearchRepository
import javax.inject.Inject

class SearchFilesUseCase @Inject constructor(
    private val searchRepository: SearchRepository
) {
    suspend operator fun invoke(filter: SearchFilter): List<FileIndexEntity> {
        return searchRepository.searchFiles(filter)
    }
}
