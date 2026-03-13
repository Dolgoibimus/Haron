package com.vamp.haron.presentation.cast.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vamp.haron.R
import com.vamp.haron.domain.model.CastImageInfo
import com.vamp.haron.domain.model.RemoteInputEvent

@Composable
fun MediaRemotePanel(
    deviceName: String,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    onRemoteInput: (RemoteInputEvent) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
    transcodePercent: Int? = null,
    onCancelTranscode: (() -> Unit)? = null,
    showTouchpadButton: Boolean = false,
    onToggleTouchpad: (() -> Unit)? = null,
    imageInfo: CastImageInfo? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Transcode progress — above "now playing" text
            if (transcodePercent != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (transcodePercent > 0) {
                            stringResource(R.string.cast_transcoding_progress, transcodePercent)
                        } else {
                            stringResource(R.string.cast_transcoding_preparing)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (onCancelTranscode != null) {
                        IconButton(
                            onClick = onCancelTranscode,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.cancel),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (transcodePercent > 0) {
                    LinearProgressIndicator(
                        progress = { transcodePercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showTouchpadButton && onToggleTouchpad != null) {
                    IconButton(onClick = onToggleTouchpad, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Mouse,
                            contentDescription = stringResource(R.string.remote_touchpad),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Spacer(Modifier.width(32.dp))
                }
                Text(
                    stringResource(R.string.cast_now_playing, deviceName),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onDisconnect, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cast_disconnect),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (imageInfo != null) {
                // Image mode: show file name + counter + prev/next only
                Text(
                    imageInfo.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.cast_image_counter, imageInfo.currentIndex + 1, imageInfo.totalCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onRemoteInput(RemoteInputEvent.Prev) },
                        enabled = imageInfo.currentIndex > 0
                    ) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.previous), modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.width(32.dp))
                    IconButton(
                        onClick = { onRemoteInput(RemoteInputEvent.Next) },
                        enabled = imageInfo.currentIndex < imageInfo.totalCount - 1
                    ) {
                        Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.next), modifier = Modifier.size(32.dp))
                    }
                }
            } else {
                // Seek bar
                if (durationMs > 0) {
                    var sliderPosition by remember(currentPositionMs) {
                        mutableFloatStateOf(currentPositionMs.toFloat() / durationMs)
                    }
                    Slider(
                        value = sliderPosition,
                        onValueChange = { sliderPosition = it },
                        onValueChangeFinished = {
                            onRemoteInput(RemoteInputEvent.SeekTo((sliderPosition * durationMs).toLong()))
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            formatTime(currentPositionMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            formatTime(durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Playback controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onRemoteInput(RemoteInputEvent.VolumeChange(-0.01f)) }) {
                        Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = null, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { onRemoteInput(RemoteInputEvent.Prev) }) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.previous), modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { onRemoteInput(RemoteInputEvent.PlayPause) },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.previous),
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { onRemoteInput(RemoteInputEvent.Next) }) {
                        Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.next), modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { onRemoteInput(RemoteInputEvent.VolumeChange(0.01f)) }) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
