package com.vamp.haron.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "book")
data class BookEntity(
    @PrimaryKey
    @ColumnInfo(name = "file_path")
    val filePath: String,

    @ColumnInfo(name = "title")
    val title: String = "",

    @ColumnInfo(name = "author")
    val author: String = "",

    @ColumnInfo(name = "format")
    val format: String = "", // epub, fb2, mobi, pdf, djvu

    @ColumnInfo(name = "file_size")
    val fileSize: Long = 0L,

    @ColumnInfo(name = "cover_path")
    val coverPath: String? = null, // path to extracted cover in cache

    @ColumnInfo(name = "annotation")
    val annotation: String? = null,

    @ColumnInfo(name = "language")
    val language: String? = null,

    @ColumnInfo(name = "series")
    val series: String? = null,

    @ColumnInfo(name = "series_number")
    val seriesNumber: Int? = null,

    @ColumnInfo(name = "progress")
    val progress: Float = 0f, // 0.0 - 1.0

    @ColumnInfo(name = "last_read")
    val lastRead: Long = 0L,

    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "scan_folder")
    val scanFolder: String = "" // which scan folder this book belongs to
)
