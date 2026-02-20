package com.vamp.haron.domain.usecase

import com.vamp.haron.domain.model.ConflictResolution
import com.vamp.haron.domain.repository.FileRepository
import javax.inject.Inject

class MoveFilesUseCase @Inject constructor(
    private val repository: FileRepository
) {
    suspend operator fun invoke(
        sourcePaths: List<String>,
        destinationDir: String,
        conflictResolution: ConflictResolution = ConflictResolution.RENAME
    ): Result<Int> {
        if (sourcePaths.isEmpty()) return Result.success(0)
        return repository.moveFiles(sourcePaths, destinationDir, conflictResolution)
    }
}
