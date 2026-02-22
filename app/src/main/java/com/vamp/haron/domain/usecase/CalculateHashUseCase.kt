package com.vamp.haron.domain.usecase

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject

data class HashResult(
    val md5: String = "",
    val sha256: String = "",
    val progress: Float = 0f
)

class CalculateHashUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    operator fun invoke(path: String): Flow<HashResult> = flow {
        val file = File(path)
        if (!file.exists() || !file.isFile) {
            emit(HashResult(md5 = "Ошибка", sha256 = "Ошибка", progress = 1f))
            return@flow
        }

        val totalSize = file.length()
        if (totalSize == 0L) {
            emit(HashResult(md5 = "d41d8cd98f00b204e9800998ecf8427e", sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", progress = 1f))
            return@flow
        }

        val md5Digest = MessageDigest.getInstance("MD5")
        val sha256Digest = MessageDigest.getInstance("SHA-256")

        val buffer = ByteArray(8192)
        var bytesRead = 0L
        var lastEmitProgress = 0f

        file.inputStream().buffered().use { input ->
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                md5Digest.update(buffer, 0, read)
                sha256Digest.update(buffer, 0, read)
                bytesRead += read

                val progress = (bytesRead.toFloat() / totalSize).coerceAtMost(1f)
                if (progress - lastEmitProgress >= 0.05f || progress >= 1f) {
                    emit(HashResult(progress = progress))
                    lastEmitProgress = progress
                }
            }
        }

        val md5Hex = md5Digest.digest().joinToString("") { "%02x".format(it) }
        val sha256Hex = sha256Digest.digest().joinToString("") { "%02x".format(it) }

        emit(HashResult(md5 = md5Hex, sha256 = sha256Hex, progress = 1f))
    }.flowOn(Dispatchers.IO)

    fun invokeFromUri(uri: android.net.Uri): Flow<HashResult> = flow {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        if (inputStream == null) {
            emit(HashResult(md5 = "Ошибка", sha256 = "Ошибка", progress = 1f))
            return@flow
        }

        val md5Digest = MessageDigest.getInstance("MD5")
        val sha256Digest = MessageDigest.getInstance("SHA-256")

        val buffer = ByteArray(8192)
        inputStream.buffered().use { input ->
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                md5Digest.update(buffer, 0, read)
                sha256Digest.update(buffer, 0, read)
            }
        }

        val md5Hex = md5Digest.digest().joinToString("") { "%02x".format(it) }
        val sha256Hex = sha256Digest.digest().joinToString("") { "%02x".format(it) }

        emit(HashResult(md5 = md5Hex, sha256 = sha256Hex, progress = 1f))
    }.flowOn(Dispatchers.IO)
}
