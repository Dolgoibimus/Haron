package com.vamp.haron.domain.usecase

import com.vamp.haron.domain.repository.FileRepository
import javax.inject.Inject

class DeleteFilesUseCase @Inject constructor(
    private val repository: FileRepository
) {
    suspend operator fun invoke(paths: List<String>): Result<Int> {
        if (paths.isEmpty()) return Result.success(0)
        return repository.deleteFiles(paths)
    }
}
