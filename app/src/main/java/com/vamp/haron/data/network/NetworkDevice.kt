package com.vamp.haron.data.network

enum class NetworkDeviceType {
    HARON,  // Another Haron instance
    SMB     // SMB/CIFS file share
}

data class NetworkDevice(
    val id: String,
    val name: String,
    val address: String,
    val port: Int,
    val type: NetworkDeviceType,
    val alias: String? = null,
    val isTrusted: Boolean = false
) {
    val displayName: String get() = alias ?: name
}
