package com.vamp.haron.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
import com.vamp.haron.R
import com.vamp.haron.common.util.iconRes
import com.vamp.haron.domain.model.ArchiveEntryInfo
import com.vamp.haron.domain.model.FileEntry
import com.vamp.haron.domain.model.PreviewData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.github.junrar.Archive
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import android.util.Base64
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.zip.ZipFile
import javax.inject.Inject

class LoadPreviewUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val MAX_IMAGE_SIZE = 1024
        private const val MAX_TEXT_LINES = 50
        private const val MAX_ARCHIVE_ENTRIES = 200
    }

    suspend operator fun invoke(entry: FileEntry): Result<PreviewData> = withContext(Dispatchers.IO) {
        try {
            val isFb2Zip = entry.name.lowercase().endsWith(".fb2.zip")
            val result = when {
                isFb2Zip -> loadFb2Zip(entry)
                entry.iconRes() == "image" -> loadImage(entry)
                entry.iconRes() == "video" -> loadVideo(entry)
                entry.iconRes() == "audio" -> loadAudio(entry)
                entry.iconRes() in listOf("text", "code") -> loadText(entry)
                entry.iconRes() == "pdf" -> loadPdf(entry)
                entry.iconRes() == "archive" -> loadArchive(entry)
                entry.iconRes() == "apk" -> loadApk(entry)
                entry.iconRes() == "document" -> loadDocument(entry)
                else -> PreviewData.UnsupportedPreview(
                    fileName = entry.name,
                    fileSize = entry.size,
                    lastModified = entry.lastModified,
                    mimeType = entry.extension
                )
            }
            Result.success(result)
        } catch (e: Throwable) {
            Result.failure(Exception(e.message ?: context.getString(R.string.preview_error_generic), e))
        }
    }

    private fun loadImage(entry: FileEntry): PreviewData {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }

        if (entry.isContentUri) {
            context.contentResolver.openInputStream(Uri.parse(entry.path))?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } else {
            BitmapFactory.decodeFile(entry.path, options)
        }

        val rawWidth = options.outWidth
        val rawHeight = options.outHeight

        // Read EXIF orientation
        val orientation = try {
            val exif = if (entry.isContentUri) {
                context.contentResolver.openInputStream(Uri.parse(entry.path))?.use {
                    ExifInterface(it)
                }
            } else {
                ExifInterface(entry.path)
            }
            exif?.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            ) ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val sampleSize = calculateInSampleSize(rawWidth, rawHeight, MAX_IMAGE_SIZE, MAX_IMAGE_SIZE)
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }

        var bitmap = if (entry.isContentUri) {
            context.contentResolver.openInputStream(Uri.parse(entry.path))?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            }
        } else {
            BitmapFactory.decodeFile(entry.path, decodeOptions)
        } ?: throw IllegalStateException("Cannot decode image")

        // Apply EXIF rotation
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f); matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f); matrix.preScale(-1f, 1f)
            }
        }
        if (!matrix.isIdentity) {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            bitmap = rotated
        }

        // Real dimensions considering rotation
        val isRotated = orientation in listOf(
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_ROTATE_270,
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_TRANSVERSE
        )
        val displayWidth = if (isRotated) rawHeight else rawWidth
        val displayHeight = if (isRotated) rawWidth else rawHeight

        return PreviewData.ImagePreview(
            fileName = entry.name,
            fileSize = entry.size,
            lastModified = entry.lastModified,
            bitmap = bitmap,
            width = displayWidth,
            height = displayHeight
        )
    }

    private fun loadVideo(entry: FileEntry): PreviewData {
        val retriever = MediaMetadataRetriever()
        try {
            try {
                if (entry.isContentUri) {
                    retriever.setDataSource(context, Uri.parse(entry.path))
                } else {
                    retriever.setDataSource(entry.path)
                }
            } catch (_: Exception) {
                // Неподдерживаемый кодек или повреждённый файл
                return PreviewData.VideoPreview(
                    fileName = entry.name,
                    fileSize = entry.size,
                    lastModified = entry.lastModified,
                    thumbnail = null,
                    durationMs = 0L
                )
            }
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            val thumbnail = try {
                retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (_: Exception) { null }

            return PreviewData.VideoPreview(
                fileName = entry.name,
                fileSize = entry.size,
                lastModified = entry.lastModified,
                thumbnail = thumbnail,
                durationMs = durationMs
            )
        } finally {
            retriever.release()
        }
    }

    private fun loadAudio(entry: FileEntry): PreviewData {
        val retriever = MediaMetadataRetriever()
        try {
            if (entry.isContentUri) {
                retriever.setDataSource(context, Uri.parse(entry.path))
            } else {
                retriever.setDataSource(entry.path)
            }
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)

            val albumArt = retriever.embeddedPicture?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }

            return PreviewData.AudioPreview(
                fileName = entry.name,
                fileSize = entry.size,
                lastModified = entry.lastModified,
                albumArt = albumArt,
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs
            )
        } finally {
            retriever.release()
        }
    }

    private fun loadText(entry: FileEntry): PreviewData {
        val lines = mutableListOf<String>()
        var totalLines = 0

        val reader = if (entry.isContentUri) {
            val stream = context.contentResolver.openInputStream(Uri.parse(entry.path))
                ?: throw IllegalStateException("Cannot open text file")
            BufferedReader(InputStreamReader(stream))
        } else {
            File(entry.path).bufferedReader()
        }

        reader.use { br ->
            var line = br.readLine()
            while (line != null) {
                totalLines++
                if (lines.size < MAX_TEXT_LINES) {
                    lines.add(line)
                }
                line = br.readLine()
            }
        }

        return PreviewData.TextPreview(
            fileName = entry.name,
            fileSize = entry.size,
            lastModified = entry.lastModified,
            content = lines.joinToString("\n"),
            totalLines = totalLines,
            extension = entry.extension
        )
    }

    private fun loadPdf(entry: FileEntry): PreviewData {
        val pfd = if (entry.isContentUri) {
            context.contentResolver.openFileDescriptor(Uri.parse(entry.path), "r")
                ?: throw IllegalStateException("Cannot open PDF")
        } else {
            ParcelFileDescriptor.open(File(entry.path), ParcelFileDescriptor.MODE_READ_ONLY)
        }

        pfd.use { descriptor ->
            val renderer = PdfRenderer(descriptor)
            renderer.use { pdf ->
                val pageCount = pdf.pageCount
                val page = pdf.openPage(0)
                val scale = MAX_IMAGE_SIZE.toFloat() / maxOf(page.width, page.height).coerceAtLeast(1)
                val bmpWidth = (page.width * scale).toInt().coerceAtLeast(1)
                val bmpHeight = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                return PreviewData.PdfPreview(
                    fileName = entry.name,
                    fileSize = entry.size,
                    lastModified = entry.lastModified,
                    firstPage = bitmap,
                    pageCount = pageCount,
                    filePath = entry.path
                )
            }
        }
    }

    private fun loadArchive(entry: FileEntry): PreviewData {
        return when (entry.extension) {
            "zip" -> loadZip(entry)
            "7z" -> load7z(entry)
            "rar" -> loadRar(entry)
            else -> PreviewData.UnsupportedPreview(
                fileName = entry.name,
                fileSize = entry.size,
                lastModified = entry.lastModified,
                mimeType = entry.extension
            )
        }
    }

    private fun loadZip(entry: FileEntry): PreviewData {
        val file = if (entry.isContentUri) copyToTemp(entry) else File(entry.path)
        try {
            val zipFile = ZipFile(file)
            zipFile.use { zip ->
                val allEntries = try {
                    zip.entries().toList()
                } catch (_: Exception) {
                    throw IllegalStateException(context.getString(R.string.archive_password_or_corrupt))
                }
                val totalEntries = allEntries.size
                val totalSize = allEntries.sumOf { it.size.coerceAtLeast(0) }
                val entryInfos = allEntries.take(MAX_ARCHIVE_ENTRIES).map { ze ->
                    ArchiveEntryInfo(ze.name, ze.size.coerceAtLeast(0), ze.isDirectory)
                }
                return PreviewData.ArchivePreview(
                    fileName = entry.name,
                    fileSize = entry.size,
                    lastModified = entry.lastModified,
                    entries = entryInfos,
                    totalEntries = totalEntries,
                    totalUncompressedSize = totalSize
                )
            }
        } catch (e: java.util.zip.ZipException) {
            throw IllegalStateException(context.getString(R.string.archive_password_or_corrupt))
        } finally {
            if (entry.isContentUri) file.delete()
        }
    }

    private fun load7z(entry: FileEntry): PreviewData {
        val file = if (entry.isContentUri) copyToTemp(entry) else File(entry.path)
        try {
            val channel = if (entry.isContentUri) {
                val bytes = file.readBytes()
                SeekableInMemoryByteChannel(bytes)
            } else {
                java.nio.channels.FileChannel.open(
                    file.toPath(), java.nio.file.StandardOpenOption.READ
                )
            }
            channel.use { ch ->
                val sevenZ = SevenZFile.builder().setSeekableByteChannel(ch).get()
                sevenZ.use { archive ->
                    val allEntries = mutableListOf<ArchiveEntryInfo>()
                    var totalSize = 0L
                    val entries = archive.entries
                    for (e in entries) {
                        totalSize += e.size.coerceAtLeast(0)
                        if (allEntries.size < MAX_ARCHIVE_ENTRIES) {
                            allEntries.add(
                                ArchiveEntryInfo(e.name, e.size.coerceAtLeast(0), e.isDirectory)
                            )
                        }
                    }
                    return PreviewData.ArchivePreview(
                        fileName = entry.name,
                        fileSize = entry.size,
                        lastModified = entry.lastModified,
                        entries = allEntries,
                        totalEntries = allEntries.size,
                        totalUncompressedSize = totalSize
                    )
                }
            }
        } finally {
            if (entry.isContentUri) file.delete()
        }
    }

    private fun loadRar(entry: FileEntry): PreviewData {
        val file = if (entry.isContentUri) copyToTemp(entry) else File(entry.path)
        try {
            val archive = try {
                Archive(file)
            } catch (e: Throwable) {
                throw IllegalStateException(context.getString(R.string.rar_open_error))
            }
            archive.use { rar ->
                val allEntries = mutableListOf<ArchiveEntryInfo>()
                var totalSize = 0L
                val headers = rar.fileHeaders ?: emptyList()
                for (h in headers) {
                    val size = h.fullUnpackSize.coerceAtLeast(0)
                    totalSize += size
                    if (allEntries.size < MAX_ARCHIVE_ENTRIES) {
                        allEntries.add(
                            ArchiveEntryInfo(
                                name = h.fileName ?: "?",
                                size = size,
                                isDirectory = h.isDirectory
                            )
                        )
                    }
                }
                return PreviewData.ArchivePreview(
                    fileName = entry.name,
                    fileSize = entry.size,
                    lastModified = entry.lastModified,
                    entries = allEntries,
                    totalEntries = allEntries.size,
                    totalUncompressedSize = totalSize
                )
            }
        } finally {
            if (entry.isContentUri) file.delete()
        }
    }

    @Suppress("DEPRECATION")
    private fun loadApk(entry: FileEntry): PreviewData {
        val file = if (entry.isContentUri) {
            copyToTemp(entry)
        } else {
            File(entry.path)
        }

        try {
            val pm = context.packageManager
            val info = pm.getPackageArchiveInfo(file.absolutePath, 0)

            if (info != null) {
                info.applicationInfo?.sourceDir = file.absolutePath
                info.applicationInfo?.publicSourceDir = file.absolutePath
                val appName = info.applicationInfo?.loadLabel(pm)?.toString()
                val icon = try {
                    info.applicationInfo?.loadIcon(pm)?.let { drawable ->
                        val bmp = Bitmap.createBitmap(
                            drawable.intrinsicWidth.coerceAtLeast(1),
                            drawable.intrinsicHeight.coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        )
                        val canvas = android.graphics.Canvas(bmp)
                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                        drawable.draw(canvas)
                        bmp
                    }
                } catch (_: Exception) { null }

                return PreviewData.ApkPreview(
                    fileName = entry.name,
                    fileSize = entry.size,
                    lastModified = entry.lastModified,
                    appName = appName,
                    packageName = info.packageName,
                    versionName = info.versionName,
                    versionCode = info.longVersionCode,
                    icon = icon
                )
            }

            return PreviewData.ApkPreview(
                fileName = entry.name,
                fileSize = entry.size,
                lastModified = entry.lastModified,
                appName = null,
                packageName = null,
                versionName = null,
                versionCode = 0,
                icon = null
            )
        } finally {
            if (entry.isContentUri) file.delete()
        }
    }

    private fun loadDocument(entry: FileEntry): PreviewData {
        return when (entry.extension) {
            "docx" -> loadDocx(entry)
            "odt" -> loadOdt(entry)
            "doc" -> loadDoc(entry)
            "rtf" -> loadRtf(entry)
            "fb2" -> loadFb2(entry)
            else -> PreviewData.UnsupportedPreview(
                fileName = entry.name,
                fileSize = entry.size,
                lastModified = entry.lastModified,
                mimeType = entry.extension
            )
        }
    }

    private fun loadFb2(entry: FileEntry): PreviewData {
        val xml = if (entry.isContentUri) {
            context.contentResolver.openInputStream(Uri.parse(entry.path))
                ?.bufferedReader()?.readText()
                ?: throw IllegalStateException(context.getString(R.string.open_file_failed))
        } else {
            File(entry.path).readText()
        }
        return parseFb2Xml(entry, xml)
    }

    private fun loadFb2Zip(entry: FileEntry): PreviewData {
        val file = if (entry.isContentUri) copyToTemp(entry) else File(entry.path)
        try {
            ZipFile(file).use { zip ->
                val fb2Entry = zip.entries().asSequence()
                    .firstOrNull { it.name.lowercase().endsWith(".fb2") }
                    ?: return PreviewData.UnsupportedPreview(
                        fileName = entry.name, fileSize = entry.size,
                        lastModified = entry.lastModified, mimeType = "fb2.zip"
                    )
                val xml = zip.getInputStream(fb2Entry).bufferedReader().readText()
                return parseFb2Xml(entry, xml)
            }
        } finally {
            if (entry.isContentUri) file.delete()
        }
    }

    private fun parseFb2Xml(entry: FileEntry, xml: String): PreviewData {
        // Extract base64 binaries
        val binaries = mutableMapOf<String, ByteArray>()
        val binRe = Regex("""<binary\s+[^>]*id="([^"]+)"[^>]*>(.*?)</binary>""", RegexOption.DOT_MATCHES_ALL)
        for (m in binRe.findAll(xml)) {
            try {
                val id = m.groupValues[1]
                val b64 = m.groupValues[2].replace(Regex("\\s"), "")
                if (b64.length > 10) {
                    binaries[id] = Base64.decode(b64, Base64.DEFAULT)
                }
            } catch (_: Exception) { }
        }

        // Extract cover image
        val coverHref = Regex(
            """<coverpage>.*?href="[#]?([^"]+)".*?</coverpage>""",
            RegexOption.DOT_MATCHES_ALL
        ).find(xml)?.groupValues?.get(1)
        val coverBitmap = coverHref?.let { href ->
            binaries[href]?.let { bytes ->
                try {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    val sampleSize = calculateInSampleSize(
                        opts.outWidth, opts.outHeight, MAX_IMAGE_SIZE, MAX_IMAGE_SIZE
                    )
                    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
                } catch (_: Exception) { null }
            }
        }

        // Extract annotation
        val annotationMatch = Regex(
            """<annotation>(.*?)</annotation>""",
            RegexOption.DOT_MATCHES_ALL
        ).find(xml)
        val annotation = if (annotationMatch != null) {
            val body = annotationMatch.groupValues[1]
            Regex("""<p>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL).findAll(body)
                .map { it.groupValues[1].replace(Regex("<[^>]+>"), "").trim() }
                .filter { it.isNotBlank() }
                .joinToString("\n")
        } else ""

        // Extract body text to fill the preview
        val bodyMatch = Regex("""<body[^>]*>(.*)</body>""", RegexOption.DOT_MATCHES_ALL).find(xml)
        val bodyText = bodyMatch?.groupValues?.get(1)?.let { body ->
            Regex("""<p>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL).findAll(body)
                .map { it.groupValues[1].replace(Regex("<[^>]+>"), "").trim() }
                .filter { it.isNotBlank() }
                .take(MAX_TEXT_LINES)
                .joinToString("\n")
        } ?: ""

        // If no cover and no annotation, fallback to plain text preview
        if (coverBitmap == null && annotation.isBlank()) {
            return textToPreview(entry, bodyText)
        }

        // Combine annotation + body text to fill the available space
        val fullText = buildString {
            if (annotation.isNotBlank()) {
                append(annotation)
                if (bodyText.isNotBlank()) append("\n\n")
            }
            append(bodyText)
        }

        return PreviewData.Fb2Preview(
            fileName = entry.name,
            fileSize = entry.size,
            lastModified = entry.lastModified,
            coverBitmap = coverBitmap,
            annotation = fullText
        )
    }

    private fun loadDocx(entry: FileEntry): PreviewData {
        val file = if (entry.isContentUri) copyToTemp(entry) else File(entry.path)
        try {
            val zipFile = ZipFile(file)
            zipFile.use { zip ->
                val xmlEntry = zip.getEntry("word/document.xml")
                    ?: throw IllegalStateException(context.getString(R.string.document_read_error))
                val xml = zip.getInputStream(xmlEntry).bufferedReader().readText()
                val text = xml.split("</w:p>").map { para ->
                    Regex("<w:t[^>]*>([^<]*)</w:t>").findAll(para)
                        .map { it.groupValues[1] }
                        .joinToString("")
                }.filter { it.isNotBlank() }.joinToString("\n")
                return textToPreview(entry, text)
            }
        } finally {
            if (entry.isContentUri) file.delete()
        }
    }

    private fun loadOdt(entry: FileEntry): PreviewData {
        val file = if (entry.isContentUri) copyToTemp(entry) else File(entry.path)
        try {
            val zipFile = ZipFile(file)
            zipFile.use { zip ->
                val xmlEntry = zip.getEntry("content.xml")
                    ?: throw IllegalStateException(context.getString(R.string.document_read_error))
                val xml = zip.getInputStream(xmlEntry).bufferedReader().readText()
                val text = xml.split("</text:p>").map { para ->
                    para.replace(Regex("<[^>]+>"), "")
                }.filter { it.isNotBlank() }.joinToString("\n")
                return textToPreview(entry, text)
            }
        } finally {
            if (entry.isContentUri) file.delete()
        }
    }

    private fun loadDoc(entry: FileEntry): PreviewData {
        val stream = if (entry.isContentUri) {
            context.contentResolver.openInputStream(Uri.parse(entry.path))
                ?: throw IllegalStateException(context.getString(R.string.open_file_failed))
        } else {
            File(entry.path).inputStream()
        }
        return try {
            stream.use { input ->
                val doc = HWPFDocument(input)
                val extractor = WordExtractor(doc)
                val text = extractor.text ?: ""
                extractor.close()
                doc.close()
                textToPreview(entry, text)
            }
        } catch (e: Exception) {
            PreviewData.UnsupportedPreview(
                fileName = entry.name,
                fileSize = entry.size,
                lastModified = entry.lastModified,
                mimeType = "doc"
            )
        }
    }

    private fun loadRtf(entry: FileEntry): PreviewData {
        val raw = if (entry.isContentUri) {
            context.contentResolver.openInputStream(Uri.parse(entry.path))
                ?.bufferedReader()?.readText()
                ?: throw IllegalStateException(context.getString(R.string.open_file_failed))
        } else {
            File(entry.path).readText()
        }
        // Strip RTF control words and groups, keep text
        val text = raw
            .replace(Regex("\\\\[a-z]+[\\d]*\\s?"), " ")  // control words
            .replace(Regex("\\\\[{}\\\\]"), "")             // escaped braces/backslash
            .replace(Regex("[{}]"), "")                      // braces
            .replace(Regex("\\s+"), " ")                    // collapse whitespace
            .trim()
        return textToPreview(entry, text)
    }

    private fun textToPreview(entry: FileEntry, text: String): PreviewData {
        val lines = text.lines()
        val content = lines.take(MAX_TEXT_LINES).joinToString("\n")
        return PreviewData.TextPreview(
            fileName = entry.name,
            fileSize = entry.size,
            lastModified = entry.lastModified,
            content = content,
            totalLines = lines.size,
            extension = entry.extension
        )
    }

    private fun copyToTemp(entry: FileEntry): File {
        val tempFile = File(context.cacheDir, "preview_${System.currentTimeMillis()}.${entry.extension}")
        context.contentResolver.openInputStream(Uri.parse(entry.path))?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Cannot open content URI")
        return tempFile
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
}
