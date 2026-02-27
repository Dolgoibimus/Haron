package com.vamp.haron.domain.usecase

import com.vamp.haron.domain.model.ComparisonStatus
import com.vamp.haron.domain.model.FolderComparisonEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

class CompareFoldersUseCase @Inject constructor() {

    suspend operator fun invoke(
        leftPath: String,
        rightPath: String,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): List<FolderComparisonEntry> =
        withContext(Dispatchers.IO) {
            val leftDir = File(leftPath)
            val rightDir = File(rightPath)

            val leftMap = collectRelativePaths(leftDir)
            val rightMap = collectRelativePaths(rightDir)

            val allPaths = (leftMap.keys + rightMap.keys).sorted()
            val total = allPaths.size

            allPaths.mapIndexed { index, relPath ->
                onProgress(index + 1, total)
                val leftFile = leftMap[relPath]
                val rightFile = rightMap[relPath]

                when {
                    leftFile != null && rightFile == null -> {
                        FolderComparisonEntry(
                            relativePath = relPath,
                            name = leftFile.name,
                            isDirectory = leftFile.isDirectory,
                            status = ComparisonStatus.LEFT_ONLY,
                            leftSize = if (leftFile.isFile) leftFile.length() else null,
                            rightSize = null,
                            leftModified = leftFile.lastModified(),
                            rightModified = null
                        )
                    }
                    leftFile == null && rightFile != null -> {
                        FolderComparisonEntry(
                            relativePath = relPath,
                            name = rightFile.name,
                            isDirectory = rightFile.isDirectory,
                            status = ComparisonStatus.RIGHT_ONLY,
                            leftSize = null,
                            rightSize = if (rightFile.isFile) rightFile.length() else null,
                            leftModified = null,
                            rightModified = rightFile.lastModified()
                        )
                    }
                    else -> {
                        val l = leftFile!!
                        val r = rightFile!!
                        val identical = when {
                            l.isDirectory && r.isDirectory -> true
                            l.isFile && r.isFile -> {
                                if (l.length() != r.length()) false
                                else md5(l) == md5(r)
                            }
                            else -> false
                        }
                        FolderComparisonEntry(
                            relativePath = relPath,
                            name = l.name,
                            isDirectory = l.isDirectory && r.isDirectory,
                            status = if (identical) ComparisonStatus.IDENTICAL else ComparisonStatus.DIFFERENT,
                            leftSize = if (l.isFile) l.length() else null,
                            rightSize = if (r.isFile) r.length() else null,
                            leftModified = l.lastModified(),
                            rightModified = r.lastModified()
                        )
                    }
                }
            }
        }

    private fun collectRelativePaths(root: File): Map<String, File> {
        val result = mutableMapOf<String, File>()
        if (!root.exists() || !root.isDirectory) return result
        root.walkTopDown().forEach { file ->
            if (file != root) {
                val rel = file.relativeTo(root).path.replace('\\', '/')
                result[rel] = file
            }
        }
        return result
    }

    private fun md5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
