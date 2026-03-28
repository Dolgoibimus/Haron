package com.vamp.haron.data.usb.ext4

import android.content.Context
import android.graphics.Bitmap
import com.vamp.core.logger.EcosystemLogger
import java.io.File
import java.io.FileOutputStream

private const val TAG = "Ext4Cache"
private const val EXT4_CACHE_PREFIX = "ext4_"
private const val EXT4_THUMB_PREFIX = "ext4_thumb_"
private const val DEFAULT_THUMB_CACHE_SIZE_MB = 100L
private const val MAX_CACHE_SIZE_BYTES = 200L * 1024 * 1024 // 200 MB
private const val MAX_SINGLE_FILE_BYTES = 50L * 1024 * 1024 // 50 MB for non-streaming
private const val CLEANUP_TARGET_BYTES = 150L * 1024 * 1024 // cleanup to 150 MB

/**
 * Manages ext4 file cache — temporary copies of ext4 files for opening/previewing.
 * Auto-cleans oldest files when cache exceeds limit.
 */
object Ext4CacheManager {

    /** Get or create cached copy of ext4 file. Returns null if too large for cache. */
    fun getCachedFile(context: Context, ext4Path: String, ext4Manager: Ext4UsbManager): File? {
        val name = ext4Path.substringAfterLast("/")
        val cacheFile = File(context.cacheDir, "$EXT4_CACHE_PREFIX$name")

        // Already cached and recent (< 5 min)
        if (cacheFile.exists() && System.currentTimeMillis() - cacheFile.lastModified() < 5 * 60 * 1000) {
            return cacheFile
        }

        // Check file size
        val size = Ext4Native.nativeFileSize(Ext4PathUtils.toInternalPath(ext4Path))
        if (size < 0) {
            EcosystemLogger.e(TAG, "Can't get size: $ext4Path")
            return null
        }
        if (size > MAX_SINGLE_FILE_BYTES) {
            EcosystemLogger.d(TAG, "File too large for cache: $name (${size / 1024 / 1024} MB > ${MAX_SINGLE_FILE_BYTES / 1024 / 1024} MB)")
            return null
        }

        // Ensure cache has room
        ensureCacheSpace(context, size)

        // Copy
        val ok = ext4Manager.copyToLocal(ext4Path, cacheFile)
        if (!ok) {
            EcosystemLogger.e(TAG, "Failed to cache: $name")
            cacheFile.delete()
            return null
        }

        EcosystemLogger.d(TAG, "Cached: $name (${size / 1024} KB)")
        return cacheFile
    }

    /** Get cached thumbnail file (small, for preview). */
    fun getCachedThumbnailData(ext4Path: String, maxBytes: Long = 512 * 1024): ByteArray? {
        val internalPath = Ext4PathUtils.toInternalPath(ext4Path)
        return Ext4Native.nativeReadFile(internalPath, maxBytes)
    }

    /** Check if file is too large for non-streaming cache. */
    fun isTooLargeForCache(ext4Path: String): Boolean {
        val size = Ext4Native.nativeFileSize(Ext4PathUtils.toInternalPath(ext4Path))
        return size > MAX_SINGLE_FILE_BYTES
    }

    /** Check if file is streamable (video/audio). */
    fun isStreamable(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in STREAMABLE_EXTENSIONS
    }

    /** Total size of ext4 cache files. */
    fun getCacheSize(context: Context): Long {
        return getExt4CacheFiles(context).sumOf { it.length() }
    }

    /** Delete all ext4 cache files. */
    fun clearCache(context: Context) {
        val files = getExt4CacheFiles(context)
        var freed = 0L
        for (f in files) {
            freed += f.length()
            f.delete()
        }
        EcosystemLogger.d(TAG, "Cache cleared: ${files.size} files, ${freed / 1024} KB freed")
    }

