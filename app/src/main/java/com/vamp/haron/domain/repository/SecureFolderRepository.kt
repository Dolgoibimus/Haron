package com.vamp.haron.domain.repository

import com.vamp.haron.domain.model.SecureFileEntry
import java.io.File

interface SecureFolderRepository {
    suspend fun protectFiles(paths: List<String>, onProgress: (Int, String) -> Unit): Result<Int>
    suspend fun unprotectFiles(ids: List<String>, onProgress: (Int, String) -> Unit): Result<Int>
    suspend fun getProtectedEntriesForDir(dirPath: String): List<SecureFileEntry>
    suspend fun getAllProtectedEntries(): List<SecureFileEntry>
    suspend fun decryptToCache(id: String): Result<File>
    suspend fun decryptToBytes(id: String): Result<ByteArray>
    suspend fun deleteFromSecureStorage(ids: List<String>, onProgress: (Int, String) -> Unit): Result<Int>
    suspend fun getSecureFolderSize(): Long
    fun isFileProtected(path: String): Boolean
    fun hasProtectedDescendants(path: String): Boolean
    fun getProtectedPaths(): Set<String>
}
