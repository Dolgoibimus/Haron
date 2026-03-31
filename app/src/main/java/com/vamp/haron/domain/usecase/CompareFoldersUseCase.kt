package com.vamp.haron.domain.usecase

import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
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
            EcosystemLogger.d(HaronConstants.TAG, "CompareFoldersUseCase: comparing left=$leftPath, right=$rightPath")
            val leftDir = File(leftPath)
            val rightDir = File(rightPath)

            val leftMap = collectRelativePaths(leftDir)
            val rightMap = collectRelativePaths(rightDir)

            val allPaths = (leftMap.keys + rightMap.keys).sorted()
            val total = allPaths.size
            EcosystemLogger.d(HaronConstants.TAG, "CompareFoldersUseCase: left=${leftMap.size} entries, right=${rightMap.size} entries, total=$total")

            val results = allPaths.mapIndexed { index, relPath ->
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
            val identical = results.count { it.status == ComparisonStatus.IDENTICAL }
            val different = results.count { it.status == ComparisonStatus.DIFFERENT }
            val leftOnly = results.count { it.status == ComparisonStatus.LEFT_ONLY }
            val rightOnly = results.count { it.status == ComparisonStatus.RIGHT_ONLY }
            EcosystemLogger.d(HaronConstants.TAG, "CompareFoldersUseCase: complete, identical=$identical, different=$different, leftOnly=$leftOnly, rightOnly=$rightOnly")
            results
        }

    private fun collectRelativePaths(root: File): Map<String, File> {
        val result = mutableMapOf<String, File>()
        if (!root.exists() || !root.isDirectory) return result
        // Limit depth to avoid OOM on huge directory trees
        root.walkTopDown().maxDepth(MAX_DEPTH).forEach { file ->
            if (file != root && result.size < MAX_ENTRIES) {
                val rel = file.relativeTo(root).path.replace('\\', '/')
                result[rel] = file
            }
        }
        if (result.size >= MAX_ENTRIES) {
            EcosystemLogger.w(HaronConstants.TAG, "CompareFoldersUseCase: truncated at $MAX_ENTRIES entries")
        }
        return result
    }

    private fun md5(file: File): String {
        if (file.length() > MAX_MD5_SIZE) {
            // For large files, compare only first+last 64KB chunks instead of full MD5
            return partialHash(file)
        }
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

    private fun partialHash(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        val chunkSize = 65536
        file.inputStream().use { input ->
            val buffer = ByteArray(chunkSize)
            val read = input.read(buffer)
            if (read > 0) digest.update(buffer, 0, read)
        }
        // Read last chunk
        val fileLen = file.length()
        if (fileLen > chunkSize) {
            java.io.RandomAccessFile(file, "r").use { raf ->
                raf.seek(fileLen - chunkSize)
                val buffer = ByteArray(chunkSize)
                val read = raf.read(buffer)
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        // Include file size in hash for extra safety
        digest.update(fileLen.toString().toByteArray())
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val MAX_DEPTH = 10
        private const val MAX_ENTRIES = 5000
        private const val MAX_MD5_SIZE = 50L * 1024 * 1024 // 50 MB — partial hash above this
    }
}
