package com.vamp.haron.common.util

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Environment
import com.vamp.core.logger.EcosystemLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipFile

private const val TAG = "XapkInstaller"

/**
 * Handles XAPK/APKS file installation:
 * - Parses manifest.json
 * - Extracts APK(s) to temp dir
 * - Copies OBB files to /Android/obb/<package>/
 * - Installs single APK via Intent or split APKs via PackageInstaller session
 */
object XapkInstaller {

    data class XapkManifest(
        val packageName: String,
        val name: String,
        val versionCode: Long,
        val versionName: String,
        val apkFiles: List<String>,       // paths inside ZIP
        val obbFiles: List<ObbEntry>
    )

    data class ObbEntry(
        val filePath: String,       // path inside ZIP
        val installPath: String     // target path on device (relative to external storage)
    )

    data class InstallResult(
        val success: Boolean,
        val isSplitApk: Boolean,
        val singleApkFile: File? = null,   // for single APK — caller handles install Intent
        val packageName: String = "",
        val error: String? = null,
        val obbCopied: Int = 0
    )

    /**
     * Check if file is XAPK/APKS format.
     */
    fun isXapk(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.endsWith(".xapk") || lower.endsWith(".apks")
    }

    /**
     * Parse manifest.json from XAPK file.
     * Returns null if not a valid XAPK.
     */
    suspend fun parseManifest(xapkFile: File): XapkManifest? = withContext(Dispatchers.IO) {
        try {
            val zip = ZipFile(xapkFile)
            val manifestEntry = zip.getEntry("manifest.json")
            if (manifestEntry == null) {
                // No manifest — try treating as APKS (just split APKs in a ZIP)
                return@withContext parseApksFormat(zip, xapkFile)
            }

            val jsonStr = zip.getInputStream(manifestEntry).bufferedReader().readText()
            val json = JSONObject(jsonStr)

            val packageName = json.getString("package_name")
            val name = json.optString("name", packageName)
            val versionCode = json.optLong("version_code", 0)
            val versionName = json.optString("version_name", "?")

            // Collect APK files
            val apkFiles = mutableListOf<String>()
            zip.entries().asSequence().forEach { entry ->
                if (!entry.isDirectory && entry.name.lowercase().endsWith(".apk")) {
                    apkFiles.add(entry.name)
                }
            }

            // Collect OBB files from expansions array
            val obbFiles = mutableListOf<ObbEntry>()
            val expansions = json.optJSONArray("expansions")
            if (expansions != null) {
                for (i in 0 until expansions.length()) {
                    val exp = expansions.getJSONObject(i)
                    val file = exp.getString("file")
                    val installPath = exp.optString("install_path", file)
                    obbFiles.add(ObbEntry(file, installPath))
                }
            }

            // Also check for OBB files not listed in manifest
            zip.entries().asSequence().forEach { entry ->
                if (!entry.isDirectory && entry.name.lowercase().endsWith(".obb")) {
                    val alreadyListed = obbFiles.any { it.filePath == entry.name }
                    if (!alreadyListed) {
                        obbFiles.add(ObbEntry(entry.name, entry.name))
                    }
                }
            }

            zip.close()

            if (apkFiles.isEmpty()) {
                EcosystemLogger.e(TAG, "parseManifest: no APK files found in XAPK")
                return@withContext null
            }

            EcosystemLogger.d(TAG, "parseManifest: pkg=$packageName, apks=${apkFiles.size}, obbs=${obbFiles.size}")
            XapkManifest(packageName, name, versionCode, versionName, apkFiles, obbFiles)
        } catch (e: Exception) {
            EcosystemLogger.e(TAG, "parseManifest: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /**
     * Parse APKS format (just a ZIP with split APKs, no manifest).
     */
    private fun parseApksFormat(zip: ZipFile, file: File): XapkManifest? {
        val apkFiles = mutableListOf<String>()
        zip.entries().asSequence().forEach { entry ->
            if (!entry.isDirectory && entry.name.lowercase().endsWith(".apk")) {
                apkFiles.add(entry.name)
            }
        }
        zip.close()

        if (apkFiles.isEmpty()) return null

        // Try to get package name from base.apk
        val baseName = file.nameWithoutExtension
        EcosystemLogger.d(TAG, "parseApksFormat: ${apkFiles.size} APKs found, name=$baseName")
        return XapkManifest(
            packageName = baseName,
            name = baseName,
            versionCode = 0,
            versionName = "?",
            apkFiles = apkFiles,
            obbFiles = emptyList()
        )
    }

    /**
     * Extract and install XAPK.
     * - Single APK: extracts to tempDir, returns file for caller to install via Intent
     * - Split APKs: installs via PackageInstaller session API
     * - OBB files: copies to /Android/obb/<package>/
     */
    suspend fun install(
        context: Context,
        xapkFile: File,
        manifest: XapkManifest
    ): InstallResult = withContext(Dispatchers.IO) {
        try {
            val tempDir = File(context.cacheDir, "xapk_install")
            tempDir.mkdirs()
            // Clean old temp files
            tempDir.listFiles()?.forEach { it.deleteRecursively() }

            val zip = ZipFile(xapkFile)

            // 1. Copy OBB files
            var obbCopied = 0
            for (obb in manifest.obbFiles) {
                val entry = zip.getEntry(obb.filePath) ?: continue
                val targetPath = obb.installPath.let {
                    if (it.startsWith("Android/")) it
                    else "Android/obb/${manifest.packageName}/${it.substringAfterLast('/')}"
                }
                val targetFile = File(Environment.getExternalStorageDirectory(), targetPath)
                targetFile.parentFile?.mkdirs()
                EcosystemLogger.d(TAG, "install: copying OBB ${obb.filePath} → ${targetFile.absolutePath} (${entry.size} bytes)")
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                obbCopied++
                EcosystemLogger.d(TAG, "install: OBB copied ($obbCopied/${manifest.obbFiles.size})")
            }

            // 2. Extract APK files
            val extractedApks = mutableListOf<File>()
            for (apkPath in manifest.apkFiles) {
                val entry = zip.getEntry(apkPath) ?: continue
                val outFile = File(tempDir, apkPath.substringAfterLast('/'))
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
                extractedApks.add(outFile)
                EcosystemLogger.d(TAG, "install: extracted ${outFile.name} (${outFile.length()} bytes)")
            }

            zip.close()

            if (extractedApks.isEmpty()) {
                return@withContext InstallResult(false, false, error = "No APK files extracted")
            }

            // 3. Install
            if (extractedApks.size == 1) {
                // Single APK — return file, caller installs via Intent
                EcosystemLogger.d(TAG, "install: single APK, returning for Intent install")
                return@withContext InstallResult(
                    success = true,
                    isSplitApk = false,
                    singleApkFile = extractedApks[0],
                    packageName = manifest.packageName,
                    obbCopied = obbCopied
                )
            }

            // Split APKs — install via PackageInstaller session
            EcosystemLogger.d(TAG, "install: split APK (${extractedApks.size} parts), using PackageInstaller session")
            installSplitApks(context, extractedApks)

            InstallResult(
                success = true,
                isSplitApk = true,
                packageName = manifest.packageName,
                obbCopied = obbCopied
            )
        } catch (e: Exception) {
            EcosystemLogger.e(TAG, "install: ${e.javaClass.simpleName}: ${e.message}")
            InstallResult(false, false, error = e.message ?: "Unknown error")
        }
    }

    /**
     * Install split APKs via PackageInstaller session API.
     */
    private fun installSplitApks(context: Context, apkFiles: List<File>) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply {
            setInstallReason(android.content.pm.PackageManager.INSTALL_REASON_USER)
            val totalSize = apkFiles.sumOf { it.length() }
            setSize(totalSize)
            // Ensure system shows confirmation dialog
            if (Build.VERSION.SDK_INT >= 31) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }

        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)

        try {
            for (apkFile in apkFiles) {
                session.openWrite(apkFile.name, 0, apkFile.length()).use { out ->
                    FileInputStream(apkFile).use { input ->
                        input.copyTo(out)
                    }
                    session.fsync(out)
                }
                EcosystemLogger.d(TAG, "installSplitApks: wrote ${apkFile.name} to session")
            }

            // Register runtime receiver to handle install status
            val action = "com.vamp.haron.XAPK_INSTALL_$sessionId"
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val status = intent?.getIntExtra(PackageInstaller.EXTRA_STATUS, -1) ?: -1
                    val message = intent?.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
                    EcosystemLogger.d(TAG, "installReceiver: status=$status, message=$message")

                    when (status) {
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            // System needs user confirmation — launch the confirm intent
                            val confirmIntent = if (Build.VERSION.SDK_INT >= 33) {
                                intent?.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent?.getParcelableExtra(Intent.EXTRA_INTENT)
                            }
                            if (confirmIntent != null) {
                                confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(confirmIntent)
                                EcosystemLogger.d(TAG, "installReceiver: launched confirm dialog")
                            }
                        }
                        PackageInstaller.STATUS_SUCCESS -> {
                            EcosystemLogger.d(TAG, "installReceiver: install SUCCESS")
                            try { context.unregisterReceiver(this) } catch (_: Exception) {}
                        }
                        else -> {
                            EcosystemLogger.e(TAG, "installReceiver: install FAILED status=$status: $message")
                            try { context.unregisterReceiver(this) } catch (_: Exception) {}
                        }
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, IntentFilter(action))
            }

            val intent = Intent(action).apply { setPackage(context.packageName) }
            val pendingIntent = PendingIntent.getBroadcast(
                context, sessionId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(pendingIntent.intentSender)
            EcosystemLogger.d(TAG, "installSplitApks: session $sessionId committed (${apkFiles.size} APKs)")
        } catch (e: Exception) {
            session.abandon()
            throw e
        }
    }
}
