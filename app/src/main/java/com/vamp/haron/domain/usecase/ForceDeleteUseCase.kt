package com.vamp.haron.domain.usecase

import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Permanently deletes files/folders bypassing trash.
 * Uses iterative bottom-up traversal — safe for deeply nested
 * "matryoshka" folders that would exhaust stack with recursion.
 */
class ForceDeleteUseCase @Inject constructor() {

    /**
     * @param onProgress called after each top-level item is deleted: (current, fileName)
     */
    suspend operator fun invoke(
        paths: List<String>,
        onProgress: (current: Int, fileName: String) -> Unit = { _, _ -> }
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var deleted = 0
            for ((index, path) in paths.withIndex()) {
                val file = File(path)
                if (!file.exists()) {
                    onProgress(index + 1, file.name)
                    continue
                }
                onProgress(index, file.name)
                if (file.isDirectory) {
                    iterativeDeleteDir(file)
                } else {
                    file.delete()
                }
                deleted++
                onProgress(index + 1, file.name)
            }
            Result.success(deleted)
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "ForceDelete error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Iterative bottom-up directory delete.
     * Collects all entries via DFS (depth-limited to 256 levels),
     * then deletes files first, then empty dirs bottom-up.
     */
    private fun iterativeDeleteDir(root: File) {
        val maxDepth = 256
        val dirs = mutableListOf<File>()
        val stack = ArrayDeque<Pair<File, Int>>()
        stack.addLast(root to 0)

        while (stack.isNotEmpty()) {
            val (current, depth) = stack.removeLast()

            if (!current.exists()) continue

            if (current.isFile) {
                current.delete()
                continue
            }

            // Directory
            if (depth >= maxDepth) {
                current.deleteRecursively()
                continue
            }

            dirs.add(current)
            val children = current.listFiles() ?: continue
            for (child in children) {
                stack.addLast(child to depth + 1)
            }
        }

        // Delete directories bottom-up (reversed DFS discovery order)
        for (dir in dirs.reversed()) {
            dir.delete()
        }
    }
}
