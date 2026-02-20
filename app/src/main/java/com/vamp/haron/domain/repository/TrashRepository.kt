package com.vamp.haron.domain.repository

import com.vamp.haron.domain.model.TrashEntry

interface TrashRepository {
    suspend fun moveToTrash(paths: List<String>): Result<Int>
    suspend fun getTrashEntries(): Result<List<TrashEntry>>
    suspend fun restoreFromTrash(ids: List<String>): Result<Int>
    suspend fun deleteFromTrash(ids: List<String>): Result<Int>
    suspend fun emptyTrash(): Result<Int>
    suspend fun cleanExpired(): Result<Int>
    suspend fun getTrashSize(): Long
}
