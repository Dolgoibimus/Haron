package com.vamp.haron.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "file_index",
    indices = [
        Index(value = ["parent_path"]),
        Index(value = ["extension"]),
        Index(value = ["name"]),
        Index(value = ["size"]),
        Index(value = ["last_modified"])
    ]
)
data class FileIndexEntity(
    @PrimaryKey
    @ColumnInfo(name = "path")
    val path: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "extension")
    val extension: String,

    @ColumnInfo(name = "size")
    val size: Long,

    @ColumnInfo(name = "last_modified")
    val lastModified: Long,

    @ColumnInfo(name = "mime_type")
    val mimeType: String,

    @ColumnInfo(name = "parent_path")
    val parentPath: String,

    @ColumnInfo(name = "is_directory")
    val isDirectory: Boolean,

    @ColumnInfo(name = "is_hidden")
    val isHidden: Boolean,

    @ColumnInfo(name = "indexed_at")
    val indexedAt: Long = System.currentTimeMillis()
)
