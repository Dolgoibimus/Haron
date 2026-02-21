package com.vamp.haron.domain.usecase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

class CreateZipUseCase @Inject constructor() {

    suspend operator fun invoke(sourcePaths: List<String>, outputPath: String) = withContext(Dispatchers.IO) {
        val outputFile = File(outputPath)
        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            for (path in sourcePaths) {
                val file = File(path)
                if (file.isDirectory) {
                    addDirectory(zos, file, file.name)
                } else {
                    addFile(zos, file, file.name)
                }
            }
        }
    }

    private fun addDirectory(zos: ZipOutputStream, dir: File, basePath: String) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            val entryPath = "$basePath/${file.name}"
            if (file.isDirectory) {
                addDirectory(zos, file, entryPath)
            } else {
                addFile(zos, file, entryPath)
            }
        }
    }

    private fun addFile(zos: ZipOutputStream, file: File, entryPath: String) {
        val entry = ZipEntry(entryPath)
        entry.time = file.lastModified()
        zos.putNextEntry(entry)
        file.inputStream().use { it.copyTo(zos) }
        zos.closeEntry()
    }
}
