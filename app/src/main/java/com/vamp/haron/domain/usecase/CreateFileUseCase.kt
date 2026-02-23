package com.vamp.haron.domain.usecase

import android.content.Context
import com.vamp.haron.R
import com.vamp.haron.domain.repository.FileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class CreateFileUseCase @Inject constructor(
    private val repository: FileRepository,
    @ApplicationContext private val appContext: Context
) {
    suspend operator fun invoke(
        parentPath: String,
        name: String,
        content: String = ""
    ): Result<String> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException(appContext.getString(R.string.error_file_name_empty)))
        }
        if (trimmed.contains('/') || trimmed.contains('\\')) {
            return Result.failure(IllegalArgumentException(appContext.getString(R.string.error_file_name_slashes)))
        }
        return repository.createFile(parentPath, trimmed, content)
    }
}
