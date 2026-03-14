package com.vamp.haron.domain.usecase

import com.vamp.haron.data.cloud.CloudManager
import javax.inject.Inject

class CloudDeleteUseCase @Inject constructor(
    private val cloudManager: CloudManager
) {
    suspend operator fun invoke(accountId: String, cloudFileId: String): Result<Unit> {
        return cloudManager.delete(accountId, cloudFileId)
    }
}
