package com.vamp.haron.domain.usecase

import android.content.Context
import com.vamp.haron.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import javax.inject.Inject

data class HashResult(
    val crc32: String = "",
    val md5: String = "",
    val sha1: String = "",
    val sha256: String = "",
    val sha512: String = "",
    val progress: Float = 0f
)

class CalculateHashUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    operator fun invoke(path: String): Flow<HashResult> = flow {
        val file = File(path)
        if (!file.exists() || !file.isFile) {
            val err = context.getString(R.string.hash_error)
            emit(HashResult(crc32 = err, md5 = err, sha1 = err, sha256 = err, sha512 = err, progress = 1f))
            return@flow
        }

        val totalSize = file.length()
        if (totalSize == 0L) {
            emit(HashResult(
                crc32 = "00000000",
                md5 = "d41d8cd98f00b204e9800998ecf8427e",
                sha1 = "da39a3ee5e6b4b0d3255bfef95601890afd80709",
                sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                sha512 = "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e",
                progress = 1f
            ))
            return@flow
        }

        val crc32 = CRC32()
        val md5Digest = MessageDigest.getInstance("MD5")
        val sha1Digest = MessageDigest.getInstance("SHA-1")
        val sha256Digest = MessageDigest.getInstance("SHA-256")
        val sha512Digest = MessageDigest.getInstance("SHA-512")

        val buffer = ByteArray(8192)
        var bytesRead = 0L
        var lastEmitProgress = 0f

        file.inputStream().buffered().use { input ->
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                crc32.update(buffer, 0, read)
                md5Digest.update(buffer, 0, read)
                sha1Digest.update(buffer, 0, read)
                sha256Digest.update(buffer, 0, read)
                sha512Digest.update(buffer, 0, read)
                bytesRead += read

                val progress = (bytesRead.toFloat() / totalSize).coerceAtMost(1f)
                if (progress - lastEmitProgress >= 0.05f || progress >= 1f) {
                    emit(HashResult(progress = progress))
                    lastEmitProgress = progress
                }
            }
        }

        val crc32Hex = "%08x".format(crc32.value)
        val md5Hex = md5Digest.digest().joinToString("") { "%02x".format(it) }
        val sha1Hex = sha1Digest.digest().joinToString("") { "%02x".format(it) }
        val sha256Hex = sha256Digest.digest().joinToString("") { "%02x".format(it) }
        val sha512Hex = sha512Digest.digest().joinToString("") { "%02x".format(it) }

        emit(HashResult(crc32 = crc32Hex, md5 = md5Hex, sha1 = sha1Hex, sha256 = sha256Hex, sha512 = sha512Hex, progress = 1f))
    }.flowOn(Dispatchers.IO)

    fun invokeFromUri(uri: android.net.Uri): Flow<HashResult> = flow {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        if (inputStream == null) {
            val err = context.getString(R.string.hash_error)
            emit(HashResult(crc32 = err, md5 = err, sha1 = err, sha256 = err, sha512 = err, progress = 1f))
            return@flow
        }

        val crc32 = CRC32()
        val md5Digest = MessageDigest.getInstance("MD5")
        val sha1Digest = MessageDigest.getInstance("SHA-1")
        val sha256Digest = MessageDigest.getInstance("SHA-256")
        val sha512Digest = MessageDigest.getInstance("SHA-512")

        val buffer = ByteArray(8192)
        inputStream.buffered().use { input ->
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                crc32.update(buffer, 0, read)
                md5Digest.update(buffer, 0, read)
                sha1Digest.update(buffer, 0, read)
                sha256Digest.update(buffer, 0, read)
                sha512Digest.update(buffer, 0, read)
            }
        }

        val crc32Hex = "%08x".format(crc32.value)
        val md5Hex = md5Digest.digest().joinToString("") { "%02x".format(it) }
        val sha1Hex = sha1Digest.digest().joinToString("") { "%02x".format(it) }
        val sha256Hex = sha256Digest.digest().joinToString("") { "%02x".format(it) }
        val sha512Hex = sha512Digest.digest().joinToString("") { "%02x".format(it) }

        emit(HashResult(crc32 = crc32Hex, md5 = md5Hex, sha1 = sha1Hex, sha256 = sha256Hex, sha512 = sha512Hex, progress = 1f))
    }.flowOn(Dispatchers.IO)
}
