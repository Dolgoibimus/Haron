package com.vamp.haron.domain.usecase

import com.vamp.haron.data.model.SortDirection
import com.vamp.haron.data.model.SortField
import com.vamp.haron.data.model.SortOrder
import com.vamp.haron.domain.model.ConflictResolution
import com.vamp.haron.domain.model.FileEntry
import com.vamp.haron.domain.model.SecureFileEntry
import com.vamp.haron.domain.repository.FileRepository
import com.vamp.haron.domain.repository.SecureFolderRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Tests for GetFilesUseCase — protected files / virtual secure folder logic.
 */
class GetFilesProtectedTest {

    private lateinit var fakeFileRepo: FakeFileRepository
    private lateinit var fakeSecureRepo: FakeSecureFolderRepository
    private lateinit var useCase: GetFilesUseCase
    private val defaultSort = SortOrder(SortField.NAME, SortDirection.ASCENDING)

    @Before
    fun setup() {
        fakeFileRepo = FakeFileRepository()
        fakeSecureRepo = FakeSecureFolderRepository()
        useCase = GetFilesUseCase(fakeFileRepo, fakeSecureRepo)
    }

    // --- Virtual Secure Path ---

    @Test
    fun `virtual secure path returns all protected entries`() = runTest {
        fakeSecureRepo.entries = listOf(
            se("1", "/data/file1.txt", "file1.txt", 100),
            se("2", "/data/file2.txt", "file2.txt", 200)
        )
        val result = useCase("__haron_secure__", defaultSort, showHidden = false)
        assertTrue(result.isSuccess)
        val files = result.getOrThrow()
        assertEquals(2, files.size)
        assertTrue(files.all { it.isProtected })
        assertEquals("file1.txt", files[0].name)
        assertEquals("file2.txt", files[1].name)
    }

    @Test
    fun `virtual secure path returns dirs with childCount`() = runTest {
        fakeSecureRepo.entries = listOf(
            se("d", "/data/mydir", "mydir", 0, isDir = true),
            se("1", "/data/mydir/a.txt", "a.txt", 100),
            se("2", "/data/mydir/b.txt", "b.txt", 200)
        )
        val result = useCase("__haron_secure__", defaultSort, showHidden = false)
        val files = result.getOrThrow()
        val dir = files.find { it.isDirectory }!!
        assertEquals("mydir", dir.name)
        assertEquals(2, dir.childCount)
    }

    @Test
    fun `virtual secure path empty entries returns empty`() = runTest {
        fakeSecureRepo.entries = emptyList()
        val result = useCase("__haron_secure__", defaultSort, showHidden = false)
        assertTrue(result.getOrThrow().isEmpty())
    }

    // --- Protected directory — direct children ---

    @Test
    fun `protected dir shows direct file children`() = runTest {
        fakeSecureRepo.entries = listOf(
            se("d", "/data/folder", "folder", 0, isDir = true),
            se("1", "/data/folder/file1.txt", "file1.txt", 100),
            se("2", "/data/folder/file2.txt", "file2.txt", 200)
        )
        val result = useCase("/data/folder", defaultSort, showHidden = false, showProtected = true)
        val files = result.getOrThrow()
        assertEquals(2, files.size)
        assertEquals("file1.txt", files[0].name)
        assertEquals("file2.txt", files[1].name)
        assertTrue(files.all { it.isProtected })
    }

    // --- Protected directory — synthesize subdirectories ---

    @Test
    fun `protected dir synthesizes virtual subdirectories`() = runTest {
        fakeSecureRepo.entries = listOf(
            se("d", "/data/folder", "folder", 0, isDir = true),
            se("1", "/data/folder/sub1/a.txt", "a.txt", 100),
            se("2", "/data/folder/sub1/b.txt", "b.txt", 200),
            se("3", "/data/folder/sub2/c.txt", "c.txt", 300)
        )
        val result = useCase("/data/folder", defaultSort, showHidden = false, showProtected = true)
        val files = result.getOrThrow()
        assertEquals(2, files.size) // sub1, sub2
        assertTrue(files.all { it.isDirectory })
        val sub1 = files.find { it.name == "sub1" }!!
        val sub2 = files.find { it.name == "sub2" }!!
        assertEquals(2, sub1.childCount)
        assertEquals(1, sub2.childCount)
        assertTrue(sub1.isProtected)
        assertEquals("/data/folder/sub1", sub1.path)
    }

