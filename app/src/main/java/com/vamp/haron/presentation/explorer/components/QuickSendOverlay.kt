package com.vamp.haron.presentation.explorer.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vamp.haron.R
import com.vamp.haron.common.util.toFileSize
import com.vamp.haron.data.network.NetworkDevice
import com.vamp.haron.domain.model.TransferProgressInfo
import com.vamp.haron.presentation.explorer.state.QuickSendState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun QuickSendOverlay(
    state: QuickSendState,
    modifier: Modifier = Modifier
) {
    when (state) {
        is QuickSendState.DraggingToDevice -> {
            DraggingToDeviceOverlay(
                fileName = state.fileName,
                anchorOffset = state.anchorOffset,
                dragOffset = state.dragOffset,
                devices = state.haronDevices,
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
    fileName: String,
    anchorOffset: Offset,
    dragOffset: Offset,
    devices: List<NetworkDevice>,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val circleRadiusDp = 30.dp
    val circleDiameterPx = with(density) { (circleRadiusDp * 2).toPx() }
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val circleRadiusPx = with(density) { circleRadiusDp.toPx() }

    // Arc radius: one circle diameter above the tap point
    val count = devices.size
    val arcAngle = PI * 2 / 3  // 120 degrees arc
    val minArcRadius = if (count > 1) {
        (count * circleDiameterPx * 1.3f / arcAngle).toFloat()
    } else {
        circleDiameterPx * 2f
    }
    val arcRadiusPx = minArcRadius.coerceIn(circleDiameterPx * 2f, screenWidthPx * 0.4f)

    // Check if there's enough space above the anchor for circles
    val spaceNeededAbove = arcRadiusPx + circleDiameterPx
    val placeAbove = anchorOffset.y > spaceNeededAbove

    // Shift anchor 50dp up (above) or down (below) for better visibility
    val extraOffsetPx = with(density) { 50.dp.toPx() }
    val shiftedAnchorY = if (placeAbove) anchorOffset.y - extraOffsetPx else anchorOffset.y + extraOffsetPx

    // Arc above the anchor (210° to 330°) or below if no space above (30° to 150°)
    val startAngle = if (placeAbove) (PI + PI / 6) else (PI / 6)
    val endAngle = if (placeAbove) (2 * PI - PI / 6) else (PI - PI / 6)
    val step = if (count > 1) (endAngle - startAngle) / (count - 1) else 0.0

    Box(modifier = modifier.fillMaxSize()) {
        // Device circles
        devices.forEachIndexed { index, device ->
            val angle = if (count > 1) startAngle + step * index else (startAngle + endAngle) / 2
            val cx = (anchorOffset.x + (arcRadiusPx * cos(angle)).toFloat())
                .coerceIn(circleRadiusPx, screenWidthPx - circleRadiusPx)
            val cy = (shiftedAnchorY + (arcRadiusPx * sin(angle)).toFloat())
                .coerceIn(circleRadiusPx, screenHeightPx - circleRadiusPx)

            // Highlight based on current finger position (dragOffset)
            val dx = dragOffset.x - cx
            val dy = dragOffset.y - cy
            val dist = sqrt(dx * dx + dy * dy)
            val isNear = dist < with(density) { 60.dp.toPx() }

            val offsetX = with(density) { cx.toDp() - circleRadiusDp }
            val offsetY = with(density) { cy.toDp() - circleRadiusDp }

            Surface(
                modifier = Modifier
                    .offset(x = offsetX, y = offsetY)
                    .size(circleRadiusDp * 2),
                shape = CircleShape,
                color = if (isNear) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 8.dp,
                tonalElevation = if (isNear) 4.dp else 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.PhoneAndroid, null,
                        Modifier.size(20.dp),
                        tint = if (isNear) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        device.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        color = if (isNear) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // File preview follows finger (dragOffset)
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
                    Spacer(Modifier.height(4.dp))
                    Row {
                        Text(
                            "${progress.bytesTransferred.toFileSize()} / ${progress.totalBytes.toFileSize()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        if (progress.speedBytesPerSec > 0) {
                            Text(
                                stringResource(R.string.transfer_speed, progress.speedBytesPerSec.toFileSize()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
                Spacer(Modifier.height(4.dp))
                Row {
                    Text(
                        "${progress.bytesTransferred.toFileSize()} / ${progress.totalBytes.toFileSize()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    if (progress.speedBytesPerSec > 0) {
                        Text(
                            stringResource(R.string.transfer_speed, progress.speedBytesPerSec.toFileSize()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
