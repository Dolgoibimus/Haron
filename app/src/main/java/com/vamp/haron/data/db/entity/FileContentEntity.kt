package com.vamp.haron.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "file_content")
data class FileContentEntity(
    @PrimaryKey
    @ColumnInfo(name = "path")
    val path: String,

    @ColumnInfo(name = "full_text")
    val fullText: String,

    @ColumnInfo(name = "indexed_at")
    val indexedAt: Long = System.currentTimeMillis()
)
