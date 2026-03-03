package com.vamp.haron.domain.model

data class ComparisonHistoryEntry(
    val leftPath: String,
    val rightPath: String,
    val timestamp: Long,
    val type: String,    // "FOLDER", "TEXT", "BINARY"
    val summary: String  // "= 12 ≠ 3 ← 1 → 0"
)
