package com.vamp.haron.data.repository

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.webkit.MimeTypeMap
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.domain.model.SecureFileEntry
import com.vamp.haron.domain.repository.SecureFolderRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Protected folder: AES-256 encryption via Android Keystore.
 * Files encrypted at rest in app-private dir, decrypted on-the-fly for viewing.
 * Maintains index of protected entries (path, name, size, addedAt).
 */
@Singleton
class SecureFolderRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SecureFolderRepository {

    private val secureDir = File(context.filesDir, HaronConstants.SECURE_DIR_NAME)
    private val indexFile = File(secureDir, HaronConstants.SECURE_INDEX_FILE)
    private val mutex = Mutex()

    // In-memory index cache
    @Volatile
    private var indexCache: MutableList<SecureFileEntry>? = null
    @Volatile
    private var protectedPathsCache: MutableSet<String>? = null

    init {
        secureDir.mkdirs()
    }

    override suspend fun protectFiles(
        paths: List<String>,
        onProgress: (Int, String) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val index = loadIndex().toMutableList()
                val protectedPaths = index.map { it.originalPath }.toMutableSet()
                val key = getOrCreateKey()
                var count = 0

                // Expand directories recursively
                val filesToProtect = mutableListOf<File>()
                for (path in paths) {
                    val file = File(path)
                    if (!file.exists()) continue
                    if (file.isDirectory) {
                        file.walkTopDown().filter { it.isFile }.forEach { filesToProtect.add(it) }
                    } else {
                        filesToProtect.add(file)
                    }
                }

                for (file in filesToProtect) {
                    if (file.absolutePath in protectedPaths) continue
                    if (!file.exists() || !file.isFile) continue

                    val id = UUID.randomUUID().toString()
                    val name = file.name
                    onProgress(count + 1, name)

                    // Encrypt file (streaming — no full file in memory)
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    cipher.init(Cipher.ENCRYPT_MODE, key)
                    val iv = cipher.iv

                    val outFile = File(secureDir, id)
                    outFile.outputStream().use { fos ->
                        fos.write(iv)
                        CipherOutputStream(fos, cipher).use { cos ->
                            file.inputStream().use { fis ->
                                fis.copyTo(cos, bufferSize = 8192)
                            }
                        }
                    }

                    // Build entry
                    val ext = file.extension.lowercase()
                    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
                    val entry = SecureFileEntry(
                        id = id,
                        originalPath = file.absolutePath,
                        originalName = name,
                        originalSize = file.length(),
                        isDirectory = false,
                        mimeType = mimeType,
                        addedAt = System.currentTimeMillis()
                    )
                    index.add(entry)
                    protectedPaths.add(file.absolutePath)

                    // Delete original
                    file.delete()
                    count++
                }

                // After encrypting all files, also protect directories that were passed
                for (path in paths) {
                    val file = File(path)
                    if (file.isDirectory && file.absolutePath !in protectedPaths) {
                        val id = UUID.randomUUID().toString()
                        val entry = SecureFileEntry(
                            id = id,
                            originalPath = file.absolutePath,
                            originalName = file.name,
                            originalSize = 0L,
                            isDirectory = true,
                            mimeType = "",
                            addedAt = System.currentTimeMillis()
                        )
                        index.add(entry)
                        protectedPaths.add(file.absolutePath)
                        file.deleteRecursively()
                        count++
                    }
                }

                saveIndex(index)
                invalidateCache()
                Result.success(count)
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "protectFiles error: ${e.message}")
                Result.failure(e)
            }
        }
    }

    override suspend fun unprotectFiles(
        ids: List<String>,
        onProgress: (Int, String) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val index = loadIndex().toMutableList()
                val key = getOrCreateKey()
                var count = 0

                for (id in ids) {
                    val entry = index.find { it.id == id } ?: continue
                    onProgress(count + 1, entry.originalName)

                    if (entry.isDirectory) {
                        // Directory entry — just recreate the folder
                        File(entry.originalPath).mkdirs()
                        index.removeAll { it.id == id }
                        count++
                        continue
                    }

                    val encFile = File(secureDir, id)
                    if (!encFile.exists()) {
                        index.removeAll { it.id == id }
                        continue
                    }

                    // Decrypt (streaming — no full file in memory)
                    val destFile = File(entry.originalPath)
                    destFile.parentFile?.mkdirs()
                    encFile.inputStream().use { fis ->
                        val iv = ByteArray(GCM_IV_LENGTH)
                        fis.read(iv)
                        val cipher = Cipher.getInstance(TRANSFORMATION)
                        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
                        CipherInputStream(fis, cipher).use { cis ->
                            destFile.outputStream().use { fos ->
                                cis.copyTo(fos, bufferSize = 8192)
                            }
                        }
                    }

                    // Cleanup
                    encFile.delete()
                    index.removeAll { it.id == id }
                    count++
                }

                saveIndex(index)
                invalidateCache()
                Result.success(count)
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "unprotectFiles error: ${e.message}")
                Result.failure(e)
            }
        }
    }

    override suspend fun getProtectedEntriesForDir(dirPath: String): List<SecureFileEntry> =
        withContext(Dispatchers.IO) {
            getIndexCached().filter { entry ->
                val parent = File(entry.originalPath).parent ?: ""
                parent == dirPath
            }
        }

    override suspend fun getAllProtectedEntries(): List<SecureFileEntry> =
        withContext(Dispatchers.IO) { getIndexCached() }

    override suspend fun decryptToCache(id: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val index = getIndexCached()
            val entry = index.find { it.id == id }
                ?: return@withContext Result.failure(Exception("Entry not found"))

            val encFile = File(secureDir, id)
            if (!encFile.exists()) return@withContext Result.failure(Exception("Encrypted file missing"))

            val key = getOrCreateKey()

            val tempDir = File(context.cacheDir, HaronConstants.SECURE_TEMP_DIR)
            tempDir.mkdirs()
            val tempFile = File(tempDir, "${id}_${entry.originalName}")

            // Decrypt file (streaming — no full file in memory)
            encFile.inputStream().use { fis ->
                val iv = ByteArray(GCM_IV_LENGTH)
                fis.read(iv)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
                CipherInputStream(fis, cipher).use { cis ->
                    tempFile.outputStream().use { fos ->
                        cis.copyTo(fos, bufferSize = 8192)
                    }
                }
            }

            Result.success(tempFile)
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "decryptToCache error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun deleteFromSecureStorage(
        ids: List<String>,
        onProgress: (Int, String) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val index = loadIndex().toMutableList()
                var count = 0

                for (id in ids) {
                    val entry = index.find { it.id == id } ?: continue
                    onProgress(count + 1, entry.originalName)

                    if (!entry.isDirectory) {
                        // Delete encrypted file
                        val encFile = File(secureDir, id)
                        encFile.delete()
                    }
                    index.removeAll { it.id == id }
                    count++
                }

                saveIndex(index)
                invalidateCache()
                Result.success(count)
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "deleteFromSecureStorage error: ${e.message}")
                Result.failure(e)
            }
        }
    }

    override suspend fun getSecureFolderSize(): Long = withContext(Dispatchers.IO) {
        getIndexCached().sumOf { it.originalSize }
    }

    override fun isFileProtected(path: String): Boolean =
        getProtectedPaths().contains(path)

    override fun hasProtectedDescendants(path: String): Boolean {
        val prefix = "$path/"
        return getProtectedPaths().any { it.startsWith(prefix) }
    }

    override fun getProtectedPaths(): Set<String> {
        protectedPathsCache?.let { return it }
        val paths = try {
            loadIndex().map { it.originalPath }.toMutableSet()
        } catch (_: Exception) {
            mutableSetOf()
        }
        protectedPathsCache = paths
        return paths
    }

    // --- Keystore ---

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val existingKey = keyStore.getKey(HaronConstants.SECURE_KEYSTORE_ALIAS, null)
        if (existingKey != null) return existingKey as SecretKey

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGen.init(
            KeyGenParameterSpec.Builder(
                HaronConstants.SECURE_KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGen.generateKey()
    }

    // --- Index ---

    private fun loadIndex(): List<SecureFileEntry> {
        if (!indexFile.exists()) return emptyList()
        return try {
            val key = getOrCreateKey()
            val allBytes = indexFile.readBytes()
            if (allBytes.size < GCM_IV_LENGTH) return emptyList()

            val iv = allBytes.copyOfRange(0, GCM_IV_LENGTH)
            val ciphertext = allBytes.copyOfRange(GCM_IV_LENGTH, allBytes.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val json = String(cipher.doFinal(ciphertext), Charsets.UTF_8)

            parseIndexJson(json)
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "loadIndex error: ${e.message}")
            emptyList()
        }
    }

    private fun saveIndex(entries: List<SecureFileEntry>) {
        val json = entriesToJson(entries)
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(json.toByteArray(Charsets.UTF_8))

        indexFile.outputStream().use { out ->
            out.write(iv)
            out.write(encrypted)
        }
    }

    private fun parseIndexJson(json: String): List<SecureFileEntry> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            SecureFileEntry(
                id = obj.getString("id"),
                originalPath = obj.getString("originalPath"),
                originalName = obj.getString("originalName"),
                originalSize = obj.getLong("originalSize"),
                isDirectory = obj.optBoolean("isDirectory", false),
                mimeType = obj.optString("mimeType", "application/octet-stream"),
                addedAt = obj.optLong("addedAt", 0L)
            )
        }
    }

    private fun entriesToJson(entries: List<SecureFileEntry>): String {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id)
                put("originalPath", e.originalPath)
                put("originalName", e.originalName)
                put("originalSize", e.originalSize)
                put("isDirectory", e.isDirectory)
                put("mimeType", e.mimeType)
                put("addedAt", e.addedAt)
            })
        }
        return arr.toString()
    }

    private fun getIndexCached(): List<SecureFileEntry> {
        indexCache?.let { return it }
        val entries = loadIndex().toMutableList()
        indexCache = entries
        return entries
    }

    private fun invalidateCache() {
        indexCache = null
        protectedPathsCache = null
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }
}
