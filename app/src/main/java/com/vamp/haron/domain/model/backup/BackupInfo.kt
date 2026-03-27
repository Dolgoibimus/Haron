package com.vamp.haron.domain.model.backup

import java.io.File

/**
 * Модель бэкапа для отображения в UI (список бэкапов).
 *
 * @param file Файл бэкапа на диске.
 * @param manifest Метаданные (прочитанные из manifest.json внутри ZIP).
 *   null если ZIP повреждён или не содержит manifest.
 * @param sizeBytes Размер файла в байтах.
 */
data class BackupInfo(
    val file: File,
    val manifest: BackupManifest?,
    val sizeBytes: Long
)
