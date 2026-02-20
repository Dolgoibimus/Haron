package com.vamp.haron.domain.repository

import com.vamp.haron.domain.model.ConflictResolution
import com.vamp.haron.domain.model.FileEntry

interface FileRepository {
    suspend fun getFiles(path: String): Result<List<FileEntry>>
    fun getStorageRoots(): List<FileEntry>
    fun getParentPath(path: String): String?
    suspend fun copyFiles(sourcePaths: List<String>, destinationDir: String,
                          conflictResolution: ConflictResolution = ConflictResolution.RENAME): Result<Int>
    suspend fun moveFiles(sourcePaths: List<String>, destinationDir: String,
                          conflictResolution: ConflictResolution = ConflictResolution.RENAME): Result<Int>
    suspend fun deleteFiles(paths: List<String>): Result<Int>
    suspend fun renameFile(path: String, newName: String): Result<String>
    suspend fun createDirectory(parentPath: String, name: String): Result<String>
    suspend fun createFile(parentPath: String, name: String, content: String = ""): Result<String>
}
