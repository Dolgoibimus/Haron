package com.vamp.haron.common.util

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.exifinterface.media.ExifInterface
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts text content from files for content search indexing.
 *
 * Three extraction modes:
 * - extractFullText: full text content (for FTS index)
 * - extractImageMeta: EXIF metadata from images
 * - extractMediaMeta: ID3/metadata from audio/video
 *
 * extractSnippet: returns first 500 chars (for UI display in search results)
 */
@Singleton
class ContentExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val MAX_SNIPPET = 500
        private const val MAX_FULL_TEXT = 100_000 // 100K chars max per file for FTS

        val TEXT_EXTENSIONS = setOf(
            "txt", "md", "log", "json", "xml", "csv", "yml", "yaml",
            "conf", "cfg", "ini", "properties", "env", "toml",
            "sql", "gradle", "kt", "java", "py", "js", "ts", "html",
            "css", "sh", "bat", "c", "cpp", "h", "htm"
        )

        val DOCUMENT_EXTENSIONS = setOf("docx", "odt", "doc", "rtf", "pdf", "fb2")

        val AUDIO_EXTENSIONS = setOf(
            "mp3", "flac", "aac", "ogg", "m4a", "wav", "wma", "opus"
        )

        val VIDEO_EXTENSIONS = setOf(
            "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "3gp",
            "m4v", "ts", "vob", "ogv", "divx", "3gpp", "mts"
        )

        val IMAGE_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "tiff", "tif",
            "heic", "heif", "raw", "cr2", "nef", "arw"
        )

        val ARCHIVE_EXTENSIONS = setOf("zip", "7z", "rar")

        private const val MAX_INNER_FILE_SIZE = 64 * 1024L // 64KB — extract small text files inside ZIP
    }

    private val scratchDir: File by lazy {
        File(context.cacheDir, "pdfbox_scratch").also { it.mkdirs() }
    }

    // --- Legacy method: snippet only (500 chars) ---

    fun extractSnippet(file: File): String {
        return try {
            val ext = file.extension.lowercase()
            when {
                ext in TEXT_EXTENSIONS -> extractTextSnippet(file)
                ext == "fb2" -> extractFb2Full(file).take(MAX_SNIPPET)
                ext == "docx" -> extractDocxSnippet(file)
                ext == "odt" -> extractOdtSnippet(file)
                ext == "doc" -> extractDocSnippet(file)
                ext == "rtf" -> extractRtfSnippet(file)
                ext == "pdf" -> extractPdfSnippet(file)
                ext in AUDIO_EXTENSIONS -> extractAudioMeta(file)
                ext in VIDEO_EXTENSIONS -> extractVideoMeta(file)
                else -> ""
            }
        } catch (_: OutOfMemoryError) {
            System.gc()
            ""
        } catch (_: Exception) {
            ""
        }
    }

    // --- Full text extraction (for FTS table) ---

    fun extractFullText(file: File): String {
        return try {
            val ext = file.extension.lowercase()
            when {
                ext in TEXT_EXTENSIONS -> extractTextFull(file)
                ext == "fb2" -> extractFb2Full(file)
                ext == "docx" -> extractDocxFull(file)
                ext == "odt" -> extractOdtFull(file)
                ext == "doc" -> extractDocFull(file)
                ext == "rtf" -> extractRtfFull(file)
                ext == "pdf" -> extractPdfFull(file)
                else -> ""
            }
        } catch (_: OutOfMemoryError) {
            System.gc()
            ""
        } catch (_: Exception) {
            ""
        }
    }

    // --- Image EXIF metadata extraction ---

    fun extractImageMeta(file: File): String {
        return try {
            val ext = file.extension.lowercase()
            if (ext !in IMAGE_EXTENSIONS) return ""

            val exif = ExifInterface(file.absolutePath)
            val parts = mutableListOf<String>()

            exif.getAttribute(ExifInterface.TAG_MAKE)?.let { parts.add("Make: $it") }
            exif.getAttribute(ExifInterface.TAG_MODEL)?.let { parts.add("Model: $it") }
            exif.getAttribute(ExifInterface.TAG_DATETIME)?.let { parts.add("Date: $it") }
            exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION)?.let { parts.add(it) }
            exif.getAttribute(ExifInterface.TAG_USER_COMMENT)?.let { parts.add(it) }
            exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH)?.let { w ->
                exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH)?.let { h ->
                    parts.add("${w}x${h}")
                }
            }
            exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)?.let { parts.add("ISO $it") }
            exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let { parts.add("Focal: $it") }
            exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)?.let {
                val lon = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE) ?: ""
                parts.add("GPS: $it, $lon")
            }
            exif.getAttribute(ExifInterface.TAG_SOFTWARE)?.let { parts.add("Software: $it") }

            parts.joinToString(" | ")
        } catch (_: Exception) {
            ""
        }
    }

    // --- Audio/Video metadata extraction ---

    fun extractMediaMeta(file: File): String {
        return try {
            val ext = file.extension.lowercase()
            if (ext !in AUDIO_EXTENSIONS && ext !in VIDEO_EXTENSIONS) return ""

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                val composer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
                val parts = listOfNotNull(title, artist, album, genre, year, composer)
                    .filter { it.isNotBlank() }
                parts.joinToString(" — ")
            } finally {
                retriever.release()
            }
        } catch (_: Exception) {
            ""
        }
    }

    fun isTextOrDocument(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext in TEXT_EXTENSIONS || ext in DOCUMENT_EXTENSIONS
    }

    fun isMediaFile(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext in AUDIO_EXTENSIONS || ext in VIDEO_EXTENSIONS
    }

    fun isImageFile(file: File): Boolean {
        return file.extension.lowercase() in IMAGE_EXTENSIONS
    }

    fun isArchiveFile(file: File): Boolean {
        return file.extension.lowercase() in ARCHIVE_EXTENSIONS
    }

    /**
     * Extract file names (and small text content) from inside ZIP/7Z/RAR archives.
     * Used for content search — searching "readme.txt" will find archives containing it.
     */
    fun extractArchiveEntries(file: File): String {
        return try {
            val ext = file.extension.lowercase()
            when (ext) {
                "zip" -> extractZipEntries(file)
                "7z" -> extract7zEntries(file)
                "rar" -> extractRarEntries(file)
                else -> ""
            }
        } catch (_: OutOfMemoryError) {
            System.gc()
            ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractZipEntries(file: File): String {
        ZipFile(file).use { zip ->
            val sb = StringBuilder()
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                sb.appendLine(entry.name)
                // Extract small text files content
                if (!entry.isDirectory && entry.size in 1..MAX_INNER_FILE_SIZE) {
                    val innerExt = entry.name.substringAfterLast('.', "").lowercase()
                    if (innerExt in TEXT_EXTENSIONS) {
                        try {
                            val text = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                            sb.appendLine(text)
                        } catch (_: Exception) { /* skip */ }
                    }
                }
            }
            return sb.toString().take(MAX_FULL_TEXT)
        }
    }

    private fun extract7zEntries(file: File): String {
        val sb = StringBuilder()
        SevenZFile(file).use { sevenZ ->
            var entry = sevenZ.nextEntry
            while (entry != null) {
                sb.appendLine(entry.name)
                entry = sevenZ.nextEntry
            }
        }
        return sb.toString().take(MAX_FULL_TEXT)
    }

    private fun extractRarEntries(file: File): String {
        val sb = StringBuilder()
        val archive = com.github.junrar.Archive(file)
        archive.use { rar ->
            for (header in rar.fileHeaders) {
                sb.appendLine(header.fileName)
            }
        }
        return sb.toString().take(MAX_FULL_TEXT)
    }

    // ==================== Private: Snippet extraction (<=500 chars) ====================

    private fun extractTextSnippet(file: File): String {
        val buf = CharArray(MAX_SNIPPET)
        val read = file.bufferedReader().use { it.read(buf) }
        return if (read > 0) String(buf, 0, read).trim() else ""
    }

    private fun extractDocxSnippet(file: File): String {
        return extractDocxFull(file).take(MAX_SNIPPET)
    }

    private fun extractOdtSnippet(file: File): String {
        return extractOdtFull(file).take(MAX_SNIPPET)
    }

    private fun extractDocSnippet(file: File): String {
        return extractDocFull(file).take(MAX_SNIPPET)
    }

    private fun extractRtfSnippet(file: File): String {
        return extractRtfFull(file).take(MAX_SNIPPET)
    }

    private fun extractPdfSnippet(file: File): String {
        val memSetting = MemoryUsageSetting.setupTempFileOnly().setTempDir(scratchDir)
        PDDocument.load(file, memSetting).use { doc ->
            val stripper = PDFTextStripper()
            stripper.startPage = 1
            stripper.endPage = minOf(2, doc.numberOfPages)
            val text = stripper.getText(doc) ?: ""
            return text.take(MAX_SNIPPET)
        }
    }

    private fun extractAudioMeta(file: File): String {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val parts = listOfNotNull(title, artist, album).filter { it.isNotBlank() }
            return parts.joinToString(" — ").take(MAX_SNIPPET)
        } finally {
            retriever.release()
        }
    }

    private fun extractVideoMeta(file: File): String {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            return (title ?: "").take(MAX_SNIPPET)
        } finally {
            retriever.release()
        }
    }

    // ==================== Private: Full text extraction ====================

    private fun extractTextFull(file: File): String {
        // Read up to MAX_FULL_TEXT chars
        val buf = CharArray(MAX_FULL_TEXT)
        val read = file.bufferedReader().use { it.read(buf) }
        return if (read > 0) String(buf, 0, read).trim() else ""
    }

    private fun extractDocxFull(file: File): String {
        ZipFile(file).use { zip ->
            val xmlEntry = zip.getEntry("word/document.xml") ?: return ""
            val xml = zip.getInputStream(xmlEntry).bufferedReader().readText()
            val raw = xml.split("</w:p>").map { para ->
                Regex("<w:t[^>]*>([^<]*)</w:t>").findAll(para)
                    .map { it.groupValues[1] }
                    .joinToString("")
            }.filter { it.isNotBlank() }.joinToString(" ")
            return decodeXmlEntities(raw).take(MAX_FULL_TEXT)
        }
    }

    private fun extractOdtFull(file: File): String {
        ZipFile(file).use { zip ->
            val xmlEntry = zip.getEntry("content.xml") ?: return ""
            val xml = zip.getInputStream(xmlEntry).bufferedReader().readText()
            val raw = xml.split("</text:p>").map { para ->
                para.replace(Regex("<[^>]+>"), "")
            }.filter { it.isNotBlank() }.joinToString(" ")
            return decodeXmlEntities(raw).take(MAX_FULL_TEXT)
        }
    }

    private fun extractDocFull(file: File): String {
        file.inputStream().use { input ->
            val doc = HWPFDocument(input)
            val extractor = WordExtractor(doc)
            val text = extractor.text ?: ""
            extractor.close()
            doc.close()
            return text.take(MAX_FULL_TEXT)
        }
    }

    private fun extractRtfFull(file: File): String {
        val buf = CharArray(MAX_FULL_TEXT)
        val read = file.reader(Charsets.ISO_8859_1).use { it.read(buf) }
        if (read <= 0) return ""
        val raw = String(buf, 0, read)
        val text = raw
            .replace(Regex("\\\\[a-z]+[\\d]*\\s?"), " ")
            .replace(Regex("\\\\[{}\\\\]"), "")
            .replace(Regex("[{}]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return text.take(MAX_FULL_TEXT)
    }

    private fun extractFb2Full(file: File): String {
        val raw = file.bufferedReader().use { it.readText().take(MAX_FULL_TEXT * 2) }
        // Extract <body> content
        val bodyMatch = Regex("<body[^>]*>(.*)</body>", RegexOption.DOT_MATCHES_ALL).find(raw)
        val body = bodyMatch?.groupValues?.get(1) ?: raw
        // Strip XML tags, decode entities
        val text = body
            .replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#160;", " ")
            .replace(Regex("&#(\\d+);")) { m ->
                val c = m.groupValues[1].toIntOrNull()
                if (c != null) c.toChar().toString() else ""
            }
            .replace(Regex("\\s+"), " ")
            .trim()
        return text.take(MAX_FULL_TEXT)
    }

    private fun decodeXmlEntities(text: String): String = text
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#160;", " ")
        .replace(Regex("&#(\\d+);")) { match ->
            val code = match.groupValues[1].toIntOrNull()
            if (code != null) code.toChar().toString() else ""
        }

    private fun extractPdfFull(file: File): String {
        val memSetting = MemoryUsageSetting.setupTempFileOnly().setTempDir(scratchDir)
        PDDocument.load(file, memSetting).use { doc ->
            val totalPages = doc.numberOfPages
            val sb = StringBuilder()
            val chunkSize = 3 // pages per chunk to limit memory

            var page = 1
            while (page <= totalPages && sb.length < MAX_FULL_TEXT) {
                val stripper = PDFTextStripper()
                stripper.startPage = page
                stripper.endPage = minOf(page + chunkSize - 1, totalPages)
                val text = stripper.getText(doc) ?: ""
                sb.append(text)
                page += chunkSize
            }

            return sb.toString().take(MAX_FULL_TEXT)
        }
    }
}
