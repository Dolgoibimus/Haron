package com.vamp.haron.domain.usecase

import com.vamp.haron.common.constants.HaronConstants
import com.vamp.core.logger.EcosystemLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

data class DuplicateFile(
    val path: String,
    val name: String,
    val lastModified: Long,
    val size: Long,
    val isOldest: Boolean = false
)

data class DuplicateGroup(
    val hash: String,
    val size: Long,
    val files: List<DuplicateFile>
) {
    val wastedSpace: Long get() = size * (files.size - 1)
}

data class DuplicateScanProgress(
    val phase: Int, // 1 = size grouping, 2 = hash calculation
    val scannedFiles: Int,
    val totalFiles: Int,
    val currentFolder: String,
    val groups: List<DuplicateGroup> = emptyList(),
    val isComplete: Boolean = false
)

class FindDuplicatesUseCase @Inject constructor() {

    operator fun invoke(rootPath: String = HaronConstants.ROOT_PATH): Flow<DuplicateScanProgress> = flow {
        // Phase 1: Group files by size
        val sizeMap = mutableMapOf<Long, MutableList<File>>()
        var scanned = 0

        fun scanDir(dir: File) {
            val children = dir.listFiles() ?: return
            for (child in children) {
                if (child.name.startsWith(".")) continue // skip hidden
                if (child.isDirectory) {
                    scanDir(child)
                } else if (child.length() > 0) {
                    sizeMap.getOrPut(child.length()) { mutableListOf() }.add(child)
                    scanned++
                    if (scanned % 500 == 0) {
                        // will be emitted below
                    }
                }
            }
        }

        emit(DuplicateScanProgress(phase = 1, scannedFiles = 0, totalFiles = 0, currentFolder = rootPath))

        val root = File(rootPath)
        scanDir(root)

        // Filter only sizes with 2+ files
        val candidates = sizeMap.filter { it.value.size >= 2 }
        val totalToHash = candidates.values.sumOf { it.size }

        emit(DuplicateScanProgress(phase = 1, scannedFiles = scanned, totalFiles = scanned, currentFolder = "Найдено $totalToHash кандидатов", isComplete = false))

        // Phase 2: Calculate MD5 for candidates
        val hashMap = mutableMapOf<String, MutableList<File>>()
        var hashed = 0

        for ((_, files) in candidates) {
            for (file in files) {
                try {
                    val hash = calculateMd5(file)
                    hashMap.getOrPut(hash) { mutableListOf() }.add(file)
                } catch (e: Exception) {
                    EcosystemLogger.e(HaronConstants.TAG, "Hash error: ${file.path} — ${e.message}")
                }
                hashed++
                if (hashed % 50 == 0) {
                    emit(DuplicateScanProgress(
                        phase = 2,
                        scannedFiles = hashed,
                        totalFiles = totalToHash,
                        currentFolder = file.parent ?: ""
                    ))
                }
            }
        }

        // Build result groups
        val groups = hashMap
            .filter { it.value.size >= 2 }
            .map { (hash, files) ->
                val oldest = files.minByOrNull { it.lastModified() }
                DuplicateGroup(
                    hash = hash,
                    size = files.first().length(),
                    files = files.map { f ->
                        DuplicateFile(
                            path = f.absolutePath,
                            name = f.name,
                            lastModified = f.lastModified(),
                            size = f.length(),
                            isOldest = f.absolutePath == oldest?.absolutePath
                        )
                    }
                )
            }
            .sortedByDescending { it.wastedSpace }

        emit(DuplicateScanProgress(
            phase = 2,
            scannedFiles = totalToHash,
            totalFiles = totalToHash,
            currentFolder = "",
            groups = groups,
            isComplete = true
        ))
    }.flowOn(Dispatchers.IO)

    private fun calculateMd5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(8192)
        file.inputStream().use { input ->
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
