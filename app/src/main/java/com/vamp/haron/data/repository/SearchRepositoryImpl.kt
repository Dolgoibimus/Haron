package com.vamp.haron.data.repository

import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.sqlite.db.SimpleSQLiteQuery
import com.vamp.haron.data.db.dao.FileIndexDao
import com.vamp.haron.data.db.entity.FileIndexEntity
import com.vamp.haron.domain.repository.DateFilter
import com.vamp.haron.domain.repository.FileCategory
import com.vamp.haron.domain.repository.IndexProgress
import com.vamp.haron.domain.repository.SearchFilter
import com.vamp.haron.domain.repository.SearchRepository
import com.vamp.haron.domain.repository.SizeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val fileIndexDao: FileIndexDao
) : SearchRepository {

    private val _indexProgress = MutableStateFlow(IndexProgress())

    override fun indexProgressFlow(): Flow<IndexProgress> = _indexProgress

    override suspend fun searchFiles(filter: SearchFilter): List<FileIndexEntity> {
        if (filter.query.isBlank() && filter.category == FileCategory.ALL
            && filter.sizeFilter == SizeFilter.ALL && filter.dateFilter == DateFilter.ALL
        ) {
            return emptyList()
        }

        val args = mutableListOf<Any>()
        val hasFileFilters = filter.category != FileCategory.ALL
                || filter.sizeFilter != SizeFilter.ALL
                || filter.dateFilter != DateFilter.ALL

        // Build file-only conditions (category, size, date)
        val fileConditions = mutableListOf<String>()

        when (filter.category) {
            FileCategory.DOCUMENTS -> fileConditions.add(
                "extension IN ('txt','pdf','doc','docx','xls','xlsx','ppt','pptx','odt','ods','odp','rtf','csv','md','html','htm','xml','json','log')"
            )
            FileCategory.IMAGES -> fileConditions.add(
                "extension IN ('jpg','jpeg','png','gif','bmp','webp','svg','ico','tiff','tif','heic','heif','raw','cr2','nef','arw')"
            )
            FileCategory.AUDIO -> fileConditions.add(
                "extension IN ('mp3','wav','ogg','flac','aac','wma','m4a','opus','aiff','alac','mid','midi')"
            )
            FileCategory.VIDEO -> fileConditions.add(
                "extension IN ('mp4','avi','mkv','mov','wmv','flv','webm','3gp','m4v','ts','vob','ogv','divx')"
            )
            FileCategory.ARCHIVES -> fileConditions.add(
                "extension IN ('zip','rar','7z','tar','gz','bz2','xz','lz','cab','iso','dmg','apk')"
            )
            FileCategory.ALL -> { /* no filter */ }
        }

        val now = System.currentTimeMillis()
        when (filter.sizeFilter) {
            SizeFilter.UNDER_1MB -> fileConditions.add("size < 1048576")
            SizeFilter.MB_1_10 -> fileConditions.add("size BETWEEN 1048576 AND 10485760")
            SizeFilter.MB_10_100 -> fileConditions.add("size BETWEEN 10485760 AND 104857600")
            SizeFilter.MB_100_1GB -> fileConditions.add("size BETWEEN 104857600 AND 1073741824")
            SizeFilter.OVER_1GB -> fileConditions.add("size > 1073741824")
            SizeFilter.ALL -> { /* no filter */ }
        }

        when (filter.dateFilter) {
            DateFilter.TODAY -> fileConditions.add("last_modified > ${now - 86_400_000L}")
            DateFilter.WEEK -> fileConditions.add("last_modified > ${now - 604_800_000L}")
            DateFilter.MONTH -> fileConditions.add("last_modified > ${now - 2_592_000_000L}")
            DateFilter.YEAR -> fileConditions.add("last_modified > ${now - 31_536_000_000L}")
            DateFilter.OLDER -> fileConditions.add("last_modified < ${now - 31_536_000_000L}")
            DateFilter.ALL -> { /* no filter */ }
        }

        // Build WHERE clause:
        // Folders always pass if name matches. File filters only apply to non-directories.
        val nameClause = if (filter.query.isNotBlank()) {
            args.add(filter.query)
            "name LIKE '%' || ? || '%'"
        } else null

        val whereClause = if (hasFileFilters) {
            // (is_directory = 1 OR (is_directory = 0 AND <file filters>)) AND name LIKE ...
            val fileFilterStr = (listOf("is_directory = 0") + fileConditions).joinToString(" AND ")
            val typeClause = "(is_directory = 1 OR ($fileFilterStr))"
            if (nameClause != null) "WHERE $nameClause AND $typeClause"
            else "WHERE $typeClause"
        } else {
            if (nameClause != null) "WHERE $nameClause" else ""
        }

        val sql = "SELECT * FROM file_index $whereClause ORDER BY is_directory DESC, name ASC LIMIT ? OFFSET ?"
        args.add(filter.limit)
        args.add(filter.offset)

        return fileIndexDao.searchFiltered(SimpleSQLiteQuery(sql, args.toTypedArray()))
    }

    override suspend fun indexAllFiles(onProgress: (IndexProgress) -> Unit) {
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            _indexProgress.value = IndexProgress(isRunning = true)
            onProgress(_indexProgress.value)

            try {
                val root = Environment.getExternalStorageDirectory()
                val batch = mutableListOf<FileIndexEntity>()
                var processed = 0

                // Count approximate total for progress
                val totalEstimate = estimateFileCount(root)
                _indexProgress.value = IndexProgress(
                    totalFiles = totalEstimate,
                    processedFiles = 0,
                    isRunning = true
                )
                onProgress(_indexProgress.value)

                indexDirectory(root, batch, startTime) { count ->
                    processed = count
                    _indexProgress.value = IndexProgress(
                        totalFiles = totalEstimate,
                        processedFiles = processed,
                        isRunning = true
                    )
                    onProgress(_indexProgress.value)
                }

                // Flush remaining batch
                if (batch.isNotEmpty()) {
                    fileIndexDao.upsertAll(batch)
                    batch.clear()
                }

                // Clean up stale entries (files deleted since last index)
                fileIndexDao.deleteStaleEntries(startTime)

                _indexProgress.value = IndexProgress(
                    totalFiles = processed,
                    processedFiles = processed,
                    isRunning = false
                )
                onProgress(_indexProgress.value)
            } catch (e: Exception) {
                _indexProgress.value = IndexProgress(isRunning = false)
                onProgress(_indexProgress.value)
            }
        }
    }

    private suspend fun indexDirectory(
        dir: File,
        batch: MutableList<FileIndexEntity>,
        indexTimestamp: Long,
        onProcessed: (Int) -> Unit
    ) {
        if (!coroutineContext.isActive) return

        val files = dir.listFiles() ?: return
        var count = _indexProgress.value.processedFiles

        for (file in files) {
            if (!coroutineContext.isActive) return

            // Skip Android system directories that are not useful
            if (file.name == "Android" && file.parentFile?.path == Environment.getExternalStorageDirectory().path) {
                continue
            }

            val ext = file.extension.lowercase()
            val mimeType = if (file.isFile) {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
            } else ""

            val entity = FileIndexEntity(
                path = file.absolutePath,
                name = file.name,
                extension = ext,
                size = if (file.isFile) file.length() else 0L,
                lastModified = file.lastModified(),
                mimeType = mimeType,
                parentPath = file.parent ?: "",
                isDirectory = file.isDirectory,
                isHidden = file.isHidden,
                indexedAt = indexTimestamp
            )

            batch.add(entity)
            count++

            if (batch.size >= 500) {
                fileIndexDao.upsertAll(batch)
                batch.clear()
                onProcessed(count)
            }

            if (file.isDirectory) {
                indexDirectory(file, batch, indexTimestamp, onProcessed)
                count = _indexProgress.value.processedFiles
            }
        }

        onProcessed(count)
    }

    private fun estimateFileCount(root: File): Int {
        // Quick estimate — count top-level items and multiply
        val topLevel = root.listFiles()?.size ?: 0
        return (topLevel * 500).coerceIn(1000, 200_000)
    }

    override suspend fun getIndexedCount(): Int {
        return fileIndexDao.getIndexedCount()
    }

    override suspend fun getLastIndexedTime(): Long? {
        return fileIndexDao.getLastIndexedTime()
    }

    override suspend fun clearIndex() {
        fileIndexDao.clearAll()
    }
}
