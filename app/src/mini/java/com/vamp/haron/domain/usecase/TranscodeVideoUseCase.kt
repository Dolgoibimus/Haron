package com.vamp.haron.domain.usecase

import android.content.Context
import com.vamp.haron.data.datastore.HaronPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

data class TranscodeProgress(
    val percent: Int = 0,
    val outputPath: String? = null,
    val error: String? = null,
    val isComplete: Boolean = false,
    val readyToStream: Boolean = false,
    val hlsDir: String? = null
)

/** No-op stub for mini variant. FFmpeg transcoding disabled. */
class TranscodeVideoUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: HaronPreferences
) {
    fun needsTranscode(filePath: String): Boolean = false

    operator fun invoke(inputPath: String): Flow<TranscodeProgress> =
        flowOf(TranscodeProgress(error = "Transcoding not available in mini build", isComplete = true))

    fun cancelTranscode() {}

    fun cleanupTempFiles() {}
}
