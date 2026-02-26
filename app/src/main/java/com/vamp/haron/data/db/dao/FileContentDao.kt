package com.vamp.haron.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.vamp.haron.data.db.entity.FileContentEntity

@Dao
interface FileContentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<FileContentEntity>)

    @Query("DELETE FROM file_content WHERE indexed_at < :threshold")
    suspend fun deleteStaleEntries(threshold: Long)

    @Query("DELETE FROM file_content")
    suspend fun clearAll()

    @RawQuery
    suspend fun searchFts(query: SupportSQLiteQuery): List<FileContentEntity>

    @Query("SELECT COUNT(*) FROM file_content")
    suspend fun getCount(): Int

    @Query("SELECT * FROM file_content WHERE path IN (:paths)")
    suspend fun getByPaths(paths: List<String>): List<FileContentEntity>

    @Query("SELECT COALESCE(SUM(LENGTH(full_text)), 0) FROM file_content")
    suspend fun getTotalTextSize(): Long

    @Query("SELECT path FROM file_content WHERE path LIKE :pathPrefix || '/%' AND LOWER(full_text) LIKE '%' || :query || '%'")
    suspend fun searchInFolder(pathPrefix: String, query: String): List<String>

    @Query("DELETE FROM file_content WHERE path IN (:paths)")
    suspend fun deleteByPaths(paths: List<String>)
}
