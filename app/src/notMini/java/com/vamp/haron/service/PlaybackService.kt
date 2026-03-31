package com.vamp.haron.service

import android.content.Intent
import android.os.Bundle
import androidx.media3.common.Player
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.vamp.haron.R
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.MediaPlayer as VlcMediaPlayer

/**
 * Media playback service using VLC under Media3 MediaSession API.
 * Holds singleton [LibVLC] + [VlcMediaPlayer] + [VlcPlayerAdapter].
 * Supports playlist, repeat modes (ALL/ONE/OFF), and custom media button for repeat toggle.
 * Audio focus and notification handled by Media3 framework.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    companion object {
        var instance: PlaybackService? = null
            private set
        private const val ACTION_REPEAT_TOGGLE = "com.vamp.haron.REPEAT_TOGGLE"
    }

    private var libVlc: LibVLC? = null
    private var vlcPlayer: VlcMediaPlayer? = null
    private var adapter: VlcPlayerAdapter? = null
    private var mediaSession: MediaSession? = null
    private val repeatCommand = SessionCommand(ACTION_REPEAT_TOGGLE, Bundle.EMPTY)

    override fun onCreate() {
        super.onCreate()
        EcosystemLogger.d(HaronConstants.TAG, "PlaybackService: onCreate")
        instance = this

        libVlc = LibVLC(this, arrayListOf(
            "--avcodec-skiploopfilter=0",
            "--avcodec-skip-frame=0",
            "--avcodec-skip-idct=0",
            "--audio-resampler", "soxr",
            "--audio-time-stretch",
            "--network-caching=600000",   // 10 min network buffer for cloud/FTP streaming
            "--file-caching=3000",        // 3 sec local file buffer
            "--live-caching=3000",
        ))

        vlcPlayer = VlcMediaPlayer(libVlc!!)
        adapter = VlcPlayerAdapter(this, vlcPlayer!!, libVlc!!).also { adp ->
            adp.onRepeatModeChanged = { mode -> updateRepeatButton(mode) }
        }

        mediaSession = MediaSession.Builder(this, adapter!!)
            .setCustomLayout(buildRepeatLayout(Player.REPEAT_MODE_ALL))
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                        .buildUpon()
                        .add(repeatCommand)
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(sessionCommands)
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction == ACTION_REPEAT_TOGGLE) {
                        toggleRepeat()
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .build()
    }

    private fun toggleRepeat() {
        val adp = adapter ?: return
        val newMode = when (adp.getCurrentRepeatMode()) {
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_ALL
        }
        adp.setRepeat(newMode)
    }

    fun updateRepeatButton(mode: Int) {
        val session = mediaSession ?: return
        session.setCustomLayout(buildRepeatLayout(mode))
    }

    private fun buildRepeatLayout(mode: Int): List<CommandButton> {
        val iconRes = when (mode) {
            Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
            Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat
            else -> R.drawable.ic_repeat_off
        }
        val button = CommandButton.Builder()
            .setDisplayName(getString(R.string.repeat_mode))
            .setIconResId(iconRes)
            .setSessionCommand(repeatCommand)
            .build()
        return listOf(button)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady) {
            EcosystemLogger.d(HaronConstants.TAG, "PlaybackService: task removed, stopping service")
            stopSelf()
        }
    }

    fun getVlcPlayer(): VlcMediaPlayer? = vlcPlayer
    fun getAdapter(): VlcPlayerAdapter? = adapter

    override fun onDestroy() {
        EcosystemLogger.d(HaronConstants.TAG, "PlaybackService: onDestroy")
        instance = null
        mediaSession?.release()
        mediaSession = null
        adapter?.cleanup()
        adapter = null
        vlcPlayer?.stop()
        vlcPlayer?.release()
        vlcPlayer = null
        libVlc?.release()
        libVlc = null
        super.onDestroy()
    }
}
