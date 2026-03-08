package com.vamp.haron.domain.usecase

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.R
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.common.util.iconRes
import com.vamp.haron.common.util.toFileEntry
import com.vamp.haron.domain.model.FileEntry
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val categoryFiles: Map<String, List<LargeFile>> = emptyMap(),
    val isScanning: Boolean = true,
    val scannedFiles: Int = 0,
    val currentPath: String = ""
)

class AnalyzeStorageUseCase @Inject constructor(
    @ApplicationContext private val appContext: Context
) {

    operator fun invoke(): Flow<StorageAnalysis> = flow {
        EcosystemLogger.d(HaronConstants.TAG, "AnalyzeStorageUseCase: starting storage analysis")
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
        val catPhotos = appContext.getString(R.string.storage_cat_photos)
        val catVideos = appContext.getString(R.string.storage_cat_videos)
        val catMusic = appContext.getString(R.string.storage_cat_music)
        val catDocuments = appContext.getString(R.string.storage_cat_documents)
        val catArchives = appContext.getString(R.string.storage_cat_archives)
        val catApk = appContext.getString(R.string.storage_cat_apk)
        val catOther = appContext.getString(R.string.storage_cat_other)
        val categoryNames = mapOf(
            "image" to catPhotos,
            "video" to catVideos,
            "audio" to catMusic,
            "pdf" to catDocuments,
            "document" to catDocuments,
            "spreadsheet" to catDocuments,
            "presentation" to catDocuments,
            "text" to catDocuments,
            "code" to catDocuments,
            "archive" to catArchives,
            "apk" to catApk
        )
        val categoryIcons = mapOf(
            catPhotos to "image",
            catVideos to "video",
            catMusic to "audio",
            catDocuments to "document",
            catArchives to "archive",
            catApk to "apk",
            catOther to "file"
        )

        // Min-heap for top-200 large files (>10MB)
        val largeFileLimit = 200
        val minHeap = PriorityQueue<LargeFile>(largeFileLimit + 1, compareBy { it.entry.size })
        val largeFileThreshold = 10L * 1024 * 1024 // 10 MB

        // Per-category min-heaps: top-200 largest files per category (no size threshold)
        val categoryHeaps = mutableMapOf<String, PriorityQueue<LargeFile>>()

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
                    val category = categoryNames[iconType] ?: catOther

                    sizeMap[category] = (sizeMap[category] ?: 0L) + file.length()
                    countMap[category] = (countMap[category] ?: 0) + 1

                    val relativePath = file.absolutePath.removePrefix(root.absolutePath + "/")
                    val largeFile = LargeFile(entry = entry, relativePath = relativePath)

                    // Track large files (>10MB global list)
                    if (file.length() >= largeFileThreshold) {
                        minHeap.add(largeFile)
                        if (minHeap.size > largeFileLimit) minHeap.poll()
                    }

                    // Track per-category top-200 (no size threshold)
                    val heap = categoryHeaps.getOrPut(category) {
                        PriorityQueue(largeFileLimit + 1, compareBy { it.entry.size })
                    }
                    heap.add(largeFile)
                    if (heap.size > largeFileLimit) heap.poll()

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
                            categoryFiles = buildCategoryFiles(categoryHeaps),
                            isScanning = true,
                            scannedFiles = scannedFiles,
                            currentPath = file.parentFile?.name ?: ""
                        ))
                    }
                }
            }

        // Final result
        EcosystemLogger.d(HaronConstants.TAG, "AnalyzeStorageUseCase: complete, scanned=$scannedFiles files, categories=${sizeMap.size}")
        val categories = buildCategories(sizeMap, countMap, categoryIcons)
        emit(StorageAnalysis(
            totalSize = totalSize,
            usedSize = usedSize,
            freeSize = freeSize,
            categories = categories,
            largeFiles = minHeap.sortedByDescending { it.entry.size },
            categoryFiles = buildCategoryFiles(categoryHeaps),
            isScanning = false,
            scannedFiles = scannedFiles,
            currentPath = ""
        ))
    }.flowOn(Dispatchers.IO)

    private fun buildCategoryFiles(
        categoryHeaps: Map<String, PriorityQueue<LargeFile>>
    ): Map<String, List<LargeFile>> {
        return categoryHeaps.mapValues { (_, heap) ->
            heap.sortedByDescending { it.entry.size }
        }
    }

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
