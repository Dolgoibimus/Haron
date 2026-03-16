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
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenDirectorySearchUseCase @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend operator fun invoke(query: String): List<WebSearchResult> = coroutineScope {
        val results = mutableListOf<WebSearchResult>()
        val parsed = QueryParser.parse(query)
        val queries = mutableListOf(parsed.searchQuery)
        if (Transliterator.containsCyrillic(parsed.searchQuery)) {
            queries.add(Transliterator.transliterate(parsed.searchQuery))
        }

        EcosystemLogger.d(HaronConstants.TAG, "OpenDirectory: keywords=${parsed.keywords}, ext=${parsed.extension}, searchQuery=\"${parsed.searchQuery}\"")

        val deferreds = queries.map { q ->
            // If extension detected, include it in DDG query for better results
            val ddgQuery = if (parsed.extension != null) "$q ${parsed.extension}" else q
            async { searchDuckDuckGo(ddgQuery) }
        }
        val allDirectoryUrls = deferreds.flatMap { it.await() }.distinct()

        EcosystemLogger.d(HaronConstants.TAG, "OpenDirectory: found ${allDirectoryUrls.size} directory URLs for \"$query\"")

        // Parse each directory listing (limit to 8 to avoid too many requests)
        val fileDeferreds = allDirectoryUrls.take(8).map { url ->
            async { parseDirectoryListing(url, parsed) }
        }
        val allFiles = fileDeferreds.flatMap { it.await() }

        // Deduplicate by URL
        val seen = mutableSetOf<String>()
        for (file in allFiles) {
            if (seen.add(file.url)) {
                results.add(file)
            }
        }
        EcosystemLogger.d(HaronConstants.TAG, "OpenDirectory: ${results.size} files after dedup")
        results
    }

    private suspend fun searchDuckDuckGo(query: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode("intitle:\"index of\" $query", "UTF-8")
            val url = "https://html.duckduckgo.com/html/?q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            response.close()

            // Parse result links from DuckDuckGo HTML
            val links = mutableListOf<String>()
            val linkRegex = Regex("""<a[^>]+class="result__a"[^>]+href="([^"]+)"""")
            for (match in linkRegex.findAll(body)) {
                val href = match.groupValues[1]
                // DuckDuckGo wraps URLs — extract the actual URL
                val actualUrl = extractDdgUrl(href)
                if (actualUrl.startsWith("http")) {
                    links.add(actualUrl)
                }
                if (links.size >= 10) break
            }

            // Fallback: try generic href parsing if class-based didn't work
            if (links.isEmpty()) {
                val fallbackRegex = Regex("""<a[^>]+href="(https?://[^"]+)"[^>]*rel="nofollow"""")
                for (match in fallbackRegex.findAll(body)) {
                    val href = match.groupValues[1]
                    if (!href.contains("duckduckgo.com")) {
                        links.add(href)
                    }
                    if (links.size >= 10) break
                }
            }

            EcosystemLogger.d(HaronConstants.TAG, "OpenDirectory: DDG returned ${links.size} links for \"$query\"")
            links
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "OpenDirectory: DDG search failed: ${e.message}")
            emptyList()
        }
    }

    private fun extractDdgUrl(href: String): String {
        // DuckDuckGo uses //duckduckgo.com/l/?uddg=<encoded_url>&...
        if (href.contains("uddg=")) {
            val start = href.indexOf("uddg=") + 5
            val end = href.indexOf('&', start).let { if (it < 0) href.length else it }
            return java.net.URLDecoder.decode(href.substring(start, end), "UTF-8")
        }
        return href
    }

    private suspend fun parseDirectoryListing(directoryUrl: String, parsed: QueryParser.ParsedQuery): List<WebSearchResult> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(directoryUrl)
                    .header("User-Agent", USER_AGENT)
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext emptyList()
                response.close()

                val results = mutableListOf<WebSearchResult>()
                // Apache/nginx directory listing: <a href="filename">filename</a> optionally followed by size
                val entryRegex = Regex("""<a\s+href="([^"?]+)"[^>]*>([^<]+)</a>\s*(.{0,60})""", RegexOption.IGNORE_CASE)
                val baseUrl = directoryUrl.trimEnd('/')

                for (match in entryRegex.findAll(body)) {
                    val href = match.groupValues[1]
                    val name = match.groupValues[2].trim()
                    val meta = match.groupValues[3].trim()

                    // Skip parent directory links, query strings, and directory links
                    if (href == "../" || href.startsWith("?") || href.startsWith("/") ||
                        name == "Parent Directory" || name == "Name" || name == "Last modified" ||
                        name == "Size" || name == "Description" || href.endsWith("/")
                    ) continue

                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext.length > 10 || ext.isEmpty()) continue // skip non-file entries

                    // Filter by extension if user specified one
                    if (parsed.extension != null && ext != parsed.extension) continue

                    // Filter by keyword relevance — file name must contain at least one keyword
                    val nameLower = name.lowercase()
                    val hasKeyword = parsed.keywords.isEmpty() || parsed.keywords.any { kw ->
                        nameLower.contains(kw)
                    }
                    if (!hasKeyword) continue

                    val fileUrl = if (href.startsWith("http")) href else "$baseUrl/$href"
                    val size = parseSizeFromMeta(meta)

                    results.add(
                        WebSearchResult(
                            title = name,
                            url = fileUrl,
                            size = size,
                            source = SearchSource.OPEN_DIRECTORY,
                            extension = ext
                        )
                    )
                    if (results.size >= 50) break
                }
                EcosystemLogger.d(HaronConstants.TAG, "OpenDirectory: parsed $directoryUrl → ${results.size} relevant files")
                results
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "OpenDirectory: failed to parse $directoryUrl: ${e.message}")
                emptyList()
            }
        }

    private fun parseSizeFromMeta(meta: String): Long {
        // Try to extract size like "1.2M", "500K", "1.5G", or "12345"
        val sizeRegex = Regex("""(\d+\.?\d*)\s*([KMGT]?)""", RegexOption.IGNORE_CASE)
        val match = sizeRegex.find(meta) ?: return -1
        val num = match.groupValues[1].toDoubleOrNull() ?: return -1
        val unit = match.groupValues[2].uppercase()
        return when (unit) {
            "K" -> (num * 1024).toLong()
            "M" -> (num * 1024 * 1024).toLong()
            "G" -> (num * 1024 * 1024 * 1024).toLong()
            "T" -> (num * 1024L * 1024 * 1024 * 1024).toLong()
            else -> if (num > 100) num.toLong() else -1 // raw bytes only if > 100
        }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
