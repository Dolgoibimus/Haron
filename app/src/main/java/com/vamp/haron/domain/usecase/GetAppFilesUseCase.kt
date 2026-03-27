package com.vamp.haron.domain.usecase

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.data.shizuku.ShizukuManager
import com.vamp.haron.domain.model.AppFileEntry
import com.vamp.haron.domain.model.AppFilesInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

private const val TAG = "GetAppFiles"

class GetAppFilesUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuManager: ShizukuManager
) {
    suspend operator fun invoke(packageName: String): AppFilesInfo = withContext(Dispatchers.IO) {
        val shizukuReady = shizukuManager.isServiceBound()
        val entries = mutableListOf<AppFileEntry>()
        var totalSize = 0L

        EcosystemLogger.d(TAG, "getAppFiles: pkg=$packageName, shizuku=$shizukuReady")

        // 1. APK path(s)
        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)

            // Base APK
            val baseApk = File(appInfo.sourceDir)
            val baseSize = baseApk.length()
            totalSize += baseSize
            entries.add(AppFileEntry(
                path = appInfo.sourceDir,
                name = "APK: ${baseApk.name}",
                size = baseSize,
                isDirectory = false,
                isAccessible = true
            ))

            // Split APKs
            appInfo.splitSourceDirs?.forEach { splitPath ->
                val splitFile = File(splitPath)
                val splitSize = splitFile.length()
                totalSize += splitSize
                entries.add(AppFileEntry(
                    path = splitPath,
                    name = "Split: ${splitFile.name}",
                    size = splitSize,
                    isDirectory = false,
                    isAccessible = true
                ))
            }

            // Native libraries
            val nativeDir = appInfo.nativeLibraryDir
            if (nativeDir != null) {
                val nativeFile = File(nativeDir)
                if (nativeFile.exists() && nativeFile.isDirectory) {
                    val nativeSize = nativeFile.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    totalSize += nativeSize
                    entries.add(AppFileEntry(
                        path = nativeDir,
                        name = "Native libs",
                        size = nativeSize,
                        isDirectory = true,
                        isAccessible = true
                    ))
                }
            }
        } catch (e: PackageManager.NameNotFoundException) {
            EcosystemLogger.e(TAG, "getAppFiles: package not found: $packageName")
        }

        // 2. /Android/obb/<package>/
        val obbDir = File(Environment.getExternalStorageDirectory(), "Android/obb/$packageName")
        if (obbDir.exists()) {
            val obbSize = obbDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            totalSize += obbSize
            val obbChildren = obbDir.listFiles()?.map { f ->
                AppFileEntry(f.absolutePath, f.name, f.length(), f.isDirectory, true)
            } ?: emptyList()
            entries.add(AppFileEntry(
                path = obbDir.absolutePath,
                name = "OBB data",
                size = obbSize,
                isDirectory = true,
                isAccessible = true,
                children = obbChildren
            ))
        }

        // 3. /Android/data/<package>/
        val extDataDir = File(Environment.getExternalStorageDirectory(), "Android/data/$packageName")
        if (extDataDir.exists()) {
            // Try direct access first
            val files = extDataDir.listFiles()
            if (files != null) {
                val dataSize = extDataDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                totalSize += dataSize
                entries.add(AppFileEntry(
                    path = extDataDir.absolutePath,
                    name = "External data",
                    size = dataSize,
                    isDirectory = true,
                    isAccessible = true
                ))
            } else if (shizukuReady) {
                // Shizuku access
                val shizukuSize = shizukuManager.calculateDirSize(extDataDir.absolutePath) ?: -1L
                if (shizukuSize > 0) totalSize += shizukuSize
                entries.add(AppFileEntry(
                    path = extDataDir.absolutePath,
                    name = "External data",
                    size = shizukuSize,
                    isDirectory = true,
                    isAccessible = true
                ))
            } else {
                entries.add(AppFileEntry(
                    path = extDataDir.absolutePath,
                    name = "External data",
                    size = -1,
                    isDirectory = true,
                    isAccessible = false
                ))
            }
        }

        // 4. /data/data/<package>/ — only with Shizuku
        val internalDataDir = "/data/data/$packageName"
        if (shizukuReady) {
            val exists = shizukuManager.exists(internalDataDir) ?: false
            if (exists) {
                val internalSize = shizukuManager.calculateDirSize(internalDataDir) ?: 0L
                if (internalSize > 0) totalSize += internalSize

                // Get subdirectory breakdown
                val shizukuFiles = shizukuManager.listFiles(internalDataDir)
                val children = shizukuFiles?.map { f ->
                    val childSize = if (f.isDirectory) {
                        shizukuManager.calculateDirSize(f.path) ?: 0L
                    } else f.size
                    AppFileEntry(
                        path = f.path,
                        name = f.name,
                        size = childSize,
                        isDirectory = f.isDirectory,
                        isAccessible = true
                    )
                } ?: emptyList()

                entries.add(AppFileEntry(
                    path = internalDataDir,
                    name = "Internal data",
                    size = internalSize,
                    isDirectory = true,
                    isAccessible = true,
                    children = children
                ))
            }
        } else {
            // Show as locked
            entries.add(AppFileEntry(
                path = internalDataDir,
                name = "Internal data",
                size = -1,
                isDirectory = true,
                isAccessible = false
            ))
        }

        EcosystemLogger.d(TAG, "getAppFiles: $packageName — ${entries.size} entries, total=${totalSize}")
        AppFilesInfo(totalSize, entries, shizukuReady)
    }
}
