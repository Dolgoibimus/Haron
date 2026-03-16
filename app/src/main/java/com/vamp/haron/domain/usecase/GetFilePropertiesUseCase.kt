package com.vamp.haron.domain.usecase

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.exifinterface.media.ExifInterface
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.R
import com.vamp.haron.common.util.iconRes
import com.vamp.haron.common.util.mimeType
import com.vamp.haron.domain.model.FileEntry
import com.tom_roush.pdfbox.pdmodel.PDDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

data class AudioTags(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val year: String = "",
    val genre: String = "",
    val duration: String = "",  // read-only (formatted mm:ss)
    val bitrate: String = ""    // read-only (e.g. "320 kbps")
) {
    val hasEditableTags: Boolean get() = title.isNotEmpty() || artist.isNotEmpty() ||
        album.isNotEmpty() || year.isNotEmpty() || genre.isNotEmpty()
    val hasAnyData: Boolean get() = hasEditableTags || duration.isNotEmpty() || bitrate.isNotEmpty()
}

data class FileProperties(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val mimeType: String,
    val isDirectory: Boolean,
    val childCount: Int = 0,
    val totalSize: Long = 0L,
    val exifData: Map<String, String> = emptyMap(),
    val audioMetadata: Map<String, String> = emptyMap(),
    val audioTags: AudioTags? = null,
    val hasEmbeddedCover: Boolean = false,
    val documentMetadata: Map<String, String> = emptyMap(),
    val permissions: String = ""
)

class GetFilePropertiesUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    operator fun invoke(entry: FileEntry): Flow<FileProperties> = flow {
        val file = File(entry.path)
        val mime = entry.mimeType()

        // Basic properties — emit fast
        val permissions = buildPermissionsString(file)
        val base = FileProperties(
            name = entry.name,
            path = entry.path,
            size = entry.size,
            lastModified = entry.lastModified,
            mimeType = mime,
            isDirectory = entry.isDirectory,
            childCount = entry.childCount,
            totalSize = entry.size,
            permissions = permissions
        )
        emit(base)

        // For directories — recursive size calculation
        if (entry.isDirectory) {
            var totalSize = 0L
            var totalFiles = 0
            file.walkTopDown().forEach { f ->
                if (f.isFile) {
                    totalSize += f.length()
                    totalFiles++
                }
            }
            emit(base.copy(totalSize = totalSize, childCount = totalFiles))
            return@flow
        }

        // For images — read EXIF
        if (entry.iconRes() == "image" && !entry.isContentUri) {
            try {
                val exif = ExifInterface(entry.path)
                val data = buildExifMap(exif)
                emit(base.copy(exifData = data))
            } catch (_: Exception) {
                // No EXIF or unreadable
            }
        }

        // For audio — read metadata tags
        if (entry.iconRes() == "audio" && !entry.isContentUri) {
            try {
                val (audioData, audioTags) = buildAudioMetadata(entry.path)
                val hasCover = checkEmbeddedCover(entry.path)
                EcosystemLogger.d(TAG, "Audio metadata: ${audioData.size} tags, hasCover=$hasCover")
                emit(base.copy(audioMetadata = audioData, audioTags = audioTags, hasEmbeddedCover = hasCover))
            } catch (e: Exception) {
                EcosystemLogger.e(TAG, "Failed to read audio metadata: ${e.message}")
            }
        }

        // For PDF — read document info
        val ext = entry.name.substringAfterLast('.', "").lowercase()
        if (ext == "pdf" && !entry.isContentUri) {
            try {
                val docMeta = buildPdfMetadata(entry.path)
                if (docMeta.isNotEmpty()) {
                    EcosystemLogger.d(TAG, "PDF metadata: ${docMeta.size} fields")
                    emit(base.copy(documentMetadata = docMeta))
                }
            } catch (e: Exception) {
                EcosystemLogger.e(TAG, "Failed to read PDF metadata: ${e.message}")
            }
        }

