package com.vamp.haron.domain.model.backup

/**
 * Результат операции восстановления бэкапа.
 *
 * [Success] — всё восстановлено без расхождений.
 * [PartialSuccess] — данные восстановлены, но есть предупреждения:
 *   - Теги привязаны к файлам, которых нет на устройстве
 *   - Избранные пути не найдены
 *   - Облачные токены протухли (refresh token невалиден — нужен повторный вход)
 *   - Книги из библиотеки не найдены на устройстве (пути сохранены на случай появления)
 * [Error] — восстановление не удалось (повреждённый ZIP, неверный пароль и т.д.)
 */
sealed class BackupResult {
    data class Success(val message: String) : BackupResult()

    data class PartialSuccess(
        val message: String,
        val warnings: List<RestoreWarning>
    ) : BackupResult()

    data class Error(val message: String) : BackupResult()
}

/**
 * Предупреждение при восстановлении — конкретная проблема с конкретными данными.
 *
 * @param section Какая секция бэкапа затронута.
 * @param type Тип предупреждения.
 * @param details Человекочитаемое описание (для UI).
 * @param affectedPaths Список путей/ключей, которые не найдены (для действия "Удалить мёртвые").
 */
data class RestoreWarning(
    val section: BackupSection,
    val type: WarningType,
    val details: String,
    val affectedPaths: List<String> = emptyList()
)

enum class WarningType {
    /** Файлы, на которые ссылаются теги/избранное/закладки, не найдены на устройстве */
    MISSING_FILES,
    /** Облачный токен протух — нужен повторный вход */
    CLOUD_TOKEN_EXPIRED,
    /** Книги из библиотеки не найдены на устройстве */
    MISSING_BOOKS
}
