package com.vamp.haron.presentation.explorer.components

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.vamp.haron.data.datastore.HaronPreferences
import com.vamp.haron.domain.model.NavbarAction
import com.vamp.haron.domain.model.NavbarConfig
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun CustomNavbar(
    config: NavbarConfig,
    onAction: (NavbarAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val pagerState = rememberPagerState(pageCount = { config.pages.size.coerceAtLeast(1) })

    // Countdown state
    var showCountdown by remember { mutableStateOf(false) }
    var countdownProgress by remember { mutableFloatStateOf(0f) }
    var countdownAction by remember { mutableStateOf(NavbarAction.NONE) }
    var countdownPosInRoot by remember { mutableStateOf(Offset.Zero) }

    Box(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) { pageIndex ->
            val page = config.pages.getOrNull(pageIndex) ?: return@HorizontalPager
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (btn in page.buttons) {
                    val hasTap = btn.tapAction != NavbarAction.NONE
                    val hasLong = btn.longAction != NavbarAction.NONE

                    if (btn.tapAction == NavbarAction.APP_ICON) {
                        // App icon button — no actions
                        AppIconButton()
                    } else if (!hasLong) {
                        // Simple tap-only button
                        NavbarIconButton(
                            action = btn.tapAction,
                            onClick = { if (hasTap) onAction(btn.tapAction) }
                        )
                    } else {
                        // Tap + long press button with countdown
                        NavbarHoldButton(
                            tapAction = btn.tapAction,
                            longAction = btn.longAction,
                            onTap = { if (hasTap) onAction(btn.tapAction) },
                            onLongComplete = { onAction(btn.longAction) },
                            onCountdownStart = { action, pos ->
                                countdownAction = action
                                countdownPosInRoot = pos
                                showCountdown = true
                                countdownProgress = 0f
                            },
                            onCountdownProgress = { countdownProgress = it },
                            onCountdownEnd = { showCountdown = false }
                        )
                    }
                }
            }
        }

        // Page indicator dots
        if (config.pages.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(config.pages.size) { i ->
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == pagerState.currentPage) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                    )
                }
            }
        }

        // Countdown circle overlay
        if (showCountdown) {
            val circleSize = 56.dp
            Box(
                modifier = Modifier
                    .size(circleSize)
                    .align(Alignment.TopStart)
                    .offset(
                        x = with(density) { countdownPosInRoot.x.toDp() } - circleSize / 2 + 24.dp,
                        y = -circleSize - 4.dp
                    )
                    .background(
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { countdownProgress },
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.error,
                    strokeWidth = 3.dp
                )
                Icon(
                    actionIcon(countdownAction),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun AppIconButton() {
    val context = LocalContext.current
    Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        val appIcon = remember {
            try {
                val pm = context.packageManager
                val drawable = pm.getApplicationIcon(context.packageName)
                val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, 64, 64)
                drawable.draw(canvas)
                bmp
            } catch (_: Exception) { null }
        }
        if (appIcon != null) {
            Image(
                bitmap = appIcon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(32.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun NavbarIconButton(
    action: NavbarAction,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val event = awaitPointerEvent()
                    if (event.changes.none { it.pressed }) {
                        onClick()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            actionIcon(action),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = actionTint(action)
        )
    }
}

@Composable
private fun NavbarHoldButton(
    tapAction: NavbarAction,
    longAction: NavbarAction,
    onTap: () -> Unit,
    onLongComplete: () -> Unit,
    onCountdownStart: (NavbarAction, Offset) -> Unit,
    onCountdownProgress: (Float) -> Unit,
    onCountdownEnd: () -> Unit
) {
    var posInRoot by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .size(48.dp)
            .onGloballyPositioned { coords -> posInRoot = coords.positionInRoot() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startPos = down.position
                    val startTime = System.currentTimeMillis()
                    val holdDelay = 300L
                    val duration = 2000L
                    var completed = false
                    var cancelled = false
                    val slop = viewConfiguration.touchSlop

                    try {
                        while (!completed && !cancelled) {
                            val event = withTimeoutOrNull(16) { awaitPointerEvent() }
                            val elapsed = System.currentTimeMillis() - startTime

                            if (elapsed >= holdDelay) {
                                onCountdownStart(longAction, posInRoot)
                                onCountdownProgress(((elapsed - holdDelay).toFloat() / (duration - holdDelay)).coerceIn(0f, 1f))
                            }

                            if (event != null) {
                                if (event.changes.none { it.pressed }) { cancelled = true; break }
                                val pos = event.changes.firstOrNull()?.position
                                if (pos != null) {
                                    val dist = kotlin.math.hypot((pos.x - startPos.x).toDouble(), (pos.y - startPos.y).toDouble())
                                    if (dist > slop * 3) { cancelled = true; break }
                                }
                            }

                            if (elapsed >= duration) { completed = true }
                        }
                    } catch (_: Exception) { cancelled = true }

                    onCountdownEnd()
                    if (completed) {
                        onLongComplete()
                    } else if (cancelled && (System.currentTimeMillis() - startTime) < holdDelay) {
                        onTap()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            actionIcon(tapAction),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = actionTint(tapAction)
        )
    }
}

@Composable
private fun actionTint(action: NavbarAction) = when (action) {
    NavbarAction.NONE -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    NavbarAction.EXIT -> MaterialTheme.colorScheme.error
    NavbarAction.DELETE -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurface
}

private fun actionIcon(action: NavbarAction): ImageVector = when (action) {
    NavbarAction.NONE -> Icons.Filled.MoreHoriz
    NavbarAction.BACK -> Icons.AutoMirrored.Filled.ArrowBack
    NavbarAction.FORWARD -> Icons.AutoMirrored.Filled.ArrowForward
    NavbarAction.UP -> Icons.Filled.VerticalAlignTop
    NavbarAction.HOME -> Icons.Filled.Home
    NavbarAction.EXIT -> Icons.AutoMirrored.Filled.ExitToApp
    NavbarAction.REFRESH -> Icons.Filled.Refresh
    NavbarAction.SEARCH -> Icons.Filled.Search
    NavbarAction.SETTINGS -> Icons.Filled.Settings
    NavbarAction.TERMINAL -> Icons.Filled.Terminal
    NavbarAction.LIBRARY -> Icons.Filled.LibraryBooks
    NavbarAction.TRANSFER -> Icons.Filled.SwapHoriz
    NavbarAction.TRASH -> Icons.Filled.Delete
    NavbarAction.STORAGE -> Icons.Filled.Storage
    NavbarAction.APPS -> Icons.Filled.Apps
    NavbarAction.DUPLICATES -> Icons.Filled.FolderOpen
    NavbarAction.SCANNER -> Icons.Filled.QrCodeScanner
    NavbarAction.SELECT_ALL -> Icons.Filled.SelectAll
    NavbarAction.TOGGLE_HIDDEN -> Icons.Filled.Visibility
    NavbarAction.CREATE_NEW -> Icons.Filled.Add
    NavbarAction.COPY -> Icons.Filled.ContentCopy
    NavbarAction.MOVE -> Icons.Filled.DriveFileMove
    NavbarAction.DELETE -> Icons.Filled.Delete
    NavbarAction.RENAME -> Icons.Filled.Edit
    NavbarAction.APP_ICON -> Icons.Filled.MoreHoriz
}
