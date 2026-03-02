package com.vamp.haron.di

import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SmbModule {

    @Provides
    @Singleton
    fun provideSmbConfig(): SmbConfig = SmbConfig.builder()
        .withTimeout(10, TimeUnit.SECONDS)
        .withReadTimeout(30, TimeUnit.SECONDS)
        .withWriteTimeout(30, TimeUnit.SECONDS)
        .withMultiProtocolNegotiate(true)
        .build()

    @Provides
    @Singleton
    fun provideSmbClient(config: SmbConfig): SMBClient = SMBClient(config)
}
