package com.vamp.haron.domain.usecase.websearch

import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.common.util.Transliterator
import com.vamp.haron.domain.model.SearchSource
import com.vamp.haron.domain.model.WebSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InternetArchiveSearchUseCase @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend operator fun invoke(
        query: String,
        contentTypes: Set<IntentDetectorUseCase.ContentType> = setOf(IntentDetectorUseCase.ContentType.GENERAL)
    ): List<WebSearchResult> = coroutineScope {
        val parsed = QueryParser.parse(query)
        val baseQuery = parsed.searchQuery

        // Build queries: plain + creator field, for each language variant
        val langVariants = mutableListOf(baseQuery, "creator:($baseQuery)")
        if (Transliterator.containsCyrillic(baseQuery)) {
            val latin = Transliterator.transliterate(baseQuery)
            langVariants.add(latin)
            langVariants.add("creator:($latin)")
        }

        EcosystemLogger.d(HaronConstants.TAG, "Archive: keywords=${parsed.keywords}, ext=${parsed.extension}, types=$contentTypes")

        // For each content type, run all language variants in parallel
        val deferreds = contentTypes.flatMap { ct ->
            langVariants.map { q -> async { searchArchive(q, parsed, ct) } }
        }
        val allResults = deferreds.flatMap { it.await() }

        // Deduplicate by URL
        val seen = mutableSetOf<String>()
        val deduped = mutableListOf<WebSearchResult>()
        for (r in allResults) {
            if (seen.add(r.url)) deduped.add(r)
        }
        EcosystemLogger.i(HaronConstants.TAG, "Archive: ${deduped.size} total after dedup")
        deduped
    }

    private suspend fun searchArchive(
        query: String,
        parsed: QueryParser.ParsedQuery,
        contentType: IntentDetectorUseCase.ContentType
    ): List<WebSearchResult> = withContext(Dispatchers.IO) {
        try {
            val mediaFilter = when {
                parsed.extension in setOf("pdf", "doc", "docx", "txt", "epub", "fb2", "djvu") -> " AND mediatype:texts"
                parsed.extension in setOf("mp3", "flac", "wav", "ogg", "m4a") -> " AND mediatype:audio"
                parsed.extension in setOf("mp4", "mkv", "avi", "mov", "webm") -> " AND mediatype:movies"
                contentType == IntentDetectorUseCase.ContentType.AUDIO -> " AND mediatype:audio"
                contentType == IntentDetectorUseCase.ContentType.VIDEO -> " AND mediatype:movies"
                contentType == IntentDetectorUseCase.ContentType.BOOK -> " AND mediatype:texts"
                contentType == IntentDetectorUseCase.ContentType.DOCUMENT -> " AND mediatype:texts"
                else -> ""
            }
            val encoded = URLEncoder.encode(query + mediaFilter, "UTF-8")
            val url = "https://archive.org/advancedsearch.php?q=$encoded" +
                    "&fl[]=identifier&fl[]=title&fl[]=item_size" +
                    "&rows=30&output=json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Haron/1.0")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            response.close()

            val json = JSONObject(body)
            val responseObj = json.optJSONObject("response") ?: return@withContext emptyList()
            val docs = responseObj.optJSONArray("docs") ?: return@withContext emptyList()

            val fileDeferreds = mutableListOf<Pair<String, String>>()
            for (i in 0 until docs.length()) {
                val doc = docs.getJSONObject(i)
                val identifier = doc.optString("identifier", "")
                val title = doc.optString("title", identifier)
                if (identifier.isEmpty()) continue
                fileDeferreds.add(identifier to title)
            }

            // Accepted extensions: use broader set for known content types
            val acceptedExts: Set<String>? = when {
                contentType == IntentDetectorUseCase.ContentType.AUDIO ->
                    setOf("mp3", "flac", "ogg", "m4a", "wav", "aac", "opus", "wma")
                contentType == IntentDetectorUseCase.ContentType.VIDEO ->
                    setOf("mp4", "mkv", "avi", "mov", "webm", "flv", "wmv", "m4v")
                contentType == IntentDetectorUseCase.ContentType.BOOK ->
                    setOf("pdf", "epub", "fb2", "djvu", "mobi", "azw3", "txt")
                contentType == IntentDetectorUseCase.ContentType.DOCUMENT ->
                    setOf("pdf", "doc", "docx", "odt", "rtf", "txt", "xls", "xlsx")
                parsed.extension != null -> setOf(parsed.extension)
                else -> null
            }

            val results = mutableListOf<WebSearchResult>()
            coroutineScope {
                val detailDeferreds = fileDeferreds.map { (id, title) ->
                    async(Dispatchers.IO) { fetchItemFiles(id, title, acceptedExts) }
                }
                for (d in detailDeferreds) results.addAll(d.await())
            }

            EcosystemLogger.d(HaronConstants.TAG, "Archive: ${results.size} files for \"$query\" [$contentType]")
            results
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "Archive: search failed: ${e.message}")
            emptyList()
        }
    }

    private fun fetchItemFiles(identifier: String, title: String, acceptedExts: Set<String>?): List<WebSearchResult> {
        return try {
            val url = "https://archive.org/metadata/$identifier/files"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Haron/1.0")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()
            response.close()

            val json = JSONObject(body)
            val result = json.optJSONArray("result") ?: return emptyList()

            val files = mutableListOf<WebSearchResult>()
            for (i in 0 until result.length()) {
                val file = result.getJSONObject(i)
                val name = file.optString("name", "")
                val size = file.optString("size", "-1").toLongOrNull() ?: -1
                val format = file.optString("format", "")

                if (name.endsWith("_meta.xml") || name.endsWith("_files.xml") ||
                    name.endsWith("_meta.sqlite") || name.endsWith("_archive.torrent") ||
                    format == "Metadata" || format == "Item Tile" ||
                    name == "__ia_thumb.jpg"
                ) continue

                val ext = name.substringAfterLast('.', "").lowercase()
                if (acceptedExts != null && ext !in acceptedExts) continue

                val downloadUrl = "https://archive.org/download/$identifier/$name"
                files.add(
                    WebSearchResult(
                        title = "$title — $name",
                        url = downloadUrl,
                        size = size,
                        source = SearchSource.INTERNET_ARCHIVE,
                        extension = ext
                    )
                )
                if (files.size >= 10) break
            }
            EcosystemLogger.d(HaronConstants.TAG, "Archive: $identifier → ${files.size} relevant files")
            files
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "Archive: failed to fetch files for $identifier: ${e.message}")
            emptyList()
        }
    }
}
