package com.vamp.haron.domain.usecase

import android.content.Context
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.R
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.domain.repository.FileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class BatchRenameUseCase @Inject constructor(
    private val repository: FileRepository,
    @ApplicationContext private val appContext: Context
) {
    /**
     * @param renames list of (oldPath, newName) pairs
     * @return number of successfully renamed files
     */
    suspend operator fun invoke(renames: List<Pair<String, String>>): Result<Int> {
        if (renames.isEmpty()) {
            return Result.success(0)
        }
        EcosystemLogger.d(HaronConstants.TAG, "BatchRenameUseCase: starting rename, count=${renames.size}")
        var successCount = 0
        val errors = mutableListOf<String>()
        for ((oldPath, newName) in renames) {
            val trimmed = newName.trim()
            if (trimmed.isEmpty() || trimmed.contains('/') || trimmed.contains('\\')) {
                continue
            }
            val currentName = File(oldPath).name
            if (currentName == trimmed) continue

            // Case-only change: filesystem is case-insensitive, dest.exists() returns true.
            // Two-step rename via temp name to avoid "file already exists" error.
            val caseOnly = currentName.equals(trimmed, ignoreCase = true)
            if (caseOnly) {
                val tempName = ".tmp_rename_${System.nanoTime()}"
                val tempResult = repository.renameFile(oldPath, tempName)
                val tempPath = tempResult.getOrNull()
                if (tempPath != null) {
                    repository.renameFile(tempPath, trimmed)
                        .onSuccess { successCount++ }
                        .onFailure { e -> errors.add("$currentName: ${e.message}") }
                } else {
                    errors.add("$currentName: ${tempResult.exceptionOrNull()?.message}")
                }
            } else {
                repository.renameFile(oldPath, trimmed)
                    .onSuccess { successCount++ }
                    .onFailure { e -> errors.add("$currentName: ${e.message}") }
            }
        }
        EcosystemLogger.d(HaronConstants.TAG, "BatchRenameUseCase: complete, success=$successCount, errors=${errors.size}")
        return if (errors.isEmpty() || successCount > 0) {
            Result.success(successCount)
        } else {
            EcosystemLogger.e(HaronConstants.TAG, "BatchRenameUseCase: all renames failed, first error=${errors.first()}")
            Result.failure(Exception(appContext.getString(R.string.batch_rename_error, errors.first())))
        }
    }
}
