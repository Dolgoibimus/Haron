package com.vamp.haron.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.vamp.haron.data.db.entity.FileIndexEntity

@Dao
interface FileIndexDao {

    @RawQuery
    suspend fun searchFiltered(query: SupportSQLiteQuery): List<FileIndexEntity>

    @Query(
        """
        SELECT * FROM file_index
        WHERE name LIKE '%' || :query || '%'
        ORDER BY name ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun searchByName(query: String, limit: Int = 200, offset: Int = 0): List<FileIndexEntity>

    @Query(
        """
        SELECT * FROM file_index
        WHERE extension = :ext
        ORDER BY name ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun searchByExtension(ext: String, limit: Int = 200, offset: Int = 0): List<FileIndexEntity>

    @RawQuery
    suspend fun searchFtsContent(query: SupportSQLiteQuery): List<FileIndexEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FileIndexEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<FileIndexEntity>)

    @Query("DELETE FROM file_index WHERE path = :path")
    suspend fun deleteByPath(path: String)

    @Query("DELETE FROM file_index WHERE path LIKE :parentPrefix || '%'")
    suspend fun deleteByParentPrefix(parentPrefix: String)

    @Query("DELETE FROM file_index WHERE indexed_at < :threshold")
    suspend fun deleteStaleEntries(threshold: Long)

    @Query("DELETE FROM file_index")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM file_index")
    suspend fun getIndexedCount(): Int

    @Query("SELECT MAX(indexed_at) FROM file_index")
    suspend fun getLastIndexedTime(): Long?
}
