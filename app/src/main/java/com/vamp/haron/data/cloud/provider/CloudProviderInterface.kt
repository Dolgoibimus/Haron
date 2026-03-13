package com.vamp.haron.data.cloud.provider

import com.vamp.haron.domain.model.CloudFileEntry
import com.vamp.haron.domain.model.CloudTransferProgress
import kotlinx.coroutines.flow.Flow
import java.io.InputStream

/**
 * Common interface for cloud storage providers (Google Drive, Dropbox, OneDrive).
 */
interface CloudProviderInterface {

    /** Check if currently authenticated */
    fun isAuthenticated(): Boolean

    /** Get auth URL for OAuth2 flow (Chrome Custom Tab or Google Sign-In) */
    fun getAuthUrl(): String?

    /** Handle OAuth2 callback with authorization code */
    suspend fun handleAuthCode(code: String): Result<Unit>

    /** Sign out and revoke tokens */
    suspend fun signOut()

    /** List files in a directory (path = "" for root) */
    suspend fun listFiles(path: String): Result<List<CloudFileEntry>>

    /** Download file to local path */
    fun downloadFile(cloudFileId: String, localPath: String): Flow<CloudTransferProgress>

    /** Upload local file to cloud directory */
    fun uploadFile(localPath: String, cloudDirPath: String, fileName: String): Flow<CloudTransferProgress>

    /** Delete file or folder */
    suspend fun delete(cloudFileId: String): Result<Unit>

    /** Create folder */
    suspend fun createFolder(parentPath: String, name: String): Result<CloudFileEntry>

    /** Rename file or folder */
    suspend fun rename(cloudFileId: String, newName: String): Result<Unit>

    /** Get current access token (for streaming proxy) */
    fun getAccessToken(): String?

    /** Ensure token is fresh (trigger refresh if needed) and return it */
    suspend fun getFreshAccessToken(): String? = getAccessToken()

    /** Move file/folder to a different parent folder */
    suspend fun moveFile(fileId: String, currentParentId: String, newParentId: String): Result<Unit>

    /** Update file content (re-upload) */
    fun updateFileContent(cloudFileId: String, localPath: String): Flow<CloudTransferProgress>
}
