package com.vamp.haron.domain.usecase

import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.audio.AudioTagData
import com.vamp.haron.common.audio.FlacTagEditor
import com.vamp.haron.common.audio.Id3TagEditor
import com.vamp.haron.common.audio.M4aTagEditor
import com.vamp.haron.common.audio.OggTagEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class SaveAudioTagsUseCase @Inject constructor() {

    suspend operator fun invoke(path: String, tags: AudioTags): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            val ext = file.extension.lowercase()
            EcosystemLogger.d(TAG, "Writing tags to: $path (format=$ext)")

            val tagData = AudioTagData(
                title = tags.title,
                artist = tags.artist,
                album = tags.album,
                year = tags.year,
                genre = tags.genre
            )

            val success = when (ext) {
                "mp3" -> Id3TagEditor.writeTags(file, tagData)
                "flac" -> FlacTagEditor.writeTags(file, tagData)
                "ogg", "oga", "opus" -> OggTagEditor.writeTags(file, tagData)
                "m4a", "m4b", "mp4", "aac", "alac" -> M4aTagEditor.writeTags(file, tagData)
                else -> {
                    EcosystemLogger.e(TAG, "Tag writing not supported for: $ext")
                    false
                }
            }

            if (success) {
                EcosystemLogger.i(TAG, "Tags saved to: $path (title=${tags.title}, artist=${tags.artist}, album=${tags.album})")
            }
            success
        } catch (e: Exception) {
            EcosystemLogger.e(TAG, "Failed to save tags: ${e.message}")
            false
        }
    }

    companion object {
        private const val TAG = "Haron/AudioTags"
    }
}
