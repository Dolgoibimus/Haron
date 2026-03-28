package com.vamp.haron.common.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.util.LruCache
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.zip.ZipFile

object ThumbnailCache {
    private const val THUMBNAIL_MAX_SIZE = 256
    // A4 proportions: 210×297mm → width:height ≈ 0.707
    private const val A4_WIDTH = 181
    private const val A4_HEIGHT = 256

    private val maxMem = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMem / 8

    private val cache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun get(path: String): Bitmap? = cache.get(path)

    fun remove(path: String) { cache.remove(path) }

    /** Load thumbnail from a URL (for cloud files with thumbnailUrl) */
    suspend fun loadFromUrl(cacheKey: String, url: String, authHeader: String? = null): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(cacheKey)?.let { return@withContext it }
        try {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = true
            if (authHeader != null) {
                connection.setRequestProperty("Authorization", authHeader)
            }
            val responseCode = connection.responseCode
            if (responseCode != 200) {
                EcosystemLogger.d(HaronConstants.TAG, "ThumbnailCache: loadFromUrl failed, responseCode=$responseCode, key=$cacheKey")
                connection.disconnect()
                return@withContext null
            }
            val bytes = connection.inputStream.readBytes()
            connection.disconnect()
            if (bytes.isEmpty()) return@withContext null

            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return@withContext null

            val sampleSize = calculateInSampleSize(
                opts.outWidth, opts.outHeight, THUMBNAIL_MAX_SIZE, THUMBNAIL_MAX_SIZE
            )
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
                ?: return@withContext null
            cache.put(cacheKey, bitmap)
            bitmap
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "ThumbnailCache: loadFromUrl error, key=$cacheKey: ${e.message}")
            null
        }
    }

    /** Download cloud file to temp, generate type-specific thumbnail, delete temp.
     *  For non-image cloud files (PDF, text, code, documents etc.) */
    suspend fun loadCloudThumbnail(
        context: Context,
        cacheKey: String,
        url: String,
        type: String
    ): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(cacheKey)?.let { return@withContext it }
        // Preserve original extension so loadDocumentThumbnail/loadFb2* can detect file type
        val fileName = cacheKey.substringAfterLast('/')
        val fileExt = when {
            fileName.lowercase().endsWith(".fb2.zip") -> ".fb2.zip"
            else -> {
                val e = fileName.substringAfterLast('.', "").lowercase()
                if (e.isNotEmpty() && e.length <= 10 && e.all { c -> c.isLetterOrDigit() }) ".$e" else ""
            }
        }
        val tempFile = File(context.cacheDir, "cloud_thumb_${cacheKey.hashCode().toUInt().toString(16)}$fileExt")
        try {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            val responseCode = connection.responseCode
            if (responseCode != 200) {
                connection.disconnect()
                return@withContext null
            }
            connection.inputStream.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            connection.disconnect()
            if (!tempFile.exists() || tempFile.length() == 0L) return@withContext null

            // Generate thumbnail from the downloaded file using existing logic
            val bitmap = loadThumbnail(context, tempFile.absolutePath, false, type)
            bitmap?.let { cache.put(cacheKey, it) }
            bitmap
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "ThumbnailCache: loadCloudThumbnail error, key=$cacheKey, type=$type: ${e.message}")
            null
        } finally {
            tempFile.delete()
        }
    }

    suspend fun loadThumbnail(
        context: Context,
        path: String,
        isContentUri: Boolean,
        type: String
    ): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(path)?.let { return@withContext it }

        // ext4:// paths — copy small portion to cache for thumbnail generation
        if (path.startsWith("ext4://")) {
            val cached = loadExt4Thumbnail(context, path, type)
            cached?.let { cache.put(path, it) }
            return@withContext cached
        }

        val isFb2Zip = path.lowercase().endsWith(".fb2.zip")

        val bitmap = try {
            when {
                isFb2Zip -> loadFb2ZipThumbnail(context, path, isContentUri)
                type == "image" -> loadImageThumbnail(context, path, isContentUri)
                type == "video" -> loadVideoThumbnail(context, path, isContentUri)
                type == "audio" -> loadAudioThumbnail(context, path, isContentUri)
                type in listOf("text", "code") -> loadTextThumbnail(context, path, isContentUri)
                type == "document" -> loadDocumentThumbnail(context, path, isContentUri)
                type == "pdf" -> loadPdfThumbnail(context, path, isContentUri)
                type == "apk" -> loadApkThumbnail(context, path, isContentUri)
                else -> null
            }
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "ThumbnailCache: loadThumbnail error, type=$type, path=${path.substringAfterLast('/')}: ${e.message}")
            null
        }

        if (bitmap == null && (path.lowercase().endsWith(".fb2") || path.lowercase().endsWith(".fb2.zip"))) {
            EcosystemLogger.d(HaronConstants.TAG, "ThumbnailCache: no cover for ${path.substringAfterLast('/')}")
        }
        bitmap?.let { cache.put(path, it) }
        bitmap
    }

    // ── Image ──

    private fun loadImageThumbnail(
        context: Context,
        path: String,
        isContentUri: Boolean
    ): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (isContentUri) {
            context.contentResolver.openInputStream(Uri.parse(path))?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } else {
            BitmapFactory.decodeFile(path, options)
        }

        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        val sampleSize = calculateInSampleSize(
            options.outWidth, options.outHeight, THUMBNAIL_MAX_SIZE, THUMBNAIL_MAX_SIZE
        )
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }

        var bitmap = if (isContentUri) {
            context.contentResolver.openInputStream(Uri.parse(path))?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            }
        } else {
            BitmapFactory.decodeFile(path, decodeOptions)
        } ?: return null

        // EXIF rotation
        val orientation = try {
            val exif = if (isContentUri) {
                context.contentResolver.openInputStream(Uri.parse(path))?.use {
                    ExifInterface(it)
                }
            } else {
                ExifInterface(path)
            }
            exif?.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            ) ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
        }
        if (!matrix.isIdentity) {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            bitmap = rotated
        }

        return bitmap
    }

    // ── Video ──

    private fun loadVideoThumbnail(
        context: Context,
        path: String,
        isContentUri: Boolean
    ): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (isContentUri) {
                retriever.setDataSource(context, Uri.parse(path))
            } else {
                retriever.setDataSource(path)
            }
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    // ── Audio → album art ──

    private fun loadAudioThumbnail(
        context: Context,
        path: String,
        isContentUri: Boolean
    ): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (isContentUri) {
                retriever.setDataSource(context, Uri.parse(path))
            } else {
                retriever.setDataSource(path)
            }
            val artBytes = retriever.embeddedPicture ?: return null
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
            val sampleSize = calculateInSampleSize(
                opts.outWidth, opts.outHeight, THUMBNAIL_MAX_SIZE, THUMBNAIL_MAX_SIZE
            )
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, decodeOpts)
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    // ── Text / Code → A4 page ──

    private fun loadTextThumbnail(
        context: Context,
        path: String,
        isContentUri: Boolean
    ): Bitmap? {
        val reader: BufferedReader = if (isContentUri) {
            val stream = context.contentResolver.openInputStream(Uri.parse(path)) ?: return null
            BufferedReader(InputStreamReader(stream))
        } else {
            File(path).bufferedReader()
        }

        val text = reader.use { br ->
            val lines = mutableListOf<String>()
            repeat(30) {
                val line = br.readLine() ?: return@repeat
                lines.add(line)
            }
            lines.joinToString("\n")
        }

        if (text.isBlank()) return null
        return renderTextA4(text)
    }

    // ── PDF → first page ──

    private fun loadPdfThumbnail(
        context: Context,
        path: String,
        isContentUri: Boolean
    ): Bitmap? {
        val pfd = if (isContentUri) {
            context.contentResolver.openFileDescriptor(Uri.parse(path), "r")
        } else {
            ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
        } ?: return null

        return pfd.use { descriptor ->
            val renderer = PdfRenderer(descriptor)
            renderer.use { pdf ->
                if (pdf.pageCount == 0) return@use null
                val page = pdf.openPage(0)
                val scale = THUMBNAIL_MAX_SIZE.toFloat() / maxOf(page.width, page.height).coerceAtLeast(1)
                val w = (page.width * scale).toInt().coerceAtLeast(1)
                val h = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bitmap
            }
        }
    }

    // ── Documents (docx, odt, doc, rtf) → extract text → A4 page ──

    private fun loadDocumentThumbnail(
        context: Context,
        path: String,
        isContentUri: Boolean
    ): Bitmap? {
        val ext = path.substringAfterLast('.', "").lowercase()
        // FB2: try cover image first, fallback to text
        if (ext == "fb2") {
            return loadFb2Thumbnail(context, path, isContentUri)
        }
        val text = when (ext) {
            "docx" -> extractDocxText(context, path, isContentUri)
            "odt" -> extractOdtText(context, path, isContentUri)
            "doc" -> extractDocText(context, path, isContentUri)
            "rtf" -> extractRtfText(context, path, isContentUri)
            else -> null
        }
        if (text.isNullOrBlank()) return null
        return renderTextA4(text)
    }

    private fun extractDocxText(context: Context, path: String, isContentUri: Boolean): String? {
        val file = if (isContentUri) copyToTemp(context, path, "docx") else File(path)
        return try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("word/document.xml") ?: return null
                val xml = zip.getInputStream(entry).bufferedReader().readText()
                xml.split("</w:p>").map { para ->
                    Regex("<w:t[^>]*>([^<]*)</w:t>").findAll(para)
                        .map { it.groupValues[1] }.joinToString("")
                }.filter { it.isNotBlank() }.joinToString("\n")
            }
        } catch (_: Exception) { null }
        finally { if (isContentUri) file.delete() }
    }

    private fun extractOdtText(context: Context, path: String, isContentUri: Boolean): String? {
        val file = if (isContentUri) copyToTemp(context, path, "odt") else File(path)
        return try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("content.xml") ?: return null
                val xml = zip.getInputStream(entry).bufferedReader().readText()
                xml.split("</text:p>").map { it.replace(Regex("<[^>]+>"), "") }
                    .filter { it.isNotBlank() }.joinToString("\n")
            }
        } catch (_: Exception) { null }
        finally { if (isContentUri) file.delete() }
    }

    private fun extractDocText(context: Context, path: String, isContentUri: Boolean): String? {
        val stream = if (isContentUri) {
            context.contentResolver.openInputStream(Uri.parse(path))
        } else {
            File(path).inputStream()
        } ?: return null
        return try {
            stream.use { input ->
                val doc = HWPFDocument(input)
                val extractor = WordExtractor(doc)
                val text = extractor.text ?: ""
                extractor.close()
                doc.close()
                text
            }
        } catch (_: Throwable) { null }
    }

    private fun extractRtfText(context: Context, path: String, isContentUri: Boolean): String? {
        val raw = try {
            if (isContentUri) {
                context.contentResolver.openInputStream(Uri.parse(path))
                    ?.bufferedReader()?.readText()
            } else {
                File(path).readText()
            }
        } catch (_: Exception) { null } ?: return null

        return raw
            .replace(Regex("\\\\[a-z]+[\\d]*\\s?"), " ")
            .replace(Regex("\\\\[{}\\\\]"), "")
            .replace(Regex("[{}]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    // ── FB2 → cover image or text A4 ──

    private fun loadFb2Thumbnail(
        context: Context,
        path: String,
        isContentUri: Boolean
    ): Bitmap? {
        val xml = try {
            if (isContentUri) {
                context.contentResolver.openInputStream(Uri.parse(path))
                    ?.bufferedReader()?.readText()
            } else {
                File(path).readText()
            }
        } catch (_: Exception) { null } ?: return null

        return parseFb2Content(xml)
    }

    private fun loadFb2ZipThumbnail(
        context: Context,
        path: String,
        isContentUri: Boolean
    ): Bitmap? {
        val file = if (isContentUri) copyToTemp(context, path, "zip") else File(path)
        return try {
            // Read via FileInputStream to workaround FUSE reporting 0 size
            val zipBytes = file.inputStream().use { it.readBytes() }
            if (zipBytes.isEmpty()) return null
            val zipStream = java.util.zip.ZipInputStream(zipBytes.inputStream())
            var result: Bitmap? = null
            var entry = zipStream.nextEntry
            while (entry != null) {
                if (entry.name.lowercase().endsWith(".fb2")) {
                    val xml = zipStream.bufferedReader().readText()
                    result = parseFb2Content(xml)
                    break
                }
                entry = zipStream.nextEntry
            }
            zipStream.close()
            result
        } catch (_: Exception) { null }
        finally { if (isContentUri) file.delete() }
    }

    private fun parseFb2Content(xml: String): Bitmap? {
        // Try to extract cover image (base64 binary)
        val coverBitmap = extractFb2Cover(xml)
        if (coverBitmap != null) return coverBitmap

        // Fallback: extract text → A4
        val text = xml.split("</p>").map { para ->
            para.replace(Regex("<[^>]+>"), "").trim()
        }.filter { it.isNotBlank() }.joinToString("\n")

        if (text.isBlank()) return null
        return renderTextA4(text)
    }

    private fun extractFb2Cover(xml: String): Bitmap? {
        // Find coverpage image href: <image l:href="#cover.jpg"/> or xlink:href
        val coverHref = Regex(
            """<coverpage>\s*<image[^>]+href="#([^"]+)"[^/]*/>\s*</coverpage>""",
            RegexOption.DOT_MATCHES_ALL
        ).find(xml)?.groupValues?.get(1)
            ?: // Try any coverpage image
            Regex(
                """<coverpage>.*?href="#([^"]+)".*?</coverpage>""",
                RegexOption.DOT_MATCHES_ALL
            ).find(xml)?.groupValues?.get(1)
            ?: return null

        // Find matching <binary> tag with this id
        val escapedHref = Regex.escape(coverHref)
        val binaryRegex = Regex(
            """<binary[^>]*\bid="$escapedHref"[^>]*>(.*?)</binary>""",
            RegexOption.DOT_MATCHES_ALL
        )
        val base64Data = binaryRegex.find(xml)?.groupValues?.get(1)
            ?.replace(Regex("\\s+"), "")
            ?: return null

        return try {
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            val opts = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            val sampleSize = calculateInSampleSize(
                opts.outWidth, opts.outHeight, THUMBNAIL_MAX_SIZE, THUMBNAIL_MAX_SIZE
            )
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
        } catch (_: Exception) { null }
    }

    // ── APK → app icon ──

    @Suppress("DEPRECATION")
    private fun loadApkThumbnail(
        context: Context,
        path: String,
        isContentUri: Boolean
    ): Bitmap? {
        val file = if (isContentUri) {
            copyToTemp(context, path, "apk")
        } else {
            File(path)
        }

        return try {
            val pm = context.packageManager
            val info = pm.getPackageArchiveInfo(file.absolutePath, 0) ?: return null
            info.applicationInfo?.sourceDir = file.absolutePath
            info.applicationInfo?.publicSourceDir = file.absolutePath

            val drawable = info.applicationInfo?.loadIcon(pm) ?: return null
            val bmp = Bitmap.createBitmap(
                drawable.intrinsicWidth.coerceAtLeast(1),
                drawable.intrinsicHeight.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp
        } catch (_: Exception) {
            null
        } catch (_: Error) {
            null
        } finally {
            if (isContentUri) file.delete()
        }
    }

    // ── Helpers ──

    private fun renderTextA4(text: String): Bitmap {
        val bitmap = Bitmap.createBitmap(A4_WIDTH, A4_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply {
            color = Color.DKGRAY
            textSize = 9f
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
        }

        var y = 12f
        for (line in text.lines()) {
            if (y > A4_HEIGHT - 6) break
            canvas.drawText(line.take(35), 6f, y, paint)
            y += 11f
        }

        return bitmap
    }

    private fun copyToTemp(context: Context, path: String, ext: String): File {
        val temp = File(context.cacheDir, "thumb_${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(Uri.parse(path))?.use { input ->
            temp.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Cannot open content URI")
        return temp
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

    /**
     * Load thumbnail for ext4:// file — copy to temp cache, generate thumbnail, delete temp.
     */
    private fun loadExt4Thumbnail(context: Context, ext4Path: String, type: String): Bitmap? {
        val name = ext4Path.substringAfterLast("/")
        val tempFile = File(context.cacheDir, "ext4_thumb_$name")
        try {
            val internalPath = com.vamp.haron.data.usb.ext4.Ext4PathUtils.toInternalPath(ext4Path)

            // For images — read first 512KB (enough for thumbnail)
            // For other types — read full file (usually small)
            val maxBytes = when (type) {
                "image" -> 512 * 1024L
                "video" -> return null // Video thumbnails need MediaMetadataRetriever with File, too heavy
                "audio" -> return null // Audio thumbnails need full file
                else -> 256 * 1024L
            }

            val data = com.vamp.haron.data.usb.ext4.Ext4Native.nativeReadFile(internalPath, maxBytes)
                ?: return null

            tempFile.writeBytes(data)
            val bitmap = loadImageThumbnail(context, tempFile.absolutePath, false)
            return bitmap
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "ext4 thumbnail error: ${e.message}")
            return null
        } finally {
            tempFile.delete()
        }
    }
}
