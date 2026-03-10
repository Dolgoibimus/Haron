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
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import java.io.File
import javax.inject.Inject

data class ZipProgress(
    val current: Int,
    val total: Int,
    val fileName: String,
    val isComplete: Boolean = false,
    val actualArchiveName: String? = null
)

class CreateZipUseCase @Inject constructor() {

    operator fun invoke(
        sourcePaths: List<String>,
        outputPath: String,
        password: String? = null,
        splitSizeMb: Int = 0
    ): Flow<ZipProgress> = flow {
        val sources = sourcePaths.map { File(it) }
        val existingSources = sources.filter { it.exists() }
        EcosystemLogger.d(HaronConstants.TAG, "CreateZipUseCase: sources=${sources.size}, existing=${existingSources.size}, output=$outputPath, split=${splitSizeMb}MB")

        if (existingSources.isEmpty()) {
            EcosystemLogger.e(HaronConstants.TAG, "CreateZipUseCase: no existing source files, aborting")
            throw IllegalStateException("No source files found")
        }

        val allFiles = collectFiles(existingSources)
        val total = allFiles.size
        EcosystemLogger.d(HaronConstants.TAG, "CreateZipUseCase: collected $total files to compress")

        if (total == 0) {
            EcosystemLogger.e(HaronConstants.TAG, "CreateZipUseCase: no files collected (empty directories?)")
            throw IllegalStateException("No files to archive (only empty directories)")
        }

        val actualOutputPath = outputPath
        val actualName = File(actualOutputPath).name

        val zipFile = ZipFile(actualOutputPath)
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

            val hasDirectories = existingSources.any { it.isDirectory }
            if (!hasDirectories) {
                zipFile.createSplitZipFile(existingSources, params, true, splitSizeBytes)
            } else {
                val parentDir = existingSources.first().parentFile!!
                val selectedNames = existingSources.map { it.name }.toSet()
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
                val ex = monitor.exception ?: Exception("ZIP creation error")
                EcosystemLogger.e(HaronConstants.TAG, "CreateZipUseCase: split zip failed — ${ex.message}")
                throw ex
            }
            val outputSize = File(actualOutputPath).length()
            EcosystemLogger.d(HaronConstants.TAG, "CreateZipUseCase: split zip complete, outputSize=$outputSize")
            emit(ZipProgress(total, total, "", isComplete = true, actualArchiveName = actualName))
        } else {
            // Non-split — add files one by one for exact per-file progress
            allFiles.forEachIndexed { index, (file, zipPath) ->
                emit(ZipProgress(index, total, file.name))
                val fileParams = ZipParameters(params).apply {
                    fileNameInZip = zipPath
                }
                zipFile.addFile(file, fileParams)
            }
            val outputSize = File(actualOutputPath).length()
            EcosystemLogger.d(HaronConstants.TAG, "CreateZipUseCase: zip complete, outputSize=$outputSize")
            emit(ZipProgress(total, total, "", isComplete = true, actualArchiveName = actualName))
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

    companion object {
        fun findUniqueZipPath(path: String): String {
            val file = File(path)
            if (!file.exists()) return path
            val parent = file.parent ?: return path
            val nameWithoutExt = file.nameWithoutExtension
            val ext = file.extension
            var counter = 1
            while (true) {
                val candidate = File(parent, "$nameWithoutExt ($counter).$ext")
                if (!candidate.exists()) return candidate.absolutePath
                counter++
            }
        }
    }
}
