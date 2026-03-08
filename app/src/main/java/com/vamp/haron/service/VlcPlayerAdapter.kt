package com.vamp.haron.service

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.vamp.haron.data.datastore.VideoPositionStore
import com.vamp.haron.domain.model.PlaylistHolder
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer as VlcMediaPlayer

@UnstableApi
class VlcPlayerAdapter(
    private val context: Context,
    private val vlcPlayer: VlcMediaPlayer,
    private val libVlc: LibVLC
) : SimpleBasePlayer(Looper.getMainLooper()) {

    private val handler = Handler(Looper.getMainLooper())
    private var playlist: List<PlaylistHolder.PlaylistItem> = emptyList()
    private var currentIndex: Int = 0
    /** Called when a track finishes — used to clear stale positions in ReadingPositionManager */
    var onTrackFinished: ((filePath: String) -> Unit)? = null
    /** Called when repeat mode changes — used to update notification custom layout */
    var onRepeatModeChanged: ((Int) -> Unit)? = null
    private var _isPlaying: Boolean = false
    private var _hasMedia: Boolean = false
    private var positionMs: Long = 0L
    private var durationMs: Long = 0L
    private var _repeatMode: Int = Player.REPEAT_MODE_ALL
    private var _isTransitioning: Boolean = false

    init {
        EcosystemLogger.d(HaronConstants.TAG, "VlcPlayerAdapter: initialized")
        vlcPlayer.setEventListener { event ->
            when (event.type) {
                VlcMediaPlayer.Event.Playing -> {
                    _isPlaying = true
                    _isTransitioning = false
                    invalidateState()
                }
                VlcMediaPlayer.Event.Paused -> {
                    _isPlaying = false
                    invalidateState()
                }
                VlcMediaPlayer.Event.Stopped -> {
                    if (!_isTransitioning) {
                        _isPlaying = false
                        invalidateState()
                    }
                }
                VlcMediaPlayer.Event.TimeChanged -> {
                    if (!_isTransitioning) {
                        positionMs = vlcPlayer.time.coerceAtLeast(0)
                        invalidateState()
                    }
                }
                VlcMediaPlayer.Event.LengthChanged -> {
                    durationMs = vlcPlayer.length.coerceAtLeast(0)
                    invalidateState()
                }
                VlcMediaPlayer.Event.EndReached -> {
                    // Save position = 0 (track finished) and clear stale reading position
                    val item = playlist.getOrNull(currentIndex)
                    if (item != null) {
                        VideoPositionStore.save(context, item.filePath, 0)
                        onTrackFinished?.invoke(item.filePath)
                    }
                    val targetIndex = when {
                        _repeatMode == Player.REPEAT_MODE_ONE -> currentIndex
                        currentIndex + 1 < playlist.size -> currentIndex + 1
                        _repeatMode == Player.REPEAT_MODE_ALL && playlist.isNotEmpty() -> 0
                        else -> -1
                    }
                    if (targetIndex >= 0) {
                        // Optimistic UI update — show next track immediately
                        _isTransitioning = true
                        currentIndex = targetIndex
                        positionMs = 0
                        durationMs = 0
                        invalidateState()
                        // Defer actual VLC playback to avoid reentrancy
                        handler.post { playItemInternal(targetIndex) }
                    } else {
                        _isPlaying = false
                        _hasMedia = false
                        invalidateState()
                    }
                }
            }
        }
    }

    fun setPlaylist(items: List<PlaylistHolder.PlaylistItem>, startIndex: Int) {
        EcosystemLogger.d(HaronConstants.TAG, "VlcPlayerAdapter: setPlaylist size=${items.size} startIndex=$startIndex")
        playlist = items
        currentIndex = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        if (items.isNotEmpty()) {
            _hasMedia = true
            playItemInternal(currentIndex)
        }
    }

    private fun playItemInternal(index: Int) {
        // Prevent stale TimeChanged events from previous track
        _isTransitioning = true

        // Save position of old track
        val oldItem = playlist.getOrNull(currentIndex)
        if (oldItem != null && positionMs > 0) {
            VideoPositionStore.save(context, oldItem.filePath, positionMs)
        }

        currentIndex = index
        val item = playlist.getOrNull(index) ?: return
        EcosystemLogger.d(HaronConstants.TAG, "VlcPlayerAdapter: playing item [$index] ${item.fileName}")
        val uri = if (item.filePath.startsWith("content://")) {
            Uri.parse(item.filePath)
        } else {
            Uri.fromFile(java.io.File(item.filePath))
        }
        val media = Media(libVlc, uri)
        media.setHWDecoderEnabled(true, false)
        media.addOption(":no-mediacodec-dr")
        media.addOption(":no-avcodec-hurry-up")
        media.addOption(":avcodec-workaround-bugs=5")
        media.addOption(":no-avcodec-dr")
        vlcPlayer.media = media
        media.release()

        // Restore saved position
        val savedPos = VideoPositionStore.load(context, item.filePath)

        vlcPlayer.play()

        if (savedPos > 0) {
            vlcPlayer.time = savedPos
            positionMs = savedPos
        } else {
            positionMs = 0
        }
        durationMs = 0
        _isPlaying = true
        _hasMedia = true
        invalidateState()
    }

    override fun getState(): State {
        val mediaItems = playlist.mapIndexed { idx, item ->
            val mediaItem = MediaItem.Builder()
                .setMediaId(item.filePath)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.fileName)
                        .build()
                )
                .build()
            MediaItemData.Builder(idx.toLong())
                .setMediaItem(mediaItem)
                .setDurationUs(
                    if (idx == currentIndex && durationMs > 0) durationMs * 1000
                    else androidx.media3.common.C.TIME_UNSET
                )
                .build()
        }

        val playbackState = when {
            !_hasMedia || playlist.isEmpty() -> STATE_IDLE
            else -> STATE_READY
        }

        return State.Builder()
            .setAvailableCommands(
                Player.Commands.Builder()
                    .addAll(
                        COMMAND_PLAY_PAUSE,
                        COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                        COMMAND_SEEK_TO_MEDIA_ITEM,
                        COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                        COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                        COMMAND_SET_REPEAT_MODE,
                        COMMAND_GET_CURRENT_MEDIA_ITEM,
                        COMMAND_GET_MEDIA_ITEMS_METADATA,
                        COMMAND_GET_TIMELINE
                    )
                    .build()
            )
            .setPlayWhenReady(_isPlaying, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(playbackState)
            .setPlaylist(mediaItems)
            .setCurrentMediaItemIndex(currentIndex)
            .setContentPositionMs(positionMs)
            .setRepeatMode(_repeatMode)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        EcosystemLogger.d(HaronConstants.TAG, "VlcPlayerAdapter: ${if (playWhenReady) "play" else "pause"}")
        if (playWhenReady) {
            vlcPlayer.play()
        } else {
            vlcPlayer.pause()
            // Save position on pause
            val item = playlist.getOrNull(currentIndex)
            if (item != null) {
                VideoPositionStore.save(context, item.filePath, vlcPlayer.time.coerceAtLeast(0))
            }
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int
    ): ListenableFuture<*> {
        if (mediaItemIndex != currentIndex && mediaItemIndex in playlist.indices) {
            playItemInternal(mediaItemIndex)
            if (positionMs > 0) {
                vlcPlayer.time = positionMs
                this.positionMs = positionMs
            }
        } else if (positionMs >= 0) {
            vlcPlayer.time = positionMs
            this.positionMs = positionMs
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        _repeatMode = repeatMode
        onRepeatModeChanged?.invoke(repeatMode)
        return Futures.immediateVoidFuture()
    }

    fun cleanup() {
        EcosystemLogger.d(HaronConstants.TAG, "VlcPlayerAdapter: cleanup")
        // Save current position before releasing
        val item = playlist.getOrNull(currentIndex)
        if (item != null) {
            VideoPositionStore.save(context, item.filePath, vlcPlayer.time.coerceAtLeast(0))
        }
        vlcPlayer.setEventListener(null)
    }

    // Direct getters for same-process UI polling (bypasses SimpleBasePlayer cache)
    fun getCurrentIndex(): Int = currentIndex
    fun isCurrentlyPlaying(): Boolean = _isPlaying
    fun getCurrentPositionMs(): Long = positionMs
    fun getCurrentDurationMs(): Long = durationMs
    fun getCurrentRepeatMode(): Int = _repeatMode
    fun setRepeat(mode: Int) {
        _repeatMode = mode
        invalidateState()
        onRepeatModeChanged?.invoke(mode)
    }
}
