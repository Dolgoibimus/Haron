package com.vamp.haron.domain.usecase

import com.vamp.haron.domain.repository.FileRepository
import javax.inject.Inject

class CreateFileUseCase @Inject constructor(
    private val repository: FileRepository
) {
    suspend operator fun invoke(
        parentPath: String,
        name: String,
        content: String = ""
    ): Result<String> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Имя файла не может быть пустым"))
        }
        if (trimmed.contains('/') || trimmed.contains('\\')) {
            return Result.failure(IllegalArgumentException("Имя файла не может содержать / или \\"))
        }
        return repository.createFile(parentPath, trimmed, content)
    }
}
