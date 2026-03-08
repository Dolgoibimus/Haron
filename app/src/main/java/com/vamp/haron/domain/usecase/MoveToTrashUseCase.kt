package com.vamp.haron.domain.usecase

import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.data.datastore.HaronPreferences
import com.vamp.haron.domain.repository.TrashRepository
import javax.inject.Inject

data class TrashMoveResult(
    val movedCount: Int,
    val evictedCount: Int = 0
)

class MoveToTrashUseCase @Inject constructor(
    private val repository: TrashRepository,
    private val preferences: HaronPreferences
) {
    suspend operator fun invoke(paths: List<String>): Result<TrashMoveResult> {
        if (paths.isEmpty()) return Result.success(TrashMoveResult(0))
        EcosystemLogger.d(HaronConstants.TAG, "MoveToTrashUseCase: moving ${paths.size} items to trash")
        val result = repository.moveToTrash(paths)
        // Auto-evict oldest entries if trash exceeds size limit
        var evictedCount = 0
        val maxMb = preferences.trashMaxSizeMb
        if (maxMb > 0) {
            evictedCount = repository.evictToFitSize(maxMb.toLong() * 1024 * 1024)
        }
        return result.map { movedCount ->
            EcosystemLogger.d(HaronConstants.TAG, "MoveToTrashUseCase: moved=$movedCount, evicted=$evictedCount")
            TrashMoveResult(movedCount, evictedCount)
        }.also { r ->
            r.onFailure { e ->
                EcosystemLogger.e(HaronConstants.TAG, "MoveToTrashUseCase: failed — ${e.message}")
            }
        }
    }
}
