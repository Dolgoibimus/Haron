package com.vamp.haron.presentation.cast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamp.haron.domain.model.CastMode
import com.vamp.haron.presentation.cast.components.MediaRemotePanel
import com.vamp.haron.presentation.cast.components.PdfPresentationController

@Composable
fun CastOverlay(
    modifier: Modifier = Modifier,
    castViewModel: CastViewModel = hiltViewModel()
) {
    val isConnected by castViewModel.isConnected.collectAsState()
    val deviceName by castViewModel.connectedDeviceName.collectAsState()
    val isPlaying by castViewModel.mediaIsPlaying.collectAsState()
    val positionMs by castViewModel.mediaPositionMs.collectAsState()
    val durationMs by castViewModel.mediaDurationMs.collectAsState()
    val castMode by castViewModel.castMode.collectAsState()
    val presentationState by castViewModel.presentationState.collectAsState()

    AnimatedVisibility(
        visible = isConnected,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(bottom = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            when (castMode) {
                CastMode.PDF_PRESENTATION -> {
                    PdfPresentationController(
                        state = presentationState,
                        deviceName = deviceName ?: "",
                        onPrevPage = { castViewModel.presentationPrevPage() },
                        onNextPage = { castViewModel.presentationNextPage() }
                    )
                }
                CastMode.SCREEN_MIRROR, CastMode.SLIDESHOW, CastMode.FILE_INFO -> {
                    // Minimal overlay — just device name, no media controls
                    MediaRemotePanel(
                        deviceName = deviceName ?: "",
                        isPlaying = false,
                        currentPositionMs = 0,
                        durationMs = 0,
                        onRemoteInput = { event -> castViewModel.sendRemoteInput(event) },
                        onDisconnect = { castViewModel.disconnect() }
                    )
                }
                CastMode.SINGLE_MEDIA -> {
                    MediaRemotePanel(
                        deviceName = deviceName ?: "",
                        isPlaying = isPlaying,
                        currentPositionMs = positionMs,
                        durationMs = durationMs,
                        onRemoteInput = { event -> castViewModel.sendRemoteInput(event) },
                        onDisconnect = { castViewModel.disconnect() }
                    )
                }
            }
        }
    }
}
