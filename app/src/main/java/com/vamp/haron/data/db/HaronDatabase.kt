package com.vamp.haron.data.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vamp.haron.data.db.dao.FileIndexDao
import com.vamp.haron.data.db.entity.FileIndexEntity

@Database(
    entities = [FileIndexEntity::class],
    version = 1,
    exportSchema = false
)
abstract class HaronDatabase : RoomDatabase() {

    abstract fun fileIndexDao(): FileIndexDao

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
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // FTS is optional — search works via LIKE on indexed columns anyway
                        try {
                            db.execSQL(
                                """
                                CREATE VIRTUAL TABLE IF NOT EXISTS file_index_fts
                                USING fts4(name, path, content='file_index')
                                """.trimIndent()
                            )
                            db.execSQL(
                                """
                                CREATE TRIGGER IF NOT EXISTS file_index_ai AFTER INSERT ON file_index BEGIN
                                    INSERT INTO file_index_fts(docid, name, path) VALUES (new.rowid, new.name, new.path);
                                END
                                """.trimIndent()
                            )
                            db.execSQL(
                                """
                                CREATE TRIGGER IF NOT EXISTS file_index_ad AFTER DELETE ON file_index BEGIN
                                    INSERT INTO file_index_fts(file_index_fts, docid, name, path) VALUES ('delete', old.rowid, old.name, old.path);
                                END
                                """.trimIndent()
                            )
                            db.execSQL(
                                """
                                CREATE TRIGGER IF NOT EXISTS file_index_au AFTER UPDATE ON file_index BEGIN
                                    INSERT INTO file_index_fts(file_index_fts, docid, name, path) VALUES ('delete', old.rowid, old.name, old.path);
                                    INSERT INTO file_index_fts(docid, name, path) VALUES (new.rowid, new.name, new.path);
                                END
                                """.trimIndent()
                            )
                            Log.d(TAG, "FTS4 table created successfully")
                        } catch (e: Exception) {
                            Log.w(TAG, "FTS not available, search will use LIKE fallback", e)
                        }
                    }
                })
                .build()
        }
    }
}
