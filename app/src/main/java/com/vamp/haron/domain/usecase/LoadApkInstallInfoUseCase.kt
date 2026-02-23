package com.vamp.haron.domain.usecase

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import com.vamp.haron.R
import com.vamp.haron.domain.model.ApkInstallInfo
import com.vamp.haron.domain.model.ApkPermissionInfo
import com.vamp.haron.domain.model.FileEntry
import com.vamp.haron.domain.model.SignatureStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class LoadApkInstallInfoUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Suppress("DEPRECATION")
    suspend operator fun invoke(entry: FileEntry): Result<ApkInstallInfo> = withContext(Dispatchers.IO) {
        val file = if (entry.isContentUri) copyToTemp(entry) else File(entry.path)
        try {
            val pm = context.packageManager
            val flags = PackageManager.GET_PERMISSIONS or
                    PackageManager.GET_SIGNING_CERTIFICATES
            val info = pm.getPackageArchiveInfo(file.absolutePath, flags)
                ?: return@withContext Result.failure(Exception(context.getString(R.string.apk_parse_error)))

            info.applicationInfo?.sourceDir = file.absolutePath
            info.applicationInfo?.publicSourceDir = file.absolutePath

            val appName = info.applicationInfo?.loadLabel(pm)?.toString()
            val icon = loadIcon(info, pm)
            val permissions = loadPermissions(info, pm)

            // Check if already installed
            val installedInfo = try {
                pm.getPackageInfo(info.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }

            val isAlreadyInstalled = installedInfo != null
            val installedVersionName = installedInfo?.versionName
            val installedVersionCode = installedInfo?.longVersionCode
            val isUpgrade = isAlreadyInstalled && installedVersionCode != null && info.longVersionCode > installedVersionCode
            val isDowngrade = isAlreadyInstalled && installedVersionCode != null && info.longVersionCode < installedVersionCode

            val signatureStatus = if (!isAlreadyInstalled) {
                SignatureStatus.NOT_INSTALLED
            } else {
                compareSignatures(info, installedInfo!!)
            }

            Result.success(
                ApkInstallInfo(
                    appName = appName,
                    packageName = info.packageName,
                    versionName = info.versionName,
                    versionCode = info.longVersionCode,
                    icon = icon,
                    fileSize = entry.size,
                    filePath = entry.path,
                    permissions = permissions,
                    isAlreadyInstalled = isAlreadyInstalled,
                    installedVersionName = installedVersionName,
                    installedVersionCode = installedVersionCode,
                    isUpgrade = isUpgrade,
                    isDowngrade = isDowngrade,
                    signatureStatus = signatureStatus
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            if (entry.isContentUri) file.delete()
        }
    }

    private fun loadIcon(info: PackageInfo, pm: PackageManager): Bitmap? {
        return try {
            info.applicationInfo?.loadIcon(pm)?.let { drawable ->
                val bmp = Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            }
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun loadPermissions(info: PackageInfo, pm: PackageManager): List<ApkPermissionInfo> {
        val requested = info.requestedPermissions ?: return emptyList()
        return requested.mapNotNull { permName ->
            try {
                val permInfo = pm.getPermissionInfo(permName, 0)
                val label = permInfo.loadLabel(pm).toString()
                val isDangerous = permInfo.protection == PermissionInfo.PROTECTION_DANGEROUS
                val sysDesc = permInfo.loadDescription(pm)?.toString()
                val description = permissionDescriptions[permName]
                    ?: sysDesc?.takeIf { it.isNotBlank() && it != label }
                    ?: generateFallbackDescription(permName)
                ApkPermissionInfo(
                    name = permName,
                    label = label,
                    description = description,
                    isDangerous = isDangerous
                )
            } catch (_: PackageManager.NameNotFoundException) {
                ApkPermissionInfo(
                    name = permName,
                    label = permName.substringAfterLast('.'),
                    description = permissionDescriptions[permName]
                        ?: generateFallbackDescription(permName),
                    isDangerous = false
                )
            }
        }
    }

    private fun generateFallbackDescription(permName: String): String {
        val short = permName.substringAfterLast('.').lowercase()
        val keywords = mapOf(
            "location" to context.getString(R.string.perm_cat_location),
            "camera" to context.getString(R.string.perm_cat_camera),
            "audio" to context.getString(R.string.perm_cat_audio),
            "sms" to context.getString(R.string.perm_cat_sms),
            "phone" to context.getString(R.string.perm_cat_phone),
            "call" to context.getString(R.string.perm_cat_call),
            "contact" to context.getString(R.string.perm_cat_contact),
            "storage" to context.getString(R.string.perm_cat_storage),
            "media" to context.getString(R.string.perm_cat_media),
            "bluetooth" to context.getString(R.string.perm_cat_bluetooth),
            "wifi" to context.getString(R.string.perm_cat_wifi),
            "network" to context.getString(R.string.perm_cat_network),
            "calendar" to context.getString(R.string.perm_cat_calendar),
            "sensor" to context.getString(R.string.perm_cat_sensor),
            "notification" to context.getString(R.string.perm_cat_notification),
            "alarm" to context.getString(R.string.perm_cat_alarm),
            "biometric" to context.getString(R.string.perm_cat_biometric),
            "nfc" to context.getString(R.string.perm_cat_nfc),
            "install" to context.getString(R.string.perm_cat_install),
            "billing" to context.getString(R.string.perm_cat_billing),
            "vibrat" to context.getString(R.string.perm_cat_vibrate),
            "boot" to context.getString(R.string.perm_cat_boot),
            "wake" to context.getString(R.string.perm_cat_wake),
            "foreground" to context.getString(R.string.perm_cat_foreground),
            "overlay" to context.getString(R.string.perm_cat_overlay),
            "shortcut" to context.getString(R.string.perm_cat_shortcut),
            "fingerprint" to context.getString(R.string.perm_cat_fingerprint)
        )
        for ((key, desc) in keywords) {
            if (short.contains(key)) return desc
        }
        return context.getString(R.string.perm_cat_default)
    }

    private val permissionDescriptions = mapOf(
        // --- Internet & network ---
        "android.permission.INTERNET" to context.getString(R.string.perm_internet),
        "android.permission.ACCESS_NETWORK_STATE" to context.getString(R.string.perm_access_network_state),
        "android.permission.ACCESS_WIFI_STATE" to context.getString(R.string.perm_access_wifi_state),
        "android.permission.CHANGE_WIFI_STATE" to context.getString(R.string.perm_change_wifi_state),
        "android.permission.CHANGE_NETWORK_STATE" to context.getString(R.string.perm_change_network_state),
        "android.permission.NEARBY_WIFI_DEVICES" to context.getString(R.string.perm_nearby_wifi_devices),

        // --- Camera & microphone ---
        "android.permission.CAMERA" to context.getString(R.string.perm_camera),
        "android.permission.RECORD_AUDIO" to context.getString(R.string.perm_record_audio),

        // --- Contacts ---
        "android.permission.READ_CONTACTS" to context.getString(R.string.perm_read_contacts),
        "android.permission.WRITE_CONTACTS" to context.getString(R.string.perm_write_contacts),
        "android.permission.GET_ACCOUNTS" to context.getString(R.string.perm_get_accounts),

        // --- Phone & calls ---
        "android.permission.READ_PHONE_STATE" to context.getString(R.string.perm_read_phone_state),
        "android.permission.READ_PHONE_NUMBERS" to context.getString(R.string.perm_read_phone_numbers),
        "android.permission.CALL_PHONE" to context.getString(R.string.perm_call_phone),
        "android.permission.ANSWER_PHONE_CALLS" to context.getString(R.string.perm_answer_phone_calls),
        "android.permission.READ_CALL_LOG" to context.getString(R.string.perm_read_call_log),
        "android.permission.WRITE_CALL_LOG" to context.getString(R.string.perm_write_call_log),
        "android.permission.PROCESS_OUTGOING_CALLS" to context.getString(R.string.perm_process_outgoing_calls),

        // --- SMS ---
        "android.permission.SEND_SMS" to context.getString(R.string.perm_send_sms),
        "android.permission.READ_SMS" to context.getString(R.string.perm_read_sms),
        "android.permission.RECEIVE_SMS" to context.getString(R.string.perm_receive_sms),
        "android.permission.RECEIVE_MMS" to context.getString(R.string.perm_receive_mms),

        // --- Location ---
        "android.permission.ACCESS_FINE_LOCATION" to context.getString(R.string.perm_access_fine_location),
        "android.permission.ACCESS_COARSE_LOCATION" to context.getString(R.string.perm_access_coarse_location),
        "android.permission.ACCESS_BACKGROUND_LOCATION" to context.getString(R.string.perm_access_background_location),

        // --- Storage & files ---
        "android.permission.READ_EXTERNAL_STORAGE" to context.getString(R.string.perm_read_external_storage),
        "android.permission.WRITE_EXTERNAL_STORAGE" to context.getString(R.string.perm_write_external_storage),
        "android.permission.MANAGE_EXTERNAL_STORAGE" to context.getString(R.string.perm_manage_external_storage),
        "android.permission.READ_MEDIA_IMAGES" to context.getString(R.string.perm_read_media_images),
        "android.permission.READ_MEDIA_VIDEO" to context.getString(R.string.perm_read_media_video),
        "android.permission.READ_MEDIA_AUDIO" to context.getString(R.string.perm_read_media_audio),

        // --- Notifications ---
        "android.permission.POST_NOTIFICATIONS" to context.getString(R.string.perm_post_notifications),
        "android.permission.ACCESS_NOTIFICATION_POLICY" to context.getString(R.string.perm_access_notification_policy),

        // --- Bluetooth ---
        "android.permission.BLUETOOTH" to context.getString(R.string.perm_bluetooth),
        "android.permission.BLUETOOTH_ADMIN" to context.getString(R.string.perm_bluetooth_admin),
        "android.permission.BLUETOOTH_CONNECT" to context.getString(R.string.perm_bluetooth_connect),
        "android.permission.BLUETOOTH_SCAN" to context.getString(R.string.perm_bluetooth_scan),
        "android.permission.BLUETOOTH_ADVERTISE" to context.getString(R.string.perm_bluetooth_advertise),

        // --- Calendar ---
        "android.permission.READ_CALENDAR" to context.getString(R.string.perm_read_calendar),
        "android.permission.WRITE_CALENDAR" to context.getString(R.string.perm_write_calendar),

        // --- Sensors & activity ---
        "android.permission.BODY_SENSORS" to context.getString(R.string.perm_body_sensors),
        "android.permission.BODY_SENSORS_BACKGROUND" to context.getString(R.string.perm_body_sensors_background),
        "android.permission.ACTIVITY_RECOGNITION" to context.getString(R.string.perm_activity_recognition),
        "android.permission.HIGH_SAMPLING_RATE_SENSORS" to context.getString(R.string.perm_high_sampling_rate_sensors),

        // --- Biometrics ---
        "android.permission.USE_BIOMETRIC" to context.getString(R.string.perm_use_biometric),
        "android.permission.USE_FINGERPRINT" to context.getString(R.string.perm_use_fingerprint),

        // --- System ---
        "android.permission.VIBRATE" to context.getString(R.string.perm_vibrate),
        "android.permission.WAKE_LOCK" to context.getString(R.string.perm_wake_lock),
        "android.permission.RECEIVE_BOOT_COMPLETED" to context.getString(R.string.perm_receive_boot_completed),
        "android.permission.FOREGROUND_SERVICE" to context.getString(R.string.perm_foreground_service),
        "android.permission.FOREGROUND_SERVICE_DATA_SYNC" to context.getString(R.string.perm_foreground_service_data_sync),
        "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" to context.getString(R.string.perm_foreground_service_media_playback),
        "android.permission.FOREGROUND_SERVICE_LOCATION" to context.getString(R.string.perm_foreground_service_location),
        "android.permission.FOREGROUND_SERVICE_CAMERA" to context.getString(R.string.perm_foreground_service_camera),
        "android.permission.FOREGROUND_SERVICE_MICROPHONE" to context.getString(R.string.perm_foreground_service_microphone),
        "android.permission.REQUEST_INSTALL_PACKAGES" to context.getString(R.string.perm_request_install_packages),
        "android.permission.REQUEST_DELETE_PACKAGES" to context.getString(R.string.perm_request_delete_packages),
        "android.permission.SYSTEM_ALERT_WINDOW" to context.getString(R.string.perm_system_alert_window),
        "android.permission.QUERY_ALL_PACKAGES" to context.getString(R.string.perm_query_all_packages),
        "android.permission.SCHEDULE_EXACT_ALARM" to context.getString(R.string.perm_schedule_exact_alarm),
        "android.permission.USE_EXACT_ALARM" to context.getString(R.string.perm_use_exact_alarm),
        "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" to context.getString(R.string.perm_request_ignore_battery_optimizations),
        "android.permission.PACKAGE_USAGE_STATS" to context.getString(R.string.perm_package_usage_stats),

        // --- NFC ---
        "android.permission.NFC" to context.getString(R.string.perm_nfc),
        "android.permission.NFC_TRANSACTION_EVENT" to context.getString(R.string.perm_nfc_transaction_event),

        // --- Shortcuts & UI ---
        "android.permission.INSTALL_SHORTCUT" to context.getString(R.string.perm_install_shortcut),

        // --- Purchases ---
        "com.android.vending.BILLING" to context.getString(R.string.perm_billing),
        "com.android.vending.CHECK_LICENSE" to context.getString(R.string.perm_check_license)
    )

    private fun compareSignatures(apkInfo: PackageInfo, installedInfo: PackageInfo): SignatureStatus {
        return try {
            val apkSigningInfo = apkInfo.signingInfo
            val installedSigningInfo = installedInfo.signingInfo
            if (apkSigningInfo == null || installedSigningInfo == null) {
                return SignatureStatus.UNKNOWN
            }

            val apkSignatures = if (apkSigningInfo.hasMultipleSigners()) {
                apkSigningInfo.apkContentsSigners
            } else {
                apkSigningInfo.signingCertificateHistory
            }

            val installedSignatures = if (installedSigningInfo.hasMultipleSigners()) {
                installedSigningInfo.apkContentsSigners
            } else {
                installedSigningInfo.signingCertificateHistory
            }

            if (apkSignatures == null || installedSignatures == null) {
                return SignatureStatus.UNKNOWN
            }

            val apkSet = apkSignatures.map { it.toByteArray().contentHashCode() }.toSet()
            val installedSet = installedSignatures.map { it.toByteArray().contentHashCode() }.toSet()

            if (apkSet == installedSet) SignatureStatus.MATCH else SignatureStatus.MISMATCH
        } catch (_: Exception) {
            SignatureStatus.UNKNOWN
        }
    }

    private fun copyToTemp(entry: FileEntry): File {
        val tempFile = File(context.cacheDir, "apk_install_${System.currentTimeMillis()}.apk")
        context.contentResolver.openInputStream(Uri.parse(entry.path))?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Cannot open content URI")
        return tempFile
    }
}
