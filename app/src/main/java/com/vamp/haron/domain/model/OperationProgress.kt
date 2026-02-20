package com.vamp.haron.domain.model

data class OperationProgress(
    val current: Int,
    val total: Int,
    val currentFileName: String,
    val type: OperationType = OperationType.COPY,
    val isComplete: Boolean = false,
    val error: String? = null
)

enum class OperationType(val label: String) {
    COPY("Копирование"),
    MOVE("Перемещение"),
    DELETE("Удаление")
}
