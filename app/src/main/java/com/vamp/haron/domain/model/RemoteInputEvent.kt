package com.vamp.haron.domain.model

sealed interface RemoteInputEvent {
    data object PlayPause : RemoteInputEvent
    data class SeekTo(val positionMs: Long) : RemoteInputEvent
    data class VolumeChange(val delta: Float) : RemoteInputEvent
    data object Next : RemoteInputEvent
    data object Prev : RemoteInputEvent

    // TV Remote (touchpad → WebSocket → browser JS)
    data class MouseMove(val dx: Float, val dy: Float) : RemoteInputEvent
    data class MouseClick(val button: Int = 0) : RemoteInputEvent
    data class Scroll(val dx: Float, val dy: Float) : RemoteInputEvent
    data class KeyPress(val keyCode: Int, val char: Char? = null) : RemoteInputEvent
    data class TextInput(val text: String) : RemoteInputEvent
}

sealed interface HidConnectionState {
    data object Disconnected : HidConnectionState
    data object Connecting : HidConnectionState
    data class Connected(val deviceName: String) : HidConnectionState
    data object NotSupported : HidConnectionState
    data class Error(val message: String) : HidConnectionState
}
