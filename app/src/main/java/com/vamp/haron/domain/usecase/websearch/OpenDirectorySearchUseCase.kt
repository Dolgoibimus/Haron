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
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend operator fun invoke(
        query: String,
        contentTypes: Set<IntentDetectorUseCase.ContentType> = setOf(IntentDetectorUseCase.ContentType.GENERAL)
    ): List<WebSearchResult> = coroutineScope {
        val parsed = QueryParser.parse(query)
        val artistQuery = parsed.searchQuery
        val queries = buildList {
            add(artistQuery)
            if (Transliterator.containsCyrillic(artistQuery)) add(Transliterator.transliterate(artistQuery))
        }

        EcosystemLogger.d(HaronConstants.TAG, "OpenDirectory: contentTypes=$contentTypes, artistQuery=\"$artistQuery\"")

        // Accepted extensions for direct file filtering
        val acceptedExts = buildAcceptedExts(parsed, contentTypes)

        // Extension group for search dorks
        val extGroup = if (parsed.extension != null) {
            parsed.extension
        } else {
            val exts = buildList {
                for (ct in contentTypes) when (ct) {
                    IntentDetectorUseCase.ContentType.AUDIO -> addAll(listOf("mp3", "flac", "ogg", "m4a", "wav"))
                    IntentDetectorUseCase.ContentType.VIDEO -> addAll(listOf("mp4", "mkv", "avi", "mov", "webm"))
                    IntentDetectorUseCase.ContentType.BOOK -> addAll(listOf("pdf", "epub", "fb2", "djvu"))
                    IntentDetectorUseCase.ContentType.DOCUMENT -> addAll(listOf("pdf", "doc", "docx", "txt"))
                    IntentDetectorUseCase.ContentType.SOFTWARE -> addAll(listOf("apk", "exe", "msi"))
                    IntentDetectorUseCase.ContentType.GENERAL -> {}
                }
            }.distinct()
            if (exts.isEmpty()) null else "(${exts.joinToString("|")})"
        }

        val relaxKeywords = parsed.extension != null ||
                contentTypes.none { it == IntentDetectorUseCase.ContentType.GENERAL }

        // Phase 1: Specialized OD indexes — return direct file links (no directory parsing)
        val mmntDeferred = async { searchMmntRu(queries, acceptedExts) }
        val filePursuitDeferred = async { searchFilePursuit(queries.first(), acceptedExts) }

        // Phase 2: Search engine dorks → directory URLs → parse directory listings
        val engineDeferreds = queries.map { q -> async { searchEngines(q, extGroup) } }
        val directoryUrls = engineDeferreds.flatMap { it.await() }.distinct()
        EcosystemLogger.d(HaronConstants.TAG, "OpenDirectory: ${directoryUrls.size} directory URLs from engines")
        val dirDeferreds = directoryUrls.take(10).map { url ->
            async { parseDirectoryListing(url, parsed, contentTypes, relaxKeywords) }
        }

        // Merge and deduplicate
        val all = mutableListOf<WebSearchResult>()
        all.addAll(mmntDeferred.await())
        all.addAll(filePursuitDeferred.await())
        all.addAll(dirDeferreds.flatMap { it.await() })

        val seen = mutableSetOf<String>()
        val deduped = mutableListOf<WebSearchResult>()
        for (r in all) { if (seen.add(r.url)) deduped.add(r) }
        EcosystemLogger.i(HaronConstants.TAG, "OpenDirectory: ${deduped.size} files total")
        deduped
    }

    /**
     * mmnt.ru — Russian open directory search engine.
     * Has its own crawled index of FTP/HTTP servers since ~2000.
     * Returns direct file links, no search engine needed.
     */
    private suspend fun searchMmntRu(
        queries: List<String>,
        acceptedExts: Set<String>?
    ): List<WebSearchResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<WebSearchResult>()
        for (q in queries.take(2)) {
            try {
                val encoded = URLEncoder.encode(q, "UTF-8")
                val url = "https://www.mmnt.ru/int/?q=$encoded"
                EcosystemLogger.d(HaronConstants.TAG, "mmnt.ru: GET $url")
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", DESKTOP_UA)
                    .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")
                    .header("Referer", "https://www.mmnt.ru/")
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: continue
                response.close()
                EcosystemLogger.d(HaronConstants.TAG, "mmnt.ru: response length=${body.length}, snippet=${body.take(300)}")

                val extPat = acceptedExts?.joinToString("|") ?: FILE_EXTS_PATTERN
                // Strategy 1: href with direct ftp/http file links
                val hrefRegex = Regex("""href="((?:ftp|https?)://[^"]+\.(?:$extPat))"""", RegexOption.IGNORE_CASE)
                for (match in hrefRegex.findAll(body)) {
                    val fileUrl = match.groupValues[1]
                    val name = fileUrl.substringAfterLast('/').decodeUrl()
                    val ext = name.substringAfterLast('.').lowercase()
                    if (acceptedExts != null && ext !in acceptedExts) continue
                    results.add(WebSearchResult(title = name, url = fileUrl, size = -1, source = SearchSource.OPEN_DIRECTORY, extension = ext))
                    if (results.size >= 30) break
                }
                // Strategy 2: plain-text URLs in page body
                if (results.isEmpty()) {
                    val plainRegex = Regex("""((?:ftp|https?)://\S+\.(?:$extPat))""", RegexOption.IGNORE_CASE)
                    for (match in plainRegex.findAll(body)) {
                        val fileUrl = match.groupValues[1].trimEnd(')', ';', ',', '"', '\'')
                        if (fileUrl.contains("mmnt.ru")) continue
                        val name = fileUrl.substringAfterLast('/').decodeUrl()
                        val ext = name.substringAfterLast('.').lowercase()
                        if (acceptedExts != null && ext !in acceptedExts) continue
                        results.add(WebSearchResult(title = name, url = fileUrl, size = -1, source = SearchSource.OPEN_DIRECTORY, extension = ext))
                        if (results.size >= 30) break
                    }
                }
                EcosystemLogger.i(HaronConstants.TAG, "mmnt.ru: ${results.size} files for \"$q\"")
                if (results.isNotEmpty()) break
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "mmnt.ru: failed: ${e.message}")
            }
        }
        results
    }

    /**
     * FilePursuit — international open directory search engine.
     * Has its own web-crawled index of open HTTP servers.
     */
    private suspend fun searchFilePursuit(
        query: String,
        acceptedExts: Set<String>?
    ): List<WebSearchResult> = withContext(Dispatchers.IO) {
        try {
            val type = when {
                acceptedExts != null && acceptedExts.any { it in AUDIO_EXTS } -> "audio"
                acceptedExts != null && acceptedExts.any { it in VIDEO_EXTS } -> "video"
                acceptedExts != null && acceptedExts.any { it in DOC_EXTS }   -> "document"
                else -> "file"
            }
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://filepursuit.com/pursuit?q=$encoded&type=$type&searchin=file"
            EcosystemLogger.d(HaronConstants.TAG, "FilePursuit: GET $url")
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", DESKTOP_UA)
                .header("Referer", "https://filepursuit.com/")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            response.close()
            EcosystemLogger.d(HaronConstants.TAG, "FilePursuit: response length=${body.length}, snippet=${body.take(300)}")

            val results = mutableListOf<WebSearchResult>()
            val extPat = acceptedExts?.joinToString("|") ?: FILE_EXTS_PATTERN
            val linkRegex = Regex("""href="(https?://(?!filepursuit\.com)[^"]+\.(?:$extPat))"""", RegexOption.IGNORE_CASE)
            for (match in linkRegex.findAll(body)) {
                val fileUrl = match.groupValues[1]
                val name = fileUrl.substringAfterLast('/').decodeUrl()
                val ext = name.substringAfterLast('.').lowercase()
                if (acceptedExts != null && ext !in acceptedExts) continue
                results.add(WebSearchResult(title = name, url = fileUrl, size = -1, source = SearchSource.OPEN_DIRECTORY, extension = ext))
                if (results.size >= 20) break
            }
            EcosystemLogger.i(HaronConstants.TAG, "FilePursuit: ${results.size} files for \"$query\"")
            results
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "FilePursuit: failed: ${e.message}")
            emptyList()
        }
    }

    /** Tries Yandex → SearXNG to find open directory URLs for dorks.
     *  DDG removed (bot detection, always 0). Bing removed (timeout). */
    private suspend fun searchEngines(artistQuery: String, extGroup: String?): List<String> =
        withContext(Dispatchers.IO) {
            val dorks = buildList {
                if (extGroup != null) {
                    add("intitle:\"index of\" -inurl:(htm|html|php|asp) $extGroup \"$artistQuery\"")
                    add("intitle:\"index of\" $extGroup $artistQuery")
                } else {
                    add("intitle:\"index of\" \"$artistQuery\"")
                    add("intitle:\"index of\" $artistQuery")
                }
            }
            EcosystemLogger.d(HaronConstants.TAG, "OpenDirectory: dork[0]=\"${dorks[0]}\"")

            // Yandex first — better for Cyrillic content, less bot-hostile than Google/Bing
            val yandexLinks = searchYandexHtml(dorks[0])
            if (yandexLinks.isNotEmpty()) {
                EcosystemLogger.d(HaronConstants.TAG, "OpenDirectory: Yandex → ${yandexLinks.size} links")
                return@withContext yandexLinks
            }

            // SearXNG fallback — useful when an instance is alive
            val searxLinks = searchSearXNG(dorks[0])
            if (searxLinks.isNotEmpty()) {
                EcosystemLogger.d(HaronConstants.TAG, "OpenDirectory: SearXNG → ${searxLinks.size} links")
                return@withContext searxLinks
            }

            EcosystemLogger.d(HaronConstants.TAG, "OpenDirectory: all engines 0 links for \"$artistQuery\"")
            emptyList()
        }

    private suspend fun searchSearXNG(dork: String): List<String> = withContext(Dispatchers.IO) {
        // Only 2 instances kept — others are consistently rate-limited
        val instances = listOf(
            "https://paulgo.io/search",
            "https://searx.be/search"
        )
        val encoded = URLEncoder.encode(dork, "UTF-8")
        for (instance in instances) {
            try {
                val url = "$instance?q=$encoded&format=json&language=all&categories=general"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", MOBILE_UA)
                    .header("Accept", "application/json")
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: continue
                response.close()
                if (!body.startsWith("{")) {
                    EcosystemLogger.d(HaronConstants.TAG, "OpenDirectory: SearXNG ($instance) non-JSON: ${body.take(80)}")
                    continue
                }
                val links = mutableListOf<String>()
                val urlRegex = Regex(""""url"\s*:\s*"(https?://[^"]+)"""")
                for (match in urlRegex.findAll(body)) {
                    val link = match.groupValues[1]
                    if (!link.contains("searx") && !link.contains("opnxng") &&
                        !link.contains("paulgo") && link.startsWith("http")) {
                        links.add(link)
                    }
                    if (links.size >= 15) break
                }
                if (links.isNotEmpty()) {
                    EcosystemLogger.d(HaronConstants.TAG, "OpenDirectory: SearXNG ($instance) → ${links.size} links")
                    return@withContext links
                }
            } catch (e: Exception) {
                EcosystemLogger.d(HaronConstants.TAG, "OpenDirectory: SearXNG ($instance) failed: ${e.message}")
            }
        }
        emptyList()
    }

    private suspend fun searchYandexHtml(dork: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(dork, "UTF-8")
            // lr=225 = Russia, noreask=1 = no query correction
            val url = "https://yandex.ru/search/?text=$encoded&lr=225&noreask=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", DESKTOP_UA)
                .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            response.close()
            EcosystemLogger.d(HaronConstants.TAG, "Yandex: response length=${body.length}, snippet=${body.take(200)}")

            val yandexDomains = setOf("yandex.ru", "yandex.com", "ya.ru", "yastatic.net", "yandex.net")
            val links = mutableListOf<String>()

            // Strategy 1: data-url attribute (Yandex embeds direct result URL here)
            val dataUrlRegex = Regex("""data-url="(https?://[^"]+)"""")
            for (match in dataUrlRegex.findAll(body)) {
                val link = match.groupValues[1]
                if (yandexDomains.none { link.contains(it) }) {
                    links.add(link)
                    if (links.size >= 15) break
                }
            }
            // Strategy 2: href on organic result anchors
            if (links.isEmpty()) {
                val hrefRegex = Regex("""href="(https?://[^"]+)"[^>]*class="[^"]*(?:organic|serp-item)[^"]*"""")
                for (match in hrefRegex.findAll(body)) {
                    val link = match.groupValues[1]
                    if (yandexDomains.none { link.contains(it) }) {
                        links.add(link)
                        if (links.size >= 15) break
                    }
                }
            }
            // Strategy 3: <cite> elements contain displayed result URLs
            if (links.isEmpty()) {
                val citeRegex = Regex("""<cite[^>]*>\s*(https?://[^\s<&]+)""")
                for (match in citeRegex.findAll(body)) {
                    val link = match.groupValues[1].trimEnd('/')
                    if (yandexDomains.none { link.contains(it) }) {
                        links.add(link)
                        if (links.size >= 15) break
                    }
                }
            }
            if (links.isEmpty()) {
                EcosystemLogger.d(HaronConstants.TAG, "Yandex: 0 links, html[3000..3500]=${body.drop(3000).take(500)}")
            }
            EcosystemLogger.d(HaronConstants.TAG, "Yandex: → ${links.size} links")
            links
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "Yandex: failed: ${e.message}")
            emptyList()
        }
    }

    private suspend fun parseDirectoryListing(
        directoryUrl: String,
        parsed: QueryParser.ParsedQuery,
        contentTypes: Set<IntentDetectorUseCase.ContentType>,
        relaxKeywords: Boolean
    ): List<WebSearchResult> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(directoryUrl)
                .header("User-Agent", MOBILE_UA)
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            response.close()

            val typeExtensions = buildAcceptedExts(parsed, contentTypes)
            val results = mutableListOf<WebSearchResult>()
            val entryRegex = Regex("""<a\s+href="([^"?]+)"[^>]*>([^<]+)</a>\s*(.{0,60})""", RegexOption.IGNORE_CASE)
            val baseUrl = directoryUrl.trimEnd('/')

            for (match in entryRegex.findAll(body)) {
                val href = match.groupValues[1]
                val name = match.groupValues[2].trim()
                val meta = match.groupValues[3].trim()

                if (href == "../" || href.startsWith("?") || href.startsWith("/") ||
                    name == "Parent Directory" || name == "Name" || name == "Last modified" ||
                    name == "Size" || name == "Description" || href.endsWith("/")
                ) continue

                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext.length > 10 || ext.isEmpty()) continue
                if (typeExtensions != null && ext !in typeExtensions) continue

                if (!relaxKeywords) {
                    val nameLower = name.lowercase()
                    val hasKeyword = parsed.keywords.isEmpty() ||
                            parsed.keywords.any { kw -> nameLower.contains(kw) }
                    if (!hasKeyword) continue
                }

                val fileUrl = if (href.startsWith("http")) href else "$baseUrl/$href"
                val size = parseSizeFromMeta(meta)
                results.add(WebSearchResult(title = name, url = fileUrl, size = size, source = SearchSource.OPEN_DIRECTORY, extension = ext))
                if (results.size >= 50) break
            }
            EcosystemLogger.d(HaronConstants.TAG, "OpenDirectory: parsed $directoryUrl → ${results.size} files (relaxed=$relaxKeywords)")
            results
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "OpenDirectory: failed to parse $directoryUrl: ${e.message}")
            emptyList()
        }
    }

    private fun buildAcceptedExts(
        parsed: QueryParser.ParsedQuery,
        contentTypes: Set<IntentDetectorUseCase.ContentType>
    ): Set<String>? {
        if (parsed.extension != null) return setOf(parsed.extension)
        val exts = buildSet {
            for (ct in contentTypes) when (ct) {
                IntentDetectorUseCase.ContentType.AUDIO ->
                    addAll(listOf("mp3", "flac", "ogg", "m4a", "wav", "aac", "wma", "opus"))
                IntentDetectorUseCase.ContentType.VIDEO ->
                    addAll(listOf("mp4", "mkv", "avi", "mov", "wmv", "webm", "m4v"))
                IntentDetectorUseCase.ContentType.BOOK ->
                    addAll(listOf("pdf", "epub", "fb2", "djvu", "mobi", "azw3"))
                IntentDetectorUseCase.ContentType.DOCUMENT ->
                    addAll(listOf("pdf", "doc", "docx", "txt", "odt", "rtf", "xls", "xlsx"))
                IntentDetectorUseCase.ContentType.SOFTWARE ->
                    addAll(listOf("apk", "exe", "msi", "deb", "dmg"))
                IntentDetectorUseCase.ContentType.GENERAL -> {}
            }
        }
        return exts.ifEmpty { null }
    }

    private fun parseSizeFromMeta(meta: String): Long {
        val sizeRegex = Regex("""(\d+\.?\d*)\s*([KMGT]?)""", RegexOption.IGNORE_CASE)
        val match = sizeRegex.find(meta) ?: return -1
        val num = match.groupValues[1].toDoubleOrNull() ?: return -1
        val unit = match.groupValues[2].uppercase()
        return when (unit) {
            "K" -> (num * 1024).toLong()
            "M" -> (num * 1024 * 1024).toLong()
            "G" -> (num * 1024 * 1024 * 1024).toLong()
            "T" -> (num * 1024L * 1024 * 1024 * 1024).toLong()
            else -> if (num > 100) num.toLong() else -1
        }
    }

    private fun String.decodeUrl(): String = try {
        java.net.URLDecoder.decode(this, "UTF-8")
    } catch (_: Exception) { this }

    companion object {
        private const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        private const val FILE_EXTS_PATTERN =
            "mp3|flac|ogg|m4a|wav|aac|opus|wma|mp4|mkv|avi|mov|webm|m4v|pdf|epub|fb2|djvu|doc|docx|zip|rar|apk|exe|txt|mobi"
        private val AUDIO_EXTS = setOf("mp3", "flac", "ogg", "m4a", "wav", "aac", "opus", "wma")
        private val VIDEO_EXTS = setOf("mp4", "mkv", "avi", "mov", "webm", "m4v", "wmv")
        private val DOC_EXTS   = setOf("pdf", "epub", "fb2", "djvu", "doc", "docx", "txt", "mobi")
    }
}
