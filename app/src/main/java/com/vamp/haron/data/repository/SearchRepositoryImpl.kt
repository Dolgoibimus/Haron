package com.vamp.haron.data.repository

import android.os.Environment
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.sqlite.db.SimpleSQLiteQuery
import com.vamp.haron.common.util.ContentExtractor
import com.vamp.haron.common.util.ImageLabeler
import com.vamp.haron.data.db.HaronDatabase
import com.vamp.haron.data.db.dao.FileContentDao
import com.vamp.haron.data.db.dao.FileIndexDao
import com.vamp.haron.data.db.entity.FileContentEntity
import com.vamp.haron.data.db.entity.FileIndexEntity
import com.vamp.haron.domain.repository.DateFilter
import com.vamp.haron.domain.repository.FileCategory
import com.vamp.haron.domain.repository.IndexMode
import com.vamp.haron.domain.repository.IndexProgress
import com.vamp.haron.domain.repository.SearchFilter
import com.vamp.haron.domain.repository.SearchRepository
import com.vamp.haron.domain.repository.SizeFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val fileIndexDao: FileIndexDao,
    private val fileContentDao: FileContentDao,
    private val contentExtractor: ContentExtractor,
    private val imageLabeler: ImageLabeler,
    private val database: HaronDatabase
) : SearchRepository {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _indexProgress = MutableStateFlow(IndexProgress())
    private val _indexCompleted = MutableStateFlow(false)

    override val indexCompleted: StateFlow<Boolean> = _indexCompleted.asStateFlow()

    override fun indexProgressFlow(): Flow<IndexProgress> = _indexProgress

    override fun startIndexByMode(mode: IndexMode) {
        if (_indexProgress.value.isRunning) return
        _indexCompleted.value = false
        appScope.launch {
            indexByMode(mode)
            _indexCompleted.value = true
        }
    }

    override fun dismissIndexCompleted() {
        _indexCompleted.value = false
    }

    override suspend fun searchContentInFolder(folderPath: String, query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return withContext(Dispatchers.IO) {
            try {
                val paths = fileContentDao.searchInFolder(folderPath, query.lowercase())
                if (paths.isEmpty()) return@withContext emptyMap()
                val entities = fileContentDao.getByPaths(paths)
                val lowerQuery = query.lowercase()
                entities.associate { entity ->
                    val fullText = entity.fullText
                    val idx = fullText.lowercase().indexOf(lowerQuery)
                    val snippet = if (idx >= 0) {
                        val start = (idx - 20).coerceAtLeast(0)
                        val end = (idx + query.length + 60).coerceAtMost(fullText.length)
                        val prefix = if (start > 0) "\u2026" else ""
                        val suffix = if (end < fullText.length) "\u2026" else ""
                        prefix + fullText.substring(start, end).replace('\n', ' ') + suffix
                    } else {
                        fullText.take(200).replace('\n', ' ')
                    }
                    entity.path to snippet
                }
            } catch (_: Exception) {
                emptyMap()
            }
        }
    }

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

        // Content search: LIKE on file_content.full_text (reliable, case-insensitive)
        val nameClause: String?
        if (filter.query.isNotBlank()) {
            if (filter.searchInContent) {
                args.add(filter.query)
                args.add(filter.query.lowercase())
                nameClause = "(fi.name LIKE '%' || ? || '%' OR fi.path IN " +
                        "(SELECT path FROM file_content WHERE LOWER(full_text) LIKE '%' || ? || '%'))"
            } else {
                args.add(filter.query)
                nameClause = "fi.name LIKE '%' || ? || '%'"
            }
        } else {
            nameClause = null
        }

        val whereClause = if (hasFileFilters) {
            val fileFilterStr = (listOf("fi.is_directory = 0") + fileConditions.map { "fi.$it" }).joinToString(" AND ")
            val typeClause = "(fi.is_directory = 1 OR ($fileFilterStr))"
            if (nameClause != null) "WHERE $nameClause AND $typeClause"
            else "WHERE $typeClause"
        } else {
            if (nameClause != null) "WHERE $nameClause" else ""
        }

        val sql = "SELECT fi.* FROM file_index fi $whereClause ORDER BY fi.is_directory DESC, fi.name ASC LIMIT ? OFFSET ?"
        args.add(filter.limit)
        args.add(filter.offset)

        val results = try {
            fileIndexDao.searchFiltered(SimpleSQLiteQuery(sql, args.toTypedArray()))
        } catch (_: Exception) {
            if (filter.query.isNotBlank()) {
                val fallbackSql = "SELECT fi.* FROM file_index fi WHERE fi.name LIKE '%' || ? || '%' ORDER BY fi.is_directory DESC, fi.name ASC LIMIT ? OFFSET ?"
                fileIndexDao.searchFiltered(SimpleSQLiteQuery(fallbackSql, arrayOf(filter.query, filter.limit, filter.offset)))
            } else {
                emptyList()
            }
        }

        // For content search: replace contentSnippet with a relevant window around the match
        if (filter.searchInContent && filter.query.isNotBlank() && results.isNotEmpty()) {
            return enrichSnippets(results, filter.query)
        }
        return results
    }

    /**
     * Replace contentSnippet with a ±200 char window around the first match in full_text.
     */
    private suspend fun enrichSnippets(
        results: List<FileIndexEntity>,
        query: String
    ): List<FileIndexEntity> {
        val paths = results.map { it.path }
        val contentMap = fileContentDao.getByPaths(paths).associateBy { it.path }
        val lowerQuery = query.lowercase()
        return results.map { entity ->
            val fullText = contentMap[entity.path]?.fullText
            if (fullText != null) {
                val idx = fullText.lowercase().indexOf(lowerQuery)
                if (idx >= 0) {
                    val start = (idx - 200).coerceAtLeast(0)
                    val end = (idx + query.length + 200).coerceAtMost(fullText.length)
                    val window = fullText.substring(start, end)
                    entity.copy(contentSnippet = window)
                } else {
                    entity
                }
            } else {
                entity
            }
        }
    }

    // --- indexAllFiles: file structure only (for ContentObserver / ScreenOnReceiver) ---

    override suspend fun indexAllFiles(onProgress: (IndexProgress) -> Unit) {
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            // Background structural index — does NOT update _indexProgress
            // to avoid blocking mode-specific indexing buttons in Search UI.
            val bgProgress = IndexProgress(isRunning = true)
            onProgress(bgProgress)

            try {
                val root = Environment.getExternalStorageDirectory()
                val batch = mutableListOf<FileIndexEntity>()
                var processed = 0

                val totalEstimate = estimateFileCount(root)

                processed = indexDirectory(root, batch, startTime, 0) { count ->
                    processed = count
                    onProgress(IndexProgress(
                        totalFiles = totalEstimate,
                        processedFiles = processed,
                        isRunning = true
                    ))
                }

                if (batch.isNotEmpty()) {
                    fileIndexDao.upsertAll(batch)
                    batch.clear()
                }

                fileIndexDao.deleteStaleEntries(startTime)
                rebuildFts()
                onProgress(IndexProgress(
                    totalFiles = processed,
                    processedFiles = processed,
                    isRunning = false
                ))
            } catch (e: Exception) {
                onProgress(IndexProgress(isRunning = false))
            }
        }
    }

    // --- indexByMode: three specialized indexing modes ---

    override suspend fun indexByMode(mode: IndexMode, onProgress: (IndexProgress) -> Unit) {
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            _indexProgress.value = IndexProgress(isRunning = true, mode = mode)
            onProgress(_indexProgress.value)

            try {
                val root = Environment.getExternalStorageDirectory()
                val files = collectFiles(root, mode)

                val total = files.size
                _indexProgress.value = IndexProgress(
                    totalFiles = total,
                    processedFiles = 0,
                    isRunning = true,
                    mode = mode
                )
                onProgress(_indexProgress.value)

                val indexBatch = mutableListOf<FileIndexEntity>()
                val contentBatch = mutableListOf<FileContentEntity>()
                val skipped = mutableListOf<String>()
                var processed = 0

                for (file in files) {
                    if (!coroutineContext.isActive) break

                    // Update current file name immediately
                    _indexProgress.value = IndexProgress(
                        totalFiles = total,
                        processedFiles = processed,
                        isRunning = true,
                        mode = mode,
                        currentFileName = file.name,
                        skippedFiles = skipped.toList()
                    )
                    onProgress(_indexProgress.value)

                    try {
                        val contentText = when (mode) {
                            IndexMode.BASIC -> {
                                if (contentExtractor.isTextOrDocument(file)) {
                                    contentExtractor.extractFullText(file)
                                } else if (contentExtractor.isImageFile(file)) {
                                    contentExtractor.extractImageMeta(file)
                                } else if (contentExtractor.isArchiveFile(file)) {
                                    contentExtractor.extractArchiveEntries(file)
                                } else ""
                            }
                            IndexMode.MEDIA -> contentExtractor.extractMediaMeta(file)
                            IndexMode.VISUAL -> {
                                val labels = imageLabeler.labelImage(file)
                                val ocrText = if (file.length() < 10 * 1024 * 1024) {
                                    imageLabeler.recognizeText(file)
                                } else ""
                                if (labels.isNotBlank() && ocrText.isNotBlank()) "$labels\n$ocrText"
                                else labels + ocrText
                            }
                        }

                        if (contentText.isNotBlank()) {
                            contentBatch.add(
                                FileContentEntity(
                                    path = file.absolutePath,
                                    fullText = contentText,
                                    indexedAt = startTime
                                )
                            )

                            // Also update snippet in file_index
                            val snippet = contentText.take(500)
                            val ext = file.extension.lowercase()
                            val mimeType = MimeTypeMap.getSingleton()
                                .getMimeTypeFromExtension(ext) ?: "application/octet-stream"
                            indexBatch.add(
                                FileIndexEntity(
                                    path = file.absolutePath,
                                    name = file.name,
                                    extension = ext,
                                    size = file.length(),
                                    lastModified = file.lastModified(),
                                    mimeType = mimeType,
                                    parentPath = file.parent ?: "",
                                    isDirectory = false,
                                    isHidden = file.isHidden,
                                    contentSnippet = snippet,
                                    indexedAt = startTime
                                )
                            )
                        }
                    } catch (_: OutOfMemoryError) {
                        skipped.add(file.absolutePath)
                        System.gc()
                    } catch (_: Exception) {
                        skipped.add(file.absolutePath)
                    }

                    processed++

                    // Periodic GC to prevent memory pressure
                    if (processed % 50 == 0) System.gc()

                    if (contentBatch.size >= 100) {
                        mergeAndUpsertContent(contentBatch)
                        contentBatch.clear()
                        if (indexBatch.isNotEmpty()) {
                            fileIndexDao.upsertAll(indexBatch)
                            indexBatch.clear()
                        }
                    }
                }

                // Flush remaining
                if (contentBatch.isNotEmpty()) {
                    mergeAndUpsertContent(contentBatch)
                }
                if (indexBatch.isNotEmpty()) {
                    fileIndexDao.upsertAll(indexBatch)
                }

                // Rebuild FTS to ensure all data is searchable
                rebuildFts()

                _indexProgress.value = IndexProgress(
                    totalFiles = processed,
                    processedFiles = processed,
                    isRunning = false,
                    mode = mode,
                    skippedFiles = skipped.toList()
                )
                onProgress(_indexProgress.value)
            } catch (e: Exception) {
                _indexProgress.value = IndexProgress(isRunning = false, mode = mode)
                onProgress(_indexProgress.value)
            }
        }
    }

    private suspend fun collectFiles(root: File, mode: IndexMode): List<File> {
        val result = mutableListOf<File>()
        collectFilesRecursive(root, mode, result)
        return result
    }

    private suspend fun collectFilesRecursive(dir: File, mode: IndexMode, result: MutableList<File>) {
        if (!coroutineContext.isActive) return
        if (shouldSkipDirectory(dir)) return
        val files = dir.listFiles() ?: return

        for (file in files) {
            if (!coroutineContext.isActive) return

            if (file.isDirectory) {
                if (!shouldSkipDirectory(file)) {
                    collectFilesRecursive(file, mode, result)
                }
            } else {
                val shouldInclude = when (mode) {
                    IndexMode.BASIC -> contentExtractor.isTextOrDocument(file)
                            || contentExtractor.isImageFile(file)
                            || contentExtractor.isArchiveFile(file)
                    IndexMode.MEDIA -> contentExtractor.isMediaFile(file)
                    IndexMode.VISUAL -> imageLabeler.isImageFile(file)
                }
                if (shouldInclude) {
                    result.add(file)
                }
            }
        }
    }

    // --- Private helpers for basic indexing ---

    /** @return total processed count after this directory */
    private suspend fun indexDirectory(
        dir: File,
        batch: MutableList<FileIndexEntity>,
        indexTimestamp: Long,
        startCount: Int,
        onProcessed: (Int) -> Unit
    ): Int {
        if (!coroutineContext.isActive) return startCount
        if (shouldSkipDirectory(dir)) return startCount

        val files = dir.listFiles() ?: return startCount
        var count = startCount

        for (file in files) {
            if (!coroutineContext.isActive) return count

            if (file.isDirectory && shouldSkipDirectory(file)) continue

            val ext = file.extension.lowercase()
            val mimeType = if (file.isFile) {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
            } else ""

            val snippet = if (file.isFile) {
                try { contentExtractor.extractSnippet(file) } catch (_: Exception) { "" }
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
                contentSnippet = snippet,
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
                count = indexDirectory(file, batch, indexTimestamp, count, onProcessed)
            }
        }

        onProcessed(count)
        return count
    }

    private val rootPath = Environment.getExternalStorageDirectory().path

    private val SKIP_DIRS = setOf(
        "Android", ".thumbnails", ".cache", ".trash",
        ".Trash", "lost+found", ".tmp", ".temp"
    )

    private fun shouldSkipDirectory(dir: File): Boolean {
        val name = dir.name
        // Top-level Android directory
        if (name == "Android" && dir.parentFile?.path == rootPath) return true
        // Hidden directories (start with .)
        if (name.startsWith(".")) return true
        // Known system/cache directories
        if (name in SKIP_DIRS) return true
        return false
    }

    private fun estimateFileCount(root: File): Int {
        val topLevel = root.listFiles()?.size ?: 0
        return (topLevel * 500).coerceIn(1000, 200_000)
    }

    override suspend fun getIndexedCount(): Int {
        return fileIndexDao.getIndexedCount()
    }

    override suspend fun getLastIndexedTime(): Long? {
        return fileIndexDao.getLastIndexedTime()
    }

    override suspend fun getContentIndexSize(): Long {
        return fileContentDao.getTotalTextSize()
    }

    override suspend fun clearIndex() {
        fileIndexDao.clearAll()
        fileContentDao.clearAll()
    }

    /**
     * Rebuild FTS4 indexes to sync with content tables.
     * Necessary when data was inserted before FTS tables existed,
     * or when triggers didn't fire due to crash/race condition.
     */
    private fun rebuildFts() {
        try {
            val db = database.openHelper.writableDatabase
            db.execSQL("INSERT INTO file_content_fts(file_content_fts) VALUES('rebuild')")
            db.execSQL("INSERT INTO file_index_fts(file_index_fts) VALUES('rebuild')")
            Log.d("SearchRepo", "FTS rebuild completed")
        } catch (e: Exception) {
            Log.w("SearchRepo", "FTS rebuild failed", e)
        }
    }

    /**
     * Merge new content with existing content for the same path.
     * Prevents overwriting (e.g. EXIF from BASIC + labels from VISUAL).
     * If new text is already contained in existing — skip (idempotent re-run).
     */
    private suspend fun mergeAndUpsertContent(batch: List<FileContentEntity>) {
        val paths = batch.map { it.path }
        val existing = fileContentDao.getByPaths(paths).associateBy { it.path }
        val merged = batch.map { entity ->
            val prev = existing[entity.path]
            if (prev != null && !prev.fullText.contains(entity.fullText)) {
                entity.copy(fullText = prev.fullText + " | " + entity.fullText)
            } else if (prev != null) {
                // Same content already exists — keep existing, update timestamp
                prev.copy(indexedAt = entity.indexedAt)
            } else {
                entity
            }
        }
        fileContentDao.upsertAll(merged)
    }
}
