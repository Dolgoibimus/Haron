package com.vamp.haron.domain.usecase

import com.vamp.haron.domain.repository.TrashRepository
import javax.inject.Inject

class RestoreFromTrashUseCase @Inject constructor(
    private val repository: TrashRepository
) {
    suspend operator fun invoke(ids: List<String>): Result<Int> {
        if (ids.isEmpty()) return Result.success(0)
        return repository.restoreFromTrash(ids)
    }
}
