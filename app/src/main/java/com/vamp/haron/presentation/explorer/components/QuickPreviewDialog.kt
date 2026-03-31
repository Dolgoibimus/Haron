package com.vamp.haron.presentation.explorer.components

import android.graphics.Bitmap
import android.net.Uri
import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.vamp.haron.R
import com.vamp.haron.common.util.iconRes
import com.vamp.haron.common.util.toDurationString
import com.vamp.haron.common.util.toFileSize
import com.vamp.haron.common.util.toRelativeDate
import com.vamp.haron.domain.model.FileEntry
import com.vamp.haron.domain.model.PlaylistHolder
import com.vamp.haron.domain.model.PreviewData
import com.vamp.haron.service.PlaybackService
import androidx.compose.foundation.layout.fillMaxSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Shared state between inline player and seekbar outside pager */
private class InlinePlayerState {
    var currentPosition by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var isActive by mutableStateOf(false)
    var seekTo: ((Long) -> Unit)? = null
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun QuickPreviewDialog(
    entry: FileEntry,
    previewData: PreviewData?,
    isLoading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onFullscreenPlay: ((positionMs: Long) -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onOpenGallery: (() -> Unit)? = null,
    onOpenPdf: (() -> Unit)? = null,
    onOpenDocument: (() -> Unit)? = null,
    onOpenArchive: (() -> Unit)? = null,
    onInstallApk: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onNavigateToFolder: ((String) -> Unit)? = null, // path → navigate to parent folder
    currentFolderPath: String = "",
    adjacentFiles: List<FileEntry> = emptyList(),
    currentFileIndex: Int = 0,
    onFileChanged: (Int) -> Unit = {},
    previewCache: Map<Int, PreviewData> = emptyMap()
) {
    val hasPager = adjacentFiles.size > 1
    val playerState = remember { InlinePlayerState() }
    val context = LocalContext.current

    // Media playlist from adjacent files
    val mediaFiles = remember(adjacentFiles) {
        adjacentFiles.mapIndexedNotNull { idx, f ->
            if (!f.isDirectory && f.iconRes() in listOf("video", "audio")) idx to f else null
        }
    }
    val pageToMediaIndex = remember(mediaFiles) {
        mediaFiles.withIndex().associate { (mediaIdx, pair) -> pair.first to mediaIdx }
    }
    val mediaToPageIndex = remember(mediaFiles) {
        mediaFiles.withIndex().associate { (mediaIdx, pair) -> mediaIdx to pair.first }
    }

    // PlaybackService connection
    var controllerFuture by remember { mutableStateOf<ListenableFuture<MediaController>?>(null) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var serviceConnected by remember { mutableStateOf(false) }
    var currentAdapterIndex by remember { mutableStateOf(-1) }

    fun startMediaPlayback(pageIndex: Int) {
        val mediaIndex = pageToMediaIndex[pageIndex] ?: return
        if (serviceConnected) {
            controller?.seekTo(mediaIndex, 0)
            controller?.play()
            return
        }
        PlaylistHolder.items = mediaFiles.map { (_, f) ->
            PlaylistHolder.PlaylistItem(f.path, f.name, f.iconRes())
        }
        PlaylistHolder.startIndex = mediaIndex
        context.startService(Intent(context, PlaybackService::class.java))
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener({
            val ctrl = future.get()
            controller = ctrl
            PlaybackService.instance?.getAdapter()?.setPlaylist(PlaylistHolder.items, mediaIndex)
            serviceConnected = true
        }, MoreExecutors.directExecutor())
    }

    val onTogglePlayPause: () -> Unit = {
        controller?.let { ctrl ->
            if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            controllerFuture?.let { MediaController.releaseFuture(it) }
            controller = null
        }
    }

    // Poll adapter for state
    LaunchedEffect(serviceConnected) {
        if (!serviceConnected) return@LaunchedEffect
        while (isActive) {
            val adapter = PlaybackService.instance?.getAdapter()
            if (adapter != null) {
                playerState.isActive = adapter.isCurrentlyPlaying()
                playerState.currentPosition = adapter.getCurrentPositionMs()
                val d = adapter.getCurrentDurationMs()
                if (d > 0) playerState.duration = d
                playerState.seekTo = { pos -> controller?.seekTo(pos) }
                currentAdapterIndex = adapter.getCurrentIndex()
            }
            delay(100)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp)
            ) {
                // Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subtitle = buildSubtitle(entry) +
                            if (hasPager) " \u00B7 ${currentFileIndex + 1}/${adjacentFiles.size}" else ""
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Show relative path (clickable) for files from deep search
                        if (onNavigateToFolder != null && currentFolderPath.isNotEmpty()) {
                            val parentDir = java.io.File(entry.path).parent ?: ""
                            if (parentDir != currentFolderPath && parentDir.startsWith(currentFolderPath)) {
                                val relativePath = parentDir.removePrefix(currentFolderPath).removePrefix("/")
                                Text(
                                    text = "\uD83D\uDCC2 $relativePath",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .padding(top = 1.dp)
                                        .clickable {
                                            onNavigateToFolder(parentDir)
                                        }
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.close),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(8.dp))

                // Content area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasPager) {
                        PreviewPagerContent(
                            entry = entry,
                            previewData = previewData,
                            isLoading = isLoading,
                            error = error,
                            currentIndex = currentFileIndex,
                            totalFiles = adjacentFiles.size,
                            adjacentFiles = adjacentFiles,
                            onPageChanged = onFileChanged,
                            onEdit = onEdit,
                            playerState = playerState,
                            previewCache = previewCache,
                            serviceConnected = serviceConnected,
                            currentAdapterIndex = currentAdapterIndex,
                            mediaToPageIndex = mediaToPageIndex,
                            onStartPlayback = ::startMediaPlayback,
                            onTogglePlayPause = onTogglePlayPause,
                            onFullscreenPlay = onFullscreenPlay?.let { cb -> { cb(0L) } }
                        )
                    } else {
                        PreviewContentBlock(
                            entry = entry,
                            previewData = previewData,
                            isLoading = isLoading,
                            error = error,
                            onEdit = onEdit,
                            playerState = playerState,
                            serviceConnected = serviceConnected,
                            onStartPlayback = { startMediaPlayback(0) },
                            onTogglePlayPause = onTogglePlayPause,
                            onFullscreenPlay = onFullscreenPlay?.let { cb -> { cb(0L) } }
                        )
                    }
                }

                // Bottom controls — only for audio
                val isMedia = previewData is PreviewData.VideoPreview || previewData is PreviewData.AudioPreview
                val isAudio = previewData is PreviewData.AudioPreview

                if (isAudio) {
                    Spacer(Modifier.height(4.dp))
                    var isDragging by remember { mutableStateOf(false) }
                    var dragValue by remember { mutableFloatStateOf(0f) }
                    Slider(
                        value = if (isDragging) dragValue
                            else if (playerState.isActive && playerState.duration > 0)
                                playerState.currentPosition.toFloat() / playerState.duration
                            else 0f,
                        onValueChange = {
                            isDragging = true
                            dragValue = it
                        },
                        onValueChangeFinished = {
                            if (playerState.isActive) {
                                val seekTarget = (dragValue * playerState.duration).toLong()
                                playerState.currentPosition = seekTarget
                                playerState.seekTo?.invoke(seekTarget)
                            }
                            isDragging = false
                        },
                        enabled = playerState.isActive,
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (playerState.isActive) 1f else 0f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (playerState.isActive) playerState.currentPosition.toDurationString() else "0:00",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = if (playerState.isActive) 1f else 0.5f
                            )
                        )
                        Text(
                            text = playerState.duration.toDurationString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = if (playerState.isActive) 1f else 0.5f
                            )
                        )
                    }
                }

