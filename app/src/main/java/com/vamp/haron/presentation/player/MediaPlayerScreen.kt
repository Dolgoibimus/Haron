package com.vamp.haron.presentation.player

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.vamp.haron.R
import com.vamp.haron.common.util.toDurationString
import com.vamp.haron.data.reading.ReadingPositionManager
import com.vamp.haron.domain.model.PlaylistHolder
import com.vamp.haron.domain.model.TransferHolder
import com.vamp.haron.presentation.cast.CastViewModel
import com.vamp.haron.presentation.cast.components.CastButton
import com.vamp.haron.presentation.cast.components.CastDeviceSheet
import com.vamp.haron.service.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.videolan.libvlc.util.VLCVideoLayout
import androidx.compose.material.icons.filled.Settings
import kotlin.math.abs

@Composable
fun MediaPlayerScreen(
    startIndex: Int,
    prefs: com.vamp.haron.data.datastore.HaronPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val castViewModel: CastViewModel = hiltViewModel(
        viewModelStoreOwner = context as androidx.activity.ComponentActivity
    )

    var showSettings by remember { mutableStateOf(false) }

    var controller by remember { mutableStateOf<MediaController?>(null) }
    var controllerFuture by remember { mutableStateOf<ListenableFuture<MediaController>?>(null) }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var currentIndex by remember { mutableIntStateOf(startIndex) }
    var showControls by remember { mutableStateOf(true) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_ALL) }
    var tapFeedback by remember { mutableStateOf<TapZone?>(null) }
    var tapCounter by remember { mutableStateOf(0) }
    var vlcAttached by remember { mutableStateOf(false) }
    var showSystemBarsTemporarily by remember { mutableStateOf(false) }
    var videoLayoutRef by remember { mutableStateOf<VLCVideoLayout?>(null) }
    var wasDetached by remember { mutableStateOf(false) }

    // Brightness & Volume swipe controls
    val audioManager = remember { context.getSystemService(AudioManager::class.java)!! }
    val maxStreamVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var brightnessLevel by remember { mutableIntStateOf(
        (context as? Activity)?.window?.attributes?.screenBrightness?.let {
            if (it < 0) 50 else (it * 100).toInt().coerceIn(0, 100)
        } ?: 50
    ) }
    var volumeLevel by remember { mutableIntStateOf(
        (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / maxStreamVolume).coerceIn(0, 100)
    ) }
    var activeSwipeGesture by remember { mutableStateOf<SwipeGesture?>(null) }

    val currentItem = PlaylistHolder.items.getOrNull(currentIndex)
    val isVideo = currentItem?.fileType == "video"
    val dndManager = remember(isVideo) { com.vamp.haron.data.player.DndManager(context, prefs, isVideo) }
    val fileName = currentItem?.fileName ?: ""
    val filePath = currentItem?.filePath ?: ""

    // Start service and connect MediaController
    DisposableEffect(Unit) {
        context.startService(Intent(context, PlaybackService::class.java))
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener({
            val ctrl = future.get()
            controller = ctrl
            // Setup callbacks (playlist will be set after VLC attachViews)
            val adapter = PlaybackService.instance?.getAdapter()
            adapter?.onTrackFinished = { filePath ->
                ReadingPositionManager.saveAsync(filePath, 0, 0L)
            }
        }, MoreExecutors.directExecutor())

        onDispose {
            PlaybackService.instance?.getAdapter()?.onTrackFinished = null
            controllerFuture?.let { MediaController.releaseFuture(it) }
            controller = null
        }
    }

    // Poll state directly from adapter fields (same process, no IPC/cache delay)
    LaunchedEffect(Unit) {
        while (isActive) {
            val adapter = PlaybackService.instance?.getAdapter()
            if (adapter != null) {
                val adapterIndex = adapter.getCurrentIndex()
                if (adapterIndex in PlaylistHolder.items.indices && adapterIndex != currentIndex) {
                    currentIndex = adapterIndex
                }
                isPlaying = adapter.isCurrentlyPlaying()
                currentPosition = adapter.getCurrentPositionMs()
                repeatMode = adapter.getCurrentRepeatMode()
                val d = adapter.getCurrentDurationMs()
                if (d > 0) duration = d else if (adapterIndex != currentIndex) duration = 0
            }
            delay(250)
        }
    }

    // Restore playback position (only if VideoPositionStore didn't reset it to 0 = track completed)
    var positionRestored by remember { mutableStateOf(false) }
    LaunchedEffect(controller, currentIndex) {
        val ctrl = controller ?: return@LaunchedEffect
        if (positionRestored) return@LaunchedEffect
        val item = PlaylistHolder.items.getOrNull(currentIndex) ?: return@LaunchedEffect
        val videoStorePos = withContext(Dispatchers.IO) {
            com.vamp.haron.data.datastore.VideoPositionStore.load(context, item.filePath)
        }
        // If VideoPositionStore has 0, the track was played to completion — don't restore stale position
        if (videoStorePos == 0L) {
            positionRestored = true
            return@LaunchedEffect
        }
        val saved = withContext(Dispatchers.IO) { ReadingPositionManager.get(item.filePath) }
        if (saved != null && saved.positionExtra > 0) {
            delay(500) // wait for playback to initialize
            ctrl.seekTo(saved.positionExtra)
        }
        positionRestored = true
    }

    // Save playback position every 5 seconds
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(5000)
            val item = PlaylistHolder.items.getOrNull(currentIndex)
            if (item != null && currentPosition > 0 && duration > 0) {
                withContext(Dispatchers.IO) {
                    ReadingPositionManager.save(item.filePath, 0, currentPosition)
                }
            }
        }
    }

    // Save position on exit
    DisposableEffect(Unit) {
        onDispose {
            val item = PlaylistHolder.items.getOrNull(currentIndex)
            if (item != null && currentPosition > 0) {
                ReadingPositionManager.saveAsync(item.filePath, 0, currentPosition)
            }
        }
    }

    // Hide VoiceFab while video is playing, show on pause
    LaunchedEffect(isPlaying, isVideo) {
        TransferHolder.voiceFabVisible.value = if (isVideo) !isPlaying else true
    }
    DisposableEffect(Unit) {
        onDispose { TransferHolder.voiceFabVisible.value = true }
    }

    // DND: activate on play, deactivate on pause/exit
    LaunchedEffect(isPlaying) {
        if (isPlaying) dndManager.activate() else dndManager.deactivate()
    }
    DisposableEffect(Unit) {
        onDispose { dndManager.deactivate() }
    }

    // Immersive mode for video
    if (isVideo) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val ctrl = WindowInsetsControllerCompat(window, view)
            ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctrl.hide(WindowInsetsCompat.Type.systemBars())
        }
        DisposableEffect(Unit) {
            onDispose {
                val window = (view.context as? Activity)?.window ?: return@onDispose
                val ctrl = WindowInsetsControllerCompat(window, view)
                ctrl.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Keep screen on while playing video
    DisposableEffect(isPlaying, isVideo) {
        view.keepScreenOn = isPlaying && isVideo
        onDispose { view.keepScreenOn = false }
    }

    // Restore system brightness on exit
    DisposableEffect(Unit) {
        onDispose {
            (context as? Activity)?.window?.let { win ->
                val params = win.attributes
                params.screenBrightness = -1f
                win.attributes = params
            }
        }
    }

    // Lifecycle: detach/re-attach VLC surface on screen off/on
    DisposableEffect(lifecycleOwner, isVideo) {
        val observer = LifecycleEventObserver { _, event ->
            if (!isVideo) return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    PlaybackService.instance?.getVlcPlayer()?.detachViews()
                    wasDetached = true
                }
                Lifecycle.Event.ON_START -> {
                    if (wasDetached) {
                        videoLayoutRef?.let { layout ->
                            if (layout.width > 0 && layout.height > 0) {
                                PlaybackService.instance?.getVlcPlayer()?.attachViews(layout, null, false, false)
                            }
                        }
                        wasDetached = false
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(showControls, isPlaying) {
        if (isVideo && showControls && isPlaying) {
            delay(3000)
            showControls = false
        }
    }

    // Long press → temporarily show system bars
    LaunchedEffect(showSystemBarsTemporarily) {
        if (showSystemBarsTemporarily && isVideo) {
            val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
            val ctrl = WindowInsetsControllerCompat(window, view)
            ctrl.show(WindowInsetsCompat.Type.systemBars())
            delay(3000)
            ctrl.hide(WindowInsetsCompat.Type.systemBars())
            showSystemBarsTemporarily = false
        }
    }

    LaunchedEffect(tapCounter) {
        if (tapFeedback != null) {
            delay(600)
            tapFeedback = null
        }
    }

    BackHandler {
        if (isVideo) {
            // Detach surface before stopping service
            PlaybackService.instance?.getVlcPlayer()?.detachViews()
            context.stopService(Intent(context, PlaybackService::class.java))
        }
        // For audio — service continues in background
        onBack()
    }

    // Attach VLC to surface when layout size is STABLE and service is ready
    // Without this, MediaCodec.configure fails (height=0) or BLASTBufferQueue rejects buffers
    LaunchedEffect(videoLayoutRef) {
        val layout = videoLayoutRef ?: return@LaunchedEffect
        // Wait for non-zero size
        while (isActive && (layout.width == 0 || layout.height == 0)) {
            delay(50)
        }
        // Wait for size to stabilize (immersive mode, system bars hiding)
        var lastW = layout.width
        var lastH = layout.height
        var stableCount = 0
        while (isActive && stableCount < 3) {
            delay(100)
            if (layout.width == lastW && layout.height == lastH) {
                stableCount++
            } else {
                lastW = layout.width
                lastH = layout.height
                stableCount = 0
            }
        }
        // Wait for VLC service
        while (isActive) {
            val vlcPlayer = PlaybackService.instance?.getVlcPlayer()
            if (vlcPlayer != null) {
                try {
                    vlcPlayer.attachViews(layout, null, false, false)
                } catch (_: IllegalStateException) {
                    vlcPlayer.detachViews()
                    vlcPlayer.attachViews(layout, null, false, false)
                }
                com.vamp.core.logger.EcosystemLogger.d(com.vamp.haron.common.constants.HaronConstants.TAG, "VLC attachViews: layout=${layout.width}x${layout.height} (stable)")
                vlcAttached = true
                // Now safe to start playback — surface is ready
                PlaybackService.instance?.getAdapter()?.setPlaylist(PlaylistHolder.items, startIndex)
                break
            }
            delay(50)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isVideo) {
            AndroidView(
                factory = { ctx ->
                    VLCVideoLayout(ctx).also { layout ->
                        videoLayoutRef = layout
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AudioPlayerContent(filePath = filePath, fileName = fileName)
        }

        // Gesture overlay: taps (rewind/play/forward) + swipes (brightness/volume/back)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    val edgePx = 30.dp.toPx()
                    val backThresholdPx = 80.dp.toPx()

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startPos = down.position
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        var moved = false
                        var gesture: SwipeGesture? = null
                        val initBrightness = brightnessLevel
                        val initVolume = volumeLevel

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break

                            if (!change.pressed) {
                                // Finger up
                                if (!moved) {
                                    // Tap — determine zone by thirds
                                    val third = w / 3f
                                    when {
                                        startPos.x < third -> {
                                            if (isVideo && !showControls) {
                                                showControls = true
                                            } else {
                                                controller?.let { ctrl ->
                                                    val newTime = (ctrl.currentPosition - 10_000).coerceAtLeast(0)
                                                    ctrl.seekTo(newTime)
                                                    currentPosition = newTime
                                                }
                                                tapFeedback = TapZone.LEFT
                                                tapCounter++
                                            }
                                        }
                                        startPos.x > 2 * third -> {
                                            if (isVideo && !showControls) {
                                                showControls = true
                                            } else {
                                                controller?.let { ctrl ->
                                                    val newTime = (ctrl.currentPosition + 10_000).coerceAtMost(ctrl.duration)
                                                    ctrl.seekTo(newTime)
                                                    currentPosition = newTime
                                                }
                                                tapFeedback = TapZone.RIGHT
                                                tapCounter++
                                            }
                                        }
                                        else -> {
                                            if (isVideo && !showControls) {
                                                showControls = true
                                            } else {
                                                if (isVideo) showControls = !showControls
                                                controller?.let { ctrl ->
                                                    if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
                                                }
                                                tapFeedback = TapZone.CENTER
                                                tapCounter++
                                            }
                                        }
                                    }
                                } else if (gesture == SwipeGesture.EDGE_BACK) {
                                    val totalDx = change.position.x - startPos.x
                                    if (totalDx > backThresholdPx) {
                                        PlaybackService.instance?.getVlcPlayer()?.detachViews()
                                        context.stopService(Intent(context, PlaybackService::class.java))
                                        onBack()
                                    }
                                }
                                activeSwipeGesture = null
                                break
                            }

                            val delta = change.position - startPos

                            if (!moved && (abs(delta.x) > viewConfiguration.touchSlop || abs(delta.y) > viewConfiguration.touchSlop)) {
                                moved = true
                                gesture = when {
                                    isVideo && startPos.x < edgePx && abs(delta.x) > abs(delta.y) -> SwipeGesture.EDGE_BACK
                                    isVideo && abs(delta.y) > abs(delta.x) && startPos.x < w / 2 -> SwipeGesture.BRIGHTNESS
                                    abs(delta.y) > abs(delta.x) -> SwipeGesture.VOLUME
                                    else -> null
                                }
                                activeSwipeGesture = gesture
                            }

                            if (moved && gesture != null) {
                                change.consume()
                                val dy = change.position.y - startPos.y

                                when (gesture) {
                                    SwipeGesture.BRIGHTNESS -> {
                                        brightnessLevel = (initBrightness - (dy / h * 100).toInt()).coerceIn(0, 100)
                                        (context as? Activity)?.window?.let { win ->
                                            val params = win.attributes
                                            params.screenBrightness = (brightnessLevel / 100f).coerceAtLeast(0.01f)
                                            win.attributes = params
                                        }
                                    }
                                    SwipeGesture.VOLUME -> {
                                        volumeLevel = (initVolume - (dy / h * 100).toInt()).coerceIn(0, 100)
                                        audioManager.setStreamVolume(
                                            AudioManager.STREAM_MUSIC,
                                            (volumeLevel * maxStreamVolume / 100).coerceIn(0, maxStreamVolume),
                                            0
                                        )
                                    }
                                    SwipeGesture.EDGE_BACK -> {}
                                    null -> {}
                                }
                            }
                        }
                    }
                }
        ) {
            // Tap feedback icons
            val feedbackAlphaLeft by animateFloatAsState(
                targetValue = if (tapFeedback == TapZone.LEFT) 1f else 0f,
                animationSpec = tween(durationMillis = 300), label = "left"
            )
            val feedbackAlphaCenter by animateFloatAsState(
                targetValue = if (tapFeedback == TapZone.CENTER) 1f else 0f,
                animationSpec = tween(durationMillis = 300), label = "center"
            )
            val feedbackAlphaRight by animateFloatAsState(
                targetValue = if (tapFeedback == TapZone.RIGHT) 1f else 0f,
                animationSpec = tween(durationMillis = 300), label = "right"
            )
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 56.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    if (feedbackAlphaLeft > 0.01f) {
                        Box(
                            Modifier.size(64.dp).alpha(feedbackAlphaLeft)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Replay10, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    if (feedbackAlphaCenter > 0.01f) {
                        Box(
                            Modifier.size(64.dp).alpha(feedbackAlphaCenter)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                null, tint = Color.White, modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    if (feedbackAlphaRight > 0.01f) {
                        Box(
                            Modifier.size(64.dp).alpha(feedbackAlphaRight)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Forward10, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }

            // Brightness indicator (left side)
            if (activeSwipeGesture == SwipeGesture.BRIGHTNESS) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 24.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.WbSunny, null, tint = Color.White, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier.width(4.dp).height(120.dp)
                                .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .fillMaxHeight(brightnessLevel / 100f)
                                    .align(Alignment.BottomCenter)
                                    .background(Color.White, RoundedCornerShape(2.dp))
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("$brightnessLevel", style = MaterialTheme.typography.bodySmall, color = Color.White)
                    }
                }
            }

            // Volume indicator (right side)
            if (activeSwipeGesture == SwipeGesture.VOLUME) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 24.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(@Suppress("DEPRECATION") Icons.Filled.VolumeUp, null, tint = Color.White, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier.width(4.dp).height(120.dp)
                                .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .fillMaxHeight(volumeLevel / 100f)
                                    .align(Alignment.BottomCenter)
                                    .background(Color.White, RoundedCornerShape(2.dp))
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("$volumeLevel", style = MaterialTheme.typography.bodySmall, color = Color.White)
                    }
                }
            }
        }

        // Controls overlay
        AnimatedVisibility(
            visible = showControls || !isVideo,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar: back + file name
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = if (isVideo) 0.5f else 0f))
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (isVideo) {
                            context.stopService(Intent(context, PlaybackService::class.java))
                        }
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White)
                    }
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        controller?.pause()
                        showSettings = true
                    }) {
                        Icon(Icons.Filled.Settings, stringResource(R.string.player_settings), tint = Color.White)
                    }
                    CastButton(
                        isConnected = castViewModel.isConnected,
                        onClick = { castViewModel.showSheet() }
                    )
                }

                Spacer(Modifier.weight(1f))

                // Bottom controls: prev/next + seekbar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = if (isVideo) 0.5f else 0f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Prev / Repeat / Play / Next buttons
                    if (PlaylistHolder.items.size > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left side: Repeat + Prev (aligned right toward center)
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Repeat mode toggle: ALL → ONE → OFF → ALL
                                IconButton(onClick = {
                                    val newMode = when (repeatMode) {
                                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                        Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
                                        else -> Player.REPEAT_MODE_ALL
                                    }
                                    PlaybackService.instance?.getAdapter()?.setRepeat(newMode)
                                    repeatMode = newMode
                                }) {
                                    Icon(
                                        imageVector = when (repeatMode) {
                                            Player.REPEAT_MODE_ONE -> Icons.Filled.RepeatOne
                                            else -> Icons.Filled.Repeat
                                        },
                                        contentDescription = stringResource(R.string.repeat_mode),
                                        tint = when (repeatMode) {
                                            Player.REPEAT_MODE_OFF -> Color.White.copy(alpha = 0.4f)
                                            else -> Color.White
                                        },
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                IconButton(onClick = {
                                    controller?.seekToPreviousMediaItem()
                                }) {
                                    Icon(Icons.Filled.SkipPrevious, stringResource(R.string.previous), tint = Color.White, modifier = Modifier.size(32.dp))
                                }
                            }

                            // Center: Play/Pause
                            IconButton(onClick = {
                                controller?.let { ctrl ->
                                    if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
                                }
                            }) {
                                Icon(
                                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp)
                                )
                            }

                            // Right side: Next (aligned left toward center)
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = {
                                    controller?.seekToNextMediaItem()
                                }) {
                                    Icon(Icons.Filled.SkipNext, stringResource(R.string.next), tint = Color.White, modifier = Modifier.size(32.dp))
                                }
                            }
                        }
                    }

                    var isDragging by remember { mutableStateOf(false) }
                    var dragValue by remember { mutableFloatStateOf(0f) }
                    Slider(
                        value = if (isDragging) dragValue
                        else if (duration > 0) currentPosition.toFloat() / duration
                        else 0f,
                        onValueChange = {
                            isDragging = true
                            dragValue = it
                        },
                        onValueChangeFinished = {
                            val seekTarget = (dragValue * duration).toLong()
                            currentPosition = seekTarget
                            controller?.seekTo(seekTarget)
                            isDragging = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(currentPosition.toDurationString(), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                        Text(duration.toDurationString(), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        // Cast device selection sheet
        val showCastSheet by castViewModel.showDeviceSheet.collectAsState()
        val castDevices by castViewModel.devices.collectAsState()
        val castSearching by castViewModel.isSearching.collectAsState()
        val castConnectedDevice by castViewModel.connectedDeviceName.collectAsState()

        if (showCastSheet) {
            CastDeviceSheet(
                devices = castDevices,
                isSearching = castSearching,
                connectedDeviceName = castConnectedDevice,
                onSelectDevice = { device ->
                    castViewModel.selectDeviceAndCast(device, filePath, fileName)
                },
                onDisconnect = { castViewModel.disconnect() },
                onDismiss = { castViewModel.hideSheet() }
            )
        }

        // Settings overlay (fullscreen, no navigation away)
        if (showSettings) {
            PlayerSettingsScreen(
                prefs = prefs,
                initialTab = if (isVideo) 0 else 1,
                onBack = { showSettings = false }
            )
        }
    }
}

private enum class TapZone { LEFT, CENTER, RIGHT }
private enum class SwipeGesture { EDGE_BACK, BRIGHTNESS, VOLUME }

@Composable
private fun AudioPlayerContent(filePath: String, fileName: String) {
    val context = LocalContext.current
    val albumArt = remember(filePath) {
        try {
            val retriever = MediaMetadataRetriever()
            if (filePath.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(filePath))
            } else {
                retriever.setDataSource(filePath)
            }
            val artBytes = retriever.embeddedPicture
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            retriever.release()
            AudioMeta(artBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }, title, artist, album)
        } catch (_: Exception) {
            AudioMeta()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (albumArt.art != null) {
            Image(
                bitmap = albumArt.art.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(240.dp).clip(RoundedCornerShape(16.dp))
            )
        } else {
            Box(
                modifier = Modifier.size(240.dp).background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.MusicNote, null, Modifier.size(80.dp), tint = Color.White.copy(alpha = 0.5f))
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = albumArt.title ?: fileName.substringBeforeLast('.'),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        if (albumArt.artist != null) {
            Spacer(Modifier.height(4.dp))
            Text(albumArt.artist, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.7f))
        }
        if (albumArt.album != null) {
            Spacer(Modifier.height(2.dp))
            Text(albumArt.album, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.5f))
        }
    }
}

private data class AudioMeta(val art: Bitmap? = null, val title: String? = null, val artist: String? = null, val album: String? = null)
