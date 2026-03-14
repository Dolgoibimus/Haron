package com.vamp.haron.domain.usecase

import com.vamp.haron.data.cloud.CloudManager
import com.vamp.haron.domain.model.CloudTransferProgress
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class CloudDownloadUseCase @Inject constructor(
    private val cloudManager: CloudManager
) {
    operator fun invoke(
        accountId: String,
        cloudFileId: String,
        localPath: String
    ): Flow<CloudTransferProgress> {
        return cloudManager.downloadFile(accountId, cloudFileId, localPath)
    }
}
