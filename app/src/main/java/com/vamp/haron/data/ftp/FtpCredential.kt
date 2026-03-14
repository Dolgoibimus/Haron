package com.vamp.haron.data.ftp

data class FtpCredential(
    val host: String,
    val port: Int = 21,
    val username: String,
    val password: String,
    val useFtps: Boolean = false,
    val displayName: String = host
)