        // For FB2 — read book info
        if ((ext == "fb2" || entry.name.lowercase().endsWith(".fb2.zip")) && !entry.isContentUri) {
            try {
                val docMeta = buildFb2Metadata(entry.path)
                if (docMeta.isNotEmpty()) {
                    EcosystemLogger.d(TAG, "FB2 metadata: ${docMeta.size} fields")
                    emit(base.copy(documentMetadata = docMeta))
                }
            } catch (e: Exception) {
                EcosystemLogger.e(TAG, "Failed to read FB2 metadata: ${e.message}")
            }
        }
    }.flowOn(Dispatchers.IO)

    fun removeExif(path: String): Boolean {
        return try {
            val exif = ExifInterface(path)
            EXIF_TAGS.forEach { tag ->
                exif.setAttribute(tag, null)
            }
            exif.saveAttributes()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun buildPermissionsString(file: File): String {
        val sb = StringBuilder()
        sb.append(if (file.canRead()) "r" else "-")
        sb.append(if (file.canWrite()) "w" else "-")
        sb.append(if (file.canExecute()) "x" else "-")
        return sb.toString()
    }

    private fun buildExifMap(exif: ExifInterface): Map<String, String> {
        val map = linkedMapOf<String, String>()
        exif.getAttribute(ExifInterface.TAG_MAKE)?.let { map[context.getString(R.string.exif_manufacturer)] = it }
        exif.getAttribute(ExifInterface.TAG_MODEL)?.let { map[context.getString(R.string.exif_camera_model)] = it }
        exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let { map[context.getString(R.string.exif_date_taken)] = it }
        exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH)?.let { w ->
            exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH)?.let { h ->
                map[context.getString(R.string.exif_image_size)] = "${w}x${h}"
            }
        }
        exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)?.let { map[context.getString(R.string.exif_iso)] = it }
        exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let { map[context.getString(R.string.exif_exposure)] = it }
        exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { map[context.getString(R.string.exif_aperture)] = "f/$it" }
        exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let { map[context.getString(R.string.exif_focal_length)] = it }
        exif.getAttribute(ExifInterface.TAG_WHITE_BALANCE)?.let {
            map[context.getString(R.string.exif_white_balance)] = if (it == "0") context.getString(R.string.exif_wb_auto) else context.getString(R.string.exif_wb_manual)
        }
        exif.getAttribute(ExifInterface.TAG_FLASH)?.let { map[context.getString(R.string.exif_flash)] = it }
        exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)?.let { lat ->
            exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)?.let { lon ->
                val latRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF) ?: ""
                val lonRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF) ?: ""
                map[context.getString(R.string.exif_gps)] = "$lat $latRef, $lon $lonRef"
            }
        }
        exif.getAttribute(ExifInterface.TAG_SOFTWARE)?.let { map[context.getString(R.string.exif_software)] = it }
        return map
    }

    private fun buildAudioMetadata(path: String): Pair<Map<String, String>, AudioTags> {
        val map = linkedMapOf<String, String>()
        val retriever = MediaMetadataRetriever()
        var title = ""
        var artist = ""
        var album = ""
        var year = ""
        var genre = ""
        var duration = ""
        var bitrate = ""
        try {
            retriever.setDataSource(path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let {
                title = it; map[context.getString(R.string.audio_title)] = it
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let {
                artist = it; map[context.getString(R.string.audio_artist)] = it
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let {
                album = it; map[context.getString(R.string.audio_album)] = it
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.let {
                year = it; map[context.getString(R.string.audio_year)] = it
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)?.let {
                genre = it; map[context.getString(R.string.audio_genre)] = it
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.let { ms ->
                val totalSec = ms.toLongOrNull()?.div(1000) ?: return@let
                val min = totalSec / 60
                val sec = totalSec % 60
                val fmt = "%d:%02d".format(min, sec)
                duration = fmt; map[context.getString(R.string.audio_duration)] = fmt
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.let { bps ->
                val kbps = (bps.toLongOrNull() ?: return@let) / 1000
                val fmt = "$kbps kbps"
                bitrate = fmt; map[context.getString(R.string.audio_bitrate)] = fmt
            }
        } finally {
            retriever.release()
        }
        val tags = AudioTags(title, artist, album, year, genre, duration, bitrate)
        return map to tags
    }

    private fun buildPdfMetadata(path: String): Map<String, String> {
        val map = linkedMapOf<String, String>()
        val file = File(path)
        PDDocument.load(file).use { doc ->
            val info = doc.documentInformation
            map[context.getString(R.string.doc_pages)] = doc.numberOfPages.toString()
            info?.title?.takeIf { it.isNotBlank() }?.let { map[context.getString(R.string.doc_title)] = it }
            info?.author?.takeIf { it.isNotBlank() }?.let { map[context.getString(R.string.doc_author)] = it }
            info?.subject?.takeIf { it.isNotBlank() }?.let { map[context.getString(R.string.doc_subject)] = it }
            info?.keywords?.takeIf { it.isNotBlank() }?.let { map[context.getString(R.string.doc_keywords)] = it }
            info?.creator?.takeIf { it.isNotBlank() }?.let { map[context.getString(R.string.doc_creator)] = it }
            info?.producer?.takeIf { it.isNotBlank() }?.let { map[context.getString(R.string.doc_producer)] = it }
            info?.creationDate?.let { cal ->
                val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                map[context.getString(R.string.doc_created)] = fmt.format(cal.time)
            }
        }
        return map
    }

    private fun buildFb2Metadata(path: String): Map<String, String> {
        val map = linkedMapOf<String, String>()
        val file = File(path)

        val text = if (path.lowercase().endsWith(".fb2.zip")) {
            // Read .fb2 from inside ZIP
            java.util.zip.ZipFile(file).use { zip ->
                val fb2Entry = zip.entries().asSequence().firstOrNull {
                    it.name.lowercase().endsWith(".fb2")
                } ?: return map
                zip.getInputStream(fb2Entry).bufferedReader().use { it.readText() }
            }
        } else {
            // Limit read to first 50KB — description is always at the top
            file.inputStream().bufferedReader().use {
                val buf = CharArray(50_000)
                val read = it.read(buf)
                if (read > 0) String(buf, 0, read) else return map
            }
        }

        // Extract <description> block
        val descMatch = Regex("<description>(.*?)</description>", RegexOption.DOT_MATCHES_ALL).find(text)
            ?: return map
        val desc = descMatch.groupValues[1]

        // Title
        Regex("<book-title>([^<]+)</book-title>").find(desc)?.groupValues?.get(1)?.trim()?.let {
            map[context.getString(R.string.doc_title)] = decodeXmlEntities(it)
        }

        // Author(s)
        val authors = Regex("<author>(.*?)</author>", RegexOption.DOT_MATCHES_ALL).findAll(desc)
        val authorNames = authors.map { authorMatch ->
            val block = authorMatch.groupValues[1]
            val first = Regex("<first-name>([^<]+)</first-name>").find(block)?.groupValues?.get(1)?.trim() ?: ""
            val middle = Regex("<middle-name>([^<]+)</middle-name>").find(block)?.groupValues?.get(1)?.trim() ?: ""
            val last = Regex("<last-name>([^<]+)</last-name>").find(block)?.groupValues?.get(1)?.trim() ?: ""
            listOf(first, middle, last).filter { it.isNotEmpty() }.joinToString(" ")
        }.filter { it.isNotEmpty() }.toList()
        if (authorNames.isNotEmpty()) {
            map[context.getString(R.string.doc_author)] = decodeXmlEntities(authorNames.joinToString(", "))
        }

        // Genre
        val genres = Regex("<genre>([^<]+)</genre>").findAll(desc)
            .map { it.groupValues[1].trim() }.toList()
        if (genres.isNotEmpty()) {
            map[context.getString(R.string.doc_genre)] = genres.joinToString(", ")
        }

        // Language
        Regex("<lang>([^<]+)</lang>").find(desc)?.groupValues?.get(1)?.trim()?.let {
            map[context.getString(R.string.doc_language)] = it.uppercase()
        }

        // Series
        Regex("<sequence\\s+name=\"([^\"]+)\"(?:\\s+number=\"(\\d+)\")?", RegexOption.DOT_MATCHES_ALL)
            .find(desc)?.let { m ->
                val name = m.groupValues[1]
                val num = m.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }
                val series = if (num != null) "$name #$num" else name
                map[context.getString(R.string.doc_series)] = decodeXmlEntities(series)
            }

        // Date
        Regex("<date[^>]*>([^<]+)</date>").find(desc)?.groupValues?.get(1)?.trim()?.let {
            map[context.getString(R.string.doc_date)] = it
        }

        // Annotation (first 300 chars)
        Regex("<annotation>(.*?)</annotation>", RegexOption.DOT_MATCHES_ALL).find(desc)?.let {
            val raw = it.groupValues[1]
                .replace(Regex("<[^>]+>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            val decoded = decodeXmlEntities(raw)
            if (decoded.isNotEmpty()) {
                val truncated = if (decoded.length > 300) decoded.take(300) + "…" else decoded
                map[context.getString(R.string.doc_annotation)] = truncated
            }
        }

        return map
    }

    private fun decodeXmlEntities(s: String): String = s
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#160;", " ")

    private fun checkEmbeddedCover(path: String): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            retriever.embeddedPicture != null
        } catch (_: Exception) {
            false
        } finally {
            retriever.release()
        }
    }

    companion object {
        private const val TAG = "Haron/FileProperties"
        private val EXIF_TAGS = listOf(
            ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL,
            ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_IMAGE_WIDTH, ExifInterface.TAG_IMAGE_LENGTH,
            ExifInterface.TAG_ISO_SPEED_RATINGS, ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_WHITE_BALANCE, ExifInterface.TAG_FLASH,
            ExifInterface.TAG_SOFTWARE, ExifInterface.TAG_ARTIST,
            ExifInterface.TAG_COPYRIGHT, ExifInterface.TAG_USER_COMMENT
        )
    }
}
