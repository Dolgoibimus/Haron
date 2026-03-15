package com.vamp.haron.data.webdav

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

@Singleton
class WebDavCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_LENGTH = 128
    }

    private val credentialFile: File
        get() = File(context.filesDir, HaronConstants.WEBDAV_CREDENTIAL_FILE)

    fun save(credential: WebDavCredential) {
        val all = loadAll().toMutableMap()
        all[credential.url] = credential
        writeAll(all)
    }

    fun load(url: String): WebDavCredential? = loadAll()[url]

    fun remove(url: String) {
        val all = loadAll().toMutableMap()
        all.remove(url)
        if (all.isEmpty()) {
            credentialFile.delete()
        } else {
            writeAll(all)
        }
    }

    fun listAll(): List<WebDavCredential> = loadAll().values.toList()

    private fun loadAll(): Map<String, WebDavCredential> {
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

    private fun writeAll(map: Map<String, WebDavCredential>) {
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

    private fun serializeCredentials(map: Map<String, WebDavCredential>): String {
        val root = JSONObject()
        for ((mapKey, cred) in map) {
            val obj = JSONObject()
            obj.put("url", cred.url)
            obj.put("username", cred.username)
            obj.put("password", cred.password)
            obj.put("displayName", cred.displayName)
            root.put(mapKey, obj)
        }
        return root.toString()
    }

    private fun parseCredentials(json: String): Map<String, WebDavCredential> {
        val root = JSONObject(json)
        val result = mutableMapOf<String, WebDavCredential>()
        for (mapKey in root.keys()) {
            val obj = root.getJSONObject(mapKey)
            result[mapKey] = WebDavCredential(
                url = obj.optString("url", ""),
                username = obj.optString("username", ""),
                password = obj.optString("password", ""),
                displayName = obj.optString("displayName", obj.optString("url", ""))
            )
        }
        return result
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val existingKey = keyStore.getKey(HaronConstants.WEBDAV_CREDENTIAL_KEYSTORE_ALIAS, null)
        if (existingKey != null) return existingKey as SecretKey

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGen.init(
            KeyGenParameterSpec.Builder(
                HaronConstants.WEBDAV_CREDENTIAL_KEYSTORE_ALIAS,
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
