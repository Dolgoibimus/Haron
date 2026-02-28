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
 * Tests for validation use cases: RenameFile, CreateFile, CreateDirectory.
 * Uses mockk for Android Context, fake for FileRepository (Result inline class issue).
 */
class ValidationUseCasesTest {

    private lateinit var context: Context
    private lateinit var fileRepo: FakeFileRepoForValidation

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        fileRepo = FakeFileRepoForValidation()

        every { context.getString(R.string.error_name_empty) } returns "Name cannot be empty"
        every { context.getString(R.string.error_name_slashes) } returns "Name cannot contain slashes"
        every { context.getString(R.string.error_file_name_empty) } returns "File name cannot be empty"
        every { context.getString(R.string.error_file_name_slashes) } returns "File name cannot contain slashes"
        every { context.getString(R.string.error_folder_name_empty) } returns "Folder name cannot be empty"
        every { context.getString(R.string.error_folder_name_slashes) } returns "Folder name cannot contain slashes"
    }

    // region RenameFileUseCase

    @Test
    fun `RenameFile - empty name fails`() = runTest {
        val useCase = RenameFileUseCase(fileRepo, context)
        val result = useCase("/test.txt", "")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `RenameFile - whitespace-only name fails`() = runTest {
        val useCase = RenameFileUseCase(fileRepo, context)
        val result = useCase("/test.txt", "   ")
        assertTrue(result.isFailure)
    }

    @Test
    fun `RenameFile - name with forward slash fails`() = runTest {
        val useCase = RenameFileUseCase(fileRepo, context)
        val result = useCase("/test.txt", "new/name.txt")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("slashes") == true)
    }

    @Test
    fun `RenameFile - name with backslash fails`() = runTest {
        val useCase = RenameFileUseCase(fileRepo, context)
        val result = useCase("/test.txt", "new\\name.txt")
        assertTrue(result.isFailure)
    }

    @Test
    fun `RenameFile - valid name delegates to repository`() = runTest {
        fileRepo.renameResult = Result.success("/newname.txt")
        val useCase = RenameFileUseCase(fileRepo, context)
        val result = useCase("/test.txt", "newname.txt")
        assertTrue(result.isSuccess)
        assertEquals("/newname.txt", result.getOrThrow())
    }

    @Test
    fun `RenameFile - trims whitespace`() = runTest {
        fileRepo.renameResult = Result.success("/trimmed.txt")
        val useCase = RenameFileUseCase(fileRepo, context)
        val result = useCase("/test.txt", "  trimmed.txt  ")
        assertTrue(result.isSuccess)
        assertEquals("/trimmed.txt", result.getOrThrow())
    }

    // endregion

    // region CreateFileUseCase

    @Test
    fun `CreateFile - empty name fails`() = runTest {
        val useCase = CreateFileUseCase(fileRepo, context)
        val result = useCase("/parent", "")
        assertTrue(result.isFailure)
    }

    @Test
    fun `CreateFile - name with slashes fails`() = runTest {
        val useCase = CreateFileUseCase(fileRepo, context)
        val result = useCase("/parent", "dir/file.txt")
        assertTrue(result.isFailure)
    }

    @Test
    fun `CreateFile - valid name delegates to repository`() = runTest {
        fileRepo.createFileResult = Result.success("/parent/file.txt")
        val useCase = CreateFileUseCase(fileRepo, context)
        val result = useCase("/parent", "file.txt")
        assertTrue(result.isSuccess)
        assertEquals("/parent/file.txt", result.getOrThrow())
    }

    @Test
    fun `CreateFile - passes content`() = runTest {
        fileRepo.createFileResult = Result.success("/parent/file.txt")
        val useCase = CreateFileUseCase(fileRepo, context)
        val result = useCase("/parent", "file.txt", "hello")
        assertTrue(result.isSuccess)
    }

    // endregion

    // region CreateDirectoryUseCase

    @Test
    fun `CreateDirectory - empty name fails`() = runTest {
        val useCase = CreateDirectoryUseCase(fileRepo, context)
        val result = useCase("/parent", "")
        assertTrue(result.isFailure)
    }

    @Test
    fun `CreateDirectory - name with slashes fails`() = runTest {
        val useCase = CreateDirectoryUseCase(fileRepo, context)
        val result = useCase("/parent", "sub/dir")
        assertTrue(result.isFailure)
    }

    @Test
    fun `CreateDirectory - valid name delegates to repository`() = runTest {
        fileRepo.createDirResult = Result.success("/parent/newdir")
        val useCase = CreateDirectoryUseCase(fileRepo, context)
        val result = useCase("/parent", "newdir")
        assertTrue(result.isSuccess)
        assertEquals("/parent/newdir", result.getOrThrow())
    }

    // endregion

    /** Fake FileRepository to avoid mockk's inline Result class issues */
    private class FakeFileRepoForValidation : FileRepository {
        var renameResult: Result<String> = Result.success("")
        var createFileResult: Result<String> = Result.success("")
        var createDirResult: Result<String> = Result.success("")

        override suspend fun getFiles(path: String) = Result.success(emptyList<FileEntry>())
        override fun getStorageRoots() = emptyList<FileEntry>()
        override fun getParentPath(path: String): String? = null
        override suspend fun copyFiles(sourcePaths: List<String>, destinationDir: String, conflictResolution: ConflictResolution) = Result.success(0)
        override suspend fun moveFiles(sourcePaths: List<String>, destinationDir: String, conflictResolution: ConflictResolution) = Result.success(0)
        override suspend fun deleteFiles(paths: List<String>) = Result.success(0)
        override suspend fun renameFile(path: String, newName: String) = renameResult
        override suspend fun createDirectory(parentPath: String, name: String) = createDirResult
        override suspend fun createFile(parentPath: String, name: String, content: String) = createFileResult
        override suspend fun copyFilesWithResolutions(sourcePaths: List<String>, destinationDir: String, resolutions: Map<String, ConflictResolution>) = Result.success(0)
        override suspend fun moveFilesWithResolutions(sourcePaths: List<String>, destinationDir: String, resolutions: Map<String, ConflictResolution>) = Result.success(0)
    }
}
