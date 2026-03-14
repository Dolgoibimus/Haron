package com.vamp.haron.presentation.cast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamp.haron.R
import com.vamp.haron.domain.model.CastMode
import com.vamp.haron.presentation.cast.components.BrowserCastPanel
import com.vamp.haron.presentation.cast.components.BtDevicePickerDialog
import com.vamp.haron.presentation.cast.components.MediaRemotePanel
import com.vamp.haron.presentation.cast.components.PdfPresentationController
import com.vamp.haron.presentation.cast.components.TouchpadPanel
import com.vamp.haron.presentation.cast.components.VirtualKeyboardPanel

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
    val showTouchpad by castViewModel.showTouchpad.collectAsState()
    val showKeyboard by castViewModel.showKeyboard.collectAsState()
    val castImageInfo by castViewModel.castImageInfo.collectAsState()
    val showBtDevicePicker by castViewModel.showBtDevicePicker.collectAsState()
    val hidConnectionState by castViewModel.hidConnectionState.collectAsState()
    val isBluetoothHidMode by castViewModel.isBluetoothHidMode.collectAsState()
    val btPairedDevices by castViewModel.btPairedDevices.collectAsState()
    val btHidWaitingForTv by castViewModel.btHidWaitingForTv.collectAsState()
    // Compute transcode percent directly from transcodeProgress (no async derived state)
    val transcodePercent = transcodeProgress?.let {
        if (!it.isComplete) it.percent else null
    }

    val isBrowserMode = castMode == CastMode.SCREEN_MIRROR ||
            castMode == CastMode.SLIDESHOW

    val showOverlay = isConnected || transcodePercent != null || browserUrl != null || isBluetoothHidMode

    AnimatedVisibility(
        visible = showOverlay,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .imePadding()
                .padding(bottom = 2.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Touchpad / Keyboard panels
                AnimatedVisibility(visible = showTouchpad) {
                    Box(modifier = Modifier.alpha(0.9f)) {
                        TouchpadPanel(
                            deviceName = deviceName ?: "",
                            onRemoteInput = { castViewModel.sendRemoteInput(it) },
                            onShowKeyboard = { castViewModel.showKeyboardPanel() },
                            onClose = { castViewModel.hideRemoteInput() }
                        )
                    }
                }
                AnimatedVisibility(visible = showKeyboard) {
                    Box(modifier = Modifier.alpha(0.9f)) {
                        VirtualKeyboardPanel(
                            deviceName = deviceName ?: "",
                            onRemoteInput = { castViewModel.sendRemoteInput(it) },
                            onShowTouchpad = { castViewModel.showTouchpadPanel() },
                            onClose = { castViewModel.hideRemoteInput() }
                        )
                    }
                }

                // Main panel
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
                        isConnected && isBrowserMode -> {
                            MediaRemotePanel(
                                deviceName = deviceName ?: "",
                                isPlaying = false,
                                currentPositionMs = 0,
                                durationMs = 0,
                                onRemoteInput = { event -> castViewModel.sendRemoteInput(event) },
                                onDisconnect = { castViewModel.disconnect() },
                                transcodePercent = transcodePercent,
                                onCancelTranscode = { castViewModel.cancelTranscode() },
                                showTouchpadButton = true,
                                onToggleTouchpad = { castViewModel.toggleTouchpad() }
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
                                onCancelTranscode = { castViewModel.cancelTranscode() },
                                imageInfo = castImageInfo
                            )
                        }
                        isBluetoothHidMode && !isConnected -> {
                            // BT HID mode without Cast — show minimal disconnect panel
                            val hidState = hidConnectionState
                            val btDeviceName = if (hidState is com.vamp.haron.domain.model.HidConnectionState.Connected) hidState.deviceName else "BT"
                            MediaRemotePanel(
                                deviceName = btDeviceName,
                                isPlaying = false,
                                currentPositionMs = 0,
                                durationMs = 0,
                                onRemoteInput = { event -> castViewModel.sendRemoteInput(event) },
                                onDisconnect = { castViewModel.disconnectBtHid() },
                                showTouchpadButton = true,
                                onToggleTouchpad = { castViewModel.toggleTouchpad() }
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

    // BT HID Setup Dialog
    if (showBtDevicePicker) {
        BtDevicePickerDialog(
            connectionState = hidConnectionState,
            pairedDevices = btPairedDevices,
            isDiscoverable = btHidWaitingForTv,
            onConnectDevice = { castViewModel.connectBtHidToDevice(it) },
            onMakeDiscoverable = { castViewModel.requestBtDiscoverable() },
            onDismiss = { castViewModel.dismissBtDevicePicker() }
        )
    }
}
