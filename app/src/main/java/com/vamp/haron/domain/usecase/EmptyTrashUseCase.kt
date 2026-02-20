package com.vamp.haron.domain.usecase

import com.vamp.haron.domain.repository.TrashRepository
import javax.inject.Inject

class EmptyTrashUseCase @Inject constructor(
    private val repository: TrashRepository
) {
    suspend operator fun invoke(): Result<Int> {
        return repository.emptyTrash()
    }
}
