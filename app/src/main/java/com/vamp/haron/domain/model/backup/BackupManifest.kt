package com.vamp.haron.domain.model.backup

/**
 * Метаданные бэкапа — хранится как manifest.json внутри ZIP-архива.
 *
 * @param formatVersion Версия формата бэкапа. При изменении структуры — инкремент.
 *   Позволяет корректно восстанавливать бэкапы, созданные старыми версиями приложения.
 * @param appVersion Версия Haron на момент создания бэкапа (для информации).
 * @param createdAt Timestamp создания (System.currentTimeMillis()).
 * @param sections Список секций, включённых в бэкап.
 * @param isEncrypted true если ZIP зашифрован паролем пользователя.
 * @param deviceName Имя устройства (Build.MODEL) — для отображения в списке бэкапов.
 * @param androidVersion Версия Android (Build.VERSION.RELEASE).
 */
data class BackupManifest(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val appVersion: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val sections: List<BackupSection> = emptyList(),
    val isEncrypted: Boolean = false,
    val deviceName: String = "",
    val androidVersion: String = ""
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 1
        const val FILE_NAME = "manifest.json"
    }
}
