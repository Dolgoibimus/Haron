package com.vamp.haron.data.cast

import android.content.Context
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.domain.model.CastDevice
import com.vamp.haron.domain.model.CastType
import com.vamp.haron.domain.model.RemoteInputEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleCastManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var castContext: CastContext? = null
    private var sessionManager: SessionManager? = null
    private var currentSession: CastSession? = null

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private val _mediaIsPlaying = MutableStateFlow(false)
    val mediaIsPlaying: StateFlow<Boolean> = _mediaIsPlaying.asStateFlow()

    private val _mediaPositionMs = MutableStateFlow(0L)
    val mediaPositionMs: StateFlow<Long> = _mediaPositionMs.asStateFlow()

    private val _mediaDurationMs = MutableStateFlow(0L)
    val mediaDurationMs: StateFlow<Long> = _mediaDurationMs.asStateFlow()

    private val remoteMediaCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val client = currentSession?.remoteMediaClient ?: return
            _mediaIsPlaying.value = client.isPlaying
            _mediaPositionMs.value = client.approximateStreamPosition
            _mediaDurationMs.value = client.streamDuration
        }
    }

    fun updateMediaPosition() {
        val client = currentSession?.remoteMediaClient ?: return
        _mediaPositionMs.value = client.approximateStreamPosition
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            currentSession = session
            _isConnected.value = true
            _connectedDeviceName.value = session.castDevice?.friendlyName
            session.remoteMediaClient?.registerCallback(remoteMediaCallback)
            EcosystemLogger.d(HaronConstants.TAG, "Cast session started: ${session.castDevice?.friendlyName}")
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            EcosystemLogger.e(HaronConstants.TAG, "Cast session start failed: $error")
        }

        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionEnded(session: CastSession, error: Int) {
            session.remoteMediaClient?.unregisterCallback(remoteMediaCallback)
            currentSession = null
            _isConnected.value = false
            _connectedDeviceName.value = null
            _mediaIsPlaying.value = false
            _mediaPositionMs.value = 0
            _mediaDurationMs.value = 0
        }

        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            currentSession = session
            _isConnected.value = true
            _connectedDeviceName.value = session.castDevice?.friendlyName
            session.remoteMediaClient?.registerCallback(remoteMediaCallback)
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {}
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
    }

    fun initialize() {
        if (!isGmsAvailable()) {
            _isAvailable.value = false
            EcosystemLogger.d(HaronConstants.TAG, "Google Play Services not available, Cast disabled")
            return
        }

        try {
            castContext = CastContext.getSharedInstance(context)
            sessionManager = castContext?.sessionManager
            sessionManager?.addSessionManagerListener(sessionListener, CastSession::class.java)
            _isAvailable.value = true
            EcosystemLogger.d(HaronConstants.TAG, "Cast initialized")
        } catch (e: Exception) {
            _isAvailable.value = false
            EcosystemLogger.e(HaronConstants.TAG, "Cast init failed: ${e.message}")
        }
    }

    fun castMedia(url: String, mimeType: String, title: String = "") {
        val session = currentSession ?: return
        val remoteMediaClient = session.remoteMediaClient ?: return

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_GENERIC).apply {
            putString(MediaMetadata.KEY_TITLE, title)
        }

        val mediaInfo = MediaInfo.Builder(url)
            .setContentType(mimeType)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setMetadata(metadata)
            .build()

        val loadRequest = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .build()

        remoteMediaClient.load(loadRequest)
        EcosystemLogger.d(HaronConstants.TAG, "Cast media: $url")
    }

    fun sendRemoteInput(event: RemoteInputEvent) {
        val client = currentSession?.remoteMediaClient ?: return
        when (event) {
            is RemoteInputEvent.PlayPause -> {
                if (client.isPlaying) client.pause() else client.play()
            }
            is RemoteInputEvent.SeekTo -> {
                client.seek(event.positionMs)
            }
            is RemoteInputEvent.VolumeChange -> {
                val current = currentSession?.volume ?: 0.5
                val newVolume = (current + event.delta).coerceIn(0.0, 1.0)
                currentSession?.setVolume(newVolume)
            }
            is RemoteInputEvent.Next -> {
                client.queueNext(null)
            }
            is RemoteInputEvent.Prev -> {
                client.queuePrev(null)
            }
        }
    }

    fun discoverCastDevices(): Flow<List<CastDevice>> = callbackFlow {
        val ctx = castContext
        if (ctx == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val router = MediaRouter.getInstance(context)
        val selector: MediaRouteSelector = try {
            ctx.mergedSelector ?: run {
                trySend(emptyList())
                close()
                return@callbackFlow
            }
        } catch (e: Exception) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val callback = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
                trySend(getCastRoutes(router, selector))
            }

            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
                trySend(getCastRoutes(router, selector))
            }

            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
                trySend(getCastRoutes(router, selector))
            }
        }

        router.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
        trySend(getCastRoutes(router, selector))

        awaitClose {
            router.removeCallback(callback)
        }
    }

    fun selectCastDevice(deviceId: String) {
        val router = MediaRouter.getInstance(context)
        val route = router.routes.find { it.id == deviceId }
        if (route != null) {
            router.selectRoute(route)
            EcosystemLogger.d(HaronConstants.TAG, "Cast route selected: ${route.name}")
        }
    }

    private fun getCastRoutes(
        router: MediaRouter,
        selector: MediaRouteSelector
    ): List<CastDevice> {
        return router.routes
            .filter { it.matchesSelector(selector) && !it.isDefault && it.isEnabled }
            .map { CastDevice(id = it.id, name = it.name, type = CastType.CHROMECAST) }
    }

    fun disconnect() {
        sessionManager?.endCurrentSession(true)
        currentSession = null
        _isConnected.value = false
        _connectedDeviceName.value = null
    }

    fun getCurrentVolume(): Double = currentSession?.volume ?: 0.5

    fun setVolume(volume: Double) {
        currentSession?.setVolume(volume.coerceIn(0.0, 1.0))
    }

    fun getCastContext(): CastContext? = castContext

    private fun isGmsAvailable(): Boolean {
        return GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }
}
