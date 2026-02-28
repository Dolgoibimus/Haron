package com.vamp.haron.domain.usecase

import android.content.Context
import com.vamp.haron.R
import com.vamp.haron.domain.model.ConflictResolution
import com.vamp.haron.domain.model.FileEntry
import com.vamp.haron.domain.repository.FileRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for BatchRenameUseCase — batch renaming with validation,
 * case-only rename (2-step), and partial success.
 * Uses fake FileRepository to avoid mockk's inline Result class issues.
 */
class BatchRenameUseCaseTest {

    private lateinit var context: Context
    private lateinit var fileRepo: FakeFileRepoForBatch
    private lateinit var useCase: BatchRenameUseCase

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        fileRepo = FakeFileRepoForBatch()
        useCase = BatchRenameUseCase(fileRepo, context)

        every { context.getString(eq(R.string.batch_rename_error), any()) } answers {
            "Batch rename error: ${args[1]}"
        }
    }

    @Test
    fun `empty list returns 0`() = runTest {
        val result = useCase(emptyList())
        assertEquals(0, result.getOrThrow())
    }

    @Test
    fun `normal rename succeeds`() = runTest {
        fileRepo.renameHandler = { _, newName -> Result.success("/dir/$newName") }
        val result = useCase(listOf("/dir/old.txt" to "new.txt"))
        assertEquals(1, result.getOrThrow())
    }

    @Test
    fun `skips identical names`() = runTest {
        val result = useCase(listOf("/dir/same.txt" to "same.txt"))
        assertEquals(0, result.getOrThrow())
    }

    @Test
    fun `skips invalid names - empty`() = runTest {
        val result = useCase(listOf("/dir/file.txt" to ""))
        assertEquals(0, result.getOrThrow())
    }

    @Test
    fun `skips invalid names - with slashes`() = runTest {
        val result = useCase(listOf("/dir/file.txt" to "new/name.txt"))
        assertEquals(0, result.getOrThrow())
    }

    @Test
    fun `case-only rename uses 2-step via temp name`() = runTest {
        var tempPath: String? = null
        fileRepo.renameHandler = { path, newName ->
            if (newName.startsWith(".tmp_rename_")) {
                tempPath = "/dir/$newName"
                Result.success(tempPath!!)
            } else {
                Result.success("/dir/$newName")
            }
        }

        val result = useCase(listOf("/dir/File.TXT" to "file.txt"))
        assertEquals(1, result.getOrThrow())
    }

    @Test
    fun `partial success returns success count`() = runTest {
        var callCount = 0
        fileRepo.renameHandler = { _, newName ->
            callCount++
            if (newName == "b_new.txt") {
                Result.failure(Exception("access denied"))
            } else {
                Result.success("/dir/$newName")
            }
        }

        val result = useCase(listOf(
            "/dir/a.txt" to "a_new.txt",
            "/dir/b.txt" to "b_new.txt",
            "/dir/c.txt" to "c_new.txt"
        ))
        assertEquals(2, result.getOrThrow())
    }

    @Test
    fun `all fail returns failure`() = runTest {
        fileRepo.renameHandler = { _, _ -> Result.failure(Exception("denied")) }

        val result = useCase(listOf(
            "/dir/a.txt" to "a_new.txt",
            "/dir/b.txt" to "b_new.txt"
        ))
        assertTrue(result.isFailure)
    }

    /** Fake FileRepository with configurable rename handler */
    private class FakeFileRepoForBatch : FileRepository {
        var renameHandler: suspend (path: String, newName: String) -> Result<String> = { _, n -> Result.success(n) }

        override suspend fun getFiles(path: String) = Result.success(emptyList<FileEntry>())
        override fun getStorageRoots() = emptyList<FileEntry>()
        override fun getParentPath(path: String): String? = null
        override suspend fun copyFiles(sourcePaths: List<String>, destinationDir: String, conflictResolution: ConflictResolution) = Result.success(0)
        override suspend fun moveFiles(sourcePaths: List<String>, destinationDir: String, conflictResolution: ConflictResolution) = Result.success(0)
        override suspend fun deleteFiles(paths: List<String>) = Result.success(0)
        override suspend fun renameFile(path: String, newName: String) = renameHandler(path, newName)
        override suspend fun createDirectory(parentPath: String, name: String) = Result.success("")
        override suspend fun createFile(parentPath: String, name: String, content: String) = Result.success("")
        override suspend fun copyFilesWithResolutions(sourcePaths: List<String>, destinationDir: String, resolutions: Map<String, ConflictResolution>) = Result.success(0)
        override suspend fun moveFilesWithResolutions(sourcePaths: List<String>, destinationDir: String, resolutions: Map<String, ConflictResolution>) = Result.success(0)
    }
}
