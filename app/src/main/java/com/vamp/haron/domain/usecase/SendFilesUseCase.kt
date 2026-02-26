package com.vamp.haron.domain.usecase

import com.vamp.haron.domain.model.DiscoveredDevice
import com.vamp.haron.domain.model.TransferProgressInfo
import com.vamp.haron.domain.model.TransferProtocol
import com.vamp.haron.domain.repository.TransferRepository
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject

class SendFilesUseCase @Inject constructor(
    private val transferRepository: TransferRepository
) {
    suspend operator fun invoke(
        files: List<File>,
        device: DiscoveredDevice,
        protocol: TransferProtocol
    ): Flow<TransferProgressInfo> {
        return transferRepository.sendFiles(files, device, protocol)
    }

    fun cancel() {
        transferRepository.cancelTransfer()
    }
}
