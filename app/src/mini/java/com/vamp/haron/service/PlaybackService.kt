package com.vamp.haron.service

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** No-op stub for mini variant. VLC player disabled. */
class PlaybackService : MediaSessionService() {

    companion object {
        var instance: PlaybackService? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
    }

    fun getVlcPlayer(): Any? = null
    fun getAdapter(): VlcPlayerAdapter? = null
    fun updateRepeatButton(mode: Int) {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
