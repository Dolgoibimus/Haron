package com.vamp.haron.data.model

enum class SortField {
    NAME, DATE, SIZE, EXTENSION
}

enum class SortDirection {
    ASCENDING, DESCENDING
}

data class SortOrder(
    val field: SortField = SortField.NAME,
    val direction: SortDirection = SortDirection.ASCENDING
)
