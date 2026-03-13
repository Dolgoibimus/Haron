package com.vamp.haron.domain.usecase

import com.vamp.haron.data.cloud.CloudManager
import com.vamp.haron.domain.model.CloudFileEntry
import com.vamp.haron.domain.model.CloudProvider
import javax.inject.Inject

class CloudCreateFolderUseCase @Inject constructor(
    private val cloudManager: CloudManager
) {
    suspend operator fun invoke(
        provider: CloudProvider,
        parentPath: String,
        name: String
    ): Result<CloudFileEntry> {
        return cloudManager.createFolder(provider, parentPath, name)
    }
}
