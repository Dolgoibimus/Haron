package com.vamp.haron.presentation.gallery

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.exifinterface.media.ExifInterface
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamp.haron.R
import com.vamp.haron.common.util.toFileSize
import com.vamp.haron.domain.model.GalleryHolder
import com.vamp.haron.domain.model.TransferHolder
import com.vamp.haron.presentation.cast.CastViewModel
import com.vamp.haron.presentation.cast.components.CastButton
import com.vamp.haron.presentation.cast.components.CastDeviceSheet
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val MAX_IMAGE_DIMENSION = 4096

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    startIndex: Int = 0,
    onBack: () -> Unit
) {
    val items = remember { GalleryHolder.items }
    if (items.isEmpty()) {
        onBack()
        return
    }

    val context = LocalContext.current
    val activity = context as? Activity
    val castViewModel: CastViewModel = hiltViewModel(
        viewModelStoreOwner = LocalContext.current as androidx.activity.ComponentActivity
    )

    // Immersive mode: hide system bars, toggle with tap
    DisposableEffect(Unit) {
        val window = activity?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, items.lastIndex),
        pageCount = { items.size }
    )
    var currentIndex by remember { mutableStateOf(startIndex.coerceIn(0, items.lastIndex)) }
    var controlsVisible by remember { mutableStateOf(false) }
    var isZoomed by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Hide VoiceFab in gallery, sync with controls visibility
    LaunchedEffect(Unit) { TransferHolder.voiceFabVisible.value = false }
    DisposableEffect(Unit) {
        onDispose {
            TransferHolder.voiceFabVisible.value = true
            TransferHolder.voiceFabPinned.value = false
        }
    }
    LaunchedEffect(controlsVisible) {
        TransferHolder.voiceFabVisible.value = controlsVisible
    }

    // Track page changes
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            currentIndex = page
            isZoomed = false
        }
    }

    // Sync system bars with controls visibility
    LaunchedEffect(controlsVisible) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (controlsVisible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
            delay(3000)
            if (!TransferHolder.voiceFabPinned.value) controlsVisible = false
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    val currentItem = items.getOrNull(currentIndex)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            userScrollEnabled = !isZoomed
        ) { page ->
            val item = items[page]
            ZoomableImagePage(
                filePath = item.filePath,
                onTap = { controlsVisible = !controlsVisible },
                onZoomChanged = { zoomed -> isZoomed = zoomed }
            )
        }

        // Top bar
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = currentItem?.fileName ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    CastButton(
                        isConnected = castViewModel.isConnected,
                        onClick = { castViewModel.showSheet() }
                    )
                    IconButton(onClick = {
                        currentItem?.let { item ->
                            coroutineScope.launch {
                                shareFile(context, item.filePath)
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Share, stringResource(R.string.share))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }

        // Bottom bar
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${currentIndex + 1} / ${items.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
                currentItem?.let { item ->
                    Text(
                        text = item.fileSize.toFileSize(),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
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
                    currentItem?.let { item ->
                        castViewModel.selectDeviceAndCast(device, item.filePath, item.fileName)
                    }
                },
                onDisconnect = { castViewModel.disconnect() },
                onDismiss = { castViewModel.hideSheet() }
            )
        }
    }
}

