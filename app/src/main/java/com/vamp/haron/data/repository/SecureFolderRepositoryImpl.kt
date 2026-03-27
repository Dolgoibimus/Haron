package com.vamp.haron.data.repository

import android.content.Context
import android.webkit.MimeTypeMap
import com.google.crypto.tink.Aead
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import com.google.crypto.tink.streamingaead.StreamingAeadKeyTemplates
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
import java.nio.ByteBuffer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Защищённая папка: файлы шифруются Tink Streaming AEAD (AES-256-GCM-HKDF, сегменты ~1МБ).
 *
 * ## Почему Tink Streaming AEAD
 *
 * Стандартный javax.crypto CipherInputStream с AES-GCM **буферит весь файл в RAM**
 * для проверки MAC-тега (известный баг JDK-8298249). Файл 253МБ → OOM и GC-шторм.
 *
 * Tink Streaming AEAD разбивает файл на сегменты (~1МБ), каждый сегмент имеет свой
 * MAC-тег и шифруется/расшифровывается независимо. RAM = 1 сегмент (~1МБ) независимо
 * от размера файла.
 *
 * ## Формат зашифрованного файла (Tink internal)
 *
 * ```
 * [Header: 1b header_size + salt(32b) + nonce_prefix(7b)]
 * [Segment_0: AES-GCM(plaintext_chunk_0, nonce=prefix||0||0) + 16b tag]
 * [Segment_1: AES-GCM(plaintext_chunk_1, nonce=prefix||1||0) + 16b tag]
 * ...
 * [Segment_N: AES-GCM(plaintext_chunk_N, nonce=prefix||N||1) + 16b tag]  // last=1
 * ```
 *
 * ## Ключи
 *
 * - **Streaming AEAD keyset** — для шифрования файлов. Хранится в SharedPreferences
 *   `__tink_secure_folder_streaming`, зашифрован Android Keystore master key.
 * - **AEAD keyset** — для шифрования индекса (маленький JSON). Хранится в SharedPreferences
 *   `__tink_secure_folder_aead`, зашифрован Android Keystore master key.
 *
 * Оба keyset генерируются один раз, Tink сам управляет ключами через AndroidKeysetManager.
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
        private const val STREAMING_KEYSET_PREF = "__tink_secure_folder_streaming"
        private const val STREAMING_KEYSET_NAME = "secure_folder_streaming_keyset"
        private const val AEAD_KEYSET_PREF = "__tink_secure_folder_aead"
        private const val AEAD_KEYSET_NAME = "secure_folder_aead_keyset"
        private const val KEYSTORE_URI = "android-keystore://haron_tink_master_key"
        private const val BUFFER_SIZE = 1024 * 1024 // 1MB — размер буфера для streaming
        /** AAD (Associated Authenticated Data) для файлов — пустой, не нужен */
        private val FILE_AAD = ByteArray(0)
    }

    init {
        AeadConfig.register()
        StreamingAeadConfig.register()
        secureDir.mkdirs()
    }

    /** Lazy-инициализация Streaming AEAD для шифрования файлов */
    private val streamingAead: StreamingAead by lazy {
        val handle = AndroidKeysetManager.Builder()
            .withSharedPref(context, STREAMING_KEYSET_NAME, STREAMING_KEYSET_PREF)
            .withKeyTemplate(StreamingAeadKeyTemplates.AES256_GCM_HKDF_1MB)
            .withMasterKeyUri(KEYSTORE_URI)
            .build()
            .keysetHandle
        handle.getPrimitive(StreamingAead::class.java)
    }

    /** Lazy-инициализация AEAD для шифрования индекса (маленький JSON) */
    private val indexAead: Aead by lazy {
        val handle = AndroidKeysetManager.Builder()
            .withSharedPref(context, AEAD_KEYSET_NAME, AEAD_KEYSET_PREF)
            .withKeyTemplate(AeadKeyTemplates.AES256_GCM)
            .withMasterKeyUri(KEYSTORE_URI)
            .build()
            .keysetHandle
        handle.getPrimitive(Aead::class.java)
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

                    // Encrypt file with Tink Streaming AEAD (1MB segments, no OOM)
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

    // ==================== TINK STREAMING ENCRYPT/DECRYPT ====================

    /**
     * Шифрование файла через Tink Streaming AEAD.
     * RAM = ~1МБ (один сегмент) независимо от размера файла.
     */
    private fun encryptFile(src: File, dest: File) {
        src.inputStream().channel.use { inChannel ->
            dest.outputStream().channel.use { outChannel ->
                streamingAead.newEncryptingChannel(outChannel, FILE_AAD).use { encChannel ->
                    val buf = ByteBuffer.allocate(BUFFER_SIZE)
                    while (inChannel.read(buf) != -1) {
                        buf.flip()
                        encChannel.write(buf)
                        buf.clear()
                    }
                }
            }
        }
    }

    /**
     * Расшифровка файла через Tink Streaming AEAD.
     * RAM = ~1МБ (один сегмент) независимо от размера файла.
     * Каждый сегмент проверяет свой MAC-тег — повреждение одного сегмента
     * не убивает весь файл (в отличие от стандартного AES-GCM).
     */
    private fun decryptFile(enc: File, dest: File) {
        enc.inputStream().channel.use { inChannel ->
            streamingAead.newDecryptingChannel(inChannel, FILE_AAD).use { decChannel ->
                dest.outputStream().channel.use { outChannel ->
                    val buf = ByteBuffer.allocate(BUFFER_SIZE)
                    while (decChannel.read(buf) != -1) {
                        buf.flip()
                        outChannel.write(buf)
                        buf.clear()
                    }
                }
            }
        }
    }

    // ==================== INDEX (AEAD — маленький JSON, не streaming) ====================

    private fun loadIndex(): List<SecureFileEntry> {
        if (!indexFile.exists()) return emptyList()
        return try {
            val encrypted = indexFile.readBytes()
            val json = String(indexAead.decrypt(encrypted, FILE_AAD), Charsets.UTF_8)
            parseIndexJson(json)
        } catch (e: Exception) {
            EcosystemLogger.e(TAG, "loadIndex error: ${e.message}")
            emptyList()
        }
    }

    private fun saveIndex(entries: List<SecureFileEntry>) {
        val json = entriesToJson(entries)
        val encrypted = indexAead.encrypt(json.toByteArray(Charsets.UTF_8), FILE_AAD)
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
