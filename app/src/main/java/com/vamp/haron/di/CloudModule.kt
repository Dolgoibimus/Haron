package com.vamp.haron.di

import com.vamp.haron.data.cloud.CloudManager
import com.vamp.haron.data.cloud.CloudTokenStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CloudModule {
    // CloudManager and CloudTokenStore are @Singleton @Inject constructor —
    // Hilt auto-provides them. This module exists for future custom bindings
    // (e.g., separate provider instances, test fakes).
}
