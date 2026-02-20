package com.vamp.haron.domain.usecase

import com.vamp.haron.domain.repository.FileRepository
import javax.inject.Inject

class CreateDirectoryUseCase @Inject constructor(
    private val repository: FileRepository
) {
    suspend operator fun invoke(parentPath: String, name: String): Result<String> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Имя папки не может быть пустым"))
        }
        if (trimmed.contains('/') || trimmed.contains('\\')) {
            return Result.failure(IllegalArgumentException("Имя папки не может содержать / или \\"))
        }
        return repository.createDirectory(parentPath, trimmed)
    }
}
