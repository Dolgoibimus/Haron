package com.vamp.haron.domain.usecase

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Effect
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Codec
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject

data class TranscodeProgress(
    val percent: Int = 0,
    val outputPath: String? = null,
    val error: String? = null,
    val isComplete: Boolean = false,
    val audioStripped: Boolean = false
)

@UnstableApi
class TranscodeVideoUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var currentTransformer: Transformer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val chromecastFormats = setOf("mp4", "m4v", "webm")

    fun needsTranscode(filePath: String): Boolean {
        val ext = File(filePath).extension.lowercase()
        return ext !in chromecastFormats
    }

    private fun getCacheFile(inputPath: String): File {
        val input = File(inputPath)
        // Include quality version in hash so cache invalidates when settings change
        val hash = "${input.name}_${input.length()}_q4".hashCode().toUInt().toString(16)
        return File(context.cacheDir, "cast_transcode_$hash.mp4")
    }

    operator fun invoke(inputPath: String): Flow<TranscodeProgress> {
        val ext = File(inputPath).extension.lowercase()

        if (ext in chromecastFormats) {
            return flowOf(
                TranscodeProgress(
                    percent = 100,
                    outputPath = inputPath,
                    isComplete = true
                )
            )
        }

        // Check cache — don't re-transcode the same file
        val cachedFile = getCacheFile(inputPath)
        if (cachedFile.exists() && cachedFile.length() > 0) {
            EcosystemLogger.d(HaronConstants.TAG, "Transcode cache hit: ${cachedFile.name} (${cachedFile.length() / 1024}KB)")
            return flowOf(
                TranscodeProgress(
                    percent = 100,
                    outputPath = cachedFile.absolutePath,
                    isComplete = true
                )
            )
        }

        return callbackFlow {
            var activeProgressRunnable: Runnable? = null

            fun stopPoller() {
                activeProgressRunnable?.let { mainHandler.removeCallbacks(it) }
                activeProgressRunnable = null
            }

            fun startPoller(transformer: Transformer, outputPath: String) {
                stopPoller()
                val holder = ProgressHolder()
                var lastLoggedPercent = -1
                val runnable = object : Runnable {
                    override fun run() {
                        val state = transformer.getProgress(holder)

                        val p = if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            holder.progress
                        } else {
                            0
                        }

                        if (p > 0 && p != lastLoggedPercent && (p % 10 == 0 || p == 1)) {
                            EcosystemLogger.d(HaronConstants.TAG, "Transcode progress: $p%")
                            lastLoggedPercent = p
                        }

                        trySend(
                            TranscodeProgress(
                                percent = p,
                                outputPath = outputPath
                            )
                        )

                        mainHandler.postDelayed(this, 500)
                    }
                }
                activeProgressRunnable = runnable
                mainHandler.postDelayed(runnable, 500)
            }

            fun startTransformer(removeAudio: Boolean) {
                val outputFile = cachedFile
                if (outputFile.exists()) outputFile.delete()
                val outputPath = outputFile.absolutePath

                EcosystemLogger.d(
                    HaronConstants.TAG,
                    "Transcode start: $inputPath → $outputPath (removeAudio=$removeAudio)"
                )

                val videoEncoderSettings = VideoEncoderSettings.Builder()
                    .setBitrate(8_000_000) // 8 Mbps CBR — stable for Chromecast streaming
                    .setBitrateMode(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
                    .setEncodingProfileLevel(
                        MediaCodecInfo.CodecProfileLevel.AVCProfileMain,
                        MediaCodecInfo.CodecProfileLevel.AVCLevel41
                    )
                    .build()

                val baseEncoderFactory = DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(videoEncoderSettings)
                    .build()

                // Wrap to inject KEY_I_FRAME_INTERVAL = 2 seconds for frequent keyframes
                val encoderFactory = CastEncoderFactory(baseEncoderFactory)

                // Cap at 1080p/30fps — Chromecast H264 High Profile 4.1 max
                val videoEffects = listOf<Effect>(
                    Presentation.createForHeight(1080)
                )

                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(encoderFactory)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(
                            composition: Composition,
                            exportResult: ExportResult
                        ) {
                            stopPoller()
                            EcosystemLogger.d(
                                HaronConstants.TAG,
                                "Transcode complete: $outputPath (audioStripped=$removeAudio, size=${outputFile.length() / 1024}KB)"
                            )

                            // Move moov box to front on IO thread — heavy file I/O, would ANR on main
                            this@callbackFlow.launch(Dispatchers.IO) {
                                val fastStarted = fastStartMp4(outputFile)
                                EcosystemLogger.d(HaronConstants.TAG, "FastStart: $fastStarted")

                                trySend(
                                    TranscodeProgress(
                                        percent = 100,
                                        outputPath = outputPath,
                                        isComplete = true,
                                        audioStripped = removeAudio
                                    )
                                )
                                channel.close()
                            }
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            stopPoller()
                            val errorMsg = exportException.message ?: "Transcode failed"
                            EcosystemLogger.e(HaronConstants.TAG, "Transcode error: $errorMsg")

                            val isAudioCodecError =
                                errorMsg.contains("AudioDecoder", ignoreCase = true)
                                    || errorMsg.contains("audio/ac3", ignoreCase = true)
                                    || errorMsg.contains("audio/eac3", ignoreCase = true)
                                    || errorMsg.contains("audio/vnd.dts", ignoreCase = true)
                                    || errorMsg.contains("audio/mp4a-latm", ignoreCase = true)
                                    || (exportException.errorCode == ExportException.ERROR_CODE_DECODER_INIT_FAILED
                                    && errorMsg.contains("audio", ignoreCase = true))

                            if (!removeAudio && isAudioCodecError) {
                                EcosystemLogger.w(
                                    HaronConstants.TAG,
                                    "Audio codec not supported, retrying without audio"
                                )
                                if (outputFile.exists()) outputFile.delete()
                                mainHandler.post { startTransformer(removeAudio = true) }
                            } else {
                                trySend(
                                    TranscodeProgress(error = errorMsg, isComplete = true)
                                )
                                channel.close()
                            }
                        }
                    })
                    .build()

                currentTransformer = transformer

                val mediaItem = MediaItem.fromUri("file://$inputPath")
                val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                    .setRemoveAudio(removeAudio)
                    .setEffects(Effects(emptyList(), videoEffects))
                    .build()

                try {
                    transformer.start(editedMediaItem, outputPath)
                    startPoller(transformer, outputPath)
                } catch (e: Exception) {
                    EcosystemLogger.e(
                        HaronConstants.TAG,
                        "Transcode start error: ${e.message}"
                    )
                    trySend(
                        TranscodeProgress(
                            error = e.message ?: "Failed to start transcode",
                            isComplete = true
                        )
                    )
                    channel.close()
                }
            }

            mainHandler.post { startTransformer(removeAudio = false) }

            awaitClose {
                stopPoller()
                mainHandler.post {
                    try {
                        currentTransformer?.cancel()
                    } catch (_: Exception) { }
                }
                currentTransformer = null
            }
        }
    }

    fun cancelTranscode() {
        val transformer = currentTransformer ?: return
        mainHandler.post {
            try {
                transformer.cancel()
            } catch (_: Exception) { }
        }
        currentTransformer = null
    }

    fun cleanupTempFiles() {
        context.cacheDir.listFiles()?.filter { it.name.startsWith("cast_transcode_") }?.forEach {
            it.delete()
            EcosystemLogger.d(HaronConstants.TAG, "Transcode cache deleted: ${it.name}")
        }
    }

    /**
     * Move moov box before mdat (qt-faststart algorithm).
     * Chromecast needs moov at the front for progressive download playback.
     *
     * Steps:
     * 1. Find top-level boxes (ftyp, moov, mdat)
     * 2. If moov already before mdat → done
     * 3. Read moov, update stco/co64 chunk offsets
     * 4. Rewrite file: ftyp + moov + mdat
     */
    private fun fastStartMp4(file: File): Boolean {
        if (!file.exists() || file.length() < 8) return false

        try {
            data class Box(val type: String, val offset: Long, val size: Long)

            val raf = RandomAccessFile(file, "r")
            val boxes = mutableListOf<Box>()
            var pos = 0L
            val fileLen = raf.length()
            val header = ByteArray(8)

            // Parse top-level boxes
            while (pos < fileLen - 8) {
                raf.seek(pos)
                raf.readFully(header)
                var boxSize = ByteBuffer.wrap(header, 0, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
                val boxType = String(header, 4, 4)

                if (boxSize == 1L) {
                    // 64-bit extended size
                    val ext = ByteArray(8)
                    raf.readFully(ext)
                    boxSize = ByteBuffer.wrap(ext).order(ByteOrder.BIG_ENDIAN).long
                } else if (boxSize == 0L) {
                    boxSize = fileLen - pos
                }

                if (boxSize < 8) break
                boxes.add(Box(boxType, pos, boxSize))
                pos += boxSize
            }

            val moov = boxes.find { it.type == "moov" }
            val mdat = boxes.find { it.type == "mdat" }

            if (moov == null || mdat == null) {
                raf.close()
                EcosystemLogger.w(HaronConstants.TAG, "FastStart: moov or mdat not found")
                return false
            }

            if (moov.offset < mdat.offset) {
                raf.close()
                EcosystemLogger.d(HaronConstants.TAG, "FastStart: moov already before mdat")
                return true
            }

            // Read moov data
            val moovData = ByteArray(moov.size.toInt())
            raf.seek(moov.offset)
            raf.readFully(moovData)

            // Calculate offset delta: moov will be placed right after ftyp,
            // shifting mdat forward by moov.size
            val moovSize = moov.size

            // Update stco and co64 chunk offsets inside moov
            updateChunkOffsets(moovData, moovSize)

            // Build new file: boxes before mdat + moov + mdat + boxes after mdat (except moov)
            val tmpFile = File(file.parent, file.name + ".tmp")
            val out = RandomAccessFile(tmpFile, "rw")

            // Write boxes that come before mdat (ftyp, etc.)
            for (box in boxes) {
                if (box.type == "moov") continue
                if (box.type == "mdat") break
                val data = ByteArray(box.size.toInt())
                raf.seek(box.offset)
                raf.readFully(data)
                out.write(data)
            }

            // Write moov (with updated offsets)
            out.write(moovData)

            // Write mdat and everything after it (except moov)
            val buffer = ByteArray(65536)
            for (box in boxes) {
                if (box.offset < mdat.offset) continue
                if (box.type == "moov") continue
                raf.seek(box.offset)
                var remaining = box.size
                while (remaining > 0) {
                    val toRead = minOf(remaining, buffer.size.toLong()).toInt()
                    val read = raf.read(buffer, 0, toRead)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    remaining -= read
                }
            }

            out.close()
            raf.close()

            // Replace original with faststarted version
            if (tmpFile.length() > 0) {
                file.delete()
                tmpFile.renameTo(file)
                EcosystemLogger.d(HaronConstants.TAG, "FastStart: moov moved to front (${moovSize / 1024}KB)")
                return true
            } else {
                tmpFile.delete()
                return false
            }
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "FastStart error: ${e.message}")
            return false
        }
    }

    /** Scan moov data for stco/co64 boxes and add offset delta to all chunk offsets */
    private fun updateChunkOffsets(moovData: ByteArray, delta: Long) {
        var pos = 8 // skip moov header
        val len = moovData.size

        fun scanContainer(start: Int, end: Int) {
            var p = start
            while (p < end - 8) {
                val boxSize = ByteBuffer.wrap(moovData, p, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
                if (boxSize < 8 || p + boxSize > end) break
                val boxType = String(moovData, p + 4, 4)

                when (boxType) {
                    "trak", "mdia", "minf", "stbl" -> {
                        scanContainer(p + 8, (p + boxSize).toInt())
                    }
                    "stco" -> {
                        // version(1) + flags(3) + entry_count(4)
                        val dataStart = p + 8
                        val entryCount = ByteBuffer.wrap(moovData, dataStart + 4, 4)
                            .order(ByteOrder.BIG_ENDIAN).int
                        for (i in 0 until entryCount) {
                            val off = dataStart + 8 + i * 4
                            val oldVal = ByteBuffer.wrap(moovData, off, 4)
                                .order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
                            val newVal = oldVal + delta
                            ByteBuffer.wrap(moovData, off, 4)
                                .order(ByteOrder.BIG_ENDIAN).putInt(newVal.toInt())
                        }
                    }
                    "co64" -> {
                        val dataStart = p + 8
                        val entryCount = ByteBuffer.wrap(moovData, dataStart + 4, 4)
                            .order(ByteOrder.BIG_ENDIAN).int
                        for (i in 0 until entryCount) {
                            val off = dataStart + 8 + i * 8
                            val oldVal = ByteBuffer.wrap(moovData, off, 8)
                                .order(ByteOrder.BIG_ENDIAN).long
                            ByteBuffer.wrap(moovData, off, 8)
                                .order(ByteOrder.BIG_ENDIAN).putLong(oldVal + delta)
                        }
                    }
                }
                p += boxSize.toInt()
            }
        }

        scanContainer(pos, len)
    }
}

/**
 * Encoder factory wrapper that caps frame rate to 30fps for Chromecast playback.
 * DefaultEncoderFactory already sets KEY_I_FRAME_INTERVAL = 1s internally.
 * Stable 30fps without drops looks better than jerky 60fps on Chromecast.
 */
@UnstableApi
private class CastEncoderFactory(
    private val delegate: Codec.EncoderFactory
) : Codec.EncoderFactory {

    override fun createForAudioEncoding(format: Format): Codec =
        delegate.createForAudioEncoding(format)

    override fun createForVideoEncoding(format: Format): Codec {
        val cappedFormat = if (format.frameRate > 30f) {
            format.buildUpon().setFrameRate(30f).build()
        } else {
            format
        }
        return delegate.createForVideoEncoding(cappedFormat)
    }

    override fun videoNeedsEncoding(): Boolean = true
}
