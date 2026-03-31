package com.vamp.haron.common.util

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mini variant — no PDF/DOC extraction (no PDFBox/POI).
 * Text, archive, image EXIF, and media metadata extraction work normally.
 */
@Singleton
class ContentExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val MAX_SNIPPET = 500
        private const val MAX_FULL_TEXT = 100_000

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

        val ARCHIVE_EXTENSIONS = setOf("zip", "7z", "rar", "tar", "gz", "tgz", "bz2", "tbz2", "xz", "txz", "gtar")

        private const val MAX_INNER_FILE_SIZE = 64 * 1024L
    }

    fun extractSnippet(file: File): String {
        return try {
            val ext = file.extension.lowercase()
            when {
                ext in TEXT_EXTENSIONS -> extractTextSnippet(file)
                ext == "fb2" -> extractFb2Full(file).take(MAX_SNIPPET)
                ext == "docx" -> extractDocxFull(file).take(MAX_SNIPPET)
                ext == "odt" -> extractOdtFull(file).take(MAX_SNIPPET)
                ext == "rtf" -> extractRtfFull(file).take(MAX_SNIPPET)
                ext in AUDIO_EXTENSIONS -> extractAudioMeta(file)
                ext in VIDEO_EXTENSIONS -> extractVideoMeta(file)
                // pdf, doc — not supported in mini
                else -> ""
            }
        } catch (_: OutOfMemoryError) { System.gc(); "" }
        catch (_: Exception) { "" }
    }

    fun extractFullText(file: File): String {
        return try {
            val ext = file.extension.lowercase()
            when {
                ext in TEXT_EXTENSIONS -> extractTextFull(file)
                ext == "fb2" -> extractFb2Full(file)
                ext == "docx" -> extractDocxFull(file)
                ext == "odt" -> extractOdtFull(file)
                ext == "rtf" -> extractRtfFull(file)
                // pdf, doc — not supported in mini
                else -> ""
            }
        } catch (_: OutOfMemoryError) { System.gc(); "" }
        catch (_: Exception) { "" }
    }

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
                exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH)?.let { h -> parts.add("${w}x${h}") }
            }
            exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)?.let { parts.add("ISO $it") }
            exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let { parts.add("Focal: $it") }
            exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)?.let {
                val lon = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE) ?: ""
                parts.add("GPS: $it, $lon")
            }
            exif.getAttribute(ExifInterface.TAG_SOFTWARE)?.let { parts.add("Software: $it") }
            parts.joinToString(" | ")
        } catch (_: Exception) { "" }
    }

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
                listOfNotNull(title, artist, album, genre, year, composer)
                    .filter { it.isNotBlank() }.joinToString(" — ")
            } finally { retriever.release() }
        } catch (_: Exception) { "" }
    }

    fun isTextOrDocument(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext in TEXT_EXTENSIONS || ext in DOCUMENT_EXTENSIONS
    }

    fun isMediaFile(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext in AUDIO_EXTENSIONS || ext in VIDEO_EXTENSIONS
    }

    fun isImageFile(file: File): Boolean = file.extension.lowercase() in IMAGE_EXTENSIONS

    fun isArchiveFile(file: File): Boolean {
        val type = archiveTypeForFile(file)
        return type in ARCHIVE_EXTENSIONS || type.startsWith("tar")
    }

    fun extractArchiveEntries(file: File): String {
        return try {
            val type = archiveTypeForFile(file)
            when (type) {
                "zip" -> extractZipEntries(file)
                "7z" -> extract7zEntries(file)
                "rar" -> extractRarEntries(file)
                "tar", "tar.gz", "tar.bz2", "tar.xz" -> extractTarEntries(file, type)
                else -> ""
            }
        } catch (_: OutOfMemoryError) { System.gc(); "" }
        catch (_: Exception) { "" }
    }

    private fun archiveTypeForFile(file: File): String {
        val lower = file.name.lowercase()
        return when {
            lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> "tar.gz"
            lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2") -> "tar.bz2"
            lower.endsWith(".tar.xz") || lower.endsWith(".txz") -> "tar.xz"
            lower.endsWith(".tar") -> "tar"
            Regex("""\.part\d+\.rar$""").containsMatchIn(lower) -> "rar"
            Regex("""\.r\d{2,}$""").containsMatchIn(lower) -> "rar"
            else -> file.extension.lowercase()
        }
    }

    private fun extractTarEntries(file: File, type: String): String {
        val sb = StringBuilder()
        val rawStream = BufferedInputStream(FileInputStream(file))
        val decompressedStream = when (type) {
            "tar.gz" -> GzipCompressorInputStream(rawStream)
            "tar.bz2" -> BZip2CompressorInputStream(rawStream)
            "tar.xz" -> XZCompressorInputStream(rawStream)
            else -> rawStream
        }
        TarArchiveInputStream(decompressedStream).use { tar ->
            var entry = tar.nextEntry
            while (entry != null) { sb.appendLine(entry.name); entry = tar.nextEntry }
        }
        return sb.toString().take(MAX_FULL_TEXT)
    }

    private fun extractZipEntries(file: File): String {
        ZipFile(file).use { zip ->
            val sb = StringBuilder()
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                sb.appendLine(entry.name)
                if (!entry.isDirectory && entry.size in 1..MAX_INNER_FILE_SIZE) {
                    val innerExt = entry.name.substringAfterLast('.', "").lowercase()
                    if (innerExt in TEXT_EXTENSIONS) {
                        try { sb.appendLine(zip.getInputStream(entry).bufferedReader().use { it.readText() }) }
                        catch (_: Exception) {}
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
            while (entry != null) { sb.appendLine(entry.name); entry = sevenZ.nextEntry }
        }
        return sb.toString().take(MAX_FULL_TEXT)
    }

    private fun extractRarEntries(file: File): String {
        val sb = StringBuilder()
        com.github.junrar.Archive(file).use { rar ->
            for (header in rar.fileHeaders) sb.appendLine(header.fileName)
        }
        return sb.toString().take(MAX_FULL_TEXT)
    }

    private fun extractTextSnippet(file: File): String {
        val buf = CharArray(MAX_SNIPPET)
        val read = file.bufferedReader().use { it.read(buf) }
        return if (read > 0) String(buf, 0, read).trim() else ""
    }

    private fun extractAudioMeta(file: File): String {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val parts = listOfNotNull(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            ).filter { it.isNotBlank() }
            return parts.joinToString(" — ").take(MAX_SNIPPET)
        } finally { retriever.release() }
    }

    private fun extractVideoMeta(file: File): String {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            return (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: "").take(MAX_SNIPPET)
        } finally { retriever.release() }
    }

    private fun extractTextFull(file: File): String {
        val buf = CharArray(MAX_FULL_TEXT)
        val read = file.bufferedReader().use { it.read(buf) }
        return if (read > 0) String(buf, 0, read).trim() else ""
    }

    private fun extractDocxFull(file: File): String {
        ZipFile(file).use { zip ->
            val xmlEntry = zip.getEntry("word/document.xml") ?: return ""
            val xml = zip.getInputStream(xmlEntry).bufferedReader().readText()
            val raw = xml.split("</w:p>").map { para ->
                Regex("<w:t[^>]*>([^<]*)</w:t>").findAll(para).map { it.groupValues[1] }.joinToString("")
            }.filter { it.isNotBlank() }.joinToString(" ")
            return decodeXmlEntities(raw).take(MAX_FULL_TEXT)
        }
    }

    private fun extractOdtFull(file: File): String {
        ZipFile(file).use { zip ->
            val xmlEntry = zip.getEntry("content.xml") ?: return ""
            val xml = zip.getInputStream(xmlEntry).bufferedReader().readText()
            val raw = xml.split("</text:p>").map { it.replace(Regex("<[^>]+>"), "") }
                .filter { it.isNotBlank() }.joinToString(" ")
            return decodeXmlEntities(raw).take(MAX_FULL_TEXT)
        }
    }

    private fun extractRtfFull(file: File): String {
        val buf = CharArray(MAX_FULL_TEXT)
        val read = file.reader(Charsets.ISO_8859_1).use { it.read(buf) }
        if (read <= 0) return ""
        return String(buf, 0, read)
            .replace(Regex("\\\\[a-z]+[\\d]*\\s?"), " ")
            .replace(Regex("\\\\[{}\\\\]"), "")
            .replace(Regex("[{}]"), "")
            .replace(Regex("\\s+"), " ")
            .trim().take(MAX_FULL_TEXT)
    }

    private fun extractFb2Full(file: File): String {
        val raw = file.bufferedReader().use { it.readText().take(MAX_FULL_TEXT * 2) }
        val bodyMatch = Regex("<body[^>]*>(.*)</body>", RegexOption.DOT_MATCHES_ALL).find(raw)
        val body = bodyMatch?.groupValues?.get(1) ?: raw
        return body
            .replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&apos;", "'").replace("&#160;", " ")
            .replace(Regex("&#(\\d+);")) { m ->
                m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: ""
            }
            .replace(Regex("\\s+"), " ").trim().take(MAX_FULL_TEXT)
    }

    private fun decodeXmlEntities(text: String): String = text
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'").replace("&#160;", " ")
        .replace(Regex("&#(\\d+);")) { match ->
            match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: ""
        }
}
