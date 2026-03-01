package com.vamp.haron.presentation.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamp.haron.R
import com.vamp.haron.data.voice.VoiceState
import com.vamp.haron.domain.model.TransferHolder
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun VoiceFab(
    viewModel: VoiceFabViewModel = hiltViewModel()
) {
    val voiceState by viewModel.voiceState.collectAsState()
    val context = LocalContext.current
    val fabVisible by TransferHolder.voiceFabVisible.collectAsState()

    if (!viewModel.isAvailable) return

    // Stop listening when mic becomes hidden (e.g. exiting reader while mic active)
    LaunchedEffect(fabVisible) {
        if (!fabVisible && voiceState == VoiceState.LISTENING) {
            viewModel.stopListening()
        }
    }

    if (!fabVisible) return

    val audioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startListening()
    }

    val isListening = voiceState == VoiceState.LISTENING
    val micColor = if (isListening) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainerHigh
    val micIconTint = if (isListening) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant

    var offsetX by remember { mutableFloatStateOf(viewModel.savedOffsetX) }
    var offsetY by remember { mutableFloatStateOf(viewModel.savedOffsetY) }
    var boxW by remember { mutableIntStateOf(0) }
    var boxH by remember { mutableIntStateOf(0) }
    var fabW by remember { mutableIntStateOf(0) }
    var fabH by remember { mutableIntStateOf(0) }

    // One-time hint
    var showHint by remember { mutableStateOf(viewModel.shouldShowHint) }

    LaunchedEffect(showHint) {
        if (showHint) {
            delay(5000)
            showHint = false
            viewModel.markHintShown()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { boxW = it.size.width; boxH = it.size.height }
    ) {
        // Hint tooltip
        AnimatedVisibility(
            visible = showHint,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
        ) {
            Text(
                text = stringResource(R.string.mic_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        SmallFloatingActionButton(
            onClick = { /* handled in pointerInput */ },
            containerColor = micColor,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 56.dp)
                .onGloballyPositioned { fabW = it.size.width; fabH = it.size.height }
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .pointerInput(Unit) {
                    val touchSlop = 10f
                    val longPressMs = 400L
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        var dragged = false
                        val longPressed = withTimeoutOrNull(longPressMs) {
                            // Wait for drag or up during long-press window
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) {
                                    // Finger lifted before long press → tap
                                    change.consume()
                                    return@withTimeoutOrNull false
                                }
                                val moved = change.positionChange()
                                if (abs(moved.x) > touchSlop || abs(moved.y) > touchSlop) {
                                    // Started dragging
                                    dragged = true
                                    val newX = (offsetX + moved.x)
                                        .coerceIn(-(boxW - fabW).toFloat(), fabW.toFloat())
                                    val newY = (offsetY + moved.y)
                                        .coerceIn(-fabH.toFloat(), (boxH - fabH).toFloat())
                                    offsetX = newX
                                    offsetY = newY
                                    change.consume()
                                    return@withTimeoutOrNull false
                                }
                            }
                            false
                        }

                        if (longPressed == null && !dragged) {
                            // Long press timeout reached without drag or up
                            if (showHint) {
                                showHint = false
                                viewModel.markHintShown()
                            }
                            TransferHolder.pendingOpenVoiceList.value = true
                            // Wait for up
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    change.consume()
                                    if (!change.pressed) break
                                }
                            } catch (_: Exception) { }
                        } else if (!dragged && longPressed == false) {
                            // Tap
                            TransferHolder.voiceFabPinned.value = true
                            if (showHint) {
                                showHint = false
                                viewModel.markHintShown()
                            }
                            if (isListening) {
                                viewModel.stopListening()
                            } else {
                                val hasPerm = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                                    PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    viewModel.startListening()
                                } else {
                                    audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        } else if (dragged) {
                            // Continue drag
                            try {
                                drag(down.id) { change ->
                                    val moved = change.positionChange()
                                    val newX = (offsetX + moved.x)
                                        .coerceIn(-(boxW - fabW).toFloat(), fabW.toFloat())
                                    val newY = (offsetY + moved.y)
                                        .coerceIn(-fabH.toFloat(), (boxH - fabH).toFloat())
                                    offsetX = newX
                                    offsetY = newY
                                    change.consume()
                                }
                            } catch (_: Exception) { }
                            viewModel.saveOffset(offsetX, offsetY)
                        }
                    }
                }
        ) {
            Icon(
                imageVector = if (isListening) Icons.Filled.MicOff else Icons.Filled.Mic,
                contentDescription = stringResource(R.string.voice_command),
                tint = micIconTint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
