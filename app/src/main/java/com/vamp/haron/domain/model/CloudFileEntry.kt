package com.vamp.haron.domain.model

data class CloudFileEntry(
    val id: String,
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val mimeType: String? = null,
    val provider: CloudProvider,
    val childCount: Int = -1,
    val thumbnailUrl: String? = null
) {
    /** Convert to FileEntry for display in explorer panels */
    fun toFileEntry(): FileEntry = FileEntry(
        name = name,
        path = "cloud://${provider.scheme}/$path",
        isDirectory = isDirectory,
        size = size,
        lastModified = lastModified,
        isHidden = false,
        extension = if (!isDirectory) name.substringAfterLast('.', "") else "",
        childCount = if (childCount >= 0) childCount else 0,
        thumbnailUrl = thumbnailUrl
    )
}
