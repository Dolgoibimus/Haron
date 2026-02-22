package com.vamp.haron.domain.usecase

import android.content.Context
import android.os.Environment
import com.vamp.haron.domain.model.InstalledAppInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class ExtractApkUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(app: InstalledAppInfo): Result<String> = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(app.apkPath)
            if (!sourceFile.exists()) {
                return@withContext Result.failure(Exception("APK не найден: ${app.apkPath}"))
            }

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            val versionSuffix = app.versionName?.let { "_v$it" } ?: ""
            val safeName = app.appName.replace(Regex("[^a-zA-Zа-яА-ЯёЁ0-9._\\- ]"), "_")
            var destFile = File(downloadsDir, "${safeName}${versionSuffix}.apk")

            // Avoid overwrite
            var counter = 1
            while (destFile.exists()) {
                destFile = File(downloadsDir, "${safeName}${versionSuffix}_($counter).apk")
                counter++
            }

            sourceFile.copyTo(destFile)
            Result.success(destFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
