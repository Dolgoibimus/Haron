package com.vamp.haron.service

import android.content.Context
import androidx.media3.common.Player
import com.vamp.haron.domain.model.PlaylistHolder

/** No-op stub for mini variant. VLC player disabled. */
class VlcPlayerAdapter(private val context: Context) {
    var onTrackFinished: ((filePath: String) -> Unit)? = null
    var onRepeatModeChanged: ((Int) -> Unit)? = null

    fun setPlaylist(items: List<PlaylistHolder.PlaylistItem>, startIndex: Int, autoPlay: Boolean = true) {}
    fun cleanup() {}
    fun getCurrentIndex(): Int = 0
    fun isCurrentlyPlaying(): Boolean = false
    fun getCurrentPositionMs(): Long = 0L
    fun getCurrentDurationMs(): Long = 0L
    fun getCurrentRepeatMode(): Int = Player.REPEAT_MODE_ALL
    fun setRepeat(mode: Int) {}
    fun togglePlay() {}
    fun togglePause() {}
}
