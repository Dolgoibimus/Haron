package com.vamp.haron.common.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.data.datastore.HaronPreferences
import com.vamp.haron.domain.usecase.ExtractArchiveUseCase
import com.vamp.haron.domain.usecase.ReadArchiveEntryUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ArchiveThumbCache"
private const val THUMBNAIL_MAX_SIZE = 256
private const val JPEG_QUALITY = 85
private const val CACHE_DIR_NAME = "archive_thumbs"

@Singleton
class ArchiveThumbnailCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val readArchiveEntryUseCase: ReadArchiveEntryUseCase,
    private val extractArchiveUseCase: ExtractArchiveUseCase,
    private val preferences: HaronPreferences
) {
    private val maxMem = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val memCacheSize = maxMem / 8

    private val memCache = object : LruCache<String, Bitmap>(memCacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    private val diskCacheDir: File
        get() = File(context.cacheDir, CACHE_DIR_NAME).also { it.mkdirs() }

    private val evictionMutex = Mutex()

    /**
     * Load a thumbnail for an image entry inside an archive.
     * Checks memory cache → disk cache → reads from archive → decodes → caches.
     */
    suspend fun loadThumbnail(
        archivePath: String,
        entryFullPath: String,
        entrySize: Long,
        password: String? = null
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = buildCacheKey(archivePath, entryFullPath, entrySize)

        // 1. Memory cache
        memCache.get(cacheKey)?.let { return@withContext it }

        // 2. Disk cache
        val diskFile = File(diskCacheDir, "$cacheKey.jpg")
        if (diskFile.exists() && diskFile.length() > 0) {
            try {
                val bitmap = BitmapFactory.decodeFile(diskFile.absolutePath)
                if (bitmap != null) {
                    memCache.put(cacheKey, bitmap)
                    return@withContext bitmap
                }
            } catch (_: Exception) {
                diskFile.delete()
            }
        }

        // 3. Read bytes from archive
        val bytes = readArchiveEntryUseCase(archivePath, entryFullPath, password)
        if (bytes == null || bytes.isEmpty()) {
            EcosystemLogger.d(TAG, "No bytes for entry=$entryFullPath")
            return@withContext null
        }

        // 4. Decode with downsampling
        val bitmap = decodeThumbnail(bytes) ?: return@withContext null

        // 5. Save to disk cache
        try {
            FileOutputStream(diskFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos)
            }
            EcosystemLogger.d(TAG, "Cached: $entryFullPath → ${diskFile.length()} bytes")
        } catch (e: Exception) {
            EcosystemLogger.e(TAG, "Failed to write disk cache: ${e.message}")
        }

        // 6. Evict old files if over limit
        evictIfNeeded()

        // 7. Put in memory cache
        memCache.put(cacheKey, bitmap)
        bitmap
    }

    /**
     * Extract archive entry to temp file, then generate a type-specific thumbnail
     * via ThumbnailCache (for text, pdf, video, document, code, apk).
     */
    suspend fun loadOrGenerateThumbnail(
        context: Context,
        archivePath: String,
        entryFullPath: String,
        entrySize: Long,
        password: String? = null,
        fileType: String
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = buildCacheKey(archivePath, entryFullPath, entrySize)

        // 1. Memory cache
        memCache.get(cacheKey)?.let { return@withContext it }

        // 2. Disk cache
        val diskFile = File(diskCacheDir, "$cacheKey.jpg")
        if (diskFile.exists() && diskFile.length() > 0) {
            try {
                val bitmap = BitmapFactory.decodeFile(diskFile.absolutePath)
                if (bitmap != null) {
                    memCache.put(cacheKey, bitmap)
                    return@withContext bitmap
                }
            } catch (_: Exception) {
                diskFile.delete()
            }
        }

        // 3. Extract to temp file
        val ext = entryFullPath.substringAfterLast('.', "bin")
        val tempFile = File(context.cacheDir, "archive_thumb_tmp_${cacheKey}.$ext")
        try {
            if (fileType == "apk") {
                // APK: use ExtractArchiveUseCase (no size limit, needs full file for PackageManager)
                val tempDir = File(context.cacheDir, "archive_thumb_extract")
                tempDir.mkdirs()
                extractArchiveUseCase(archivePath, tempDir.absolutePath, setOf(entryFullPath), password)
                    .collect { /* wait */ }
                val extracted = File(tempDir, entryFullPath.substringAfterLast('/'))
                if (!extracted.exists() || extracted.length() == 0L) {
                    // Try with full path
                    val extractedFull = File(tempDir, entryFullPath)
                    if (extractedFull.exists() && extractedFull.length() > 0) {
                        extractedFull.renameTo(tempFile)
                    } else {
                        EcosystemLogger.d(TAG, "APK extract failed for $entryFullPath")
                        return@withContext null
                    }
                } else {
                    extracted.renameTo(tempFile)
                }
                // Clean up extract dir
                tempDir.deleteRecursively()
            } else {
                // Other types: read bytes via ReadArchiveEntryUseCase (10MB limit)
                val bytes = readArchiveEntryUseCase(archivePath, entryFullPath, password)
                if (bytes == null || bytes.isEmpty()) return@withContext null
                tempFile.writeBytes(bytes)
            }

            // 4. Generate thumbnail via ThumbnailCache
            val bitmap = ThumbnailCache.loadThumbnail(context, tempFile.absolutePath, false, fileType)
                ?: return@withContext null

            // 5. Save to disk cache
            try {
                FileOutputStream(diskFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos)
                }
            } catch (_: Exception) { /* ignore disk write errors */ }

            evictIfNeeded()
            memCache.put(cacheKey, bitmap)
            bitmap
        } finally {
            tempFile.delete()
        }
    }

    /** Current disk cache size in bytes. */
    fun getCacheSizeBytes(): Long {
        val dir = diskCacheDir
        if (!dir.exists()) return 0L
        return dir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    /** Clear all disk and memory cache. */
    fun clearCache() {
        memCache.evictAll()
        val dir = diskCacheDir
        if (dir.exists()) {
            dir.listFiles()?.forEach { it.delete() }
        }
        EcosystemLogger.i(TAG, "Cache cleared")
    }

    private fun decodeThumbnail(bytes: ByteArray): Bitmap? {
        // Get dimensions first
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null

        // Calculate inSampleSize
        val sampleSize = calculateInSampleSize(
            opts.outWidth, opts.outHeight, THUMBNAIL_MAX_SIZE, THUMBNAIL_MAX_SIZE
        )
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
    }

    private fun calculateInSampleSize(
        rawWidth: Int, rawHeight: Int,
        reqWidth: Int, reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (rawHeight > reqHeight || rawWidth > reqWidth) {
            val halfHeight = rawHeight / 2
            val halfWidth = rawWidth / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private suspend fun evictIfNeeded() {
        val limitMb = preferences.archiveThumbCacheSizeMb
        if (limitMb == 0) return // no limit

        evictionMutex.withLock {
            val limitBytes = limitMb.toLong() * 1024 * 1024
            val dir = diskCacheDir
            val files = dir.listFiles() ?: return
            val totalSize = files.sumOf { it.length() }

            if (totalSize <= limitBytes) return

            // Sort by lastModified (oldest first), delete until under limit
            val sorted = files.sortedBy { it.lastModified() }
            var freed = 0L
            val excess = totalSize - limitBytes
            for (file in sorted) {
                if (freed >= excess) break
                val size = file.length()
                if (file.delete()) {
                    freed += size
                }
            }
            EcosystemLogger.d(TAG, "Evicted ${freed / 1024}KB from disk cache")
        }
    }

    private fun buildCacheKey(archivePath: String, entryFullPath: String, entrySize: Long): String {
        val raw = "$archivePath!$entryFullPath@$entrySize"
        val md5 = MessageDigest.getInstance("MD5").digest(raw.toByteArray())
        return md5.joinToString("") { "%02x".format(it) }
    }
}
