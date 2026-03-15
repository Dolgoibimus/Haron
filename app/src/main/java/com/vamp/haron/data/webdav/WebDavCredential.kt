package com.vamp.haron.data.webdav

data class WebDavCredential(
    val url: String,
    val username: String,
    val password: String,
    val displayName: String = url
)