@Composable
private fun ZoomableImagePage(
    filePath: String,
    onTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit = {}
) {
    var bitmap by remember(filePath) { mutableStateOf<Bitmap?>(null) }
    var imageWidth by remember(filePath) { mutableStateOf(0) }
    var imageHeight by remember(filePath) { mutableStateOf(0) }
    var isLoading by remember(filePath) { mutableStateOf(true) }
    val context = LocalContext.current

    // Load image
    LaunchedEffect(filePath) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val isContentUri = filePath.startsWith("content://")

                // Decode bounds
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                if (isContentUri) {
                    context.contentResolver.openInputStream(Uri.parse(filePath))?.use {
                        BitmapFactory.decodeStream(it, null, options)
                    }
                } else {
                    BitmapFactory.decodeFile(filePath, options)
                }

                val rawW = options.outWidth
                val rawH = options.outHeight

                // Calculate sample size for large images
                val sampleSize = calculateSampleSize(rawW, rawH, MAX_IMAGE_DIMENSION)
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }

                var bmp = if (isContentUri) {
                    context.contentResolver.openInputStream(Uri.parse(filePath))?.use {
                        BitmapFactory.decodeStream(it, null, decodeOpts)
                    }
                } else {
                    BitmapFactory.decodeFile(filePath, decodeOpts)
                }

                // Apply EXIF rotation
                if (bmp != null) {
                    val orientation = try {
                        val exif = if (isContentUri) {
                            context.contentResolver.openInputStream(Uri.parse(filePath))?.use {
                                ExifInterface(it)
                            }
                        } else {
                            ExifInterface(filePath)
                        }
                        exif?.getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                        ) ?: ExifInterface.ORIENTATION_NORMAL
                    } catch (_: Exception) {
                        ExifInterface.ORIENTATION_NORMAL
                    }

                    bmp = applyExifRotation(bmp, orientation)
                    imageWidth = bmp.width
                    imageHeight = bmp.height
                    bitmap = bmp
                }
            } catch (_: Exception) {
                bitmap = null
            }
        }
        isLoading = false
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
        }
        return
    }

    val bmp = bitmap
    if (bmp == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.failed_to_load), color = Color.White.copy(alpha = 0.6f))
        }
        return
    }

    // Zoom state
    var scale by remember(filePath) { mutableFloatStateOf(1f) }
    var offsetX by remember(filePath) { mutableFloatStateOf(0f) }
    var offsetY by remember(filePath) { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(filePath) {
                val touchSlop = viewConfiguration.touchSlop
                var lastTapTime = 0L
                var pendingTapJob: Job? = null

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var wasMoved = false
                    var wasMultiTouch = false

                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }

                        if (pressed.size >= 2) {
                            wasMultiTouch = true
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            if (newScale > 1f) {
                                val maxOffsetX = (newScale - 1f) * imageWidth / 2f
                                val maxOffsetY = (newScale - 1f) * imageHeight / 2f
                                offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                            scale = newScale
                            onZoomChanged(newScale > 1.01f)
                            event.changes.forEach { it.consume() }
                        } else if (pressed.size == 1 && scale > 1.01f) {
                            wasMoved = true
                            val pan = event.calculatePan()
                            val maxOffsetX = (scale - 1f) * imageWidth / 2f
                            val maxOffsetY = (scale - 1f) * imageHeight / 2f
                            offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                            offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                            event.changes.forEach { it.consume() }
                        } else if (pressed.size == 1 && !wasMoved) {
                            val change = pressed.first()
                            if ((change.position - down.position).getDistance() > touchSlop) {
                                wasMoved = true
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    // Tap / double-tap detection
                    if (!wasMultiTouch && !wasMoved) {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < 300) {
                            // Double tap
                            pendingTapJob?.cancel()
                            lastTapTime = 0L
                            if (scale > 1.1f) {
                                scale = 1f; offsetX = 0f; offsetY = 0f
                                onZoomChanged(false)
                            } else {
                                scale = 2f
                                onZoomChanged(true)
                            }
                        } else {
                            // Single tap (delayed to allow double-tap)
                            lastTapTime = now
                            pendingTapJob?.cancel()
                            pendingTapJob = scope.launch {
                                delay(300)
                                onTap()
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )
    }
}

private fun calculateSampleSize(rawW: Int, rawH: Int, maxDim: Int): Int {
    var sample = 1
    if (rawW > maxDim || rawH > maxDim) {
        val halfW = rawW / 2
        val halfH = rawH / 2
        while (halfW / sample >= maxDim && halfH / sample >= maxDim) {
            sample *= 2
        }
    }
    return sample
}

private fun applyExifRotation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.preScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.preScale(-1f, 1f)
        }
        else -> return bitmap
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun shareFile(context: android.content.Context, filePath: String) {
    try {
        val uri = if (filePath.startsWith("content://")) {
            Uri.parse(filePath)
        } else {
            Uri.fromFile(File(filePath))
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)))
    } catch (_: Exception) {
        // Silently ignore share errors
    }
}
