package com.vamp.haron.domain.usecase

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import com.vamp.haron.domain.model.InstalledAppInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject

class GetInstalledAppsUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Suppress("DEPRECATION")
    operator fun invoke(): Flow<List<InstalledAppInfo>> = flow {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val result = mutableListOf<InstalledAppInfo>()

        for ((index, appInfo) in apps.withIndex()) {
            try {
                val pkgInfo = pm.getPackageInfo(appInfo.packageName, 0)
                val apkFile = File(appInfo.sourceDir)
                val icon = try {
                    val drawable = appInfo.loadIcon(pm)
                    val size = 48
                    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, size, size)
                    drawable.draw(canvas)
                    bmp
                } catch (_: Exception) {
                    null
                }

                result.add(
                    InstalledAppInfo(
                        appName = appInfo.loadLabel(pm).toString(),
                        packageName = appInfo.packageName,
                        versionName = pkgInfo.versionName,
                        versionCode = pkgInfo.longVersionCode,
                        apkSize = apkFile.length(),
                        installDate = pkgInfo.firstInstallTime,
                        lastUpdateDate = pkgInfo.lastUpdateTime,
                        icon = icon,
                        apkPath = appInfo.sourceDir,
                        isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    )
                )
            } catch (_: Exception) {
                // Skip apps we can't read
            }

            // Emit in batches of 20 for responsiveness
            if ((index + 1) % 20 == 0) {
                emit(result.toList())
            }
        }
        // Final emit
        emit(result.toList())
    }.flowOn(Dispatchers.IO)
}
