package com.vamp.haron.service

import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.MediaPlayer as VlcMediaPlayer

@UnstableApi
class PlaybackService : MediaSessionService() {

    companion object {
        var instance: PlaybackService? = null
            private set
    }

    private var libVlc: LibVLC? = null
    private var vlcPlayer: VlcMediaPlayer? = null
    private var adapter: VlcPlayerAdapter? = null
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        instance = this

        libVlc = LibVLC(this, arrayListOf(
            "--no-avcodec-hurry-up",
            "--no-avcodec-dr",
            "--avcodec-skiploopfilter=-1",
            "--avcodec-skip-frame=0",
            "--avcodec-skip-idct=0",
            "--no-skip-frames",
            "--no-drop-late-frames",
            "--avcodec-workaround-bugs=5",
            "--file-caching=300",
            "--aout=opensles",
        ))

        vlcPlayer = VlcMediaPlayer(libVlc!!)
        adapter = VlcPlayerAdapter(this, vlcPlayer!!, libVlc!!)
        mediaSession = MediaSession.Builder(this, adapter!!).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady) {
            stopSelf()
        }
    }

    fun getVlcPlayer(): VlcMediaPlayer? = vlcPlayer
    fun getAdapter(): VlcPlayerAdapter? = adapter

    override fun onDestroy() {
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
