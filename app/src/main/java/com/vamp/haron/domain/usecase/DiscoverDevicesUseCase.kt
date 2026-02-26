package com.vamp.haron.domain.usecase

import com.vamp.haron.domain.model.DiscoveredDevice
import com.vamp.haron.domain.repository.TransferRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class DiscoverDevicesUseCase @Inject constructor(
    private val transferRepository: TransferRepository
) {
    operator fun invoke(): Flow<List<DiscoveredDevice>> {
        return transferRepository.discoverDevices()
    }

    fun stop() {
        transferRepository.stopDiscovery()
    }
}
