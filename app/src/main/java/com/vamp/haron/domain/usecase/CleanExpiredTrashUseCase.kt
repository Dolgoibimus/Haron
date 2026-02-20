package com.vamp.haron.domain.usecase

import com.vamp.haron.domain.repository.TrashRepository
import javax.inject.Inject

class CleanExpiredTrashUseCase @Inject constructor(
    private val repository: TrashRepository
) {
    suspend operator fun invoke(): Result<Int> {
        return repository.cleanExpired()
    }
}
