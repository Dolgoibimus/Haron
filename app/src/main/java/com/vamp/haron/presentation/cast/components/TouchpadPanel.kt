package com.vamp.haron.presentation.cast.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vamp.haron.R
import com.vamp.haron.domain.model.RemoteInputEvent

private const val SENSITIVITY = 2f
private const val TAP_THRESHOLD_PX = 15f
private const val TAP_TIMEOUT_MS = 250L

@Composable
fun TouchpadPanel(
    deviceName: String,
    onRemoteInput: (RemoteInputEvent) -> Unit,
    onShowKeyboard: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Mouse, null,
                        Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.remote_touchpad),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row {
                    IconButton(onClick = onShowKeyboard, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Keyboard,
                            contentDescription = stringResource(R.string.remote_keyboard),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.close),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Touchpad area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            val startPos = down.position
                            val startTime = System.currentTimeMillis()
                            var lastPos = startPos
                            var totalDist = 0f
                            var pointerCount = 1

                            // Track gesture
                            while (true) {
                                val event = awaitPointerEvent()
                                val currentCount = event.changes.size
                                if (currentCount > pointerCount) pointerCount = currentCount

                                if (event.type == PointerEventType.Move) {
                                    if (currentCount >= 2) {
                                        // 2+ fingers = scroll
                                        val change = event.changes.first()
                                        val dx = change.position.x - change.previousPosition.x
                                        val dy = change.position.y - change.previousPosition.y
                                        if (dx != 0f || dy != 0f) {
                                            onRemoteInput(RemoteInputEvent.Scroll(dx * 0.5f, dy * 0.5f))
                                        }
                                        event.changes.forEach { it.consume() }
                                    } else {
                                        // 1 finger = mouse move
                                        val change = event.changes.first()
                                        val dx = change.position.x - lastPos.x
                                        val dy = change.position.y - lastPos.y
                                        lastPos = change.position
                                        totalDist += kotlin.math.abs(dx) + kotlin.math.abs(dy)
                                        if (dx != 0f || dy != 0f) {
                                            onRemoteInput(
                                                RemoteInputEvent.MouseMove(
                                                    dx * SENSITIVITY,
                                                    dy * SENSITIVITY
                                                )
                                            )
                                        }
                                        change.consume()
                                    }
                                }

                                // All pointers up
                                if (event.changes.all { !it.pressed }) {
                                    val elapsed = System.currentTimeMillis() - startTime
                                    if (elapsed < TAP_TIMEOUT_MS && totalDist < TAP_THRESHOLD_PX) {
                                        // Tap = click
                                        onRemoteInput(RemoteInputEvent.MouseClick(0))
                                    }
                                    break
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.remote_touchpad_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                deviceName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
