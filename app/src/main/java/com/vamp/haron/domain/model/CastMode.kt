package com.vamp.haron.domain.model

enum class CastMode {
    SINGLE_MEDIA,
    SLIDESHOW,
    PDF_PRESENTATION,
    SCREEN_MIRROR
}

data class SlideshowConfig(
    val intervalSec: Int = 5,
    val loop: Boolean = true,
    val shuffle: Boolean = false
)

data class PresentationState(
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val pdfPath: String = ""
)

data class CastImageInfo(
    val currentIndex: Int,
    val totalCount: Int,
    val fileName: String
)
