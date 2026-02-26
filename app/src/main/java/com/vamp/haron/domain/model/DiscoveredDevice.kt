package com.vamp.haron.domain.model

data class DiscoveredDevice(
    val id: String,
    val name: String,
    val address: String,
    val supportedProtocols: Set<TransferProtocol>,
    val isHaron: Boolean = false,
    val port: Int = 0,
    val alias: String? = null,
    val isTrusted: Boolean = false
) {
    val displayName: String get() {
        if (alias != null) return alias
        if (isHaron && name.startsWith("Haron-")) {
            // "Haron-MODEL-abcd1234" → "MODEL"
            return name.removePrefix("Haron-").replace(Regex("-[a-f0-9]{8}$"), "")
        }
        return name
    }
}
