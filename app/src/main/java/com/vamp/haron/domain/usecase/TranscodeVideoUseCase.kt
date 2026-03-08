package com.vamp.haron.domain.usecase

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.data.datastore.HaronPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import java.io.File
import javax.inject.Inject

data class TranscodeProgress(
    val percent: Int = 0,
    val outputPath: String? = null,
    val error: String? = null,
    val isComplete: Boolean = false,
    val readyToStream: Boolean = false,
    /** HLS directory path — if set, cast via HLS playlist URL */
    val hlsDir: String? = null
)

class TranscodeVideoUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: HaronPreferences
) {
    private var currentSession: FFmpegSession? = null

    private val chromecastFormats = setOf("mp4", "m4v", "webm")

    /** Start HLS cast after 30 sec of content (3 segments × 10 sec) */
    private val streamThresholdMs = 30_000L

    private val cacheDir: File
        get() = File(context.filesDir, "transcode_cache").also { it.mkdirs() }

    fun needsTranscode(filePath: String): Boolean {
        val ext = File(filePath).extension.lowercase()
        return ext !in chromecastFormats
    }

    private fun getHlsDir(inputPath: String): File {
        val input = File(inputPath)
        val hash = "${input.name}_${input.length()}_q6".hashCode().toUInt().toString(16)
        return File(cacheDir, "hls_$hash")
    }

    private fun isHlsCached(hlsDir: File): Boolean {
        val playlist = File(hlsDir, "playlist.m3u8")
        if (!playlist.exists()) return false
        return try {
            playlist.readText().contains("#EXT-X-ENDLIST")
        } catch (_: Exception) {
            false
        }
    }

    private fun evictExpiredCache() {
        val ttlMs = preferences.transcodeCacheTtlHours * 3600_000L
        val now = System.currentTimeMillis()
        cacheDir.listFiles()?.forEach { entry ->
            val isOldMp4 = entry.isFile && entry.name.startsWith("cast_transcode_")
            val isHls = entry.isDirectory && entry.name.startsWith("hls_")
            if (isOldMp4 || isHls) {
                if (now - entry.lastModified() > ttlMs) {
                    entry.deleteRecursively()
                    EcosystemLogger.d(HaronConstants.TAG, "Transcode cache evicted (TTL): ${entry.name}")
                }
            }
        }
    }

    private fun getDurationMs(inputPath: String): Long {
        return try {
            val session = FFprobeKit.getMediaInformation(inputPath)
            val info = session.mediaInformation
            val durationStr = info?.duration
            EcosystemLogger.d(HaronConstants.TAG, "TranscodeVideoUseCase: FFprobe duration=$durationStr")
            if (durationStr == null) return 0L
            (durationStr.toDouble() * 1000).toLong()
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "TranscodeVideoUseCase: getDurationMs CRASH: ${e.javaClass.simpleName}: ${e.message}")
            0L
        }
    }

    /**
     * Detect best available H.264 encoder.
     * Priority: libx264 (software, best quality) > h264_mediacodec (hardware) > mpeg4 (fallback).
     */
    private enum class VideoEncoder { LIBX264, H264_MEDIACODEC, MPEG4 }

    private val bestEncoder: VideoEncoder by lazy {
        try {
            EcosystemLogger.d(HaronConstants.TAG, "TranscodeVideoUseCase: detecting encoders...")
            val session = FFmpegKit.execute("-encoders")
            val output = session.output ?: ""
            val result = when {
                output.contains("libx264") -> VideoEncoder.LIBX264
                output.contains("h264_mediacodec") -> VideoEncoder.H264_MEDIACODEC
                else -> VideoEncoder.MPEG4
            }
            EcosystemLogger.d(HaronConstants.TAG, "TranscodeVideoUseCase: bestEncoder=$result")
            result
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "TranscodeVideoUseCase: encoder detection CRASH: ${e.javaClass.simpleName}: ${e.message}")
            VideoEncoder.MPEG4
        }
    }

    private fun buildHlsCommand(inputPath: String, hlsDir: File): String {
        val segPattern = File(hlsDir, "seg_%05d.ts").absolutePath
        val playlistPath = File(hlsDir, "playlist.m3u8").absolutePath

        return buildString {
            append("-i \"$inputPath\" ")
            when (bestEncoder) {
                VideoEncoder.LIBX264 -> {
                    append("-c:v libx264 -profile:v high -level:v 4.1 ")
                    append("-preset medium -crf 20 ")
                    append("-maxrate 8M -bufsize 16M ")
                }
                VideoEncoder.H264_MEDIACODEC -> {
                    append("-c:v h264_mediacodec ")
                    append("-b:v 8M -maxrate 8M -bufsize 16M ")
                }
                VideoEncoder.MPEG4 -> {
                    append("-c:v mpeg4 -q:v 3 ")
                    append("-maxrate 8M -bufsize 16M ")
                }
            }
            append("-vf \"scale='min(1920,iw)':'min(1080,ih)':force_original_aspect_ratio=decrease\" ")
            append("-g 60 -r 30 ")
            append("-c:a aac -ac 2 -b:a 192k ")
            append("-f hls -hls_time 10 -hls_list_size 0 ")
            append("-hls_segment_type mpegts ")
            append("-hls_playlist_type event ")
            append("-hls_segment_filename \"$segPattern\" ")
            append("-y \"$playlistPath\"")
        }
    }

    operator fun invoke(inputPath: String): Flow<TranscodeProgress> {
        EcosystemLogger.d(HaronConstants.TAG, "TranscodeVideoUseCase.invoke ENTER: $inputPath")
        val ext = File(inputPath).extension.lowercase()

        if (ext in chromecastFormats) {
            EcosystemLogger.d(HaronConstants.TAG, "TranscodeVideoUseCase: no transcode needed (ext=$ext)")
            return flowOf(
                TranscodeProgress(
                    percent = 100,
                    outputPath = inputPath,
                    isComplete = true
                )
            )
        }

        // Evict expired cache entries
        try {
            evictExpiredCache()
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "TranscodeVideoUseCase: evictExpiredCache CRASH: ${e.message}")
        }

        // Check HLS cache
        val hlsDir = getHlsDir(inputPath)
        EcosystemLogger.d(HaronConstants.TAG, "TranscodeVideoUseCase: hlsDir=${hlsDir.absolutePath}, cached=${isHlsCached(hlsDir)}")
        if (isHlsCached(hlsDir)) {
            EcosystemLogger.d(HaronConstants.TAG, "HLS cache hit: ${hlsDir.name}")
            return flowOf(
                TranscodeProgress(
                    percent = 100,
                    hlsDir = hlsDir.absolutePath,
                    readyToStream = true,
                    isComplete = true
                )
            )
        }

        // Clean partial HLS and prepare directory
        hlsDir.deleteRecursively()
        hlsDir.mkdirs()

        EcosystemLogger.d(HaronConstants.TAG, "TranscodeVideoUseCase: creating callbackFlow (HLS)")
        return callbackFlow {
            EcosystemLogger.d(HaronConstants.TAG, "TranscodeVideoUseCase: getting duration via FFprobe")
            val durationMs = getDurationMs(inputPath)

            val cmd = buildHlsCommand(inputPath, hlsDir)
            EcosystemLogger.d(HaronConstants.TAG, "Transcode start (HLS): $inputPath → ${hlsDir.name} (duration=${durationMs}ms, encoder=$bestEncoder)")
            EcosystemLogger.d(HaronConstants.TAG, "TranscodeVideoUseCase: FFmpeg cmd=$cmd")

            try {
                val session = FFmpegKit.executeAsync(cmd,
                    { session ->
                        // Completion callback
                        val returnCode = session.returnCode
                        EcosystemLogger.d(HaronConstants.TAG, "TranscodeVideoUseCase: completion callback, rc=$returnCode")
                        if (ReturnCode.isSuccess(returnCode)) {
                            val segCount = hlsDir.listFiles()?.count { it.extension == "ts" } ?: 0
                            EcosystemLogger.d(HaronConstants.TAG, "Transcode complete: ${hlsDir.name} ($segCount segments)")
                            trySend(
                                TranscodeProgress(
                                    percent = 100,
                                    hlsDir = hlsDir.absolutePath,
                                    readyToStream = true,
                                    isComplete = true
                                )
                            )
                        } else if (ReturnCode.isCancel(returnCode)) {
                            EcosystemLogger.d(HaronConstants.TAG, "Transcode cancelled")
                            hlsDir.deleteRecursively()
                            trySend(
                                TranscodeProgress(error = "Cancelled", isComplete = true)
                            )
                        } else {
                            val errorMsg = session.failStackTrace ?: "Transcode failed (rc=${returnCode?.value})"
                            EcosystemLogger.e(HaronConstants.TAG, "Transcode error: $errorMsg")
                            hlsDir.deleteRecursively()
                            trySend(
                                TranscodeProgress(error = errorMsg, isComplete = true)
                            )
                        }
                        channel.close()
                    },
                    { log ->
                        EcosystemLogger.d("FFmpeg", log.message ?: "")
                    },
                    { statistics ->
                        // Statistics callback — progress
                        if (durationMs > 0) {
                            val timeMs = statistics.time.toLong()
                            val p = ((timeMs * 100) / durationMs).toInt().coerceIn(0, 99)
                            val ready = timeMs >= streamThresholdMs
                            trySend(
                                TranscodeProgress(
                                    percent = p,
                                    hlsDir = hlsDir.absolutePath,
                                    readyToStream = ready
                                )
                            )
                        }
                    }
                )

                currentSession = session
                EcosystemLogger.d(HaronConstants.TAG, "TranscodeVideoUseCase: session created, id=${session.sessionId}")
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "TranscodeVideoUseCase: FFmpegKit.executeAsync CRASH: ${e.javaClass.simpleName}: ${e.message}")
                e.printStackTrace()
                trySend(TranscodeProgress(error = "FFmpeg init failed: ${e.message}", isComplete = true))
                channel.close()
            }

            awaitClose {
                try {
                    currentSession?.cancel()
                } catch (_: Exception) { }
                currentSession = null
            }
        }
    }

    fun cancelTranscode() {
        try {
            currentSession?.cancel()
        } catch (_: Exception) { }
        currentSession = null
    }

    fun cleanupTempFiles() {
        cacheDir.listFiles()?.forEach { entry ->
            val isOldMp4 = entry.isFile && entry.name.startsWith("cast_transcode_")
            val isHls = entry.isDirectory && entry.name.startsWith("hls_")
            if (isOldMp4 || isHls) {
                entry.deleteRecursively()
                EcosystemLogger.d(HaronConstants.TAG, "Transcode cache deleted: ${entry.name}")
            }
        }
    }
}
