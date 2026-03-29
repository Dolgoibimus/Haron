package com.vamp.haron.domain.model

/**
 * State of torrent streaming operation.
 */
sealed class TorrentStreamState {
    data object Idle : TorrentStreamState()
    data object FetchingMetadata : TorrentStreamState()
    data class Buffering(val percent: Int, val downloadSpeed: Long, val piecesDownloaded: Int = 0) : TorrentStreamState()
    data class Ready(val filePath: String, val fileName: String) : TorrentStreamState()
    data class Streaming(
        val filePath: String,
        val fileName: String,
        val progress: Int,
        val downloadSpeed: Long,
        val seeds: Int,
        val peers: Int
    ) : TorrentStreamState()
    data class Error(val message: String) : TorrentStreamState()
}

/**
 * File inside a torrent for selection UI.
 */
data class TorrentFileInfo(
    val index: Int,
    val name: String,
    val size: Long,
    val isVideo: Boolean
)