    @Test
    fun `protected dir mixes files and synthesized dirs`() = runTest {
        fakeSecureRepo.entries = listOf(
            se("d", "/data/folder", "folder", 0, isDir = true),
            se("1", "/data/folder/readme.txt", "readme.txt", 50),
            se("2", "/data/folder/sub/deep.txt", "deep.txt", 100)
        )
        val result = useCase("/data/folder", defaultSort, showHidden = false, showProtected = true)
        val files = result.getOrThrow()
        assertEquals(2, files.size) // dir "sub" + file "readme.txt"
        assertTrue(files[0].isDirectory) // dirs first
        assertEquals("sub", files[0].name)
        assertEquals("readme.txt", files[1].name)
    }

    // --- Virtual subdirectory (not in index, but has protected descendants) ---

    @Test
    fun `virtual subdir via hasProtectedDescendants shows children`() = runTest {
        // /data/folder is protected (in index), /data/folder/sub is NOT in index
        // but files under /data/folder/sub exist
        fakeSecureRepo.entries = listOf(
            se("d", "/data/folder", "folder", 0, isDir = true),
            se("1", "/data/folder/sub/file1.txt", "file1.txt", 100),
            se("2", "/data/folder/sub/file2.txt", "file2.txt", 200)
        )
        val result = useCase("/data/folder/sub", defaultSort, showHidden = false, showProtected = true)
        val files = result.getOrThrow()
        assertEquals(2, files.size)
        assertEquals("file1.txt", files[0].name)
        assertEquals("file2.txt", files[1].name)
    }

    @Test
    fun `deep virtual subdir synthesizes further subdirs`() = runTest {
        fakeSecureRepo.entries = listOf(
            se("d", "/data/folder", "folder", 0, isDir = true),
            se("1", "/data/folder/a/b/c.txt", "c.txt", 100)
        )
        // Navigate into /data/folder/a
        val result = useCase("/data/folder/a", defaultSort, showHidden = false, showProtected = true)
        val files = result.getOrThrow()
        assertEquals(1, files.size)
        assertTrue(files[0].isDirectory)
        assertEquals("b", files[0].name)
        assertEquals(1, files[0].childCount)
    }

    // --- showProtected = false should NOT trigger protected branch ---

    @Test
    fun `showProtected false does not show protected entries`() = runTest {
        fakeSecureRepo.entries = listOf(
            se("d", "/data/folder", "folder", 0, isDir = true),
            se("1", "/data/folder/file.txt", "file.txt", 100)
        )
        fakeFileRepo.filesMap = mapOf("/data/folder" to emptyList())
        val result = useCase("/data/folder", defaultSort, showHidden = false, showProtected = false)
        val files = result.getOrThrow()
        assertTrue(files.isEmpty())
    }

    // --- Normal dir with showProtected mixes virtual entries ---

    @Test
    fun `normal dir with showProtected mixes in protected entries`() = runTest {
        val realFile = fe("real.txt", "/normal/real.txt", size = 500)
        fakeFileRepo.filesMap = mapOf("/normal" to listOf(realFile))
        fakeSecureRepo.entries = listOf(
            se("1", "/normal/secret.txt", "secret.txt", 300)
        )
        val result = useCase("/normal", defaultSort, showHidden = false, showProtected = true)
        val files = result.getOrThrow()
        assertEquals(2, files.size)
        assertEquals("real.txt", files[0].name)
        assertEquals("secret.txt", files[1].name)
        assertTrue(files[1].isProtected)
    }

    @Test
    fun `normal dir does not duplicate existing real file`() = runTest {
        val realFile = fe("same.txt", "/normal/same.txt", size = 500)
        fakeFileRepo.filesMap = mapOf("/normal" to listOf(realFile))
        fakeSecureRepo.entries = listOf(
            se("1", "/normal/same.txt", "same.txt", 300)
        )
        val result = useCase("/normal", defaultSort, showHidden = false, showProtected = true)
        val files = result.getOrThrow()
        assertEquals(1, files.size) // no duplicate
    }

