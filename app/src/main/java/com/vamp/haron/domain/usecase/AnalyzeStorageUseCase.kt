package com.vamp.haron.domain.usecase

import android.os.Environment
import android.os.StatFs
import com.vamp.haron.common.util.iconRes
import com.vamp.haron.common.util.toFileEntry
import com.vamp.haron.domain.model.FileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.util.PriorityQueue
import javax.inject.Inject

data class StorageCategory(
    val name: String,
    val size: Long,
    val fileCount: Int,
    val icon: String
)

data class LargeFile(
    val entry: FileEntry,
    val relativePath: String
)

data class StorageAnalysis(
    val totalSize: Long = 0L,
    val usedSize: Long = 0L,
    val freeSize: Long = 0L,
    val categories: List<StorageCategory> = emptyList(),
    val largeFiles: List<LargeFile> = emptyList(),
    val isScanning: Boolean = true,
    val scannedFiles: Int = 0,
    val currentPath: String = ""
)

class AnalyzeStorageUseCase @Inject constructor() {

    operator fun invoke(): Flow<StorageAnalysis> = flow {
        val root = Environment.getExternalStorageDirectory()
        val statFs = StatFs(root.absolutePath)
        val totalSize = statFs.totalBytes
        val freeSize = statFs.availableBytes
        val usedSize = totalSize - freeSize

        emit(StorageAnalysis(
            totalSize = totalSize,
            usedSize = usedSize,
            freeSize = freeSize,
            isScanning = true
        ))

        // Category accumulators
        val sizeMap = mutableMapOf<String, Long>()
        val countMap = mutableMapOf<String, Int>()
        val categoryNames = mapOf(
            "image" to "Фото",
            "video" to "Видео",
            "audio" to "Музыка",
            "pdf" to "Документы",
            "document" to "Документы",
            "spreadsheet" to "Документы",
            "presentation" to "Документы",
            "text" to "Документы",
            "code" to "Документы",
            "archive" to "Архивы",
            "apk" to "APK"
        )
        val categoryIcons = mapOf(
            "Фото" to "image",
            "Видео" to "video",
            "Музыка" to "audio",
            "Документы" to "document",
            "Архивы" to "archive",
            "APK" to "apk",
            "Прочее" to "file"
        )

        // Min-heap for top-50 large files (>10MB)
        val minHeap = PriorityQueue<LargeFile>(51, compareBy { it.entry.size })
        val largeFileThreshold = 10L * 1024 * 1024 // 10 MB

        var scannedFiles = 0
        var lastEmitTime = System.currentTimeMillis()

        root.walkTopDown()
            .onEnter { dir ->
                // Skip hidden system directories
                !dir.name.startsWith(".") || dir == root
            }
            .forEach { file ->
                if (file.isFile) {
                    scannedFiles++
                    val entry = file.toFileEntry()
                    val iconType = entry.iconRes()
                    val category = categoryNames[iconType] ?: "Прочее"

                    sizeMap[category] = (sizeMap[category] ?: 0L) + file.length()
                    countMap[category] = (countMap[category] ?: 0) + 1

                    // Track large files
                    if (file.length() >= largeFileThreshold) {
                        val relativePath = file.absolutePath.removePrefix(root.absolutePath + "/")
                        val largeFile = LargeFile(entry = entry, relativePath = relativePath)
                        minHeap.add(largeFile)
                        if (minHeap.size > 50) minHeap.poll()
                    }

                    // Emit progress every 500ms
                    val now = System.currentTimeMillis()
                    if (now - lastEmitTime > 500) {
                        lastEmitTime = now
                        val categories = buildCategories(sizeMap, countMap, categoryIcons)
                        emit(StorageAnalysis(
                            totalSize = totalSize,
                            usedSize = usedSize,
                            freeSize = freeSize,
                            categories = categories,
                            largeFiles = minHeap.sortedByDescending { it.entry.size },
                            isScanning = true,
                            scannedFiles = scannedFiles,
                            currentPath = file.parentFile?.name ?: ""
                        ))
                    }
                }
            }

        // Final result
        val categories = buildCategories(sizeMap, countMap, categoryIcons)
        emit(StorageAnalysis(
            totalSize = totalSize,
            usedSize = usedSize,
            freeSize = freeSize,
            categories = categories,
            largeFiles = minHeap.sortedByDescending { it.entry.size },
            isScanning = false,
            scannedFiles = scannedFiles,
            currentPath = ""
        ))
    }.flowOn(Dispatchers.IO)

    private fun buildCategories(
        sizeMap: Map<String, Long>,
        countMap: Map<String, Int>,
        categoryIcons: Map<String, String>
    ): List<StorageCategory> {
        return sizeMap.entries
            .map { (name, size) ->
                StorageCategory(
                    name = name,
                    size = size,
                    fileCount = countMap[name] ?: 0,
                    icon = categoryIcons[name] ?: "file"
                )
            }
            .sortedByDescending { it.size }
    }
}
