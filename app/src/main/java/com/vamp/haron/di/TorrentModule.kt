package com.vamp.haron.di

import com.vamp.haron.data.torrent.TorrentStreamRepositoryImpl
import com.vamp.haron.domain.repository.TorrentStreamRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TorrentModule {
    @Binds
    @Singleton
    abstract fun bindTorrentStreamRepository(
        impl: TorrentStreamRepositoryImpl
    ): TorrentStreamRepository
}
