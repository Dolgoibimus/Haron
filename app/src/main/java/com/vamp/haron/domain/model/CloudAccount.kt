package com.vamp.haron.domain.model

data class CloudAccount(
    val provider: CloudProvider,
    val email: String,
    val displayName: String
)
