package com.vamp.haron.presentation.player

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.vamp.haron.R
import com.vamp.haron.common.util.toDurationString
import com.vamp.haron.domain.model.PlaylistHolder
import com.vamp.haron.presentation.cast.CastViewModel
import com.vamp.haron.presentation.cast.components.CastButton
import com.vamp.haron.presentation.cast.components.CastDeviceSheet
import com.vamp.haron.service.PlaybackService
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.videolan.libvlc.util.VLCVideoLayout

@Composable
fun MediaPlayerScreen(
    startIndex: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val castViewModel: CastViewModel = hiltViewModel()

    var controller by remember { mutableStateOf<MediaController?>(null) }
    var controllerFuture by remember { mutableStateOf<ListenableFuture<MediaController>?>(null) }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var currentIndex by remember { mutableIntStateOf(startIndex) }
    var showControls by remember { mutableStateOf(true) }
    var tapFeedback by remember { mutableStateOf<TapZone?>(null) }
    var tapCounter by remember { mutableStateOf(0) }
    var showSystemBarsTemporarily by remember { mutableStateOf(false) }
    var videoLayoutRef by remember { mutableStateOf<VLCVideoLayout?>(null) }
    var wasDetached by remember { mutableStateOf(false) }

    val currentItem = PlaylistHolder.items.getOrNull(currentIndex)
    val isVideo = currentItem?.fileType == "video"
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
            // Initialize playlist in adapter
            PlaybackService.instance?.getAdapter()?.setPlaylist(PlaylistHolder.items, startIndex)
        }, MoreExecutors.directExecutor())

        onDispose {
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
                val d = adapter.getCurrentDurationMs()
                if (d > 0) duration = d else if (adapterIndex != currentIndex) duration = 0
            }
            delay(100)
        }
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
                            PlaybackService.instance?.getVlcPlayer()?.attachViews(layout, null, false, false)
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

    // Attach VLC to surface when both layout and service are ready
    LaunchedEffect(videoLayoutRef) {
        val layout = videoLayoutRef ?: return@LaunchedEffect
        while (isActive) {
            val vlcPlayer = PlaybackService.instance?.getVlcPlayer()
            if (vlcPlayer != null) {
                try {
                    vlcPlayer.attachViews(layout, null, false, false)
                } catch (_: IllegalStateException) {
                    vlcPlayer.detachViews()
                    vlcPlayer.attachViews(layout, null, false, false)
                }
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

        // YouTube-style tap zones
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 56.dp)
        ) {
            // Left — rewind 10s
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
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
                            },
                            onLongPress = { showSystemBarsTemporarily = true }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val feedbackAlpha by animateFloatAsState(
                    targetValue = if (tapFeedback == TapZone.LEFT) 1f else 0f,
                    animationSpec = tween(durationMillis = 300),
                    label = "left"
                )
                if (feedbackAlpha > 0.01f) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .alpha(feedbackAlpha)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Replay10, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
            }

            // Center — play/pause
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
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
                            },
                            onLongPress = { showSystemBarsTemporarily = true }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val feedbackAlpha by animateFloatAsState(
                    targetValue = if (tapFeedback == TapZone.CENTER) 1f else 0f,
                    animationSpec = tween(durationMillis = 300),
                    label = "center"
                )
                if (feedbackAlpha > 0.01f) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .alpha(feedbackAlpha)
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

            // Right — forward 10s
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
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
                            },
                            onLongPress = { showSystemBarsTemporarily = true }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val feedbackAlpha by animateFloatAsState(
                    targetValue = if (tapFeedback == TapZone.RIGHT) 1f else 0f,
                    animationSpec = tween(durationMillis = 300),
                    label = "right"
                )
                if (feedbackAlpha > 0.01f) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .alpha(feedbackAlpha)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Forward10, null, tint = Color.White, modifier = Modifier.size(32.dp))
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
                    CastButton(
                        castManager = castViewModel.castManager,
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
                    // Prev / Next buttons
                    if (PlaylistHolder.items.size > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                controller?.seekToPreviousMediaItem()
                            }) {
                                Icon(Icons.Filled.SkipPrevious, stringResource(R.string.previous), tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                            Spacer(Modifier.width(24.dp))
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
                            Spacer(Modifier.width(24.dp))
                            IconButton(onClick = {
                                controller?.seekToNextMediaItem()
                            }) {
                                Icon(Icons.Filled.SkipNext, stringResource(R.string.next), tint = Color.White, modifier = Modifier.size(32.dp))
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
    }
}

private enum class TapZone { LEFT, CENTER, RIGHT }

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
