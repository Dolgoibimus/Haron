package com.vamp.haron.domain.usecase

import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ExcludeFileFilter
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import net.lingala.zip4j.progress.ProgressMonitor
import java.io.File
import javax.inject.Inject

data class ZipProgress(
    val current: Int,
    val total: Int,
    val fileName: String,
    val isComplete: Boolean = false
)

class CreateZipUseCase @Inject constructor() {

    operator fun invoke(
        sourcePaths: List<String>,
        outputPath: String,
        password: String? = null,
        splitSizeMb: Int = 0
    ): Flow<ZipProgress> = flow {
        val sources = sourcePaths.map { File(it) }
        val allFiles = collectFiles(sources)
        val total = allFiles.size

        val zipFile = ZipFile(outputPath)
        if (!password.isNullOrEmpty()) {
            zipFile.setPassword(password.toCharArray())
        }

        val params = ZipParameters().apply {
            compressionMethod = CompressionMethod.DEFLATE
            if (!password.isNullOrEmpty()) {
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            }
        }

        if (splitSizeMb > 0) {
            // Split ZIP — batch operation, poll ProgressMonitor for per-file progress
            val splitSizeBytes = splitSizeMb * 1024L * 1024L
            zipFile.isRunInThread = true

            val hasDirectories = sources.any { it.isDirectory }
            if (!hasDirectories) {
                zipFile.createSplitZipFile(sources, params, true, splitSizeBytes)
            } else {
                val parentDir = sources.first().parentFile!!
                val selectedNames = sources.map { it.name }.toSet()
                val splitParams = ZipParameters(params).apply {
                    isIncludeRootFolder = false
                    excludeFileFilter = ExcludeFileFilter { file ->
                        val topLevel = file.toRelativeString(parentDir).split('/', '\\').first()
                        topLevel !in selectedNames
                    }
                }
                zipFile.createSplitZipFileFromFolder(parentDir, splitParams, true, splitSizeBytes)
            }

            val monitor = zipFile.progressMonitor
            // Wait for operation to start (max 2 seconds)
            var waitCount = 0
            while (monitor.state != ProgressMonitor.State.BUSY && waitCount < 200) {
                delay(10)
                waitCount++
            }

            var lastFileName = ""
            var current = 0
            while (monitor.state == ProgressMonitor.State.BUSY) {
                val fn = monitor.fileName ?: ""
                if (fn.isNotEmpty() && fn != lastFileName) {
                    lastFileName = fn
                    current++
                    emit(ZipProgress(current.coerceAtMost(total), total, fn.substringAfterLast('/')))
                }
                delay(50)
            }

            if (monitor.result == ProgressMonitor.Result.ERROR) {
                throw monitor.exception ?: Exception("ZIP creation error")
            }
            emit(ZipProgress(total, total, "", isComplete = true))
        } else {
            // Non-split — add files one by one for exact per-file progress
            allFiles.forEachIndexed { index, (file, zipPath) ->
                emit(ZipProgress(index, total, file.name))
                val fileParams = ZipParameters(params).apply {
                    fileNameInZip = zipPath
                }
                zipFile.addFile(file, fileParams)
            }
            emit(ZipProgress(total, total, "", isComplete = true))
        }
    }.flowOn(Dispatchers.IO)

    private fun collectFiles(sources: List<File>): List<Pair<File, String>> {
        val result = mutableListOf<Pair<File, String>>()
        for (source in sources) {
            if (source.isDirectory) {
                source.walkTopDown().filter { it.isFile }.forEach { file ->
                    val relativePath = "${source.name}/${file.relativeTo(source).path}"
                    result.add(file to relativePath.replace('\\', '/'))
                }
            } else {
                result.add(source to source.name)
            }
        }
        return result
    }
}
