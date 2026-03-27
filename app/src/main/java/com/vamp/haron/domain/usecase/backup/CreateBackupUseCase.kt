package com.vamp.haron.domain.usecase.backup

import com.vamp.haron.data.backup.BackupManager
import com.vamp.haron.domain.model.backup.BackupSection
import java.io.File
import javax.inject.Inject

/**
 * Создание бэкапа выбранных секций.
 *
 * Оркестрирует вызов [BackupManager.createBackup]:
 * 1. Пользователь выбирает секции (настройки, учётки, книги)
 * 2. Опционально задаёт пароль для шифрования
 * 3. BackupManager собирает данные, создаёт ZIP, шифрует если нужно
 * 4. Возвращает файл бэкапа или ошибку
 *
 * Пароль обязателен если выбрана секция CREDENTIALS — учётки содержат
 * логины/пароли в открытом виде (расшифрованные из Keystore).
 */
class CreateBackupUseCase @Inject constructor(
    private val backupManager: BackupManager
) {
    suspend operator fun invoke(
        sections: Set<BackupSection>,
        password: String? = null,
        secureFileIds: Set<String>? = null,
        onProgress: ((Int, Int) -> Unit)? = null
    ): Result<File> {
        return backupManager.createBackup(sections, password, secureFileIds, onProgress)
    }
}
