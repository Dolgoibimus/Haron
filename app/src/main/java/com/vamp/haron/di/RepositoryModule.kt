package com.vamp.haron.di

import com.vamp.haron.data.repository.FileRepositoryImpl
import com.vamp.haron.data.repository.SecureFolderRepositoryImpl
import com.vamp.haron.data.repository.TrashRepositoryImpl
import com.vamp.haron.domain.repository.FileRepository
import com.vamp.haron.domain.repository.SecureFolderRepository
import com.vamp.haron.domain.repository.TrashRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindFileRepository(impl: FileRepositoryImpl): FileRepository

    @Binds
    @Singleton
    abstract fun bindTrashRepository(impl: TrashRepositoryImpl): TrashRepository

    @Binds
    @Singleton
    abstract fun bindSecureFolderRepository(impl: SecureFolderRepositoryImpl): SecureFolderRepository
}
