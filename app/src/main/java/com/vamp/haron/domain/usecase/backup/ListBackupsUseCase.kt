package com.vamp.haron.domain.usecase.backup

import com.vamp.haron.data.backup.BackupManager
import com.vamp.haron.domain.model.backup.BackupInfo
import javax.inject.Inject

/**
 * Получение списка существующих бэкапов из папки Download/HaronBackup/.
 * Отсортированы по дате (новые первые).
 */
class ListBackupsUseCase @Inject constructor(
    private val backupManager: BackupManager
) {
    operator fun invoke(): List<BackupInfo> {
        return backupManager.listBackups()
    }
}
