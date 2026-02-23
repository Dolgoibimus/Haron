package com.vamp.haron.domain.repository

import com.vamp.haron.data.db.entity.FileIndexEntity
import kotlinx.coroutines.flow.Flow

enum class FileCategory {
    ALL, DOCUMENTS, IMAGES, AUDIO, VIDEO, ARCHIVES
}

enum class SizeFilter {
    ALL, UNDER_1MB, MB_1_10, MB_10_100, MB_100_1GB, OVER_1GB
}

enum class DateFilter {
    ALL, TODAY, WEEK, MONTH, YEAR, OLDER
}

data class SearchFilter(
    val query: String = "",
    val category: FileCategory = FileCategory.ALL,
    val sizeFilter: SizeFilter = SizeFilter.ALL,
    val dateFilter: DateFilter = DateFilter.ALL,
    val limit: Int = 200,
    val offset: Int = 0
)

data class IndexProgress(
    val totalFiles: Int = 0,
    val processedFiles: Int = 0,
    val isRunning: Boolean = false
)

interface SearchRepository {
    suspend fun searchFiles(filter: SearchFilter): List<FileIndexEntity>
    suspend fun indexAllFiles(onProgress: (IndexProgress) -> Unit = {})
    suspend fun getIndexedCount(): Int
    suspend fun getLastIndexedTime(): Long?
    suspend fun clearIndex()
    fun indexProgressFlow(): Flow<IndexProgress>
}
