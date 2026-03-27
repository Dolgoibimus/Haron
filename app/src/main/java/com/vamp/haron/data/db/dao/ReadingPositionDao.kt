package com.vamp.haron.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vamp.haron.data.db.entity.ReadingPositionEntity

@Dao
interface ReadingPositionDao {

    @Query("SELECT * FROM reading_position WHERE file_path = :filePath")
    suspend fun get(filePath: String): ReadingPositionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReadingPositionEntity)

    @Query("DELETE FROM reading_position WHERE file_path = :filePath")
    suspend fun delete(filePath: String)

    @Query("DELETE FROM reading_position WHERE last_opened < :threshold")
    suspend fun deleteOld(threshold: Long)

    @Query("SELECT * FROM reading_position ORDER BY last_opened DESC")
    suspend fun getAll(): List<ReadingPositionEntity>
}
