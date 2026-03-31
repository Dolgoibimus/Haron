package com.vamp.haron.common.util

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** No-op stub for mini variant. Image labeling disabled. */
@Singleton
class ImageLabeler @Inject constructor(
    private val tesseractOcr: TesseractOcr
) {
    companion object {
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "bmp", "webp")
    }

    fun isImageFile(file: File): Boolean = file.extension.lowercase() in IMAGE_EXTENSIONS

    suspend fun labelImage(file: File): String = ""

    fun recognizeText(file: File): String = ""
}
