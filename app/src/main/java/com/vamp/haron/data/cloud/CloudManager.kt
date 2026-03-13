package com.vamp.haron.data.cloud

import android.content.Context
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.data.cloud.provider.CloudProviderInterface
import com.vamp.haron.data.cloud.provider.DropboxProvider
import com.vamp.haron.data.cloud.provider.GoogleDriveProvider
import com.vamp.haron.data.cloud.provider.OneDriveProvider
import com.vamp.haron.data.cloud.provider.YandexDiskProvider
import com.vamp.haron.domain.model.CloudAccount
import com.vamp.haron.domain.model.CloudFileEntry
import com.vamp.haron.domain.model.CloudProvider
import com.vamp.haron.domain.model.CloudTransferProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton facade for cloud storage operations.
 * Pattern: same as SmbManager — wraps provider-specific implementations.
 */
@Singleton
class CloudManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenStore: CloudTokenStore
) {
    private val providers = mutableMapOf<CloudProvider, CloudProviderInterface>()

    init {
        providers[CloudProvider.GOOGLE_DRIVE] = GoogleDriveProvider(context, tokenStore)
        providers[CloudProvider.DROPBOX] = DropboxProvider(context, tokenStore)
        providers[CloudProvider.ONEDRIVE] = OneDriveProvider(context, tokenStore)
        providers[CloudProvider.YANDEX_DISK] = YandexDiskProvider(context, tokenStore)
    }

    fun getProvider(provider: CloudProvider): CloudProviderInterface {
        return providers[provider]!!
    }

    fun isAuthenticated(provider: CloudProvider): Boolean {
        return providers[provider]?.isAuthenticated() ?: false
    }

    fun getConnectedAccounts(): List<CloudAccount> {
        return tokenStore.getConnectedProviders().mapNotNull { provider ->
            val tokens = tokenStore.load(provider) ?: return@mapNotNull null
            CloudAccount(
                provider = provider,
                email = tokens.email,
                displayName = tokens.displayName
            )
        }
    }

    fun getAuthUrl(provider: CloudProvider): String? {
        return providers[provider]?.getAuthUrl()
    }

    suspend fun handleAuthCode(provider: CloudProvider, code: String): Result<Unit> {
        return providers[provider]?.handleAuthCode(code)
            ?: Result.failure(Exception("Unknown provider"))
    }

    suspend fun signOut(provider: CloudProvider) {
        providers[provider]?.signOut()
    }

    suspend fun listFiles(provider: CloudProvider, path: String): Result<List<CloudFileEntry>> {
        return providers[provider]?.listFiles(path)
            ?: Result.failure(Exception("Unknown provider"))
    }

    fun downloadFile(provider: CloudProvider, cloudFileId: String, localPath: String): Flow<CloudTransferProgress> {
        return providers[provider]?.downloadFile(cloudFileId, localPath)
            ?: throw Exception("Unknown provider")
    }

    fun uploadFile(provider: CloudProvider, localPath: String, cloudDirPath: String, fileName: String): Flow<CloudTransferProgress> {
        return providers[provider]?.uploadFile(localPath, cloudDirPath, fileName)
            ?: throw Exception("Unknown provider")
    }

    suspend fun delete(provider: CloudProvider, cloudFileId: String): Result<Unit> {
        return providers[provider]?.delete(cloudFileId)
            ?: Result.failure(Exception("Unknown provider"))
    }

    suspend fun createFolder(provider: CloudProvider, parentPath: String, name: String): Result<CloudFileEntry> {
        return providers[provider]?.createFolder(parentPath, name)
            ?: Result.failure(Exception("Unknown provider"))
    }

    suspend fun moveFile(
        provider: CloudProvider,
        fileId: String,
        currentParentId: String,
        newParentId: String
    ): Result<Unit> {
        return providers[provider]?.moveFile(fileId, currentParentId, newParentId)
            ?: Result.failure(Exception("Unknown provider"))
    }

    suspend fun rename(provider: CloudProvider, cloudFileId: String, newName: String): Result<Unit> {
        return providers[provider]?.rename(cloudFileId, newName)
            ?: Result.failure(Exception("Unknown provider"))
    }

    fun getAccessToken(provider: CloudProvider): String? {
        return providers[provider]?.getAccessToken()
    }

    suspend fun getFreshAccessToken(provider: CloudProvider): String? {
        return providers[provider]?.getFreshAccessToken()
    }

    fun updateFileContent(provider: CloudProvider, cloudFileId: String, localPath: String): Flow<CloudTransferProgress> {
        return providers[provider]?.updateFileContent(cloudFileId, localPath)
            ?: throw Exception("Unknown provider")
    }

    /**
     * Parse cloud:// URI into (provider, path).
     * Format: cloud://gdrive/path, cloud://dropbox/path, cloud://onedrive/path
     */
    fun parseCloudUri(uri: String): Pair<CloudProvider, String>? {
        if (!uri.startsWith("cloud://")) return null
        val rest = uri.removePrefix("cloud://")
        val scheme = rest.substringBefore('/')
        val path = rest.substringAfter('/', "")
        val provider = CloudProvider.entries.find { it.scheme == scheme } ?: return null
        return provider to path
    }
}
