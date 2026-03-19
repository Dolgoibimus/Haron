package com.vamp.haron.domain.model

/**
 * In-process static holder for passing gallery data
 * from ExplorerViewModel to GalleryScreen.
 */
object GalleryHolder {
    data class GalleryItem(
        val filePath: String,
        val fileName: String,
        val fileSize: Long = 0L,
        /** Direct image URL for cloud files — load via network instead of local file */
        val imageUrl: String? = null
    )

    var items: List<GalleryItem> = emptyList()
    var startIndex: Int = 0
}
