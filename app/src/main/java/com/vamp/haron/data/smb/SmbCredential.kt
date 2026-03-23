package com.vamp.haron.data.smb

data class SmbCredential(
    val host: String,
    val username: String,
    val password: String,
    val domain: String = "",
    val displayName: String = ""
)
