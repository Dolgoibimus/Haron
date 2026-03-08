package com.vamp.haron.domain.usecase

import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.domain.repository.TrashRepository
import javax.inject.Inject

class RestoreFromTrashUseCase @Inject constructor(
    private val repository: TrashRepository
) {
    suspend operator fun invoke(ids: List<String>): Result<Int> {
        if (ids.isEmpty()) return Result.success(0)
        EcosystemLogger.d(HaronConstants.TAG, "RestoreFromTrashUseCase: restoring ${ids.size} items")
        return repository.restoreFromTrash(ids).also { result ->
            result.onSuccess { count ->
                EcosystemLogger.d(HaronConstants.TAG, "RestoreFromTrashUseCase: restored $count items")
            }
            result.onFailure { e ->
                EcosystemLogger.e(HaronConstants.TAG, "RestoreFromTrashUseCase: restore failed — ${e.message}")
            }
        }
    }
}
