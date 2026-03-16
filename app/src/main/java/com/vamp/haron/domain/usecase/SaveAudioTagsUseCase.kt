package com.vamp.haron.domain.usecase

import com.vamp.core.logger.EcosystemLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import javax.inject.Inject

class SaveAudioTagsUseCase @Inject constructor() {

    suspend operator fun invoke(path: String, tags: AudioTags): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault

            setField(tag, FieldKey.TITLE, tags.title)
            setField(tag, FieldKey.ARTIST, tags.artist)
            setField(tag, FieldKey.ALBUM, tags.album)
            setField(tag, FieldKey.YEAR, tags.year)
            setField(tag, FieldKey.GENRE, tags.genre)

            audioFile.commit()
            EcosystemLogger.i(TAG, "Tags saved to: $path (title=${tags.title}, artist=${tags.artist}, album=${tags.album})")
            true
        } catch (e: Exception) {
            EcosystemLogger.e(TAG, "Failed to save tags: ${e.message}")
            false
        }
    }

    private fun setField(tag: org.jaudiotagger.tag.Tag, key: FieldKey, value: String) {
        if (value.isNotEmpty()) {
            tag.setField(key, value)
        } else {
            try { tag.deleteField(key) } catch (_: Exception) {}
        }
    }

    companion object {
        private const val TAG = "Haron/AudioTags"
    }
}
