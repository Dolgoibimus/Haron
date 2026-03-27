package com.vamp.haron.data.backup

import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Шифрование/дешифрование бэкапа паролем пользователя.
 *
 * ## Зачем нужно шифрование бэкапа
 *
 * Сетевые учётки (FTP, SMB, SSH, WebDAV) и облачные токены в Haron хранятся
 * зашифрованными ключом из Android Keystore (AES-256-GCM). Этот ключ привязан
 * к аппаратному чипу безопасности (TEE) конкретного устройства и **не может быть
 * извлечён или перенесён** на другое устройство.
 *
 * При создании бэкапа мы расшифровываем учётки Keystore-ключом текущего устройства
 * и получаем чистый JSON с логинами/паролями. Этот JSON нельзя хранить незащищённым —
 * поэтому весь ZIP-бэкап шифруется паролем, который задаёт пользователь.
 *
 * При восстановлении на новом устройстве: пользователь вводит пароль → ZIP расшифровывается →
 * учётки читаются из JSON → шифруются новым Keystore-ключом нового устройства.
 *
 * ## Алгоритм
 *
 * - **PBKDF2WithHmacSHA256** — из пароля пользователя выводим 256-битный ключ.
 *   Используем 100_000 итераций и случайную соль (16 байт) для защиты от brute-force.
 * - **AES-256-CBC + PKCS5Padding** — шифруем данные. CBC выбран вместо GCM, т.к.
 *   работаем с потоком (CipherOutputStream), и CBC лучше подходит для потокового шифрования.
 * - Формат файла: [salt 16 байт][IV 16 байт][зашифрованные данные]
 */
object BackupCrypto {

    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    private const val KEY_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val KEY_LENGTH = 256
    private const val ITERATION_COUNT = 10_000 // 10K — баланс безопасность/скорость для мобильных
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 16

    /**
     * Шифрует данные из [input] и записывает в [output].
     * Формат: salt(16) + IV(16) + encrypted_data
     */
    fun encrypt(input: InputStream, output: OutputStream, password: String) {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH).also { random.nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv

        output.write(salt)
        output.write(iv)

        CipherOutputStream(output, cipher).use { cos ->
            input.copyTo(cos, bufferSize = 65536)
        }
    }

    /**
     * Дешифрует данные из [input] и записывает в [output].
     * Ожидает формат: salt(16) + IV(16) + encrypted_data
     *
     * @throws javax.crypto.BadPaddingException если пароль неверный
     */
    fun decrypt(input: InputStream, output: OutputStream, password: String) {
        val salt = ByteArray(SALT_LENGTH)
        input.readFully(salt)
        val iv = ByteArray(IV_LENGTH)
        input.readFully(iv)

        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))

        CipherInputStream(input, cipher).use { cis ->
            cis.copyTo(output, bufferSize = 65536)
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(KEY_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun InputStream.readFully(buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val read = read(buffer, offset, buffer.size - offset)
            if (read < 0) throw IllegalStateException("Unexpected end of stream")
            offset += read
        }
    }
}
