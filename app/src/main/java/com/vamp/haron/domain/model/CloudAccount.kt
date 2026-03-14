package com.vamp.haron.domain.model

data class CloudAccount(
    val accountId: String,      // "gdrive:alice@gmail.com"
    val provider: CloudProvider,
    val email: String,
    val displayName: String
)
