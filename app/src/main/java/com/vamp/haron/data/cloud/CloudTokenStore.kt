package com.vamp.haron.data.cloud

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.domain.model.CloudProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted token storage for cloud providers.
 * Multi-account: keys are "scheme:email" (e.g. "gdrive:alice@gmail.com").
 * Pattern: AES-256-GCM via Android Keystore.
 */
@Singleton
class CloudTokenStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val KEYSTORE_ALIAS = "haron_cloud_tokens"
        private const val FILE_NAME = "cloud_tokens.dat"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_LENGTH = 12
    }

    data class CloudTokens(
        val accessToken: String,
        val refreshToken: String? = null,
        val email: String = "",
        val displayName: String = ""
    )

    private val file: File
        get() = File(context.filesDir, FILE_NAME)

    fun saveByKey(key: String, tokens: CloudTokens) {
        val map = loadAll().toMutableMap()
        map[key] = tokens
        writeEncrypted(serializeMap(map))
        EcosystemLogger.d(HaronConstants.TAG, "CloudTokenStore: saved tokens for $key")
    }

    fun loadByKey(key: String): CloudTokens? {
        return loadAll()[key]
    }

    fun removeByKey(key: String) {
        val map = loadAll().toMutableMap()
        map.remove(key)
        if (map.isEmpty()) {
            file.delete()
        } else {
            writeEncrypted(serializeMap(map))
        }
        EcosystemLogger.d(HaronConstants.TAG, "CloudTokenStore: removed tokens for $key")
    }

    /** Get all stored accounts as map: key → tokens */
    fun getAllAccounts(): Map<String, CloudTokens> {
        return loadAll()
    }

    /** Get all account keys for a specific provider type */
    fun getAccountIds(provider: CloudProvider): List<String> {
        return loadAll().keys.filter { it.startsWith("${provider.scheme}:") }
    }

    /** Get list of connected provider types (for backward compat) */
    fun getConnectedProviders(): List<CloudProvider> {
        val keys = loadAll().keys
        return CloudProvider.entries.filter { provider ->
            keys.any { it.startsWith("${provider.scheme}:") }
        }
    }

    private fun loadAll(): Map<String, CloudTokens> {
        if (!file.exists()) return emptyMap()
        return try {
            val decrypted = readDecrypted()
            val map = deserializeMap(decrypted)
            // Migrate old format: keys without ':' → "scheme:email"
            val needsMigration = map.keys.any { !it.contains(':') }
            if (needsMigration) {
                val migrated = migrateOldKeys(map)
                writeEncrypted(serializeMap(migrated))
                EcosystemLogger.d(HaronConstants.TAG, "CloudTokenStore: migrated ${map.size} keys to multi-account format")
                migrated
            } else {
                map
            }
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "CloudTokenStore: load error: ${e.message}")
            emptyMap()
        }
    }

    /** Migrate old single-account keys (e.g. "gdrive") to multi-account ("gdrive:email") */
    private fun migrateOldKeys(map: Map<String, CloudTokens>): Map<String, CloudTokens> {
        val result = mutableMapOf<String, CloudTokens>()
        for ((key, tokens) in map) {
            if (key.contains(':')) {
                result[key] = tokens
            } else {
                // Old format: key = scheme (e.g. "gdrive")
                val email = tokens.email.ifEmpty { "account" }
                val newKey = "$key:$email"
                result[newKey] = tokens
                EcosystemLogger.d(HaronConstants.TAG, "CloudTokenStore: migrated '$key' → '$newKey'")
            }
        }
        return result
    }

    private fun serializeMap(map: Map<String, CloudTokens>): String {
        return map.entries.joinToString("\n") { (key, tokens) ->
            "$key\t${tokens.accessToken}\t${tokens.refreshToken.orEmpty()}\t${tokens.email}\t${tokens.displayName}"
        }
    }

    private fun deserializeMap(data: String): Map<String, CloudTokens> {
        if (data.isBlank()) return emptyMap()
        return data.lines().mapNotNull { line ->
            val parts = line.split("\t")
            if (parts.size >= 2) {
                parts[0] to CloudTokens(
                    accessToken = parts[1],
                    refreshToken = parts.getOrNull(2)?.ifEmpty { null },
                    email = parts.getOrElse(3) { "" },
                    displayName = parts.getOrElse(4) { "" }
                )
            } else null
        }.toMap()
    }

    private fun writeEncrypted(plaintext: String) {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        file.writeBytes(iv + encrypted)
    }

    private fun readDecrypted(): String {
        val bytes = file.readBytes()
        if (bytes.size < IV_LENGTH + 1) return ""
        val iv = bytes.copyOfRange(0, IV_LENGTH)
        val encrypted = bytes.copyOfRange(IV_LENGTH, bytes.size)
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val entry = ks.getEntry(KEYSTORE_ALIAS, null)
        if (entry is KeyStore.SecretKeyEntry) return entry.secretKey

        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }
}
