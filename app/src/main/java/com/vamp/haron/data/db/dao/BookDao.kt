package com.vamp.haron.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vamp.haron.data.db.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT file_path, title, author, format, file_size, cover_path, NULL as annotation, language, series, series_number, progress, last_read, added_at, scan_folder FROM book ORDER BY last_read DESC, added_at DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM book ORDER BY last_read DESC, added_at DESC")
    suspend fun getAll(): List<BookEntity>

    @Query("SELECT * FROM book WHERE file_path = :path")
    suspend fun getByPath(path: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: BookEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNew(book: BookEntity)

    @Query("DELETE FROM book WHERE file_path = :path")
    suspend fun delete(path: String)

    @Query("DELETE FROM book WHERE scan_folder = :folder")
    suspend fun deleteByFolder(folder: String)

    @Query("UPDATE book SET progress = :progress, last_read = :lastRead WHERE file_path = :path")
    suspend fun updateProgress(path: String, progress: Float, lastRead: Long = System.currentTimeMillis())

    @Query("SELECT DISTINCT scan_folder FROM book WHERE scan_folder != '' ORDER BY scan_folder")
    suspend fun getScanFolders(): List<String>
}
