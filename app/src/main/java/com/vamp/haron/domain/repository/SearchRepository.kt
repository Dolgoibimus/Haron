package com.vamp.haron.domain.repository

import com.vamp.haron.data.db.entity.FileIndexEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class FileCategory {
    ALL, DOCUMENTS, IMAGES, AUDIO, VIDEO, ARCHIVES
}

enum class SizeFilter {
    ALL, UNDER_1MB, MB_1_10, MB_10_100, MB_100_1GB, OVER_1GB
}

enum class DateFilter {
    ALL, TODAY, WEEK, MONTH, YEAR, OLDER
}

enum class IndexMode {
    BASIC, MEDIA, VISUAL
}

data class SearchFilter(
    val query: String = "",
    val category: FileCategory = FileCategory.ALL,
    val sizeFilter: SizeFilter = SizeFilter.ALL,
    val dateFilter: DateFilter = DateFilter.ALL,
    val searchInContent: Boolean = false,
    val limit: Int = 200,
    val offset: Int = 0
)

data class IndexProgress(
    val totalFiles: Int = 0,
    val processedFiles: Int = 0,
    val isRunning: Boolean = false,
    val mode: IndexMode? = null,
    val currentFileName: String? = null,
    val skippedFiles: List<String> = emptyList()
)

interface SearchRepository {
    suspend fun searchFiles(filter: SearchFilter): List<FileIndexEntity>
    suspend fun indexAllFiles(onProgress: (IndexProgress) -> Unit = {})
    suspend fun indexByMode(mode: IndexMode, onProgress: (IndexProgress) -> Unit = {})
    fun startIndexByMode(mode: IndexMode)
    suspend fun getIndexedCount(): Int
    suspend fun getLastIndexedTime(): Long?
    suspend fun getContentIndexSize(): Long
    suspend fun clearIndex()
    fun indexProgressFlow(): Flow<IndexProgress>
    val indexCompleted: StateFlow<Boolean>
    fun dismissIndexCompleted()
    suspend fun searchContentInFolder(folderPath: String, query: String): Map<String, String>
    suspend fun indexFolderContent(folderPath: String, force: Boolean = false, onProgress: (Int, Int) -> Unit = { _, _ -> })
    suspend fun isFolderContentIndexed(folderPath: String): Boolean
}