                // Action buttons (fullscreen / edit / open)
                val isImage = previewData is PreviewData.ImagePreview ||
                    (isLoading && entry.iconRes() == "image")
                val isPdf = previewData is PreviewData.PdfPreview
                val isArchive = previewData is PreviewData.ArchivePreview
                val isText = previewData is PreviewData.TextPreview
                val isFb2 = previewData is PreviewData.Fb2Preview
                val isDocument = (isText && entry.iconRes() == "document") || isFb2
                val isApk = previewData is PreviewData.ApkPreview
                val isEditableText = isText && onEdit != null && entry.iconRes() in listOf("text", "code")
                val hasAction = isMedia || isImage || isPdf || isArchive || isDocument || isEditableText || isApk

                if (hasAction) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))
                    when {
                        isMedia && onFullscreenPlay != null -> {
                            Button(
                                onClick = { onFullscreenPlay(playerState.currentPosition) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Fullscreen, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.play_fullscreen))
                            }
                        }
                        isImage && onOpenGallery != null -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onOpenGallery,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Fullscreen, null, Modifier.size(20.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.open_in_gallery))
                                }
                                if (onDelete != null) {
                                    IconButton(
                                        onClick = onDelete,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.DeleteOutline,
                                            contentDescription = stringResource(R.string.delete),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                        isPdf && onOpenPdf != null -> {
                            Button(
                                onClick = onOpenPdf,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Fullscreen, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.open_in_reader))
                            }
                        }
                        isArchive && onOpenArchive != null -> {
                            Button(
                                onClick = onOpenArchive,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Fullscreen, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.open_archive))
                            }
                        }
                        isDocument && onOpenDocument != null -> {
                            Button(
                                onClick = onOpenDocument,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Fullscreen, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.open_in_reader))
                            }
                        }
                        isEditableText -> {
                            Button(
                                onClick = onEdit,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Edit, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.edit_action))
                            }
                        }
                        isApk && onInstallApk != null -> {
                            Button(
                                onClick = onInstallApk,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.PlayArrow, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.install_action))
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Pager for all file types ---

