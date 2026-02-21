package com.vamp.haron.domain.model

/**
 * In-process static holder for passing playlist data
 * from ExplorerViewModel to PlaybackService / MediaPlayerScreen.
 */
object PlaylistHolder {
    data class PlaylistItem(
        val filePath: String,
        val fileName: String,
        val fileType: String // "audio" | "video"
    )

    var items: List<PlaylistItem> = emptyList()
    var startIndex: Int = 0
}
