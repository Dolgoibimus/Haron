package com.vamp.haron.data.backup

import android.content.Context
import android.os.Build
import android.os.Environment
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.data.cloud.CloudTokenStore
import com.vamp.haron.data.datastore.HaronPreferences
import com.vamp.haron.data.datastore.VideoPositionStore
import com.vamp.haron.data.db.dao.BookDao
import com.vamp.haron.data.db.dao.ReadingPositionDao
import com.vamp.haron.data.db.entity.BookEntity
import com.vamp.haron.data.db.entity.ReadingPositionEntity
import com.vamp.haron.data.ftp.FtpCredential
import com.vamp.haron.data.ftp.FtpCredentialStore
import com.vamp.haron.data.smb.SmbCredential
import com.vamp.haron.data.smb.SmbCredentialStore
import com.vamp.haron.data.terminal.SshCredential
import com.vamp.haron.data.terminal.SshCredentialStore
import com.vamp.haron.data.webdav.WebDavCredential
import com.vamp.haron.data.webdav.WebDavCredentialStore
import com.vamp.haron.domain.model.SecureFileEntry
import com.vamp.haron.domain.model.backup.BackupInfo
import com.vamp.haron.domain.model.backup.BackupManifest
import com.vamp.haron.domain.model.backup.BackupResult
import com.vamp.haron.domain.model.backup.BackupSection
import com.vamp.haron.domain.model.backup.RestoreWarning
import com.vamp.haron.domain.model.backup.WarningType
import com.vamp.haron.domain.repository.SecureFolderRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core of the Haron backup system.
 *
 * What is backed up:
 * - SETTINGS: all SharedPreferences (UI settings, sorting, file tags, favorites, bookmarks, gestures)
 * - CREDENTIALS: network credentials FTP/SMB/SSH/WebDAV + cloud tokens (Yandex/GDrive/Dropbox)
 * - BOOKS: book library (Room: book + reading_position) and video positions
 * - SECURE_FOLDER: protected folder files (decrypted during backup)
 *
 * Why credentials are decrypted during backup:
 * Credentials are stored encrypted with an Android Keystore key (AES-256-GCM). This key is bound
 * to the hardware security chip (TEE) of the specific device and cannot be transferred to another
 * device. During backup, credentials are decrypted with the current device Keystore key and placed
 * in a ZIP protected by the user password. On restore, encrypted with a new Keystore key.
 *
 * ZIP archive structure:
 * - manifest.json: backup metadata (version, sections, device)
 * - settings.json: SharedPreferences as JSON
 * - credentials/ftp.json, smb.json, ssh.json, webdav.json, cloud_tokens.json
 * - books/library.json, reading_positions.json, video_positions.json
 * - secure_folder/index.json: protected file metadata
 * - secure_folder/files/id.ext: decrypted files (NO_COMPRESSION)
 *
 * Why secure folder files use NO_COMPRESSION:
 * Protected folder files can be large (APK, video, archives). Many are already compressed,
 * so re-compression provides no size benefit but doubles RAM usage.
 *
 * Why readZipEntries skips secure_folder/files/:
 * Binary files cannot be read as String without OOM (90MB APK = 268MB String in UTF-16).
 * They are handled in a separate ZIP pass with byte-level extraction.
 *
 * Float/Long type preservation issue with JSON:
 * JSON stores all numbers as Double. To preserve Float and Long types in SharedPreferences,
 * settings.json includes __float_keys__ and __long_keys__ arrays.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: HaronPreferences,
    private val ftpCredentialStore: FtpCredentialStore,
    private val smbCredentialStore: SmbCredentialStore,
    private val sshCredentialStore: SshCredentialStore,
    private val webDavCredentialStore: WebDavCredentialStore,
    private val cloudTokenStore: CloudTokenStore,
    private val bookDao: BookDao,
    private val readingPositionDao: ReadingPositionDao,
    private val secureFolderRepository: SecureFolderRepository
) {

    companion object {
        private const val TAG = HaronConstants.TAG + "/Backup"
        private const val BACKUP_DIR = "HaronBackup"
        const val ENCRYPTED_EXT = ".hbk"
        const val PLAIN_EXT = ".zip"
    }

    fun getBackupDir(): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            BACKUP_DIR
        )
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun getSecureFolderEntries(): List<SecureFileEntry> {
        return secureFolderRepository.getAllProtectedEntries()
    }

    suspend fun createBackup(
        sections: Set<BackupSection>,
        password: String? = null,
        secureFileIds: Set<String>? = null,
        onProgress: ((Int, Int) -> Unit)? = null
    ): Result<File> {
        val tempZip = File(context.cacheDir, "backup_temp_${System.currentTimeMillis()}.zip")
        try {
            EcosystemLogger.d(TAG, "createBackup: sections=$sections, encrypted=${password != null}")

            val secureEntries = if (sections.contains(BackupSection.SECURE_FOLDER)) {
                val all = secureFolderRepository.getAllProtectedEntries()
                if (secureFileIds != null) all.filter { it.id in secureFileIds } else all
            } else {
                emptyList()
            }
            val secureFileCount = secureEntries.count { !it.isDirectory }
            val nonSecureSections = sections.filter { it != BackupSection.SECURE_FOLDER }
            val totalSteps = nonSecureSections.size + secureFileCount + 1
            var currentStep = 0

            ZipOutputStream(BufferedOutputStream(FileOutputStream(tempZip))).use { zos ->
                if (sections.contains(BackupSection.SETTINGS)) {
                    writeZipEntry(zos, "settings.json", exportSettings())
                    currentStep++
                    onProgress?.invoke(currentStep, totalSteps)
                    EcosystemLogger.d(TAG, "createBackup: settings exported")
                }

                if (sections.contains(BackupSection.CREDENTIALS)) {
                    writeZipEntry(zos, "credentials/ftp.json", exportFtpCredentials())
                    writeZipEntry(zos, "credentials/smb.json", exportSmbCredentials())
                    writeZipEntry(zos, "credentials/ssh.json", exportSshCredentials())
                    writeZipEntry(zos, "credentials/webdav.json", exportWebDavCredentials())
                    writeZipEntry(zos, "credentials/cloud_tokens.json", exportCloudTokens())
                    currentStep++
                    onProgress?.invoke(currentStep, totalSteps)
                    EcosystemLogger.d(TAG, "createBackup: credentials exported")
                }

                if (sections.contains(BackupSection.BOOKS)) {
                    writeZipEntry(zos, "books/library.json", exportBooks())
                    writeZipEntry(zos, "books/reading_positions.json", exportReadingPositions())
                    writeZipEntry(zos, "books/video_positions.json", exportVideoPositions())
                    currentStep++
                    onProgress?.invoke(currentStep, totalSteps)
                    EcosystemLogger.d(TAG, "createBackup: books exported")
                }

                if (sections.contains(BackupSection.SECURE_FOLDER)) {
                    exportSecureFolder(zos, secureFileIds) { step, total, name ->
                        currentStep++
                        onProgress?.invoke(currentStep, totalSteps)
                        EcosystemLogger.d(TAG, "createBackup: secure file $step/$total: $name")
                    }
                }

                val manifest = BackupManifest(
                    appVersion = getAppVersion(),
                    createdAt = System.currentTimeMillis(),
                    sections = sections.toList(),
                    isEncrypted = password != null,
                    deviceName = Build.MODEL,
                    androidVersion = Build.VERSION.RELEASE
                )
                writeZipEntry(zos, BackupManifest.FILE_NAME, manifestToJson(manifest))
            }

            val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val ext = if (password != null) ENCRYPTED_EXT else PLAIN_EXT
            val backupFile = File(getBackupDir(), "haron_backup_$dateStr$ext")

            if (password != null) {
                FileInputStream(tempZip).buffered().use { input ->
                    FileOutputStream(backupFile).buffered().use { output ->
                        BackupCrypto.encrypt(input, output, password)
                    }
                }
                EcosystemLogger.d(TAG, "createBackup: encrypted to ${backupFile.name}")
            } else {
                tempZip.copyTo(backupFile, overwrite = true)
                EcosystemLogger.d(TAG, "createBackup: copied to ${backupFile.name}")
            }

            onProgress?.invoke(totalSteps, totalSteps)
            EcosystemLogger.i(TAG, "createBackup: done, size=${backupFile.length()} bytes")
            return Result.success(backupFile)
        } catch (e: Exception) {
            EcosystemLogger.e(TAG, "createBackup: failed: ${e.message}")
            return Result.failure(e)
        } finally {
            tempZip.delete()
        }
    }

    suspend fun restoreBackup(
        backupFile: File,
        password: String? = null,
        sections: Set<BackupSection>? = null,
        onProgress: ((Int, Int, String) -> Unit)? = null
    ): BackupResult {
        EcosystemLogger.d(TAG, "restoreBackup: file=${backupFile.name}, encrypted=${password != null}")

        val zipFile: File
        val tempDecrypted: File?

        if (password != null) {
            onProgress?.invoke(0, 100, "Decrypting...")
            tempDecrypted = File(context.cacheDir, "backup_decrypt_${System.currentTimeMillis()}.zip")
            try {
                FileInputStream(backupFile).buffered().use { input ->
                    FileOutputStream(tempDecrypted).buffered().use { output ->
                        BackupCrypto.decrypt(input, output, password)
                    }
                }
            } catch (e: Exception) {
                tempDecrypted.delete()
                EcosystemLogger.e(TAG, "restoreBackup: decryption failed: ${e.message}")
                return BackupResult.Error("Wrong password")
            }
            zipFile = tempDecrypted
        } else {
            zipFile = backupFile
            tempDecrypted = null
        }

        try {
            onProgress?.invoke(5, 100, "Reading backup...")
            val entries = readZipEntries(zipFile)
            val manifestJson = entries[BackupManifest.FILE_NAME]
                ?: return BackupResult.Error("No manifest found in backup")
            val manifest = jsonToManifest(manifestJson)
            val sectionsToRestore = sections ?: manifest.sections.toSet()
            val warnings = mutableListOf<RestoreWarning>()

            EcosystemLogger.d(TAG, "restoreBackup: manifest v${manifest.formatVersion}, sections=$sectionsToRestore")

            // Granular progress: each sub-step counts
            val hasSettings = sectionsToRestore.contains(BackupSection.SETTINGS)
            val hasCreds = sectionsToRestore.contains(BackupSection.CREDENTIALS)
            val hasBooks = sectionsToRestore.contains(BackupSection.BOOKS)
            val hasSecure = sectionsToRestore.contains(BackupSection.SECURE_FOLDER)
            val totalSteps = (if (hasSettings) 1 else 0) +
                (if (hasCreds) 5 else 0) +  // ftp+smb+ssh+webdav+cloud
                (if (hasBooks) 3 else 0) +   // library+positions+video
                (if (hasSecure) 3 else 0)    // clear+extract+encrypt
            var step = 0
            fun progress(name: String) { onProgress?.invoke(step, totalSteps, name); step++ }

            if (hasSettings) {
                progress("Settings")
                val json = entries["settings.json"]
                if (json != null) {
                    warnings.addAll(restoreSettings(json))
                    EcosystemLogger.d(TAG, "restoreBackup: settings restored")
                }
            }

            if (hasCreds) {
                progress("FTP")
                entries["credentials/ftp.json"]?.let { restoreFtpCredentials(it) }
                progress("SMB")
                entries["credentials/smb.json"]?.let { restoreSmbCredentials(it) }
                progress("SSH")
                entries["credentials/ssh.json"]?.let { restoreSshCredentials(it) }
                progress("WebDAV")
                entries["credentials/webdav.json"]?.let { restoreWebDavCredentials(it) }
                progress("Cloud")
                (entries["credentials/cloud_tokens.json"] ?: entries["credentials/cloud.json"])?.let {
                    warnings.addAll(restoreCloudTokens(it))
                }
                EcosystemLogger.d(TAG, "restoreBackup: credentials restored")
            }

            if (hasBooks) {
                progress("Books")
                (entries["books/library.json"] ?: entries["database/books.json"])?.let {
                    warnings.addAll(restoreBooks(it))
                }
                progress("Reading positions")
                (entries["books/reading_positions.json"] ?: entries["database/reading_positions.json"])?.let {
                    restoreReadingPositions(it)
                }
                progress("Video positions")
                (entries["books/video_positions.json"] ?: entries["database/video_positions.json"])?.let {
                    restoreVideoPositions(it)
                }
                EcosystemLogger.d(TAG, "restoreBackup: books restored")
            }

            if (hasSecure) {
                progress("Clearing secure folder")
                // Очищаем существующие защищённые файлы перед восстановлением,
                // чтобы избежать дубликатов при повторном восстановлении
                val existingEntries = secureFolderRepository.getAllProtectedEntries()
                if (existingEntries.isNotEmpty()) {
                    secureFolderRepository.deleteFromSecureStorage(
                        existingEntries.map { it.id }
                    ) { _, _ -> }
                    EcosystemLogger.d(TAG, "restoreBackup: cleared ${existingEntries.size} existing secure entries")
                }
                progress("Secure folder")
                restoreSecureFolder(zipFile)
                progress("Done")
                EcosystemLogger.d(TAG, "restoreBackup: secure folder restored")
            }

            onProgress?.invoke(totalSteps, totalSteps, "Done")

            EcosystemLogger.i(TAG, "restoreBackup: done, warnings=${warnings.size}")
            return if (warnings.isEmpty()) {
                BackupResult.Success("Backup restored successfully")
            } else {
                BackupResult.PartialSuccess("Backup restored with warnings", warnings)
            }
        } catch (e: Exception) {
            EcosystemLogger.e(TAG, "restoreBackup: failed: ${e.message}")
            return BackupResult.Error("Restore failed: ${e.message}")
        } finally {
            tempDecrypted?.delete()
        }
    }

    fun readBackupInfo(file: File): BackupInfo {
        return try {
            val entries = readZipEntries(file, onlyManifest = true)
            val manifestJson = entries[BackupManifest.FILE_NAME]
            val manifest = if (manifestJson != null) jsonToManifest(manifestJson) else null
            BackupInfo(file = file, manifest = manifest, sizeBytes = file.length())
        } catch (e: Exception) {
            EcosystemLogger.e(TAG, "readBackupInfo: failed for ${file.name}: ${e.message}")
            BackupInfo(file = file, manifest = null, sizeBytes = file.length())
        }
    }

    fun listBackups(): List<BackupInfo> {
        val dir = getBackupDir()
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.extension == PLAIN_EXT.removePrefix(".") || it.extension == ENCRYPTED_EXT.removePrefix(".") }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                if (file.extension == ENCRYPTED_EXT.removePrefix(".")) {
                    BackupInfo(file = file, manifest = null, sizeBytes = file.length())
                } else {
                    readBackupInfo(file)
                }
            }
            ?: emptyList()
    }

    private fun exportSettings(): String {
        val prefs = context.getSharedPreferences(HaronConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val json = JSONObject()
        val floatKeys = JSONArray()
        val longKeys = JSONArray()

        for ((key, value) in prefs.all) {
            when (value) {
                is Boolean -> json.put(key, value)
                is Int -> json.put(key, value)
                is Long -> {
                    json.put(key, value)
                    longKeys.put(key)
                }
                is Float -> {
                    json.put(key, value.toDouble())
                    floatKeys.put(key)
                }
                is String -> json.put(key, value)
                is Set<*> -> json.put(key, JSONArray(value))
            }
        }

        json.put("__float_keys__", floatKeys)
        json.put("__long_keys__", longKeys)
        return json.toString(2)
    }

    private fun exportFtpCredentials(): String {
        val arr = JSONArray()
        for (cred in ftpCredentialStore.listAll()) {
            val obj = JSONObject()
            obj.put("host", cred.host)
            obj.put("port", cred.port)
            obj.put("username", cred.username)
            obj.put("password", cred.password)
            obj.put("useFtps", cred.useFtps)
            obj.put("displayName", cred.displayName)
            arr.put(obj)
        }
        return arr.toString(2)
    }

    private fun exportSmbCredentials(): String {
        val arr = JSONArray()
        for (cred in smbCredentialStore.listAll()) {
            val obj = JSONObject()
            obj.put("host", cred.host)
            obj.put("username", cred.username)
            obj.put("password", cred.password)
            obj.put("domain", cred.domain)
            obj.put("displayName", cred.displayName)
            arr.put(obj)
        }
        return arr.toString(2)
    }

    private fun exportSshCredentials(): String {
        val arr = JSONArray()
        for (cred in sshCredentialStore.listAll()) {
            val obj = JSONObject()
            obj.put("user", cred.user)
            obj.put("host", cred.host)
            obj.put("port", cred.port)
            obj.put("password", cred.password)
            arr.put(obj)
        }
        return arr.toString(2)
    }

    private fun exportWebDavCredentials(): String {
        val arr = JSONArray()
        for (cred in webDavCredentialStore.listAll()) {
            val obj = JSONObject()
            obj.put("url", cred.url)
            obj.put("username", cred.username)
            obj.put("password", cred.password)
            obj.put("displayName", cred.displayName)
            arr.put(obj)
        }
        return arr.toString(2)
    }

    private fun exportCloudTokens(): String {
        val json = JSONObject()
        for ((key, tokens) in cloudTokenStore.getAllAccounts()) {
            val obj = JSONObject()
            obj.put("accessToken", tokens.accessToken)
            obj.put("refreshToken", tokens.refreshToken ?: "")
            obj.put("email", tokens.email)
            obj.put("displayName", tokens.displayName)
            json.put(key, obj)
        }
        return json.toString(2)
    }

    private suspend fun exportBooks(): String {
        val arr = JSONArray()
        for (book in bookDao.getAll()) {
            val obj = JSONObject()
            obj.put("filePath", book.filePath)
            obj.put("title", book.title)
            obj.put("author", book.author)
            obj.put("format", book.format)
            obj.put("fileSize", book.fileSize)
            obj.put("coverPath", book.coverPath ?: "")
            obj.put("annotation", book.annotation ?: "")
            obj.put("language", book.language ?: "")
            obj.put("series", book.series ?: "")
            obj.put("seriesNumber", book.seriesNumber ?: -1)
            obj.put("progress", book.progress.toDouble())
            obj.put("lastRead", book.lastRead)
            obj.put("addedAt", book.addedAt)
            obj.put("scanFolder", book.scanFolder)
            arr.put(obj)
        }
        return arr.toString(2)
    }

    private suspend fun exportReadingPositions(): String {
        val arr = JSONArray()
        for (pos in readingPositionDao.getAll()) {
            val obj = JSONObject()
            obj.put("filePath", pos.filePath)
            obj.put("position", pos.position)
            obj.put("positionExtra", pos.positionExtra)
            obj.put("lastOpened", pos.lastOpened)
            arr.put(obj)
        }
        return arr.toString(2)
    }

    private fun exportVideoPositions(): String {
        val prefs = context.getSharedPreferences(HaronConstants.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("video_positions", "{}") ?: "{}"
    }

    private fun restoreSettings(json: String): List<RestoreWarning> {
        val warnings = mutableListOf<RestoreWarning>()
        val obj = JSONObject(json)

        val floatKeys = mutableSetOf<String>()
        val longKeys = mutableSetOf<String>()
        obj.optJSONArray("__float_keys__")?.let { arr ->
            for (i in 0 until arr.length()) floatKeys.add(arr.getString(i))
        }
        obj.optJSONArray("__long_keys__")?.let { arr ->
            for (i in 0 until arr.length()) longKeys.add(arr.getString(i))
        }

        val prefs = context.getSharedPreferences(HaronConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val missingFiles = mutableListOf<String>()

        for (key in obj.keys()) {
            if (key == "__float_keys__" || key == "__long_keys__") continue
            val value = obj.get(key)
            when {
                key in floatKeys && value is Number -> editor.putFloat(key, value.toFloat())
                key in longKeys && value is Number -> editor.putLong(key, value.toLong())
                value is Boolean -> editor.putBoolean(key, value)
                value is Int -> editor.putInt(key, value)
                value is Number -> {
                    val d = value.toDouble()
                    if (d == d.toLong().toDouble() && d >= Int.MIN_VALUE.toDouble() && d <= Int.MAX_VALUE.toDouble()) {
                        editor.putInt(key, d.toInt())
                    } else {
                        editor.putFloat(key, d.toFloat())
                    }
                }
                value is String -> {
                    editor.putString(key, value)
                    if ((key.startsWith("tag_") || key.startsWith("fav_") || key.startsWith("bookmark_"))
                        && value.startsWith("/") && !File(value).exists()
                    ) {
                        missingFiles.add(value)
                    }
                }
                value is JSONArray -> {
                    val set = mutableSetOf<String>()
                    for (i in 0 until value.length()) {
                        val item = value.getString(i)
                        set.add(item)
                        if (item.startsWith("/") && !File(item).exists()) {
                            missingFiles.add(item)
                        }
                    }
                    editor.putStringSet(key, set)
                }
            }
        }

        editor.apply()
        EcosystemLogger.d(TAG, "restoreSettings: applied ${obj.length() - 2} keys")

        if (missingFiles.isNotEmpty()) {
            warnings.add(
                RestoreWarning(
                    section = BackupSection.SETTINGS,
                    type = WarningType.MISSING_FILES,
                    details = "Some referenced files not found on device (${missingFiles.size} paths)",
                    affectedPaths = missingFiles
                )
            )
        }
        return warnings
    }

    private fun restoreFtpCredentials(json: String) {
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val cred = FtpCredential(
                host = obj.getString("host"),
                port = obj.getInt("port"),
                username = obj.getString("username"),
                password = obj.getString("password"),
                useFtps = obj.optBoolean("useFtps", false),
                displayName = obj.optString("displayName", obj.getString("host"))
            )
            ftpCredentialStore.save(cred)
        }
        EcosystemLogger.d(TAG, "restoreFtpCredentials: restored ${arr.length()} entries")
    }

    private fun restoreSmbCredentials(json: String) {
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val host = obj.getString("host")
            val cred = SmbCredential(
                host = host,
                username = obj.getString("username"),
                password = obj.getString("password"),
                domain = obj.optString("domain", ""),
                displayName = obj.optString("displayName", "")
            )
            smbCredentialStore.save(host, cred)
        }
        EcosystemLogger.d(TAG, "restoreSmbCredentials: restored ${arr.length()} entries")
    }

    private fun restoreSshCredentials(json: String) {
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val cred = SshCredential(
                user = obj.getString("user"),
                host = obj.getString("host"),
                port = obj.getInt("port"),
                password = obj.getString("password")
            )
            sshCredentialStore.save(cred)
        }
        EcosystemLogger.d(TAG, "restoreSshCredentials: restored ${arr.length()} entries")
    }

    private fun restoreWebDavCredentials(json: String) {
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val cred = WebDavCredential(
                url = obj.getString("url"),
                username = obj.getString("username"),
                password = obj.getString("password"),
                displayName = obj.optString("displayName", obj.getString("url"))
            )
            webDavCredentialStore.save(cred)
        }
        EcosystemLogger.d(TAG, "restoreWebDavCredentials: restored ${arr.length()} entries")
    }

    private fun restoreCloudTokens(json: String): List<RestoreWarning> {
        val obj = JSONObject(json)
        for (key in obj.keys()) {
            val tokenObj = obj.getJSONObject(key)
            val tokens = CloudTokenStore.CloudTokens(
                accessToken = tokenObj.getString("accessToken"),
                refreshToken = tokenObj.optString("refreshToken", "").ifEmpty { null },
                email = tokenObj.optString("email", ""),
                displayName = tokenObj.optString("displayName", "")
            )
            cloudTokenStore.saveByKey(key, tokens)
        }
        EcosystemLogger.d(TAG, "restoreCloudTokens: restored ${obj.length()} accounts")
        return emptyList()
    }

    private suspend fun restoreBooks(json: String): List<RestoreWarning> {
        val warnings = mutableListOf<RestoreWarning>()
        val arr = JSONArray(json)
        val missingPaths = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val filePath = obj.getString("filePath")
            val book = BookEntity(
                filePath = filePath,
                title = obj.optString("title", ""),
                author = obj.optString("author", ""),
                format = obj.optString("format", ""),
                fileSize = obj.optLong("fileSize", 0L),
                coverPath = obj.optString("coverPath", "").ifEmpty { null },
                annotation = obj.optString("annotation", "").ifEmpty { null },
                language = obj.optString("language", "").ifEmpty { null },
                series = obj.optString("series", "").ifEmpty { null },
                seriesNumber = obj.optInt("seriesNumber", -1).let { if (it == -1) null else it },
                progress = obj.optDouble("progress", 0.0).toFloat(),
                lastRead = obj.optLong("lastRead", 0L),
                addedAt = obj.optLong("addedAt", System.currentTimeMillis()),
                scanFolder = obj.optString("scanFolder", "")
            )
            bookDao.upsert(book)
            if (!File(filePath).exists()) {
                missingPaths.add(filePath)
            }
        }
        EcosystemLogger.d(TAG, "restoreBooks: restored ${arr.length()} books, missing=${missingPaths.size}")
        if (missingPaths.isNotEmpty()) {
            warnings.add(
                RestoreWarning(
                    section = BackupSection.BOOKS,
                    type = WarningType.MISSING_BOOKS,
                    details = "Some book files not found on device (${missingPaths.size} books)",
                    affectedPaths = missingPaths
                )
            )
        }
        return warnings
    }

    private suspend fun restoreReadingPositions(json: String) {
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val entity = ReadingPositionEntity(
                filePath = obj.getString("filePath"),
                position = obj.optInt("position", 0),
                positionExtra = obj.optLong("positionExtra", 0L),
                lastOpened = obj.optLong("lastOpened", System.currentTimeMillis())
            )
            readingPositionDao.upsert(entity)
        }
        EcosystemLogger.d(TAG, "restoreReadingPositions: restored ${arr.length()} entries")
    }

    private fun restoreVideoPositions(json: String) {
        val prefs = context.getSharedPreferences(HaronConstants.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("video_positions", json).apply()
        EcosystemLogger.d(TAG, "restoreVideoPositions: restored")
    }
    private suspend fun exportSecureFolder(
        zos: ZipOutputStream,
        selectedIds: Set<String>?,
        onProgress: ((Int, Int, String) -> Unit)?
    ) {
        val allEntries = secureFolderRepository.getAllProtectedEntries()
        val entries = if (selectedIds != null) {
            allEntries.filter { it.id in selectedIds }
        } else {
            allEntries
        }

        val indexArr = JSONArray()
        for (entry in entries) {
            val obj = JSONObject()
            obj.put("id", entry.id)
            obj.put("originalPath", entry.originalPath)
            obj.put("originalName", entry.originalName)
            obj.put("originalSize", entry.originalSize)
            obj.put("isDirectory", entry.isDirectory)
            obj.put("mimeType", entry.mimeType)
            obj.put("addedAt", entry.addedAt)
            indexArr.put(obj)
        }
        writeZipEntry(zos, "secure_folder/index.json", indexArr.toString(2))

        val files = entries.filter { !it.isDirectory }
        var fileStep = 0
        for (entry in files) {
            fileStep++
            try {
                val decrypted = secureFolderRepository.decryptToCache(entry.id).getOrThrow()
                try {
                    val zipEntryName = "secure_folder/files/${entry.originalName}"
                    val zipEntry = ZipEntry(zipEntryName)
                    zipEntry.method = ZipEntry.STORED
                    zipEntry.size = decrypted.length()
                    zipEntry.compressedSize = decrypted.length()
                    val crc = CRC32()
                    BufferedInputStream(FileInputStream(decrypted)).use { bis ->
                        val buf = ByteArray(8192)
                        var len: Int
                        while (bis.read(buf).also { len = it } != -1) {
                            crc.update(buf, 0, len)
                        }
                    }
                    zipEntry.crc = crc.value

                    zos.setLevel(Deflater.NO_COMPRESSION)
                    zos.putNextEntry(zipEntry)
                    BufferedInputStream(FileInputStream(decrypted)).use { bis ->
                        val buf = ByteArray(8192)
                        var len: Int
                        while (bis.read(buf).also { len = it } != -1) {
                            zos.write(buf, 0, len)
                        }
                    }
                    zos.closeEntry()
                    zos.setLevel(Deflater.DEFAULT_COMPRESSION)
                } finally {
                    decrypted.delete()
                }
                onProgress?.invoke(fileStep, files.size, entry.originalName)
            } catch (e: Exception) {
                EcosystemLogger.e(TAG, "exportSecureFolder: failed to export ${entry.id}: ${e.message}")
            }
        }
        EcosystemLogger.d(TAG, "exportSecureFolder: exported ${files.size} files, ${entries.size} total entries")
    }

    private suspend fun restoreSecureFolder(zipFile: File) = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
        val tempDir = File(context.cacheDir, "secure_restore_${System.currentTimeMillis()}")
        try {
            tempDir.mkdirs()

            var indexJson: String? = null
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == "secure_folder/index.json") {
                        indexJson = zis.bufferedReader().readText()
                        break
                    }
                    entry = zis.nextEntry
                }
            }

            if (indexJson == null) {
                EcosystemLogger.e(TAG, "restoreSecureFolder: no index.json found")
                return@withContext
            }

            // Parse index to map fileName → originalPath
            val indexArr = JSONArray(indexJson)
            val nameToOriginalPath = mutableMapOf<String, String>()
            for (i in 0 until indexArr.length()) {
                val obj = indexArr.getJSONObject(i)
                nameToOriginalPath[obj.getString("originalName")] = obj.getString("originalPath")
            }

            // Extract files to their ORIGINAL paths (so protectFiles records correct originalPath)
            val extractedPaths = mutableListOf<String>()
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name.startsWith("secure_folder/files/") && !entry.isDirectory) {
                        val fileName = entry.name.substringAfterLast('/')
                        val originalPath = nameToOriginalPath[fileName]
                        // Restore to original path if known, otherwise to tempDir
                        val outFile = if (originalPath != null) {
                            File(originalPath).also { it.parentFile?.mkdirs() }
                        } else {
                            File(tempDir, fileName)
                        }
                        BufferedOutputStream(FileOutputStream(outFile)).use { bos ->
                            val buf = ByteArray(65536)
                            var len: Int
                            while (zis.read(buf).also { len = it } != -1) {
                                bos.write(buf, 0, len)
                            }
                        }
                        extractedPaths.add(outFile.absolutePath)
                    }
                    entry = zis.nextEntry
                }
            }

            if (extractedPaths.isNotEmpty()) {
                secureFolderRepository.protectFiles(extractedPaths) { step, name ->
                    EcosystemLogger.d(TAG, "restoreSecureFolder: re-encrypting $step: $name")
                }
            }
            EcosystemLogger.d(TAG, "restoreSecureFolder: restored ${extractedPaths.size} files")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun writeZipEntry(zos: ZipOutputStream, name: String, content: String) {
        val entry = ZipEntry(name)
        zos.putNextEntry(entry)
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    /**
     * Reads all text entries from a ZIP file into a map.
     * CRITICAL: Skips entries starting with "secure_folder/files/" as they are binary files
     * and reading them as String causes OOM (90MB APK = 268MB String in UTF-16).
     */
    private fun readZipEntries(file: File, onlyManifest: Boolean = false): Map<String, String> {
        val result = mutableMapOf<String, String>()
        ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && !entry.name.startsWith("secure_folder/files/")) {
                    val content = zis.bufferedReader().readText()
                    result[entry.name] = content
                    if (onlyManifest && entry.name == BackupManifest.FILE_NAME) {
                        break
                    }
                }
                entry = zis.nextEntry
            }
        }
        return result
    }

    private fun manifestToJson(manifest: BackupManifest): String {
        val json = JSONObject()
        json.put("formatVersion", manifest.formatVersion)
        json.put("appVersion", manifest.appVersion)
        json.put("createdAt", manifest.createdAt)
        json.put("sections", JSONArray(manifest.sections.map { it.name }))
        json.put("isEncrypted", manifest.isEncrypted)
        json.put("deviceName", manifest.deviceName)
        json.put("androidVersion", manifest.androidVersion)
        return json.toString(2)
    }

    private fun jsonToManifest(json: String): BackupManifest {
        val obj = JSONObject(json)
        val sectionsArr = obj.getJSONArray("sections")
        val sections = mutableListOf<BackupSection>()
        for (i in 0 until sectionsArr.length()) {
            try {
                sections.add(BackupSection.valueOf(sectionsArr.getString(i)))
            } catch (_: IllegalArgumentException) {
                // Unknown section from newer version — skip
            }
        }
        return BackupManifest(
            formatVersion = obj.optInt("formatVersion", 1),
            appVersion = obj.optString("appVersion", ""),
            createdAt = obj.optLong("createdAt", 0L),
            sections = sections,
            isEncrypted = obj.optBoolean("isEncrypted", false),
            deviceName = obj.optString("deviceName", ""),
            androidVersion = obj.optString("androidVersion", "")
        )
    }

    private fun getAppVersion(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }
}
