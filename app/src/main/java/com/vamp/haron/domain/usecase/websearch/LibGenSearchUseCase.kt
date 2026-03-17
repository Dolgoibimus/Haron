package com.vamp.haron.domain.usecase.websearch

import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.domain.model.SearchSource
import com.vamp.haron.domain.model.WebSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Searches Library Genesis (libgen.is) for books and documents.
 * No API key needed. Returns direct download links via download.library.lol.
 */
@Singleton
class LibGenSearchUseCase @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend operator fun invoke(
        query: String,
        contentTypes: Set<IntentDetectorUseCase.ContentType> = setOf(IntentDetectorUseCase.ContentType.GENERAL)
    ): List<WebSearchResult> = withContext(Dispatchers.IO) {
        // LibGen serves books/documents. Skip if none of the requested types are book/document/general.
        val hasRelevantType = contentTypes.any { ct ->
            ct == IntentDetectorUseCase.ContentType.BOOK ||
            ct == IntentDetectorUseCase.ContentType.DOCUMENT ||
            ct == IntentDetectorUseCase.ContentType.GENERAL
        }
        if (!hasRelevantType) return@withContext emptyList()

        try {
            val parsed = QueryParser.parse(query)
            val searchQuery = parsed.searchQuery

            // Build accepted extensions from all requested types
            val acceptedExts: Set<String>? = if (parsed.extension != null) {
                setOf(parsed.extension)
            } else {
                val exts = buildSet {
                    for (ct in contentTypes) when (ct) {
                        IntentDetectorUseCase.ContentType.BOOK ->
                            addAll(listOf("pdf", "epub", "fb2", "djvu", "mobi", "azw3", "txt"))
                        IntentDetectorUseCase.ContentType.DOCUMENT ->
                            addAll(listOf("pdf", "doc", "docx", "txt", "odt", "rtf", "xls", "xlsx"))
                        IntentDetectorUseCase.ContentType.GENERAL -> {} // null = accept all
                        else -> {}
                    }
                }
                exts.ifEmpty { null } // GENERAL with no book/doc → accept all
            }

            EcosystemLogger.d(HaronConstants.TAG, "LibGen: searching \"$searchQuery\" exts=$acceptedExts")

            val encoded = URLEncoder.encode(searchQuery, "UTF-8")
            val url = "https://libgen.is/search.php?req=$encoded" +
                    "&res=25&view=simple&phrase=1&column=def&lg_topic=libgen&open=0"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            response.close()

            val results = parseResults(body, acceptedExts)
            EcosystemLogger.i(HaronConstants.TAG, "LibGen: ${results.size} results for \"$searchQuery\"")
            results
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "LibGen: search failed: ${e.message}")
            emptyList()
        }
    }

    private fun parseResults(html: String, acceptedExts: Set<String>?): List<WebSearchResult> {
        val results = mutableListOf<WebSearchResult>()
        val stripTags = Regex("""<[^>]+>""")

        // Match table rows containing a book md5 link
        val rowRegex = Regex("""<tr[^>]*>(.*?)</tr>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val md5Regex = Regex("""book/index\.php\?md5=([A-Fa-f0-9]{32})""", RegexOption.IGNORE_CASE)

        for (rowMatch in rowRegex.findAll(html)) {
            val row = rowMatch.groupValues[1]
            val md5 = md5Regex.find(row)?.groupValues?.get(1) ?: continue

            // Extract all <td> text content
            val tds = Regex("""<td[^>]*>(.*?)</td>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(row)
                .map { it.groupValues[1].replace(stripTags, " ").replace(Regex("\\s+"), " ").trim() }
                .filter { it.isNotEmpty() }
                .toList()

            // LibGen column order: ID, Author, Title, Publisher, Year, Pages, Language, Size, Extension
            if (tds.size < 7) continue
            val author = tds.getOrNull(1)?.take(80) ?: ""
            val title = tds.getOrNull(2)?.take(120) ?: continue
            if (title.isBlank()) continue
            val ext = tds.getOrNull(8)?.lowercase()?.trim()?.takeIf { it.length in 2..6 } ?: "pdf"
            val sizeStr = tds.getOrNull(7) ?: ""
            val size = parseSizeString(sizeStr)

            if (acceptedExts != null && ext !in acceptedExts) continue

            // download.library.lol/main/{md5} redirects to the actual file on a mirror
            val downloadUrl = "https://download.library.lol/main/${md5.lowercase()}"
            val displayTitle = if (author.isNotEmpty()) "$title — $author" else title

            results.add(
                WebSearchResult(
                    title = displayTitle,
                    url = downloadUrl,
                    size = size,
                    source = SearchSource.LIBGEN,
                    extension = ext
                )
            )
            if (results.size >= 20) break
        }
        return results
    }

    private fun parseSizeString(s: String): Long {
        val m = Regex("""(\d+(?:[.,]\d+)?)\s*([KMGkm][Bb]?)""").find(s) ?: return -1
        val num = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return -1
        return when (m.groupValues[2].uppercase().first()) {
            'K' -> (num * 1024).toLong()
            'M' -> (num * 1024 * 1024).toLong()
            'G' -> (num * 1024 * 1024 * 1024).toLong()
            else -> num.toLong()
        }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    }
}
