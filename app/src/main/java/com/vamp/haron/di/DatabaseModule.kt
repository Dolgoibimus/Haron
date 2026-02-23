package com.vamp.haron.di

import android.content.Context
import com.vamp.haron.data.db.HaronDatabase
import com.vamp.haron.data.db.dao.FileIndexDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideHaronDatabase(@ApplicationContext context: Context): HaronDatabase {
        return HaronDatabase.build(context)
    }

    @Provides
    @Singleton
    fun provideFileIndexDao(database: HaronDatabase): FileIndexDao {
        return database.fileIndexDao()
    }
}