@Composable
private fun PreviewPagerContent(
    entry: FileEntry,
    previewData: PreviewData?,
    isLoading: Boolean,
    error: String?,
    currentIndex: Int,
    totalFiles: Int,
    adjacentFiles: List<FileEntry>,
    onPageChanged: (Int) -> Unit,
    onEdit: (() -> Unit)?,
    playerState: InlinePlayerState,
    previewCache: Map<Int, PreviewData>,
    serviceConnected: Boolean = false,
    currentAdapterIndex: Int = -1,
    mediaToPageIndex: Map<Int, Int> = emptyMap(),
    onStartPlayback: ((Int) -> Unit)? = null,
    onTogglePlayPause: () -> Unit = {},
    onFullscreenPlay: (() -> Unit)? = null
) {
    val pagerState = rememberPagerState(
        initialPage = currentIndex,
        pageCount = { totalFiles }
    )
    val dummyPlayerState = remember { InlinePlayerState() }

    // Notify ViewModel when page changes
    val currentOnPageChanged by rememberUpdatedState(onPageChanged)
    LaunchedEffect(pagerState) {
        var previousPage = pagerState.settledPage
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page != previousPage) {
                previousPage = page
                currentOnPageChanged(page)
            }
        }
    }

    // Auto-advance pager when adapter changes track (folder repeat)
    LaunchedEffect(currentAdapterIndex) {
        if (currentAdapterIndex < 0) return@LaunchedEffect
        val targetPage = mediaToPageIndex[currentAdapterIndex]
        if (targetPage != null && targetPage != pagerState.currentPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Pager (underneath) — video pages show thumbnail only
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { page ->
            when {
                page == currentIndex -> {
                    PreviewContentBlock(
                        entry = entry,
                        previewData = previewData,
                        isLoading = isLoading,
                        error = error,
                        onEdit = onEdit,
                        playerState = playerState,
                        serviceConnected = serviceConnected,
                        onStartPlayback = { onStartPlayback?.invoke(page) },
                        onTogglePlayPause = onTogglePlayPause,
                        onFullscreenPlay = onFullscreenPlay
                    )
                }
                page in previewCache -> {
                    val cachedData = previewCache[page]!!
                    val cachedEntry = adjacentFiles[page]
                    PreviewContentBlock(
                        entry = cachedEntry,
                        previewData = cachedData,
                        isLoading = false,
                        error = null,
                        onEdit = null,
                        playerState = if (cachedData is PreviewData.VideoPreview || cachedData is PreviewData.AudioPreview) playerState else dummyPlayerState,
                        serviceConnected = serviceConnected,
                        onStartPlayback = { onStartPlayback?.invoke(page) },
                        onTogglePlayPause = onTogglePlayPause,
                        onFullscreenPlay = onFullscreenPlay
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.AutoMirrored.Filled.InsertDriveFile,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = adjacentFiles.getOrNull(page)?.name ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

    }
}

// --- Single preview content block ---

@Composable
private fun PreviewContentBlock(
    entry: FileEntry,
    previewData: PreviewData?,
    isLoading: Boolean,
    error: String?,
    onEdit: (() -> Unit)?,
    playerState: InlinePlayerState = InlinePlayerState(),
    serviceConnected: Boolean = false,
    onStartPlayback: (() -> Unit)? = null,
    onTogglePlayPause: () -> Unit = {},
    onFullscreenPlay: (() -> Unit)? = null
) {
    when {
        isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        }
        error != null -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
        previewData != null -> {
            when (previewData) {
                is PreviewData.ImagePreview -> ImagePreviewContent(previewData)
                is PreviewData.VideoPreview -> {
                    InlineVideoContent(
                        thumbnail = previewData.thumbnail,
                        onFullscreenPlay = onFullscreenPlay ?: {}
                    )
                }
                is PreviewData.AudioPreview -> {
                    InlineAudioContent(
                        data = previewData,
                        playerState = playerState,
                        serviceConnected = serviceConnected,
                        onStartPlayback = onStartPlayback,
                        onTogglePlayPause = onTogglePlayPause
                    )
                }
                is PreviewData.TextPreview -> {
                    TextPreviewContent(previewData)
                }
                is PreviewData.PdfPreview -> PdfPreviewContent(previewData)
                is PreviewData.ArchivePreview -> ArchivePreviewContent(previewData)
                is PreviewData.ApkPreview -> ApkPreviewContent(previewData)
                is PreviewData.Fb2Preview -> Fb2PreviewContent(previewData)
                is PreviewData.UnsupportedPreview -> UnsupportedPreviewContent()
            }
        }
    }
}

// --- Inline Video (thumbnail + fullscreen play button) ---

@Composable
private fun InlineVideoContent(
    thumbnail: Bitmap?,
    onFullscreenPlay: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            )
        }
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f),
                    RoundedCornerShape(28.dp)
                )
                .clickable { onFullscreenPlay() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.PlayCircleFilled,
                contentDescription = stringResource(R.string.play),
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

// --- Inline Audio ---

@Composable
private fun InlineAudioContent(
    data: PreviewData.AudioPreview,
    playerState: InlinePlayerState,
    serviceConnected: Boolean,
    onStartPlayback: (() -> Unit)?,
    onTogglePlayPause: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    if (serviceConnected) onTogglePlayPause()
                    else onStartPlayback?.invoke()
                },
            contentAlignment = Alignment.Center
        ) {
            if (data.albumArt != null) {
                Image(
                    bitmap = data.albumArt.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(80.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.MusicNote,
                        null,
                        Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
                        RoundedCornerShape(18.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (playerState.isActive) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = data.title ?: data.fileName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (data.artist != null) {
                Text(
                    text = data.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (data.album != null) {
                Text(
                    text = data.album,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// --- Static preview content ---

@Composable
private fun ImagePreviewContent(data: PreviewData.ImagePreview) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            bitmap = data.bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${data.width} \u00D7 ${data.height}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TextPreviewContent(data: PreviewData.TextPreview) {
    val lines = remember(data.content) { data.content.lines() }
    val lineNumStyle = MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    )
    val textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 6.dp)
            ) {
                // Line numbers
                Column(
                    modifier = Modifier.padding(start = 2.dp, end = 6.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    lines.forEachIndexed { index, _ ->
                        Text(
                            text = "${index + 1}",
                            style = lineNumStyle,
                            maxLines = 1
                        )
                    }
                }
                // Text content
                Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                    lines.forEach { line ->
                        Text(
                            text = line.ifEmpty { " " },
                            style = textStyle,
                            softWrap = true
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (lines.size < data.totalLines)
                stringResource(R.string.shown_lines_format, lines.size, data.totalLines)
            else
                stringResource(R.string.total_lines, data.totalLines),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PdfPreviewContent(data: PreviewData.PdfPreview) {
    // Always show only first page — no inner HorizontalPager to avoid
    // swipe conflict with the outer file pager. Full navigation in reader.
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            bitmap = data.firstPage.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.pages_count, data.pageCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ArchivePreviewContent(data: PreviewData.ArchivePreview) {
    Column(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
        ) {
            items(data.entries) { archiveEntry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (archiveEntry.isDirectory) Icons.Filled.Folder
                        else Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (archiveEntry.isDirectory)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = archiveEntry.name,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!archiveEntry.isDirectory && archiveEntry.size > 0) {
                        Text(
                            text = archiveEntry.size.toFileSize(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.archive_summary, data.totalEntries, data.totalUncompressedSize.toFileSize()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ApkPreviewContent(data: PreviewData.ApkPreview) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (data.icon != null) {
            Image(
                bitmap = data.icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(128.dp)
                    .clip(RoundedCornerShape(20.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(128.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = data.appName ?: data.fileName,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        if (data.packageName != null) {
            Text(
                text = data.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
        if (data.versionName != null) {
            Text(
                text = "v${data.versionName} (${data.versionCode})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun Fb2PreviewContent(data: PreviewData.Fb2Preview) {
    when {
        data.coverBitmap != null && data.annotation.isNotBlank() -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(2.dp)
            ) {
                Fb2FloatLayout(
                    coverBitmap = data.coverBitmap,
                    annotation = data.annotation,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        data.coverBitmap != null -> {
            Box(
                modifier = Modifier.fillMaxSize().padding(2.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Image(
                    bitmap = data.coverBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .clip(RoundedCornerShape(6.dp))
                )
            }
        }
        data.annotation.isNotBlank() -> {
            Text(
                text = data.annotation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(2.dp)
            )
        }
        else -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.preview_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Float layout: image pinned top-left, text wraps around it line-by-line */
@Composable
private fun Fb2FloatLayout(
    coverBitmap: Bitmap,
    annotation: String,
    modifier: Modifier = Modifier
) {
    val spacingPx = with(LocalDensity.current) { 8.dp.roundToPx() }
    val bodySmall = MaterialTheme.typography.bodySmall
    val textColor = MaterialTheme.colorScheme.onSurface
    val textMeasurer = rememberTextMeasurer()

    SubcomposeLayout(modifier = modifier) { constraints ->
        val totalWidth = constraints.maxWidth
        val imageMaxWidth = (totalWidth * 0.4f).toInt()

        val imagePlaceable = subcompose("cover") {
            Image(
                bitmap = coverBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = Alignment.TopStart,
                modifier = Modifier.clip(RoundedCornerShape(6.dp))
            )
        }.first().measure(
            Constraints(maxWidth = imageMaxWidth, minWidth = 0)
        )

        val imageH = imagePlaceable.height
        val rightX = imagePlaceable.width + spacingPx
        val narrowW = (totalWidth - rightX).coerceAtLeast(1)

        // Measure full text at narrow width to find the split line
        val narrowResult = textMeasurer.measure(
            text = AnnotatedString(annotation),
            style = bodySmall,
            constraints = Constraints(maxWidth = narrowW)
        )

        // Find last line that fully fits beside the image
        var splitLineIdx = -1
        for (i in 0 until narrowResult.lineCount) {
            if (narrowResult.getLineBottom(i).toInt() <= imageH) {
                splitLineIdx = i
            } else break
        }

        val splitOffset = if (splitLineIdx >= 0) {
            narrowResult.getLineEnd(splitLineIdx)
        } else 0

        val topText = annotation.substring(0, splitOffset).trimEnd()
        val bottomText = annotation.substring(splitOffset).trimStart()

        // Top text beside image (narrow)
        val topPlaceable = if (topText.isNotEmpty()) {
            subcompose("topText") {
                Text(text = topText, style = bodySmall, color = textColor)
            }.first().measure(Constraints(maxWidth = narrowW))
        } else null

        // Bottom text at full width, starting below image
        val bottomPlaceable = if (bottomText.isNotEmpty()) {
            subcompose("bottomText") {
                Text(text = bottomText, style = bodySmall, color = textColor)
            }.first().measure(Constraints(maxWidth = totalWidth))
        } else null

        val bottomY = imageH
        val totalHeight = bottomY + (bottomPlaceable?.height ?: 0)

        layout(totalWidth, maxOf(imageH, totalHeight)) {
            imagePlaceable.placeRelative(0, 0)
            topPlaceable?.placeRelative(rightX, 0)
            bottomPlaceable?.placeRelative(0, bottomY)
        }
    }
}

@Composable
private fun UnsupportedPreviewContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.preview_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun buildSubtitle(entry: FileEntry): String {
    val parts = mutableListOf<String>()
    parts.add(entry.size.toFileSize())
    if (entry.extension.isNotEmpty()) parts.add(entry.extension)
    parts.add(entry.lastModified.toRelativeDate())
    return parts.joinToString(" \u00B7 ")
}

