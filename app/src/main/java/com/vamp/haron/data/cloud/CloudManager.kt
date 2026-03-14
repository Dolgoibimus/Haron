package com.vamp.haron.data.cloud

import android.content.Context
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.data.cloud.provider.CloudProviderInterface
import com.vamp.haron.data.cloud.provider.DropboxProvider
import com.vamp.haron.data.cloud.provider.GoogleDriveProvider
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
 * Parsed cloud URI with backward-compatible destructuring.
 * component1() = provider, component2() = path (matches old Pair<CloudProvider, String>).
 */
data class CloudPath(
    val provider: CloudProvider,
    val path: String,
    val accountId: String
)

/**
 * Singleton facade for cloud storage operations.
 * Supports multiple accounts per provider type.
 */
@Singleton
class CloudManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenStore: CloudTokenStore
) {
    /** Auth providers — one per type, only for getAuthUrl() and handleAuthCode() */
    private val authProviders = mapOf<CloudProvider, CloudProviderInterface>(
        CloudProvider.GOOGLE_DRIVE to GoogleDriveProvider(context, tokenStore, ""),
        CloudProvider.DROPBOX to DropboxProvider(context, tokenStore, ""),
        CloudProvider.YANDEX_DISK to YandexDiskProvider(context, tokenStore, "")
    )

    /** Account providers — one per connected account, for all data operations */
    private val accountProviders = mutableMapOf<String, CloudProviderInterface>()

    init {
        rebuildAccountProviders()
    }

    /** Rebuild accountProviders from tokenStore */
    private fun rebuildAccountProviders() {
        accountProviders.clear()
        for ((key, _) in tokenStore.getAllAccounts()) {
            val scheme = key.substringBefore(':')
            val provider = CloudProvider.entries.find { it.scheme == scheme } ?: continue
            accountProviders[key] = createProvider(provider, key)
        }
        EcosystemLogger.d(HaronConstants.TAG, "CloudManager: rebuilt ${accountProviders.size} account providers")
    }

    private fun createProvider(provider: CloudProvider, tokenKey: String): CloudProviderInterface {
        return when (provider) {
            CloudProvider.GOOGLE_DRIVE -> GoogleDriveProvider(context, tokenStore, tokenKey)
            CloudProvider.DROPBOX -> DropboxProvider(context, tokenStore, tokenKey)
            CloudProvider.YANDEX_DISK -> YandexDiskProvider(context, tokenStore, tokenKey)
        }
    }

    fun getConnectedAccounts(): List<CloudAccount> {
        return tokenStore.getAllAccounts().map { (key, tokens) ->
            val scheme = key.substringBefore(':')
            val provider = CloudProvider.entries.first { it.scheme == scheme }
            CloudAccount(
                accountId = key,
                provider = provider,
                email = tokens.email,
                displayName = tokens.displayName
            )
        }
    }

    fun getAuthUrl(provider: CloudProvider): String? {
        return authProviders[provider]?.getAuthUrl()
    }

    suspend fun handleAuthCode(provider: CloudProvider, code: String): Result<String> {
        val result = authProviders[provider]?.handleAuthCode(code)
            ?: return Result.failure(Exception("Unknown provider"))
        result.onSuccess { rebuildAccountProviders() }
        return result
    }

    suspend fun signOut(accountId: String) {
        accountProviders[accountId]?.signOut()
        accountProviders.remove(accountId)
    }

    suspend fun listFiles(accountId: String, path: String): Result<List<CloudFileEntry>> {
        return accountProviders[accountId]?.listFiles(path)
            ?: Result.failure(Exception("Account not found: $accountId"))
    }

    fun downloadFile(accountId: String, cloudFileId: String, localPath: String): Flow<CloudTransferProgress> {
        return accountProviders[accountId]?.downloadFile(cloudFileId, localPath)
            ?: throw Exception("Account not found: $accountId")
    }

    fun uploadFile(accountId: String, localPath: String, cloudDirPath: String, fileName: String): Flow<CloudTransferProgress> {
        return accountProviders[accountId]?.uploadFile(localPath, cloudDirPath, fileName)
            ?: throw Exception("Account not found: $accountId")
    }

    suspend fun delete(accountId: String, cloudFileId: String): Result<Unit> {
        return accountProviders[accountId]?.delete(cloudFileId)
            ?: Result.failure(Exception("Account not found: $accountId"))
    }

    suspend fun createFolder(accountId: String, parentPath: String, name: String): Result<CloudFileEntry> {
        return accountProviders[accountId]?.createFolder(parentPath, name)
            ?: Result.failure(Exception("Account not found: $accountId"))
    }

    suspend fun moveFile(
        accountId: String,
        fileId: String,
        currentParentId: String,
        newParentId: String
    ): Result<Unit> {
        return accountProviders[accountId]?.moveFile(fileId, currentParentId, newParentId)
            ?: Result.failure(Exception("Account not found: $accountId"))
    }

    suspend fun rename(accountId: String, cloudFileId: String, newName: String): Result<Unit> {
        return accountProviders[accountId]?.rename(cloudFileId, newName)
            ?: Result.failure(Exception("Account not found: $accountId"))
    }

    fun getAccessToken(accountId: String): String? {
        return accountProviders[accountId]?.getAccessToken()
    }

    suspend fun getFreshAccessToken(accountId: String): String? {
        return accountProviders[accountId]?.getFreshAccessToken()
    }

    fun updateFileContent(accountId: String, cloudFileId: String, localPath: String): Flow<CloudTransferProgress> {
        return accountProviders[accountId]?.updateFileContent(cloudFileId, localPath)
            ?: throw Exception("Account not found: $accountId")
    }

    /**
     * Parse cloud:// URI into CloudPath.
     * Format: cloud://scheme/path OR cloud://scheme:email/path
     * Backward compat: cloud://gdrive/path → accountId = first gdrive account
     */
    fun parseCloudUri(uri: String): CloudPath? {
        if (!uri.startsWith("cloud://")) return null
        val rest = uri.removePrefix("cloud://")
        val authority = rest.substringBefore('/')
        val path = rest.substringAfter('/', "")

        val scheme = authority.substringBefore(':')
        val provider = CloudProvider.entries.find { it.scheme == scheme } ?: return null

        val accountId = if (':' in authority) {
            // Explicit accountId: "gdrive:alice@gmail.com"
            authority
        } else {
            // Backward compat: use first account of this provider
            val accounts = tokenStore.getAccountIds(provider)
            accounts.firstOrNull() ?: authority
        }

        return CloudPath(provider, path, accountId)
    }
}
