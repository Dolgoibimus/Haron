package com.vamp.haron.domain.usecase.websearch

/**
 * Parses user search query to extract file extension, content type hints, and keywords.
 * Examples:
 *   "diana ankudinova mp3"  → extension="mp3", keywords=["diana","ankudinova"], contentHints={}
 *   "книга война и мир"     → extension=null,  keywords=["война","мир"],       contentHints={"book"}
 *   "война и мир книга аудиокнига" → contentHints={"book","audiobook"}
 */
object QueryParser {

    data class ParsedQuery(
        val originalQuery: String,
        val searchQuery: String,      // query without extension word AND content type words
        val keywords: List<String>,   // lowercase keywords for relevance filtering
        val extension: String?,       // detected file extension, null if none
        val contentHints: Set<String> // detected content type hints ("book","audio","video",...)
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

    // Maps content type words (in any language) to hint strings
    val CONTENT_TYPE_WORDS: Map<String, String> = mapOf(
        // AUDIO
        "музыка" to "audio", "music" to "audio",
        "песня" to "audio", "песни" to "audio",
        "song" to "audio", "songs" to "audio",
        "альбом" to "audio", "album" to "audio",
        "трек" to "audio", "tracks" to "audio", "track" to "audio",
        "аудио" to "audio",
        // AUDIOBOOK (also audio type)
        "аудиокнига" to "audiobook", "аудиокниги" to "audiobook",
        "audiobook" to "audiobook", "audiobooks" to "audiobook",
        // VIDEO
        "фильм" to "video", "фильмы" to "video",
        "кино" to "video", "cinema" to "video",
        "movie" to "video", "movies" to "video",
        "film" to "video", "films" to "video",
        "видео" to "video", "video" to "video",
        "сериал" to "video", "сериалы" to "video",
        "series" to "video", "serial" to "video",
        "аниме" to "video", "anime" to "video",
        "мультфильм" to "video", "cartoon" to "video",
        // BOOK
        "книга" to "book", "книги" to "book",
        "book" to "book", "books" to "book",
        "роман" to "book", "романы" to "book",
        "учебник" to "book", "учебники" to "book",
        "читать" to "book", "читаю" to "book",
        "литература" to "book", "literature" to "book",
        // DOCUMENT
        "документ" to "document", "документы" to "document",
        "document" to "document", "documents" to "document",
        "статья" to "document", "статьи" to "document",
        "article" to "document", "articles" to "document",
        "реферат" to "document", "доклад" to "document",
        "диссертация" to "document", "thesis" to "document",
        // SOFTWARE
        "программа" to "software", "программы" to "software",
        "приложение" to "software", "приложения" to "software",
        "software" to "software", "app" to "software",
        "игра" to "software", "игры" to "software",
        "game" to "software", "games" to "software",
    )

    fun parse(query: String): ParsedQuery {
        val words = query.trim().lowercase().split("\\s+".toRegex())
        var extension: String? = null
        val keywords = mutableListOf<String>()
        val contentHints = mutableSetOf<String>()

        for (word in words) {
            val cleaned = word.trim('.', ',', ';')
            when {
                cleaned in KNOWN_EXTENSIONS && extension == null -> extension = cleaned
                CONTENT_TYPE_WORDS.containsKey(cleaned) -> contentHints.add(CONTENT_TYPE_WORDS[cleaned]!!)
                cleaned.isNotEmpty() -> keywords.add(cleaned)
            }
        }

        // searchQuery: actual search terms without extension word and without content type words
        val searchQuery = keywords.joinToString(" ").ifEmpty { query.trim() }

        return ParsedQuery(
            originalQuery = query.trim(),
            searchQuery = searchQuery,
            keywords = keywords,
            extension = extension,
            contentHints = contentHints
        )
    }
}
