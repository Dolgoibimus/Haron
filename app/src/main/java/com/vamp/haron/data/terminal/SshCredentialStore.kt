package com.vamp.haron.data.terminal

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.vamp.haron.common.constants.HaronConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

data class SshCredential(
    val user: String,
    val host: String,
    val port: Int,
    val password: String
)

@Singleton
class SshCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_LENGTH = 128
        const val MAX_ENTRIES = 20
    }

    private val credentialFile: File
        get() = File(context.filesDir, HaronConstants.SSH_CREDENTIAL_FILE)

    fun save(credential: SshCredential) {
        val all = loadAll().toMutableMap()
        val key = makeKey(credential.user, credential.host, credential.port)
        all[key] = credential
        // Enforce max entries — remove oldest if over limit
        while (all.size > MAX_ENTRIES) {
            all.remove(all.keys.first())
        }
        writeAll(all)
    }

    fun load(user: String, host: String, port: Int): SshCredential? {
        return loadAll()[makeKey(user, host, port)]
    }

    fun listAll(): List<SshCredential> = loadAll().values.toList()

    fun remove(user: String, host: String, port: Int) {
        val all = loadAll().toMutableMap()
        all.remove(makeKey(user, host, port))
        if (all.isEmpty()) {
            credentialFile.delete()
        } else {
            writeAll(all)
        }
    }

    private fun makeKey(user: String, host: String, port: Int): String =
        "$user@$host:$port"

    private fun loadAll(): Map<String, SshCredential> {
        val file = credentialFile
        if (!file.exists()) return emptyMap()
        return try {
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            file.inputStream().use { fis ->
                val iv = ByteArray(GCM_IV_LENGTH)
                fis.read(iv)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
                CipherInputStream(fis, cipher).use { cis ->
                    val json = cis.bufferedReader().readText()
                    parseCredentials(json)
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun writeAll(map: Map<String, SshCredential>) {
        val json = serializeCredentials(map)
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        credentialFile.outputStream().use { fos ->
            fos.write(iv)
            CipherOutputStream(fos, cipher).use { cos ->
                cos.write(json.toByteArray(Charsets.UTF_8))
            }
        }
    }

    private fun serializeCredentials(map: Map<String, SshCredential>): String {
        val root = JSONObject()
        for ((mapKey, cred) in map) {
            val obj = JSONObject()
            obj.put("user", cred.user)
            obj.put("host", cred.host)
            obj.put("port", cred.port)
            obj.put("password", cred.password)
            root.put(mapKey, obj)
        }
        return root.toString()
    }

    private fun parseCredentials(json: String): Map<String, SshCredential> {
        val root = JSONObject(json)
        val result = mutableMapOf<String, SshCredential>()
        for (mapKey in root.keys()) {
            val obj = root.getJSONObject(mapKey)
            result[mapKey] = SshCredential(
                user = obj.optString("user", ""),
                host = obj.optString("host", ""),
                port = obj.optInt("port", 22),
                password = obj.optString("password", "")
            )
        }
        return result
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val existingKey = keyStore.getKey(HaronConstants.SSH_CREDENTIAL_KEYSTORE_ALIAS, null)
        if (existingKey != null) return existingKey as SecretKey

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGen.init(
            KeyGenParameterSpec.Builder(
                HaronConstants.SSH_CREDENTIAL_KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGen.generateKey()
    }
}
