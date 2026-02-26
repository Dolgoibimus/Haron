package com.vamp.haron.di

import com.vamp.haron.data.repository.CastRepositoryImpl
import com.vamp.haron.domain.repository.CastRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CastModule {

    @Binds
    @Singleton
    abstract fun bindCastRepository(impl: CastRepositoryImpl): CastRepository
}
