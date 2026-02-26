package com.vamp.haron.common.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Wrapper over ML Kit Image Labeling.
 * Downscales images to max 640px to save RAM, runs label detection,
 * returns combined EN+RU tags string.
 */
@Singleton
class ImageLabeler @Inject constructor() {

    companion object {
        private const val MAX_IMAGE_SIZE = 640
        private const val MAX_OCR_IMAGE_SIZE = 1024 // Larger for better OCR readability
        private const val MIN_CONFIDENCE = 0.7f
        private const val OCR_TIMEOUT_MS = 10_000L

        private val IMAGE_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "bmp", "webp"
        )
    }

    fun isImageFile(file: File): Boolean {
        return file.extension.lowercase() in IMAGE_EXTENSIONS
    }

    /**
     * Labels an image file and returns EN+RU tags string.
     * Returns empty string on failure.
     */
    suspend fun labelImage(file: File): String {
        val bitmap = decodeBitmap(file) ?: return ""
        return try {
            val labels = detectLabels(bitmap)
            if (labels.isEmpty()) return ""
            ImageLabelDictionary.buildSearchableText(labels)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * OCR: recognizes text on an image (signs, screenshots, documents).
     * Returns recognized text or empty string on failure.
     * Timeout: 10 seconds per file.
     */
    suspend fun recognizeText(file: File): String {
        val bitmap = decodeBitmapForOcr(file) ?: return ""
        return try {
            withTimeoutOrNull(OCR_TIMEOUT_MS) {
                detectText(bitmap)
            } ?: ""
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun detectText(bitmap: Bitmap): String {
        return suspendCancellableCoroutine { cont ->
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            recognizer.process(inputImage)
                .addOnSuccessListener { result ->
                    val text = result.textBlocks.joinToString("\n") { it.text }
                    cont.resume(text)
                }
                .addOnFailureListener {
                    cont.resume("")
                }

            cont.invokeOnCancellation {
                recognizer.close()
            }
        }
    }

    private fun decodeBitmapForOcr(file: File): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            val width = options.outWidth
            val height = options.outHeight
            if (width <= 0 || height <= 0) return null
            var sampleSize = 1
            while (width / sampleSize > MAX_OCR_IMAGE_SIZE || height / sampleSize > MAX_OCR_IMAGE_SIZE) {
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

    private fun decodeBitmap(file: File): Bitmap? {
        return try {
            // First pass — get dimensions only
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)

            val width = options.outWidth
            val height = options.outHeight
            if (width <= 0 || height <= 0) return null

            // Calculate inSampleSize to downscale
            var sampleSize = 1
            while (width / sampleSize > MAX_IMAGE_SIZE || height / sampleSize > MAX_IMAGE_SIZE) {
                sampleSize *= 2
            }

            // Second pass — decode with downscale
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            System.gc()
            null
        }
    }

    private suspend fun detectLabels(bitmap: Bitmap): List<String> {
        return suspendCancellableCoroutine { cont ->
            val options = ImageLabelerOptions.Builder()
                .setConfidenceThreshold(MIN_CONFIDENCE)
                .build()
            val labeler = ImageLabeling.getClient(options)
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            labeler.process(inputImage)
                .addOnSuccessListener { labels ->
                    val result = labels.map { it.text }
                    cont.resume(result)
                }
                .addOnFailureListener {
                    cont.resume(emptyList())
                }

            cont.invokeOnCancellation {
                labeler.close()
            }
        }
    }
}
