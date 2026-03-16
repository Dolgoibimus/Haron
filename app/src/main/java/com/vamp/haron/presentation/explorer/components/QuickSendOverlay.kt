package com.vamp.haron.presentation.explorer.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vamp.haron.R
import com.vamp.haron.presentation.common.ProgressInfoRow
import com.vamp.haron.common.util.toFileSize
import com.vamp.haron.data.network.NetworkDevice
import com.vamp.haron.domain.model.TransferProgressInfo
import com.vamp.haron.presentation.explorer.state.QuickSendState
import kotlin.math.roundToInt

@Composable
fun QuickSendOverlay(
    state: QuickSendState,
    modifier: Modifier = Modifier
) {
    when (state) {
        is QuickSendState.DraggingToDevice -> {
            DraggingToDeviceOverlay(
                dragOffset = state.dragOffset,
                devices = state.haronDevices,
                showAtBottom = state.fromTopPanel,
                modifier = modifier
            )
        }
        is QuickSendState.Sending -> {
            SendingOverlay(
                deviceName = state.deviceName,
                progress = state.progress,
                modifier = modifier
            )
        }
        is QuickSendState.Idle -> { /* nothing */ }
    }
}

@Composable
private fun DraggingToDeviceOverlay(
    dragOffset: Offset,
    devices: List<NetworkDevice>,
    showAtBottom: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    // Track each row's bounds in window coordinates for hit-test
    val rowBounds = remember { mutableStateMapOf<Int, Rect>() }

    Box(modifier = modifier.fillMaxSize()) {
        // Device list — at top or bottom depending on which panel initiated drag
        Column(
            modifier = Modifier
                .align(if (showAtBottom) Alignment.BottomCenter else Alignment.TopCenter)
                .fillMaxWidth()
                .then(
                    if (!showAtBottom) Modifier.windowInsetsPadding(WindowInsets.statusBars)
                    else Modifier
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            devices.forEachIndexed { index, device ->
                if (index > 0) Spacer(Modifier.height(4.dp))

                val bounds = rowBounds[index]
                val isHighlighted = bounds != null && bounds.contains(dragOffset)

                DeviceRow(
                    device = device,
                    isHighlighted = isHighlighted,
                    modifier = Modifier.onGloballyPositioned { coords ->
                        rowBounds[index] = coords.boundsInWindow()
                    }
                )
            }
        }

        // File preview follows finger
        Surface(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (dragOffset.x - with(density) { 24.dp.toPx() }).roundToInt(),
                        (dragOffset.y - with(density) { 24.dp.toPx() }).roundToInt()
                    )
                }
                .size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.InsertDriveFile, null,
                    Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: NetworkDevice,
    isHighlighted: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "marquee")
    val marqueeOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = -1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "marquee"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isHighlighted) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 4.dp,
        tonalElevation = if (isHighlighted) 4.dp else 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.PhoneAndroid, null,
                Modifier.size(24.dp),
                tint = if (isHighlighted) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clipToBounds()
            ) {
                Text(
                    device.displayName,
                    modifier = Modifier.marqueeOffset(marqueeOffset),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    softWrap = false,
                    color = if (isHighlighted) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/** Shifts text left by a fraction of (textWidth - containerWidth) for marquee effect */
private fun Modifier.marqueeOffset(fraction: Float): Modifier = layout { measurable, constraints ->
    // Measure with unbounded width to get full text width
    val placeable = measurable.measure(
        constraints.copy(maxWidth = androidx.compose.ui.unit.Constraints.Infinity)
    )
    val containerWidth = constraints.maxWidth
    val overflow = (placeable.width - containerWidth).coerceAtLeast(0)
    layout(containerWidth, placeable.height) {
        if (overflow > 0) {
            val offsetX = (fraction * overflow).roundToInt()
            placeable.placeRelative(offsetX, 0)
        } else {
            placeable.placeRelative(0, 0)
        }
    }
}

@Composable
private fun SendingOverlay(
    deviceName: String,
    progress: TransferProgressInfo?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.PhoneAndroid,
                        contentDescription = null,
                        Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.quick_send_sending, deviceName),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (progress != null && progress.currentFileName.isNotEmpty()) {
                            Text(
                                progress.currentFileName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (progress != null && progress.totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }
                if (progress != null && progress.totalBytes > 0) {
                    ProgressInfoRow(
                        speed = if (progress.speedBytesPerSec > 0) "${progress.speedBytesPerSec.toFileSize()}/s" else "",
                        counter = "${progress.bytesTransferred.toFileSize()} / ${progress.totalBytes.toFileSize()}",
                        percent = "${(progress.fraction * 100).toInt()}%"
                    )
                }
            }
        }
    }
}

@Composable
fun QuickReceiveOverlay(
    progress: TransferProgressInfo,
    deviceName: String?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = null,
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.quick_receive_receiving),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (progress.currentFileName.isNotEmpty()) {
                        Text(
                            progress.currentFileName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (progress.totalBytes > 0) {
                LinearProgressIndicator(
                    progress = { progress.fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.tertiary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
                ProgressInfoRow(
                    speed = if (progress.speedBytesPerSec > 0) "${progress.speedBytesPerSec.toFileSize()}/s" else "",
                    counter = "${progress.bytesTransferred.toFileSize()} / ${progress.totalBytes.toFileSize()}",
                    percent = "${(progress.fraction * 100).toInt()}%"
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.tertiary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }
        }
    }
}
