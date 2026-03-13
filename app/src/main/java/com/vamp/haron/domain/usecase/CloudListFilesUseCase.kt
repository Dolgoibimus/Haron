package com.vamp.haron.domain.usecase

import com.vamp.haron.data.cloud.CloudManager
import com.vamp.haron.domain.model.CloudFileEntry
import com.vamp.haron.domain.model.CloudProvider
import javax.inject.Inject

class CloudListFilesUseCase @Inject constructor(
    private val cloudManager: CloudManager
) {
    suspend operator fun invoke(provider: CloudProvider, path: String): Result<List<CloudFileEntry>> {
        return cloudManager.listFiles(provider, path)
    }
}
