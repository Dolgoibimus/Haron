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

    suspend operator fun invoke(query: String): List<WebSearchResult> = coroutineScope {
        val parsed = QueryParser.parse(query)
        val queries = mutableListOf(query)
        if (Transliterator.containsCyrillic(query)) {
            queries.add(Transliterator.transliterate(query))
        }

        EcosystemLogger.d(HaronConstants.TAG, "Archive: keywords=${parsed.keywords}, ext=${parsed.extension}")

        val deferreds = queries.map { q ->
            async { searchArchive(q, parsed) }
        }
        val allResults = deferreds.flatMap { it.await() }

        // Deduplicate by URL
        val seen = mutableSetOf<String>()
        val deduped = mutableListOf<WebSearchResult>()
        for (r in allResults) {
            if (seen.add(r.url)) {
                deduped.add(r)
            }
        }
        deduped
    }

    private suspend fun searchArchive(query: String, parsed: QueryParser.ParsedQuery): List<WebSearchResult> = withContext(Dispatchers.IO) {
        try {
            // Use mediatype filter if extension implies a type
            val mediaFilter = when (parsed.extension) {
                "pdf", "doc", "docx", "txt", "epub", "fb2", "djvu" -> " AND mediatype:texts"
                "mp3", "flac", "wav", "ogg", "m4a" -> " AND mediatype:audio"
                "mp4", "mkv", "avi", "mov", "webm" -> " AND mediatype:movies"
                else -> ""
            }
            val encoded = URLEncoder.encode(query + mediaFilter, "UTF-8")
            val url = "https://archive.org/advancedsearch.php?q=$encoded" +
                    "&fl[]=identifier&fl[]=title&fl[]=item_size" +
                    "&rows=20&output=json"
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

            val results = mutableListOf<WebSearchResult>()
            val fileDeferreds = mutableListOf<Pair<String, String>>() // identifier, title

            for (i in 0 until docs.length()) {
                val doc = docs.getJSONObject(i)
                val identifier = doc.optString("identifier", "")
                val title = doc.optString("title", identifier)
                val itemSize = doc.optLong("item_size", -1)

                if (identifier.isEmpty()) continue

                fileDeferreds.add(identifier to title)
            }

            // Fetch file details for each item
            coroutineScope {
                val detailDeferreds = fileDeferreds.map { (identifier, title) ->
                    async(Dispatchers.IO) { fetchItemFiles(identifier, title, parsed) }
                }
                for (deferred in detailDeferreds) {
                    results.addAll(deferred.await())
                }
            }

            EcosystemLogger.d(HaronConstants.TAG, "Archive: ${results.size} files for \"$query\"")
            results
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "Archive: search failed: ${e.message}")
            emptyList()
        }
    }

    private fun fetchItemFiles(identifier: String, title: String, parsed: QueryParser.ParsedQuery): List<WebSearchResult> {
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

                // Skip metadata files
                if (name.endsWith("_meta.xml") || name.endsWith("_files.xml") ||
                    name.endsWith("_meta.sqlite") || name.endsWith("_archive.torrent") ||
                    format == "Metadata" || format == "Item Tile" ||
                    name == "__ia_thumb.jpg"
                ) continue

                val ext = name.substringAfterLast('.', "").lowercase()

                // Filter by extension if user specified one
                if (parsed.extension != null && ext != parsed.extension) continue

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
