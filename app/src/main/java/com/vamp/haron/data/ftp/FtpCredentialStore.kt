package com.vamp.haron.data.ftp

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
class FtpCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_LENGTH = 128
    }

    private val credentialFile: File
        get() = File(context.filesDir, HaronConstants.FTP_CREDENTIAL_FILE)

    fun save(credential: FtpCredential) {
        val all = loadAll().toMutableMap()
        val key = FtpPathUtils.connectionKey(credential.host, credential.port)
        all[key] = credential
        writeAll(all)
    }

    fun load(host: String, port: Int): FtpCredential? =
        loadAll()[FtpPathUtils.connectionKey(host, port)]

    fun remove(host: String, port: Int) {
        val all = loadAll().toMutableMap()
        all.remove(FtpPathUtils.connectionKey(host, port))
        if (all.isEmpty()) {
            credentialFile.delete()
        } else {
            writeAll(all)
        }
    }

    fun listAll(): List<FtpCredential> = loadAll().values.toList()

    private fun loadAll(): Map<String, FtpCredential> {
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

    private fun writeAll(map: Map<String, FtpCredential>) {
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

    private fun serializeCredentials(map: Map<String, FtpCredential>): String {
        val root = JSONObject()
        for ((connKey, cred) in map) {
            val obj = JSONObject()
            obj.put("host", cred.host)
            obj.put("port", cred.port)
            obj.put("username", cred.username)
            obj.put("password", cred.password)
            obj.put("useFtps", cred.useFtps)
            obj.put("displayName", cred.displayName)
            root.put(connKey, obj)
        }
        return root.toString()
    }

    private fun parseCredentials(json: String): Map<String, FtpCredential> {
        val root = JSONObject(json)
        val result = mutableMapOf<String, FtpCredential>()
        for (connKey in root.keys()) {
            val obj = root.getJSONObject(connKey)
            result[connKey] = FtpCredential(
                host = obj.optString("host", ""),
                port = obj.optInt("port", HaronConstants.FTP_DEFAULT_PORT),
                username = obj.optString("username", ""),
                password = obj.optString("password", ""),
                useFtps = obj.optBoolean("useFtps", false),
                displayName = obj.optString("displayName", obj.optString("host", ""))
            )
        }
        return result
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val existingKey = keyStore.getKey(HaronConstants.FTP_CREDENTIAL_KEYSTORE_ALIAS, null)
        if (existingKey != null) return existingKey as SecretKey

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGen.init(
            KeyGenParameterSpec.Builder(
                HaronConstants.FTP_CREDENTIAL_KEYSTORE_ALIAS,
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
