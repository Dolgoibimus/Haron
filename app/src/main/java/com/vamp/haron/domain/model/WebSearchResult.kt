package com.vamp.haron.domain.model

data class WebSearchResult(
    val title: String,
    val url: String,
    val size: Long = -1,
    val source: SearchSource,
    val seeders: Int = 0,
    val extension: String = "",
    val isMagnet: Boolean = false
)

enum class SearchSource { OPEN_DIRECTORY, TORRENT, INTERNET_ARCHIVE }