    // --- Sorting ---

    @Test
    fun `protected dir sorts dirs before files`() = runTest {
        fakeSecureRepo.entries = listOf(
            se("d", "/data/folder", "folder", 0, isDir = true),
            se("1", "/data/folder/zebra.txt", "zebra.txt", 50),
            se("2", "/data/folder/alpha/deep.txt", "deep.txt", 100)
        )
        val result = useCase("/data/folder", defaultSort, showHidden = false, showProtected = true)
        val files = result.getOrThrow()
        assertEquals(2, files.size)
        assertTrue(files[0].isDirectory) // dir "alpha" first
        assertEquals("alpha", files[0].name)
        assertEquals("zebra.txt", files[1].name)
    }

    // --- Helpers ---

    private fun se(
        id: String, path: String, name: String, size: Long,
        isDir: Boolean = false, mimeType: String = "application/octet-stream"
    ) = SecureFileEntry(id, path, name, size, isDir, mimeType, System.currentTimeMillis())

    private fun fe(
        name: String, path: String, isDir: Boolean = false,
        size: Long = 0, isHidden: Boolean = false
    ) = FileEntry(name, path, isDir, size, 0L, name.substringAfterLast('.', ""), isHidden, childCount = 0)

    // --- Fake implementations ---

    private class FakeFileRepository : FileRepository {
        var filesMap: Map<String, List<FileEntry>> = emptyMap()

        override suspend fun getFiles(path: String) =
            if (path in filesMap) Result.success(filesMap[path]!!)
            else Result.failure(IllegalArgumentException("Path not found: $path"))
        override fun getStorageRoots() = emptyList<FileEntry>()
        override fun getParentPath(path: String): String? = null
        override suspend fun copyFiles(sourcePaths: List<String>, destinationDir: String, conflictResolution: ConflictResolution) =
            Result.success(0)
        override suspend fun moveFiles(sourcePaths: List<String>, destinationDir: String, conflictResolution: ConflictResolution) =
            Result.success(0)
        override suspend fun deleteFiles(paths: List<String>) = Result.success(0)
        override suspend fun renameFile(path: String, newName: String) = Result.success("")
        override suspend fun createDirectory(parentPath: String, name: String) = Result.success("")
        override suspend fun createFile(parentPath: String, name: String, content: String) = Result.success("")
        override suspend fun copyFilesWithResolutions(sourcePaths: List<String>, destinationDir: String, resolutions: Map<String, ConflictResolution>) =
            Result.success(0)
        override suspend fun moveFilesWithResolutions(sourcePaths: List<String>, destinationDir: String, resolutions: Map<String, ConflictResolution>) =
            Result.success(0)
    }

    private class FakeSecureFolderRepository : SecureFolderRepository {
        var entries: List<SecureFileEntry> = emptyList()

        override suspend fun protectFiles(paths: List<String>, onProgress: (Int, String) -> Unit) =
            Result.success(0)
        override suspend fun unprotectFiles(ids: List<String>, onProgress: (Int, String) -> Unit) =
            Result.success(0)
        override suspend fun getProtectedEntriesForDir(dirPath: String): List<SecureFileEntry> {
            val prefix = "$dirPath/"
            return entries.filter { entry ->
                entry.originalPath.startsWith(prefix) &&
                    !entry.originalPath.removePrefix(prefix).contains('/')
            }
        }
        override suspend fun getAllProtectedEntries() = entries
        override suspend fun decryptToBytes(id: String) = Result.failure<ByteArray>(Exception("test"))
        override suspend fun decryptToCache(id: String) = Result.failure<File>(Exception("test"))
        override suspend fun deleteFromSecureStorage(ids: List<String>, onProgress: (Int, String) -> Unit) =
            Result.success(0)
        override suspend fun getSecureFolderSize() = entries.sumOf { it.originalSize }
        override fun isFileProtected(path: String) = entries.any { it.originalPath == path }
        override fun hasProtectedDescendants(path: String) = entries.any { it.originalPath.startsWith("$path/") }
        override fun getProtectedPaths() = entries.map { it.originalPath }.toSet()
    }
}
