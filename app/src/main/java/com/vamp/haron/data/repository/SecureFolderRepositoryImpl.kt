package com.vamp.haron.data.repository

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.webkit.MimeTypeMap
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.common.crypto.StreamingCipher
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
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Защищённая папка: файлы шифруются сегментированным AES-256-GCM (сегменты ~1МБ).
 *
 * ## Почему сегментированное шифрование
 *
 * Стандартный javax.crypto CipherInputStream с AES-GCM **буферит весь файл в RAM**
 * для проверки MAC-тега (известный баг JDK-8298249). Файл 253МБ → OOM и GC-шторм.
 *
 * [StreamingCipher] разбивает файл на сегменты (~1МБ), каждый сегмент имеет свой
 * MAC-тег и шифруется/расшифровывается независимо. RAM = 1 сегмент (~1МБ) независимо
 * от размера файла.
 *
 * ## Ключи
 *
 * - **fileKey** — AES-256 ключ для шифрования файлов. Хранится в Android Keystore.
 * - **indexKey** — AES-256 ключ для шифрования индекса (маленький JSON). Хранится в Android Keystore.
 *
 * Оба ключа генерируются один раз, Android Keystore обеспечивает hardware-backed хранение.
 */
@Singleton
class SecureFolderRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SecureFolderRepository {

    private val secureDir = File(context.filesDir, HaronConstants.SECURE_DIR_NAME)
    private val indexFile = File(secureDir, HaronConstants.SECURE_INDEX_FILE)
    private val mutex = Mutex()

    @Volatile
    private var indexCache: MutableList<SecureFileEntry>? = null
    @Volatile
    private var protectedPathsCache: MutableSet<String>? = null

    companion object {
        private const val TAG = "${HaronConstants.TAG}/Secure"
        private const val FILE_KEY_ALIAS = "haron_secure_file_key"
        private const val INDEX_KEY_ALIAS = "haron_secure_index_key"
        private const val BUFFER_SIZE = 1024 * 1024 // 1MB — размер буфера для streaming
    }

    init {
        secureDir.mkdirs()
    }

    /** Lazy-инициализация AES-256 ключа для шифрования файлов (Android Keystore) */
    private val fileKey: SecretKey by lazy {
        getOrCreateKey(FILE_KEY_ALIAS)
    }

    /** Lazy-инициализация AES-256 ключа для шифрования индекса (Android Keystore) */
    private val indexKey: SecretKey by lazy {
        getOrCreateKey(INDEX_KEY_ALIAS)
    }

    private fun getOrCreateKey(alias: String): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        if (ks.containsAlias(alias)) {
            val existingKey = (ks.getEntry(alias, null) as KeyStore.SecretKeyEntry).secretKey
            // Test: old key may lack setRandomizedEncryptionRequired(false)
            if (testCallerIv(existingKey)) return existingKey
            EcosystemLogger.i(TAG, "Key $alias lacks caller-IV support, recreating")
            ks.deleteEntry(alias)
        }
        val keyGen = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        keyGen.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(false) // allow caller-provided IV for segmented encryption
                .build()
        )
        return keyGen.generateKey()
    }

    /** Quick test: can we encrypt with a caller-provided IV? */
    private fun testCallerIv(key: SecretKey): Boolean {
        return try {
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            val testNonce = ByteArray(12) { it.toByte() }
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, testNonce))
            true
        } catch (_: Exception) {
            false
        }
    }

    // ==================== PROTECT / UNPROTECT ====================

    override suspend fun protectFiles(
        paths: List<String>,
        onProgress: (Int, String) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val index = loadIndex().toMutableList()
                val protectedPaths = index.map { it.originalPath }.toMutableSet()
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

                    // Encrypt file with StreamingCipher (1MB segments, no OOM)
                    val outFile = File(secureDir, id)
                    encryptFile(file, outFile)

                    val ext = file.extension.lowercase()
                    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                        ?: "application/octet-stream"
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
                    file.delete()
                    count++
                }

                // Protect directory entries
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
                EcosystemLogger.i(TAG, "protectFiles: $count files encrypted")
                Result.success(count)
            } catch (e: Exception) {
                EcosystemLogger.e(TAG, "protectFiles error: ${e.message}")
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
                var count = 0

                for (id in ids) {
                    val entry = index.find { it.id == id } ?: continue
                    onProgress(count + 1, entry.originalName)

                    if (entry.isDirectory) {
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

                    val destFile = File(entry.originalPath)
                    destFile.parentFile?.mkdirs()
                    decryptFile(encFile, destFile)

                    encFile.delete()
                    index.removeAll { it.id == id }
                    count++
                }

                saveIndex(index)
                invalidateCache()
                EcosystemLogger.i(TAG, "unprotectFiles: $count files decrypted")
                Result.success(count)
            } catch (e: Exception) {
                EcosystemLogger.e(TAG, "unprotectFiles error: ${e.message}")
                Result.failure(e)
            }
        }
    }

    // ==================== QUERIES ====================

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

            val tempDir = File(context.cacheDir, HaronConstants.SECURE_TEMP_DIR)
            tempDir.mkdirs()
            val tempFile = File(tempDir, "${id}_${entry.originalName}")

            decryptFile(encFile, tempFile)

            Result.success(tempFile)
        } catch (e: Exception) {
            EcosystemLogger.e(TAG, "decryptToCache error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun decryptToBytes(id: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val encFile = File(secureDir, id)
            if (!encFile.exists()) return@withContext Result.failure(Exception("Encrypted file missing"))
            Result.success(StreamingCipher.decryptToByteArray(fileKey, encFile))
        } catch (e: Exception) {
            EcosystemLogger.e(TAG, "decryptToBytes error: ${e.message}")
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
                        File(secureDir, id).delete()
                    }
                    index.removeAll { it.id == id }
                    count++
                }

                saveIndex(index)
                invalidateCache()
                Result.success(count)
            } catch (e: Exception) {
                EcosystemLogger.e(TAG, "deleteFromSecureStorage error: ${e.message}")
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

    // ==================== STREAMING ENCRYPT/DECRYPT ====================

    /**
     * Шифрование файла через StreamingCipher (сегментированный AES-256-GCM).
     * RAM = ~1МБ (один сегмент) независимо от размера файла.
     */
    private fun encryptFile(src: File, dest: File) {
        StreamingCipher.encrypt(fileKey, src, dest)
    }

    /**
     * Расшифровка файла через StreamingCipher (сегментированный AES-256-GCM).
     * RAM = ~1МБ (один сегмент) независимо от размера файла.
     * Каждый сегмент проверяет свой MAC-тег — повреждение одного сегмента
     * не убивает весь файл (в отличие от стандартного AES-GCM).
     */
    private fun decryptFile(enc: File, dest: File) {
        StreamingCipher.decrypt(fileKey, enc, dest)
    }

    // ==================== INDEX (маленький JSON, не streaming) ====================

    private fun loadIndex(): List<SecureFileEntry> {
        if (!indexFile.exists()) return emptyList()
        return try {
            val encrypted = indexFile.readBytes()
            val json = String(StreamingCipher.decryptBytes(indexKey, encrypted), Charsets.UTF_8)
            parseIndexJson(json)
        } catch (e: Exception) {
            EcosystemLogger.e(TAG, "loadIndex error: ${e.message}")
            emptyList()
        }
    }

    private fun saveIndex(entries: List<SecureFileEntry>) {
        val json = entriesToJson(entries)
        val encrypted = StreamingCipher.encryptBytes(indexKey, json.toByteArray(Charsets.UTF_8))
        indexFile.writeBytes(encrypted)
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
}
