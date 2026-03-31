package com.vamp.haron.domain.repository

import com.vamp.haron.domain.model.TorrentFileInfo
import com.vamp.haron.domain.model.TorrentStreamState
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for torrent streaming.
 * Full flavor: real implementation with libtorrent4j.
 * Play flavor: no-op stub.
 */
interface TorrentStreamRepository {

    val state: StateFlow<TorrentStreamState>

    /**
     * Start streaming from magnet link or .torrent file path.
     * @param uri magnet:?xt=... or file path to .torrent
     * @param fileIndex index of file to stream (-1 = auto-select largest video)
     */
    suspend fun startStream(uri: String, fileIndex: Int = -1)

    /**
     * Get list of files inside torrent (after metadata fetched).
     */
    suspend fun getFiles(uri: String): List<TorrentFileInfo>

    /**
     * Stop streaming and clean up.
     */
    fun stopStream()

    /**
     * Clean torrent cache (downloaded temp files).
     */
    suspend fun cleanCache()

    /**
     * Whether torrent streaming is available in this build.
     */
    val isAvailable: Boolean

    /**
     * Check if a specific piece has been downloaded.
     */
    fun havePiece(pieceIndex: Int): Boolean = false

    /**
     * Get the piece index that contains the given byte offset within the target file.
     */
    fun pieceIndexForOffset(offset: Long): Int = -1

    /**
     * Wait until the given piece is downloaded.
     * Returns true if piece is available, false on timeout.
     */
    suspend fun waitForPiece(pieceIndex: Int, timeoutMs: Long = 30_000): Boolean = false

    /**
     * Piece length in bytes.
     */
    val pieceLength: Long get() = 0

    /**
     * First piece index of the target file.
     */
    val firstPieceIndex: Int get() = 0

    /**
     * Target file total size.
     */
    val streamFileSize: Long get() = 0

    /**
     * Target file path on disk.
     */
    val streamFilePath: String get() = ""
}
