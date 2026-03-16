package com.vamp.haron.domain.usecase.websearch

/**
 * Parses user search query to extract file extension and keywords.
 * Example: "it berufe pdf" → extension="pdf", keywords=["it", "berufe"], searchQuery="it berufe"
 */
object QueryParser {

    data class ParsedQuery(
        val originalQuery: String,
        val searchQuery: String,      // query without extension (for API search)
        val keywords: List<String>,   // lowercase keywords for relevance filtering
        val extension: String?        // detected file extension, null if none
    )

    private val KNOWN_EXTENSIONS = setOf(
        // Documents
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp",
        "txt", "rtf", "csv", "epub", "fb2", "djvu", "mobi", "azw3",
        // Archives
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso",
        // Audio
        "mp3", "flac", "wav", "aac", "ogg", "wma", "m4a", "opus",
        // Video
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp",
        // Images
        "jpg", "jpeg", "png", "gif", "bmp", "svg", "webp", "tiff", "ico",
        // Code / Data
        "apk", "exe", "msi", "deb", "rpm", "dmg", "json", "xml", "sql", "html"
    )

    fun parse(query: String): ParsedQuery {
        val words = query.trim().lowercase().split("\\s+".toRegex())
        var extension: String? = null
        val keywords = mutableListOf<String>()

        for (word in words) {
            val cleaned = word.trim('.', ',', ';')
            if (cleaned in KNOWN_EXTENSIONS && extension == null) {
                extension = cleaned
            } else if (cleaned.isNotEmpty()) {
                keywords.add(cleaned)
            }
        }

        // Build search query without the extension word
        val searchQuery = if (extension != null) {
            keywords.joinToString(" ")
        } else {
            query.trim()
        }

        return ParsedQuery(
            originalQuery = query.trim(),
            searchQuery = searchQuery.ifEmpty { query.trim() },
            keywords = keywords,
            extension = extension
        )
    }
}