    /** Ensure enough space by deleting oldest files. */
    private fun ensureCacheSpace(context: Context, needed: Long) {
        val current = getCacheSize(context)
        if (current + needed <= MAX_CACHE_SIZE_BYTES) return

        val files = getExt4CacheFiles(context).sortedBy { it.lastModified() }
        var freed = 0L
        val target = current + needed - CLEANUP_TARGET_BYTES

        for (f in files) {
            if (freed >= target) break
            freed += f.length()
            f.delete()
            EcosystemLogger.d(TAG, "Evicted: ${f.name} (${f.length() / 1024} KB)")
        }
    }

    private fun getExt4CacheFiles(context: Context): List<File> {
        return context.cacheDir.listFiles()
            ?.filter { it.name.startsWith(EXT4_CACHE_PREFIX) }
            ?: emptyList()
    }

    private val STREAMABLE_EXTENSIONS = setOf(
        // Video
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp", "ts", "m2ts", "mpg", "mpeg",
        // Audio
        "mp3", "flac", "aac", "ogg", "opus", "wav", "wma", "m4a", "aif", "aiff", "ape", "alac"
    )

    // ── Thumbnail Disk Cache ──

    private fun thumbCacheKey(ext4Path: String): String {
        return ext4Path.hashCode().toUInt().toString(16)
    }

    /** Get cached thumbnail from disk. Returns null if not cached. */
    fun getCachedThumbnailFromDisk(context: Context, ext4Path: String): File? {
        val key = thumbCacheKey(ext4Path)
        val file = File(context.cacheDir, "$EXT4_THUMB_PREFIX$key.jpg")
        return if (file.exists()) file else null
    }

    /** Save bitmap thumbnail to disk cache. */
    fun saveThumbnailToDisk(context: Context, ext4Path: String, bitmap: Bitmap, maxSizeMb: Long = DEFAULT_THUMB_CACHE_SIZE_MB) {
        val key = thumbCacheKey(ext4Path)
        val file = File(context.cacheDir, "$EXT4_THUMB_PREFIX$key.jpg")
        ensureThumbCacheSpace(context, 50 * 1024, maxSizeMb) // estimate 50KB per thumb
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
        } catch (e: Exception) {
            EcosystemLogger.e(TAG, "saveThumbnailToDisk failed: ${e.message}")
        }
    }

    /** Total size of ext4 thumbnail cache. */
    fun getThumbCacheSize(context: Context): Long {
        return getExt4ThumbCacheFiles(context).sumOf { it.length() }
    }

    /** Delete all ext4 thumbnail cache files. */
    fun clearThumbCache(context: Context) {
        val files = getExt4ThumbCacheFiles(context)
        var freed = 0L
        for (f in files) {
            freed += f.length()
            f.delete()
        }
        EcosystemLogger.d(TAG, "Thumb cache cleared: ${files.size} files, ${freed / 1024} KB freed")
    }

    /** Total cache size (files + thumbnails). */
    fun getTotalCacheSize(context: Context): Long {
        return getCacheSize(context) + getThumbCacheSize(context)
    }

    /** Clear all ext4 caches (files + thumbnails). */
    fun clearAllCaches(context: Context) {
        clearCache(context)
        clearThumbCache(context)
    }

    private fun ensureThumbCacheSpace(context: Context, needed: Long, maxSizeMb: Long) {
        val maxBytes = maxSizeMb * 1024 * 1024
        val current = getThumbCacheSize(context)
        if (current + needed <= maxBytes) return

        val files = getExt4ThumbCacheFiles(context).sortedBy { it.lastModified() }
        var freed = 0L
        val target = current + needed - (maxBytes * 3 / 4)

        for (f in files) {
            if (freed >= target) break
            freed += f.length()
            f.delete()
        }
    }

    private fun getExt4ThumbCacheFiles(context: Context): List<File> {
        return context.cacheDir.listFiles()
            ?.filter { it.name.startsWith(EXT4_THUMB_PREFIX) }
            ?: emptyList()
    }
}
