package com.vamp.haron.domain.model

data class CastDevice(
    val id: String,
    val name: String,
    val type: CastType
)

enum class CastType {
    CHROMECAST,
    MIRACAST,
    DLNA
}
