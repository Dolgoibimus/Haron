package com.vamp.haron.domain.model

import android.graphics.Bitmap

data class InstalledAppInfo(
    val appName: String,
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val apkSize: Long,
    val installDate: Long,
    val lastUpdateDate: Long,
    val icon: Bitmap?,
    val apkPath: String,
    val isSystemApp: Boolean
)
