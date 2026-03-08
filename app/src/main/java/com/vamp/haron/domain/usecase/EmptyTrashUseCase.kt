package com.vamp.haron.domain.usecase

import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.domain.repository.TrashRepository
import javax.inject.Inject

class EmptyTrashUseCase @Inject constructor(
    private val repository: TrashRepository
) {
    suspend operator fun invoke(): Result<Int> {
        EcosystemLogger.d(HaronConstants.TAG, "EmptyTrashUseCase: emptying trash")
        return repository.emptyTrash().also { result ->
            result.onSuccess { count ->
                EcosystemLogger.d(HaronConstants.TAG, "EmptyTrashUseCase: deleted $count items from trash")
            }
            result.onFailure { e ->
                EcosystemLogger.e(HaronConstants.TAG, "EmptyTrashUseCase: empty trash failed — ${e.message}")
            }
        }
    }
}
