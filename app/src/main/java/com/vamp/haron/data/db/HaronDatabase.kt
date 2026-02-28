package com.vamp.haron.data.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vamp.haron.data.db.dao.FileContentDao
import com.vamp.haron.data.db.dao.FileIndexDao
import com.vamp.haron.data.db.dao.ReadingPositionDao
import com.vamp.haron.data.db.entity.FileContentEntity
import com.vamp.haron.data.db.entity.FileIndexEntity
import com.vamp.haron.data.db.entity.ReadingPositionEntity

@Database(
    entities = [FileIndexEntity::class, FileContentEntity::class, ReadingPositionEntity::class],
    version = 4,
    exportSchema = false
)
abstract class HaronDatabase : RoomDatabase() {

    abstract fun fileIndexDao(): FileIndexDao
    abstract fun fileContentDao(): FileContentDao
    abstract fun readingPositionDao(): ReadingPositionDao

    companion object {
        private const val TAG = "HaronDatabase"

        fun build(context: Context): HaronDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                HaronDatabase::class.java,
                "haron_db"
            )
                .fallbackToDestructiveMigration(true)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        try {
                            // FTS4 for file_index (name + path + snippet search)
                            db.execSQL(
                                """
                                CREATE VIRTUAL TABLE IF NOT EXISTS file_index_fts
                                USING fts4(name, path, content_snippet, content='file_index')
                                """.trimIndent()
                            )
                            db.execSQL(
                                """
                                CREATE TRIGGER IF NOT EXISTS file_index_ai AFTER INSERT ON file_index BEGIN
                                    INSERT INTO file_index_fts(docid, name, path, content_snippet) VALUES (new.rowid, new.name, new.path, new.content_snippet);
                                END
                                """.trimIndent()
                            )
                            db.execSQL(
                                """
                                CREATE TRIGGER IF NOT EXISTS file_index_ad AFTER DELETE ON file_index BEGIN
                                    INSERT INTO file_index_fts(file_index_fts, docid, name, path, content_snippet) VALUES ('delete', old.rowid, old.name, old.path, old.content_snippet);
                                END
                                """.trimIndent()
                            )
                            db.execSQL(
                                """
                                CREATE TRIGGER IF NOT EXISTS file_index_au AFTER UPDATE ON file_index BEGIN
                                    INSERT INTO file_index_fts(file_index_fts, docid, name, path, content_snippet) VALUES ('delete', old.rowid, old.name, old.path, old.content_snippet);
                                    INSERT INTO file_index_fts(docid, name, path, content_snippet) VALUES (new.rowid, new.name, new.path, new.content_snippet);
                                END
                                """.trimIndent()
                            )

                            // FTS4 for file_content (full text search)
                            db.execSQL(
                                """
                                CREATE VIRTUAL TABLE IF NOT EXISTS file_content_fts
                                USING fts4(full_text, content='file_content')
                                """.trimIndent()
                            )
                            db.execSQL(
                                """
                                CREATE TRIGGER IF NOT EXISTS file_content_ai AFTER INSERT ON file_content BEGIN
                                    INSERT INTO file_content_fts(docid, full_text) VALUES (new.rowid, new.full_text);
                                END
                                """.trimIndent()
                            )
                            db.execSQL(
                                """
                                CREATE TRIGGER IF NOT EXISTS file_content_ad AFTER DELETE ON file_content BEGIN
                                    INSERT INTO file_content_fts(file_content_fts, docid, full_text) VALUES ('delete', old.rowid, old.full_text);
                                END
                                """.trimIndent()
                            )
                            db.execSQL(
                                """
                                CREATE TRIGGER IF NOT EXISTS file_content_au AFTER UPDATE ON file_content BEGIN
                                    INSERT INTO file_content_fts(file_content_fts, docid, full_text) VALUES ('delete', old.rowid, old.full_text);
                                    INSERT INTO file_content_fts(docid, full_text) VALUES (new.rowid, new.full_text);
                                END
                                """.trimIndent()
                            )

                            Log.d(TAG, "FTS4 tables created successfully")
                        } catch (e: Exception) {
                            Log.w(TAG, "FTS not available, search will use LIKE fallback", e)
                        }
                    }
                })
                .build()
        }
    }
}
