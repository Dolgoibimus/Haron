package com.vamp.haron.presentation.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamp.haron.R
import com.vamp.haron.data.voice.VoiceState
import kotlin.math.roundToInt

@Composable
fun VoiceFab(
    viewModel: VoiceFabViewModel = hiltViewModel()
) {
    val voiceState by viewModel.voiceState.collectAsState()
    val context = LocalContext.current

    if (!viewModel.isAvailable) return

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

    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var boxW by remember { mutableIntStateOf(0) }
    var boxH by remember { mutableIntStateOf(0) }
    var fabW by remember { mutableIntStateOf(0) }
    var fabH by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { boxW = it.size.width; boxH = it.size.height }
    ) {
        SmallFloatingActionButton(
            onClick = {
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
            },
            containerColor = micColor,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 56.dp)
                .onGloballyPositioned { fabW = it.size.width; fabH = it.size.height }
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val newX = (offsetX + dragAmount.x)
                            .coerceIn(-(boxW - fabW).toFloat(), fabW.toFloat())
                        val newY = (offsetY + dragAmount.y)
                            .coerceIn(-fabH.toFloat(), (boxH - fabH).toFloat())
                        offsetX = newX
                        offsetY = newY
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
