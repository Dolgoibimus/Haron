package com.vamp.haron.domain.model

sealed interface RemoteInputEvent {
    data object PlayPause : RemoteInputEvent
    data class SeekTo(val positionMs: Long) : RemoteInputEvent
    data class VolumeChange(val delta: Float) : RemoteInputEvent
    data object Next : RemoteInputEvent
    data object Prev : RemoteInputEvent
}
