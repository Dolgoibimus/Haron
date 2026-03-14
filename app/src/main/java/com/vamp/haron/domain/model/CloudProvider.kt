package com.vamp.haron.domain.model

enum class CloudProvider(val scheme: String, val displayName: String) {
    GOOGLE_DRIVE("gdrive", "Google Drive"),
    DROPBOX("dropbox", "Dropbox"),
    YANDEX_DISK("yandex", "Yandex Disk")
}
