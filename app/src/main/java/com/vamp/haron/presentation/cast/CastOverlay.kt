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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamp.haron.domain.model.CastMode
import com.vamp.haron.presentation.cast.components.BrowserCastPanel
import com.vamp.haron.presentation.cast.components.MediaRemotePanel
import com.vamp.haron.presentation.cast.components.PdfPresentationController

@Composable
fun CastOverlay(
    modifier: Modifier = Modifier,
    castViewModel: CastViewModel = hiltViewModel(
        viewModelStoreOwner = LocalContext.current as androidx.activity.ComponentActivity
    )
) {
    val isConnected by castViewModel.isConnected.collectAsState()
    val deviceName by castViewModel.connectedDeviceName.collectAsState()
    val isPlaying by castViewModel.mediaIsPlaying.collectAsState()
    val positionMs by castViewModel.mediaPositionMs.collectAsState()
    val durationMs by castViewModel.mediaDurationMs.collectAsState()
    val castMode by castViewModel.castMode.collectAsState()
    val presentationState by castViewModel.presentationState.collectAsState()
    val transcodeProgress by castViewModel.transcodeProgress.collectAsState()
    val browserUrl by castViewModel.browserUrl.collectAsState()
    // Compute transcode percent directly from transcodeProgress (no async derived state)
    val transcodePercent = transcodeProgress?.let {
        if (!it.isComplete) it.percent else null
    }

    val showOverlay = isConnected || transcodePercent != null || browserUrl != null

    AnimatedVisibility(
        visible = showOverlay,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(bottom = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(modifier = Modifier.alpha(0.8f)) {
                when {
                    browserUrl != null -> {
                        BrowserCastPanel(
                            url = browserUrl!!,
                            castMode = castMode,
                            onClose = { castViewModel.closeBrowserCast() }
                        )
                    }
                    isConnected && castMode == CastMode.PDF_PRESENTATION -> {
                        PdfPresentationController(
                            state = presentationState,
                            deviceName = deviceName ?: "",
                            onPrevPage = { castViewModel.presentationPrevPage() },
                            onNextPage = { castViewModel.presentationNextPage() },
                            onDisconnect = { castViewModel.disconnect() }
                        )
                    }
                    isConnected && (castMode == CastMode.SCREEN_MIRROR || castMode == CastMode.SLIDESHOW || castMode == CastMode.FILE_INFO) -> {
                        MediaRemotePanel(
                            deviceName = deviceName ?: "",
                            isPlaying = false,
                            currentPositionMs = 0,
                            durationMs = 0,
                            onRemoteInput = { event -> castViewModel.sendRemoteInput(event) },
                            onDisconnect = { castViewModel.disconnect() },
                            transcodePercent = transcodePercent,
                            onCancelTranscode = { castViewModel.cancelTranscode() }
                        )
                    }
                    isConnected -> {
                        MediaRemotePanel(
                            deviceName = deviceName ?: "",
                            isPlaying = isPlaying,
                            currentPositionMs = positionMs,
                            durationMs = durationMs,
                            onRemoteInput = { event -> castViewModel.sendRemoteInput(event) },
                            onDisconnect = { castViewModel.disconnect() },
                            transcodePercent = transcodePercent,
                            onCancelTranscode = { castViewModel.cancelTranscode() }
                        )
                    }
                    transcodePercent != null -> {
                        MediaRemotePanel(
                            deviceName = deviceName ?: "",
                            isPlaying = false,
                            currentPositionMs = 0,
                            durationMs = 0,
                            onRemoteInput = {},
                            onDisconnect = {},
                            transcodePercent = transcodePercent,
                            onCancelTranscode = { castViewModel.cancelTranscode() }
                        )
                    }
                }
            }
        }
    }
}
