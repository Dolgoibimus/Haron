package com.vamp.haron.domain.usecase

import com.vamp.haron.domain.repository.FileRepository
import javax.inject.Inject

class CopyFilesUseCase @Inject constructor(
    private val repository: FileRepository
) {
    suspend operator fun invoke(sourcePaths: List<String>, destinationDir: String): Result<Int> {
        if (sourcePaths.isEmpty()) return Result.success(0)
        return repository.copyFiles(sourcePaths, destinationDir)
    }
}
