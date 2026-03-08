package com.vamp.haron.domain.usecase

import com.vamp.haron.data.datastore.HaronPreferences
import com.vamp.haron.domain.model.TrashEntry
import com.vamp.haron.domain.repository.TrashRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for MoveToTrashUseCase — trash move with auto-eviction.
 * Uses fake TrashRepository to avoid mockk inline Result issues.
 */
class MoveToTrashUseCaseTest {

    private lateinit var trashRepo: FakeTrashRepoForMove
    private lateinit var preferences: HaronPreferences
    private lateinit var useCase: MoveToTrashUseCase

    @Before
    fun setUp() {
        trashRepo = FakeTrashRepoForMove()
        preferences = mockk(relaxed = true)
        useCase = MoveToTrashUseCase(trashRepo, preferences)
    }

    @Test
    fun `empty list returns 0`() = runTest {
        val result = useCase(emptyList())
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().movedCount)
    }

    @Test
    fun `move without eviction`() = runTest {
        trashRepo.moveResult = Result.success(1)
        every { preferences.trashMaxSizeMb } returns 0 // unlimited

        val result = useCase(listOf("/file.txt"))
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().movedCount)
        assertEquals(0, result.getOrThrow().evictedCount)
    }

    @Test
    fun `move with auto-eviction`() = runTest {
        trashRepo.moveResult = Result.success(1)
        trashRepo.evictResult = 3
        every { preferences.trashMaxSizeMb } returns 500

        val result = useCase(listOf("/big.zip"))
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().movedCount)
        assertEquals(3, result.getOrThrow().evictedCount)
    }

    @Test
    fun `repository failure propagates`() = runTest {
        trashRepo.moveResult = Result.failure(Exception("disk full"))
        every { preferences.trashMaxSizeMb } returns 500

        val result = useCase(listOf("/file.txt"))
        assertTrue(result.isFailure)
    }

    /** Fake TrashRepository to avoid mockk inline Result issues */
    private class FakeTrashRepoForMove : TrashRepository {
        var moveResult: Result<Int> = Result.success(0)
        var evictResult: Int = 0

        override suspend fun moveToTrash(paths: List<String>) = moveResult
        override suspend fun getTrashEntries() = Result.success(emptyList<TrashEntry>())
        override suspend fun restoreFromTrash(ids: List<String>) = Result.success(0)
        override suspend fun deleteFromTrash(ids: List<String>) = Result.success(0)
        override suspend fun deleteFromTrashWithProgress(ids: List<String>, onProgress: suspend (Int, Int, String) -> Unit) = Result.success(0)
        override suspend fun emptyTrash() = Result.success(0)
        override suspend fun cleanExpired() = Result.success(0)
        override suspend fun getTrashSize() = 0L
        override suspend fun evictToFitSize(maxSizeBytes: Long) = evictResult
    }
}
