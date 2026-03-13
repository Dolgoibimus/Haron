package com.vamp.haron.domain.model

enum class CloudProvider(val scheme: String, val displayName: String) {
    GOOGLE_DRIVE("gdrive", "Google Drive"),
    DROPBOX("dropbox", "Dropbox"),
    ONEDRIVE("onedrive", "OneDrive"),
    YANDEX_DISK("yandex", "Yandex Disk")
}
