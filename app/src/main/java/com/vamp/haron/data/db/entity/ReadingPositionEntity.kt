package com.vamp.haron.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_position")
data class ReadingPositionEntity(
    @PrimaryKey
    @ColumnInfo(name = "file_path")
    val filePath: String,

    @ColumnInfo(name = "position")
    val position: Int = 0,

    @ColumnInfo(name = "position_extra")
    val positionExtra: Long = 0L,

    @ColumnInfo(name = "last_opened")
    val lastOpened: Long = System.currentTimeMillis()
)
