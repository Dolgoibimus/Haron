package com.vamp.haron.domain.usecase.websearch

import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class WebNavigatorPage(
    val url: String,
    val title: String,
    val links: List<WebNavigatorLink>,
    val isLoading: Boolean = false,
    val error: String? = null
)

data class WebNavigatorLink(
    val href: String,      // absolute URL
    val text: String,      // display text
    val isFile: Boolean,   // true if direct file download
    val extension: String? // file extension if isFile
)

@Singleton
class WebNavigateUseCase @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val FILE_EXTENSIONS = setOf(
        "mp3", "flac", "wav", "aac", "ogg", "wma", "m4a", "opus",
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp",
        "pdf", "epub", "fb2", "djvu", "mobi", "azw3",
        "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "rtf", "txt",
        "zip", "rar", "7z", "tar", "gz", "iso",
        "apk", "exe", "msi", "deb", "dmg",
        "jpg", "jpeg", "png", "gif", "bmp", "webp",
        "torrent"
    )

    suspend fun fetch(url: String): WebNavigatorPage = withContext(Dispatchers.IO) {
        try {
            EcosystemLogger.d(HaronConstants.TAG, "WebNavigator: fetching $url")
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext WebNavigatorPage(
                url = url, title = url, links = emptyList(), error = "Empty response"
            )
            response.close()

            val title = extractTitle(body) ?: url.substringAfterLast('/').ifEmpty { url }
            val links = extractLinks(url, body)
            EcosystemLogger.d(HaronConstants.TAG, "WebNavigator: $url → ${links.size} links (${links.count { it.isFile }} files)")
            WebNavigatorPage(url = url, title = title, links = links)
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "WebNavigator: fetch failed for $url: ${e.message}")
            WebNavigatorPage(url = url, title = url, links = emptyList(), error = e.message)
        }
    }

    private fun extractTitle(html: String): String? {
        val match = Regex("""<title[^>]*>([^<]{1,120})</title>""", RegexOption.IGNORE_CASE).find(html)
        return match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun extractLinks(pageUrl: String, html: String): List<WebNavigatorLink> {
        val base = try { URI(pageUrl) } catch (_: Exception) { null }
        val links = mutableListOf<WebNavigatorLink>()
        val seen = mutableSetOf<String>()

        val linkRegex = Regex("""<a\s+[^>]*href\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val stripTags = Regex("""<[^>]+>""")

        for (match in linkRegex.findAll(html)) {
            val href = match.groupValues[1].trim()
            val rawText = match.groupValues[2].replace(stripTags, " ").replace(Regex("\\s+"), " ").trim()

            // Skip useless links
            if (href.isBlank() || href.startsWith("#") || href.startsWith("javascript:")
                || href.startsWith("mailto:") || href == "..") continue
            if (rawText.isEmpty()) continue

            // Resolve to absolute URL
            val absoluteUrl = resolveUrl(base, href) ?: continue
            if (!absoluteUrl.startsWith("http")) continue
            if (!seen.add(absoluteUrl)) continue

            val ext = absoluteUrl.substringAfterLast('.', "")
                .lowercase().substringBefore('?').substringBefore('#')
            val isFile = ext.isNotEmpty() && ext.length <= 6 && ext in FILE_EXTENSIONS

            val displayText = rawText.take(100).ifEmpty { href.substringAfterLast('/').take(80) }

            links.add(WebNavigatorLink(
                href = absoluteUrl,
                text = displayText,
                isFile = isFile,
                extension = if (isFile) ext else null
            ))
            if (links.size >= 100) break
        }
        return links
    }

    private fun resolveUrl(base: URI?, href: String): String? {
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        return try {
            base?.resolve(href)?.toString()
        } catch (_: Exception) { null }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
