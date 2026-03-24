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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.InsertDriveFile
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
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.vamp.haron.presentation.settings.loadCustomNavbarIcon
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

@Composable
fun CustomNavbar(
    config: NavbarConfig,
    onAction: (NavbarAction) -> Unit,
    shiftModeActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val pagerState = rememberPagerState(pageCount = { config.pages.size.coerceAtLeast(1) })

    // Countdown state
    var showCountdown by remember { mutableStateOf(false) }
    var countdownProgress by remember { mutableFloatStateOf(0f) }
    var countdownAction by remember { mutableStateOf(NavbarAction.NONE) }
    var countdownPosInRoot by remember { mutableStateOf(Offset.Zero) }

    // Radial menu state
    var radialMenuVisible by remember { mutableStateOf(false) }
    var radialMenuPosInRoot by remember { mutableStateOf(Offset.Zero) }
    var radialMenuType by remember { mutableStateOf(NavbarAction.NONE) }
    var radialHighlight by remember { mutableStateOf(-1) } // -1=none, 0=left, 1=right

    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(pagerState.pageCount) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startPos = down.position
                    val slop = viewConfiguration.touchSlop
                    var decided = false
                    var isHorizontal = false

                    try {
                        while (!decided) {
                            val event = awaitPointerEvent()
                            if (event.changes.none { it.pressed }) break
                            val pos = event.changes.firstOrNull()?.position ?: continue
                            val dx = abs(pos.x - startPos.x)
                            val dy = abs(pos.y - startPos.y)
                            if (dx > slop || dy > slop) {
                                isHorizontal = dx > dy
                                decided = true
                            }
                        }
                    } catch (_: Exception) {}

                    if (isHorizontal && decided) {
                        // Track horizontal drag to completion
                        var totalDx = 0f
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.none { it.pressed }) break
                                val pos = event.changes.firstOrNull()?.position ?: continue
                                totalDx = pos.x - startPos.x
                                event.changes.forEach { it.consume() }
                            }
                        } catch (_: Exception) {}

                        // Swipe threshold: 40dp
                        val thresholdPx = with(density) { 40.dp.toPx() }
                        val currentPage = pagerState.currentPage
                        if (totalDx < -thresholdPx && currentPage < pagerState.pageCount - 1) {
                            scope.launch { pagerState.animateScrollToPage(currentPage + 1) }
                        } else if (totalDx > thresholdPx && currentPage > 0) {
                            scope.launch { pagerState.animateScrollToPage(currentPage - 1) }
                        }
                    }
                    // Vertical gesture: not consumed → passes through to system
                }
            }
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
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
                    val isRadial = btn.tapAction in listOf(
                        NavbarAction.COPY_MOVE,
                        NavbarAction.DELETE_MENU,
                        NavbarAction.CREATE_MENU
                    )
                    // Arrow keys have built-in long actions
                    val arrowLong = when (btn.tapAction) {
                        NavbarAction.ARROW_UP -> NavbarAction.SWITCH_PANEL
                        NavbarAction.ARROW_DOWN -> NavbarAction.TOGGLE_SHIFT
                        NavbarAction.ARROW_LEFT -> NavbarAction.UP // go up/parent
                        NavbarAction.ARROW_RIGHT -> NavbarAction.ENTER_FOLDER
                        else -> null
                    }
                    val effectiveLong = arrowLong ?: btn.longAction
                    val hasLong = effectiveLong != NavbarAction.NONE

                    if (isRadial) {
                        NavbarRadialButton(
                            action = btn.tapAction,
                            onTap = { onAction(btn.tapAction) },
                            onRadialShow = { action, pos ->
                                radialMenuType = action
                                radialMenuPosInRoot = pos
                                radialMenuVisible = true
                                radialHighlight = -1
                            },
                            onRadialMove = { highlight -> radialHighlight = highlight },
                            onRadialSelect = { selectedAction ->
                                radialMenuVisible = false
                                if (selectedAction != NavbarAction.NONE) onAction(selectedAction)
                            },
                            onRadialCancel = { radialMenuVisible = false }
                        )
                    } else if (!hasLong) {
                        NavbarIconButton(
                            action = btn.tapAction,
                            onClick = { if (hasTap) onAction(btn.tapAction) }
                        )
                    } else {
                        val isArrow = btn.tapAction in listOf(
                            NavbarAction.ARROW_UP, NavbarAction.ARROW_DOWN,
                            NavbarAction.ARROW_LEFT, NavbarAction.ARROW_RIGHT
                        )
                        val isShiftBtn = btn.tapAction == NavbarAction.ARROW_DOWN && shiftModeActive
                        NavbarHoldButton(
                            tapAction = btn.tapAction,
                            longAction = effectiveLong,
                            onTap = { if (hasTap) onAction(btn.tapAction) },
                            onLongComplete = { onAction(effectiveLong) },
                            onCountdownStart = { action, pos ->
                                countdownAction = action
                                countdownPosInRoot = pos
                                showCountdown = true
                                countdownProgress = 0f
                            },
                            onCountdownProgress = { countdownProgress = it },
                            onCountdownEnd = { showCountdown = false },
                            holdDurationMs = if (isArrow) 1300L else 2600L,
                            highlighted = isShiftBtn
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

        // Radial menu overlay (two circles above button)
        if (radialMenuVisible) {
            val circleSize = 52.dp
            val spacing = 16.dp
            val glowWidth = 20.dp
            val radialOptions = radialMenuOptions(radialMenuType)
            val btnCenterX = with(density) { radialMenuPosInRoot.x.toDp() } + 24.dp

            // Screen width for edge clamping
            val screenWidthDp = with(density) {
                (this@Box).let { _ -> 0.dp } // placeholder
            }
            val configuration = LocalContext.current.resources.configuration
            val screenWidth = configuration.screenWidthDp.dp

            radialOptions.forEachIndexed { index, (action, icon, label) ->
                val isLeft = index == 0
                var offsetX = if (isLeft) btnCenterX - circleSize - spacing / 2
                    else btnCenterX + spacing / 2

                // Clamp to screen edges (2dp margin)
                val margin = 2.dp
                if (offsetX < margin) offsetX = margin
                if (offsetX + circleSize > screenWidth - margin) offsetX = screenWidth - margin - circleSize

                val isHighlighted = radialHighlight == index
                val bgColor = if (isHighlighted)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f)
                val iconTint = if (isHighlighted)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else if (action == NavbarAction.DELETE || action == NavbarAction.DELETE_MENU)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onSurface

                Box(
                    modifier = Modifier
                        .size(circleSize)
                        .align(Alignment.TopStart)
                        .offset(
                            x = offsetX,
                            y = -circleSize - 38.dp
                        )
                        .graphicsLayer {
                            if (isHighlighted) {
                                scaleX = 2f
                                scaleY = 2f
                            }
                            clip = false
                        }
                        .background(bgColor, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = label,
                        modifier = Modifier.size(24.dp),
                        tint = iconTint
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
private fun NavbarIconButton(
    action: NavbarAction,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val customBmp = remember(action) { loadCustomNavbarIcon(context, action) }

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
        if (customBmp != null) {
            Image(
                bitmap = customBmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Icon(
                actionIcon(action),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = actionTint(action)
            )
        }
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
    onCountdownEnd: () -> Unit,
    holdDurationMs: Long = 2600L,
    highlighted: Boolean = false
) {
    val context = LocalContext.current
    val customBmp = remember(tapAction) { loadCustomNavbarIcon(context, tapAction) }
    var posInRoot by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .size(48.dp)
            .then(if (highlighted) Modifier.background(
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
                shape = CircleShape
            ) else Modifier)
            .onGloballyPositioned { coords -> posInRoot = coords.positionInRoot() }
            .pointerInput(holdDurationMs) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startPos = down.position
                    val startTime = System.currentTimeMillis()
                    val tapThreshold = 500L
                    val countdownDelay = holdDurationMs * 600L / 2600L
                    val duration = holdDurationMs
                    val slop = viewConfiguration.touchSlop

                    // Phase 1: detect quick tap (await UP directly, no polling)
                    var isTap = false
                    var draggedAway = false
                    withTimeoutOrNull(tapThreshold) {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.none { it.pressed }) { isTap = true; break }
                            val pos = event.changes.firstOrNull()?.position
                            if (pos != null) {
                                val dist = kotlin.math.hypot((pos.x - startPos.x).toDouble(), (pos.y - startPos.y).toDouble())
                                if (dist > slop * 3) { draggedAway = true; break }
                            }
                        }
                    }

                    if (isTap) {
                        onTap()
                        return@awaitEachGesture
                    }
                    if (draggedAway) return@awaitEachGesture

                    // Phase 2: long press with countdown animation
                    var completed = false
                    var cancelled = false

                    try {
                        while (!completed && !cancelled) {
                            val event = withTimeoutOrNull(16) { awaitPointerEvent() }
                            val elapsed = System.currentTimeMillis() - startTime

                            if (elapsed >= countdownDelay) {
                                onCountdownStart(longAction, posInRoot)
                                onCountdownProgress(((elapsed - countdownDelay).toFloat() / (duration - countdownDelay)).coerceIn(0f, 1f))
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
                    if (completed) onLongComplete()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (customBmp != null) {
            Image(
                bitmap = customBmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Icon(
                actionIcon(tapAction),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = actionTint(tapAction)
            )
        }
    }
}

/**
 * Returns the two radial menu options for a given radial action.
 * Each option: (NavbarAction to dispatch, icon, label)
 */
private fun radialMenuOptions(action: NavbarAction): List<Triple<NavbarAction, ImageVector, String>> = when (action) {
    NavbarAction.COPY_MOVE -> listOf(
        Triple(NavbarAction.COPY, Icons.Filled.ContentCopy, "Copy"),
        Triple(NavbarAction.MOVE, Icons.Filled.DriveFileMove, "Move")
    )
    NavbarAction.DELETE_MENU -> listOf(
        Triple(NavbarAction.DELETE, Icons.Filled.Delete, "Trash"),
        Triple(NavbarAction.FORCE_DELETE, Icons.Filled.DeleteForever, "Forever")
    )
    NavbarAction.CREATE_MENU -> listOf(
        Triple(NavbarAction.CREATE_FILE, Icons.Filled.InsertDriveFile, "File"),
        Triple(NavbarAction.CREATE_NEW, Icons.Filled.CreateNewFolder, "Folder")
    )
    else -> emptyList()
}

@Composable
private fun NavbarRadialButton(
    action: NavbarAction,
    onTap: () -> Unit,
    onRadialShow: (NavbarAction, Offset) -> Unit,
    onRadialMove: (Int) -> Unit,
    onRadialSelect: (NavbarAction) -> Unit,
    onRadialCancel: () -> Unit
) {
    val context = LocalContext.current
    val customBmp = remember(action) { loadCustomNavbarIcon(context, action) }
    val density = LocalDensity.current
    var posInRoot by remember { mutableStateOf(Offset.Zero) }
    val options = radialMenuOptions(action)

    Box(
        modifier = Modifier
            .size(48.dp)
            .onGloballyPositioned { coords -> posInRoot = coords.positionInRoot() }
            .pointerInput(Unit) {
                val circleSizePx = with(density) { 52.dp.toPx() }
                val spacingPx = with(density) { 16.dp.toPx() }
                val circleRadius = circleSizePx / 2
                val circleCenterY = -(circleSizePx + with(density) { 38.dp.toPx() }) + circleRadius
                val btnCenterX = size.width / 2f

                // Left circle center
                val leftCx = btnCenterX - spacingPx / 2 - circleRadius
                val rightCx = btnCenterX + spacingPx / 2 + circleRadius

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startPos = down.position
                    val startTime = System.currentTimeMillis()
                    val tapThreshold = 400L
                    val holdDelay = 400L
                    val slop = viewConfiguration.touchSlop
                    var radialShown = false

                    // Phase 1: detect quick tap
                    var isTap = false
                    var draggedAway = false
                    withTimeoutOrNull(tapThreshold) {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.none { it.pressed }) { isTap = true; break }
                            val pos = event.changes.firstOrNull()?.position
                            if (pos != null) {
                                val dist = kotlin.math.hypot(
                                    (pos.x - startPos.x).toDouble(),
                                    (pos.y - startPos.y).toDouble()
                                )
                                if (dist > slop * 3) { draggedAway = true; break }
                            }
                        }
                    }

                    if (isTap) {
                        // Default tap action
                        val defaultAction = when (action) {
                            NavbarAction.COPY_MOVE -> NavbarAction.COPY
                            NavbarAction.DELETE_MENU -> NavbarAction.DELETE
                            NavbarAction.CREATE_MENU -> NavbarAction.CREATE_NEW
                            else -> NavbarAction.NONE
                        }
                        onRadialSelect(defaultAction)
                        return@awaitEachGesture
                    }
                    if (draggedAway) { onRadialCancel(); return@awaitEachGesture }

                    // Phase 2: show radial menu, track finger
                    onRadialShow(action, posInRoot)
                    radialShown = true
                    var selectedIndex = -1

                    try {
                        while (true) {
                            val event = withTimeoutOrNull(16) { awaitPointerEvent() }
                            if (event != null) {
                                if (event.changes.none { it.pressed }) break
                                val pos = event.changes.firstOrNull()?.position ?: continue
                                // Hit-test against circles (pos is relative to button)
                                val distLeft = kotlin.math.hypot(
                                    (pos.x - leftCx).toDouble(),
                                    (pos.y - circleCenterY).toDouble()
                                )
                                val distRight = kotlin.math.hypot(
                                    (pos.x - rightCx).toDouble(),
                                    (pos.y - circleCenterY).toDouble()
                                )
                                selectedIndex = when {
                                    distLeft < circleRadius * 1.3 -> 0
                                    distRight < circleRadius * 1.3 -> 1
                                    else -> -1
                                }
                                onRadialMove(selectedIndex)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } catch (_: Exception) {}

                    if (selectedIndex >= 0 && selectedIndex < options.size) {
                        onRadialSelect(options[selectedIndex].first)
                    } else {
                        onRadialCancel()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (customBmp != null) {
            Image(
                bitmap = customBmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Icon(
                actionIcon(action),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = actionTint(action)
            )
        }
    }
}


@Composable
private fun actionTint(action: NavbarAction) = when (action) {
    NavbarAction.NONE -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    NavbarAction.EXIT -> MaterialTheme.colorScheme.error
    NavbarAction.DELETE -> MaterialTheme.colorScheme.error
    NavbarAction.DELETE_MENU -> MaterialTheme.colorScheme.error
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
    NavbarAction.COPY_MOVE -> Icons.Filled.ContentCopy
    NavbarAction.DELETE_MENU -> Icons.Filled.Delete
    NavbarAction.CREATE_MENU -> Icons.Filled.Add
    NavbarAction.FORCE_DELETE -> Icons.Filled.DeleteForever
    NavbarAction.CREATE_FILE -> Icons.Filled.InsertDriveFile
    NavbarAction.ARROW_UP -> Icons.Filled.KeyboardArrowUp
    NavbarAction.ARROW_DOWN -> Icons.Filled.KeyboardArrowDown
    NavbarAction.ARROW_LEFT -> Icons.Filled.KeyboardArrowLeft
    NavbarAction.ARROW_RIGHT -> Icons.Filled.KeyboardArrowRight
    NavbarAction.SWITCH_PANEL -> Icons.Filled.SwapHoriz
    NavbarAction.ENTER_FOLDER -> Icons.Filled.KeyboardArrowRight
    NavbarAction.CURSOR_LEFT -> Icons.Filled.KeyboardArrowLeft
    NavbarAction.CURSOR_RIGHT -> Icons.Filled.KeyboardArrowRight
    NavbarAction.TOGGLE_SHIFT -> Icons.Filled.SelectAll
}
