package com.vamp.haron.domain.usecase.backup

import com.vamp.haron.data.backup.BackupManager
import com.vamp.haron.domain.model.backup.BackupResult
import com.vamp.haron.domain.model.backup.BackupSection
import java.io.File
import javax.inject.Inject

/**
 * Восстановление бэкапа из файла.
 *
 * Процесс:
 * 1. Если файл зашифрован (.hbk) — расшифровываем паролем пользователя
 * 2. Читаем manifest.json — определяем какие секции есть в бэкапе
 * 3. Восстанавливаем выбранные секции:
 *    - Настройки → SharedPreferences (перезаписывают текущие)
 *    - Учётки → расшифрованный JSON → CredentialStore.save() → шифрование новым Keystore
 *    - Книги → Room DB (upsert — не удаляет существующие)
 * 4. Проверяем расхождения:
 *    - Теги/избранное/закладки → существуют ли файлы на устройстве
 *    - Книги → существуют ли файлы
 * 5. Возвращаем [BackupResult] с отчётом
 *
 * ## Важно: облачные токены
 *
 * Refresh token может протухнуть если:
 * - Прошло много времени с момента бэкапа (Yandex ~1 год, Google ~6 мес)
 * - Пользователь сменил пароль аккаунта
 * - Провайдер отозвал токен (подозрительная активность)
 *
 * Проверка валидности токенов требует сетевого запроса — это делается
 * в BackupViewModel после восстановления (попытка refresh → если ошибка → warning).
 */
class RestoreBackupUseCase @Inject constructor(
    private val backupManager: BackupManager
) {
    suspend operator fun invoke(
        backupFile: File,
        password: String? = null,
        sections: Set<BackupSection>? = null,
        onProgress: ((Int, Int, String) -> Unit)? = null
    ): BackupResult {
        return backupManager.restoreBackup(backupFile, password, sections, onProgress)
    }
}
