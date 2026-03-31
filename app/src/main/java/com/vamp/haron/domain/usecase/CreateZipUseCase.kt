package com.vamp.haron.domain.usecase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.common.util.AesZipHelper
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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
        val hasPassword = !password.isNullOrEmpty()
        val splitSizeBytes = if (splitSizeMb > 0) splitSizeMb * 1024L * 1024L else 0L

        if (hasPassword) {
            // AES-256 encrypted ZIP
            if (splitSizeBytes > 0) {
                AesZipHelper.createSplitZip(
                    actualOutputPath, allFiles, password!!.toCharArray(), splitSizeBytes
                ) { index, t, name ->
                    // Note: can't emit from callback in flow, progress tracked below
                }
                // Emit progress after completion (split is done synchronously inside helper)
                allFiles.forEachIndexed { index, (file, _) ->
                    emit(ZipProgress(index, total, file.name))
                }
            } else {
                AesZipHelper.createEncryptedZip(actualOutputPath, allFiles, password!!.toCharArray()) { index, t, name ->
                    // Synchronous callback — can't emit from here
                }
                // Emit progress for each file
                allFiles.forEachIndexed { index, (file, _) ->
                    emit(ZipProgress(index, total, file.name))
                }
            }
            val outputSize = File(actualOutputPath).length()
            EcosystemLogger.d(HaronConstants.TAG, "CreateZipUseCase: encrypted zip complete, outputSize=$outputSize")
            emit(ZipProgress(total, total, "", isComplete = true, actualArchiveName = actualName))
        } else if (splitSizeBytes > 0) {
            // Unencrypted split ZIP
            AesZipHelper.createSplitZip(actualOutputPath, allFiles, null, splitSizeBytes) { index, t, name ->
                // Synchronous callback
            }
            allFiles.forEachIndexed { index, (file, _) ->
                emit(ZipProgress(index, total, file.name))
            }
            val outputSize = File(actualOutputPath).length()
            EcosystemLogger.d(HaronConstants.TAG, "CreateZipUseCase: split zip complete, outputSize=$outputSize")
            emit(ZipProgress(total, total, "", isComplete = true, actualArchiveName = actualName))
        } else {
            // Non-split, non-encrypted — use standard ZipOutputStream with per-file progress
            ZipOutputStream(FileOutputStream(actualOutputPath)).use { zos ->
                allFiles.forEachIndexed { index, (file, zipPath) ->
                    emit(ZipProgress(index, total, file.name))
                    val entry = ZipEntry(zipPath)
                    entry.time = file.lastModified()
                    zos.putNextEntry(entry)
                    FileInputStream(file).use { fis -> fis.copyTo(zos) }
                    zos.closeEntry()
                }
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
