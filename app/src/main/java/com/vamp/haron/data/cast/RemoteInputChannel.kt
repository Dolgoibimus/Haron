package com.vamp.haron.data.cast

import com.vamp.haron.domain.model.RemoteInputEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Channel for TV remote input events.
 * TouchpadPanel/VirtualKeyboard → JSON → WebSocket → browser JS on TV.
 */
@Singleton
class RemoteInputChannel @Inject constructor() {

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 128)
    val events: SharedFlow<String> = _events.asSharedFlow()

    fun send(event: RemoteInputEvent) {
        val json = when (event) {
            is RemoteInputEvent.MouseMove ->
                """{"type":"move","dx":${event.dx},"dy":${event.dy}}"""
            is RemoteInputEvent.MouseClick ->
                """{"type":"click","button":${event.button}}"""
            is RemoteInputEvent.Scroll ->
                """{"type":"scroll","dx":${event.dx},"dy":${event.dy}}"""
            is RemoteInputEvent.KeyPress ->
                """{"type":"key","keyCode":${event.keyCode},"char":"${event.char ?: ""}"}"""
            is RemoteInputEvent.TextInput ->
                """{"type":"text","text":"${escapeJson(event.text)}"}"""
            else -> return // media events not sent via WebSocket
        }
        _events.tryEmit(json)
    }

    private fun escapeJson(text: String): String {
        return text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
