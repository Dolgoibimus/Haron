package com.vamp.haron.domain.model

enum class StegoPhase {
    IDLE, COPYING_CARRIER, ENCRYPTING, APPENDING, DETECTING, EXTRACTING, DONE, ERROR
}

data class StegoProgress(
    val phase: StegoPhase = StegoPhase.IDLE,
    val progress: Float = 0f,
    val message: String = ""
)

data class StegoDetectResult(
    val hasHiddenData: Boolean,
    val payloadName: String? = null,
    val payloadSize: Long = 0
)

sealed interface StegoResult {
    data class Hidden(val outputPath: String, val payloadSize: Long) : StegoResult
    data class Extracted(val outputPath: String, val payloadName: String) : StegoResult
    data class Error(val message: String) : StegoResult
}

object StegoHolder {
    var carrierPath: String = ""
    var payloadPath: String = ""
}
