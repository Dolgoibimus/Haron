package com.vamp.haron.domain.usecase.websearch

import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Detects the likely content type(s) for a search query.
 * Fast path 1: known file extension in query → immediate result.
 * Fast path 2: content keyword ("книга", "музыка", "фильм"…) → immediate multi-type result.
 * Slow path: DuckDuckGo instant answers API → entity/abstract analysis.
 */
object IntentDetectorUseCase {

    enum class ContentType(val suggestedExtension: String?) {
        AUDIO("mp3"),
        VIDEO("mkv"),
        BOOK("pdf"),
        DOCUMENT("docx"),
        SOFTWARE("apk"),
        GENERAL(null)
    }

    data class IntentDetectionResult(
        val contentType: ContentType,               // primary type
        val contentTypes: Set<ContentType> = emptySet(), // all types (empty = single primary)
        val heading: String? = null,
        val abstractText: String? = null
    ) {
        /** All effective content types — falls back to singleton set of primary. */
        val effectiveTypes: Set<ContentType>
            get() = contentTypes.ifEmpty { setOf(contentType) }
    }

    private val AUDIO_EXTENSIONS = setOf("mp3", "flac", "wav", "aac", "ogg", "wma", "m4a", "opus")
    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp")
    private val BOOK_EXTENSIONS = setOf("pdf", "epub", "fb2", "djvu", "mobi", "azw3")
    private val DOC_EXTENSIONS = setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "txt", "rtf")
    private val SOFTWARE_EXTENSIONS = setOf("apk", "exe", "msi", "deb", "dmg")

    // Maps QueryParser hint strings to ContentType
    private val HINT_TO_TYPE = mapOf(
        "audio" to ContentType.AUDIO,
        "audiobook" to ContentType.AUDIO, // audiobooks are audio files
        "video" to ContentType.VIDEO,
        "book" to ContentType.BOOK,
        "document" to ContentType.DOCUMENT,
        "software" to ContentType.SOFTWARE,
    )

    suspend fun detect(query: String): IntentDetectionResult = withContext(Dispatchers.IO) {
        val parsed = QueryParser.parse(query)

        // Fast path 1: file extension in query
        if (parsed.extension != null) {
            val fast = when (parsed.extension) {
                in AUDIO_EXTENSIONS -> ContentType.AUDIO
                in VIDEO_EXTENSIONS -> ContentType.VIDEO
                in BOOK_EXTENSIONS -> ContentType.BOOK
                in DOC_EXTENSIONS -> ContentType.DOCUMENT
                in SOFTWARE_EXTENSIONS -> ContentType.SOFTWARE
                else -> ContentType.GENERAL
            }
            EcosystemLogger.d(HaronConstants.TAG, "IntentDetector: fast path ext=${parsed.extension} → $fast")
            return@withContext IntentDetectionResult(contentType = fast)
        }

        // Fast path 2: content keyword(s) in query ("книга", "музыка", "фильм"…)
        if (parsed.contentHints.isNotEmpty()) {
            val types = parsed.contentHints.mapNotNull { HINT_TO_TYPE[it] }.toSet()
            if (types.isNotEmpty()) {
                // Primary type: prefer audio if "audiobook" hint present alongside book
                val primary = when {
                    types.size == 1 -> types.first()
                    ContentType.AUDIO in types && ContentType.BOOK in types -> ContentType.BOOK
                    else -> types.first()
                }
                EcosystemLogger.d(HaronConstants.TAG, "IntentDetector: keyword hints=${parsed.contentHints} → types=$types primary=$primary")
                return@withContext IntentDetectionResult(contentType = primary, contentTypes = types)
            }
        }

        // Slow path: DuckDuckGo instant answers
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val connection = URL("https://api.duckduckgo.com/?q=$encoded&format=json&no_redirect=1&no_html=1&t=HaronApp")
                .openConnection() as HttpURLConnection
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
            )
            connection.connectTimeout = 5_000
            connection.readTimeout = 8_000
            val json = try {
                connection.inputStream.bufferedReader().readText()
            } finally {
                connection.disconnect()
            }
            val result = parseFromJson(json)
            EcosystemLogger.d(HaronConstants.TAG, "IntentDetector: DDG → ${result.contentType} heading=${result.heading} (json len=${json.length})")
            result
        } catch (e: Exception) {
            EcosystemLogger.d(HaronConstants.TAG, "IntentDetector: DDG failed (${e.message}), fallback=GENERAL")
            IntentDetectionResult(contentType = ContentType.GENERAL)
        }
    }

    private fun extractField(json: String, field: String): String? {
        val key = "\"$field\":"
        val start = json.indexOf(key).takeIf { it >= 0 } ?: return null
        val valueStart = start + key.length
        val trimmed = json.substring(valueStart).trimStart()
        if (!trimmed.startsWith('"')) return null
        val sb = StringBuilder()
        var i = 1
        while (i < trimmed.length) {
            val c = trimmed[i]
            if (c == '\\' && i + 1 < trimmed.length) { sb.append(trimmed[i + 1]); i += 2; continue }
            if (c == '"') break
            sb.append(c)
            i++
        }
        return sb.toString().trim().takeIf { it.isNotEmpty() }
    }

    private fun parseFromJson(json: String): IntentDetectionResult {
        val heading = extractField(json, "Heading")
        val abstractText = extractField(json, "AbstractText")?.take(200)
        val lc = json.lowercase()
        val contentType = when {
            lc.contains("\"entity\":\"musician\"") ||
            lc.contains("\"entity\":\"band\"") ||
            lc.contains("\"entity\":\"musical artist\"") ||
            lc.contains("\"entity\":\"album\"") ||
            lc.contains("\"entity\":\"song\"") ||
            lc.contains("music group") || lc.contains("music band") ||
            lc.contains("discography") || lc.contains("\"singer\"") ||
            (lc.contains("rapper") && !lc.contains("wrapper")) -> ContentType.AUDIO

            lc.contains("\"entity\":\"film\"") ||
            lc.contains("\"entity\":\"tv series\"") ||
            lc.contains("\"entity\":\"tv show\"") ||
            lc.contains("box office") ||
            lc.contains("director of the film") -> ContentType.VIDEO

            lc.contains("\"entity\":\"book\"") ||
            lc.contains("\"entity\":\"novel\"") ||
            lc.contains("\"entity\":\"author\"") ||
            lc.contains("novelist") || lc.contains("authored by") ||
            (lc.contains("author") && lc.contains("published")) -> ContentType.BOOK

            lc.contains("\"entity\":\"software\"") ||
            lc.contains("programming language") ||
            lc.contains("open-source software") ||
            lc.contains("mobile app") -> ContentType.SOFTWARE

            else -> ContentType.GENERAL
        }
        return IntentDetectionResult(
            contentType = contentType,
            heading = heading,
            abstractText = abstractText
        )
    }
}
