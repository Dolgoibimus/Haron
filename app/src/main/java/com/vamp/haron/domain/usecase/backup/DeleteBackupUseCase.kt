package com.vamp.haron.domain.usecase.backup

import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import java.io.File
import javax.inject.Inject

/** Удаление файла бэкапа. */
class DeleteBackupUseCase @Inject constructor() {
    operator fun invoke(file: File): Boolean {
        val deleted = file.delete()
        EcosystemLogger.d("${HaronConstants.TAG}/Backup", "deleteBackup: ${file.name}, success=$deleted")
        return deleted
    }
}
