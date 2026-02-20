package com.vamp.haron.domain.usecase

import com.vamp.haron.domain.repository.FileRepository
import javax.inject.Inject

class RenameFileUseCase @Inject constructor(
    private val repository: FileRepository
) {
    suspend operator fun invoke(path: String, newName: String): Result<String> {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Имя не может быть пустым"))
        }
        if (trimmed.contains('/') || trimmed.contains('\\')) {
            return Result.failure(IllegalArgumentException("Имя не может содержать / или \\"))
        }
        return repository.renameFile(path, trimmed)
    }
}
