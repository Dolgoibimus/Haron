package com.vamp.haron.domain.model

import android.graphics.Bitmap

data class ApkInstallInfo(
    val appName: String?,
    val packageName: String?,
    val versionName: String?,
    val versionCode: Long,
    val icon: Bitmap?,
    val fileSize: Long,
    val filePath: String,
    val permissions: List<ApkPermissionInfo>,
    val isAlreadyInstalled: Boolean,
    val installedVersionName: String?,
    val installedVersionCode: Long?,
    val isUpgrade: Boolean,
    val isDowngrade: Boolean,
    val signatureStatus: SignatureStatus
)

data class ApkPermissionInfo(
    val name: String,
    val label: String,
    val description: String,
    val isDangerous: Boolean
)

enum class SignatureStatus {
    NOT_INSTALLED,
    MATCH,
    MISMATCH,
    UNKNOWN
}
