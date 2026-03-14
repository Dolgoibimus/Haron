package com.vamp.haron.domain.usecase

import com.vamp.haron.data.cloud.CloudManager
import com.vamp.haron.domain.model.CloudFileEntry
import javax.inject.Inject

class CloudListFilesUseCase @Inject constructor(
    private val cloudManager: CloudManager
) {
    suspend operator fun invoke(accountId: String, path: String): Result<List<CloudFileEntry>> {
        return cloudManager.listFiles(accountId, path)
    }
}
