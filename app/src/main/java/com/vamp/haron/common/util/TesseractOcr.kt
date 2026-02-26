package com.vamp.haron.common.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TesseractOcr @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TesseractOcr"
        private const val MAX_IMAGE_SIZE = 1200
        private val LANGUAGES = listOf("eng", "rus")
    }

    private val dataPath: String = File(context.filesDir, "tesseract").absolutePath
    private var initialized = false

    fun init() {
        if (initialized) return
        try {
            copyTrainedData()
            initialized = true
            Log.d(TAG, "Tessdata copied, ready")
        } catch (e: Exception) {
            Log.w(TAG, "Init failed", e)
        }
    }

    private fun copyTrainedData() {
        val tessDir = File(dataPath, "tessdata")
        if (!tessDir.exists()) tessDir.mkdirs()

        for (lang in LANGUAGES) {
            val fileName = "$lang.traineddata"
            val outFile = File(tessDir, fileName)
            if (outFile.exists() && outFile.length() > 0) continue

            try {
                context.assets.open("tessdata/$fileName").use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Copied $fileName (${outFile.length()} bytes)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to copy $fileName", e)
            }
        }
    }

    /**
     * Recognize text from an image file using Tesseract.
     * Creates a new TessBaseAPI instance per call (thread-safe).
     * Returns recognized text or empty string on failure.
     */
    fun recognizeText(file: File): String {
        if (!initialized) return ""
        val bitmap = decodeBitmap(file) ?: return ""
        return try {
            recognizeFromBitmap(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    fun recognizeFromBitmap(bitmap: Bitmap): String {
        if (!initialized) return ""
        val api = TessBaseAPI()
        return try {
            if (!api.init(dataPath, "eng+rus")) {
                Log.w(TAG, "TessBaseAPI.init failed")
                return ""
            }
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            api.setImage(bitmap)
            val text = api.utF8Text ?: ""
            text.trim()
        } catch (e: Exception) {
            Log.w(TAG, "Recognition failed: ${e.message}")
            ""
        } finally {
            api.recycle()
        }
    }

    private fun decodeBitmap(file: File): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            val width = options.outWidth
            val height = options.outHeight
            if (width <= 0 || height <= 0) return null
            var sampleSize = 1
            while (width / sampleSize > MAX_IMAGE_SIZE || height / sampleSize > MAX_IMAGE_SIZE) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            System.gc()
            null
        }
    }
}
