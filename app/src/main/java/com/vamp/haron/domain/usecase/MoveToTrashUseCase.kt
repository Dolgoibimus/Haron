package com.vamp.haron.domain.usecase

import com.vamp.haron.domain.repository.TrashRepository
import javax.inject.Inject

class MoveToTrashUseCase @Inject constructor(
    private val repository: TrashRepository
) {
    suspend operator fun invoke(paths: List<String>): Result<Int> {
        if (paths.isEmpty()) return Result.success(0)
        return repository.moveToTrash(paths)
    }
}
