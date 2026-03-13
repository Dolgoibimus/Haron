package com.vamp.haron.domain.usecase

import com.vamp.haron.data.cloud.CloudManager
import com.vamp.haron.domain.model.CloudProvider
import javax.inject.Inject

class CloudDeleteUseCase @Inject constructor(
    private val cloudManager: CloudManager
) {
    suspend operator fun invoke(provider: CloudProvider, cloudFileId: String): Result<Unit> {
        return cloudManager.delete(provider, cloudFileId)
    }
}
