package com.vamp.haron.domain.usecase

import com.vamp.haron.data.cloud.CloudManager
import com.vamp.haron.domain.model.CloudTransferProgress
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class CloudUploadUseCase @Inject constructor(
    private val cloudManager: CloudManager
) {
    operator fun invoke(
        accountId: String,
        localPath: String,
        cloudDirPath: String,
        fileName: String
    ): Flow<CloudTransferProgress> {
        return cloudManager.uploadFile(accountId, localPath, cloudDirPath, fileName)
    }
}
