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
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TorrentSearchUseCase @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend operator fun invoke(query: String): List<WebSearchResult> = coroutineScope {
        val parsed = QueryParser.parse(query)
        val queries = mutableListOf(query) // Use full query for torrent (usually better match)
        if (Transliterator.containsCyrillic(query)) {
            queries.add(Transliterator.transliterate(query))
        }

        EcosystemLogger.d(HaronConstants.TAG, "Torrent: keywords=${parsed.keywords}, ext=${parsed.extension}")

        val deferreds = queries.map { q ->
            async { searchApiBay(q) }
        }
        val allResults = deferreds.flatMap { it.await() }

        // Deduplicate by info_hash (embedded in magnet URL)
        val seen = mutableSetOf<String>()
        val deduped = mutableListOf<WebSearchResult>()
        for (r in allResults) {
            val hash = r.url.substringAfter("btih:", "").substringBefore("&")
            if (hash.isNotEmpty() && seen.add(hash)) {
                deduped.add(r)
            }
        }

        // Filter by extension if user specified one (check in torrent name)
        val filtered = if (parsed.extension != null) {
            deduped.filter { r ->
                val nameLower = r.title.lowercase()
                nameLower.contains(".${parsed.extension}") || nameLower.contains(parsed.extension!!)
            }
        } else {
            deduped
        }

        EcosystemLogger.d(HaronConstants.TAG, "Torrent: ${deduped.size} deduped, ${filtered.size} after ext filter")

        // Sort by seeders desc
        filtered.sortedByDescending { it.seeders }
    }

    private suspend fun searchApiBay(query: String): List<WebSearchResult> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://apibay.org/q.php?q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Haron/1.0")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            response.close()

            val jsonArray = JSONArray(body)
            val results = mutableListOf<WebSearchResult>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val name = obj.optString("name", "")
                val infoHash = obj.optString("info_hash", "")
                val size = obj.optLong("size", -1)
                val seeders = obj.optInt("seeders", 0)

                // Skip "no results found" placeholder
                if (infoHash == "0000000000000000000000000000000000000000" || name.isEmpty()) continue
                if (seeders <= 0) continue

                val encodedName = URLEncoder.encode(name, "UTF-8")
                val magnetUri = "magnet:?xt=urn:btih:$infoHash&dn=$encodedName"

                val ext = name.substringAfterLast('.', "").lowercase()
                    .let { if (it.length > 10) "" else it }

                results.add(
                    WebSearchResult(
                        title = name,
                        url = magnetUri,
                        size = size,
                        source = SearchSource.TORRENT,
                        seeders = seeders,
                        extension = ext,
                        isMagnet = true
                    )
                )
            }

            EcosystemLogger.d(HaronConstants.TAG, "Torrent: ${results.size} results for \"$query\"")
            results
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "Torrent: search failed: ${e.message}")
            emptyList()
        }
    }
}
