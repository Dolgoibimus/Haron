package com.vamp.haron.common.util

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** No-op stub for mini variant. OCR disabled. */
@Singleton
class TesseractOcr @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun init() {}
    fun recognizeText(file: java.io.File): String = ""
    fun recognizeFromBitmap(bitmap: Bitmap): String = ""
}
