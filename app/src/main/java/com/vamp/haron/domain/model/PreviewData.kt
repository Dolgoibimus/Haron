package com.vamp.haron.domain.model

import android.graphics.Bitmap

sealed interface PreviewData {
    val fileName: String
    val fileSize: Long
    val lastModified: Long

    data class ImagePreview(
        override val fileName: String,
        override val fileSize: Long,
        override val lastModified: Long,
        val bitmap: Bitmap,
        val width: Int,
        val height: Int
    ) : PreviewData

    data class VideoPreview(
        override val fileName: String,
        override val fileSize: Long,
        override val lastModified: Long,
        val thumbnail: Bitmap?,
        val durationMs: Long
    ) : PreviewData

    data class AudioPreview(
        override val fileName: String,
        override val fileSize: Long,
        override val lastModified: Long,
        val albumArt: Bitmap?,
        val title: String?,
        val artist: String?,
        val album: String?,
        val durationMs: Long
    ) : PreviewData

    data class TextPreview(
        override val fileName: String,
        override val fileSize: Long,
        override val lastModified: Long,
        val content: String,
        val totalLines: Int,
        val extension: String
    ) : PreviewData

    data class PdfPreview(
        override val fileName: String,
        override val fileSize: Long,
        override val lastModified: Long,
        val firstPage: Bitmap,
        val pageCount: Int,
        val filePath: String = ""
    ) : PreviewData

    data class ArchivePreview(
        override val fileName: String,
        override val fileSize: Long,
        override val lastModified: Long,
        val entries: List<ArchiveEntryInfo>,
        val totalEntries: Int,
        val totalUncompressedSize: Long
    ) : PreviewData

    data class ApkPreview(
        override val fileName: String,
        override val fileSize: Long,
        override val lastModified: Long,
        val appName: String?,
        val packageName: String?,
        val versionName: String?,
        val versionCode: Long,
        val icon: Bitmap?
    ) : PreviewData

    data class Fb2Preview(
        override val fileName: String,
        override val fileSize: Long,
        override val lastModified: Long,
        val coverBitmap: Bitmap?,
        val annotation: String
    ) : PreviewData

    data class UnsupportedPreview(
        override val fileName: String,
        override val fileSize: Long,
        override val lastModified: Long,
        val mimeType: String?
    ) : PreviewData
}

data class ArchiveEntryInfo(
    val name: String,
    val size: Long,
    val isDirectory: Boolean
)
