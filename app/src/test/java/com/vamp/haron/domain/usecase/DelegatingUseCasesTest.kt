package com.vamp.haron.domain.usecase

import com.vamp.haron.data.db.entity.FileIndexEntity
import com.vamp.haron.domain.model.CastDevice
import com.vamp.haron.domain.model.CastType
import com.vamp.haron.domain.model.ConflictResolution
import com.vamp.haron.domain.model.FileEntry
import com.vamp.haron.domain.model.StegoResult
import com.vamp.haron.domain.repository.CastRepository
import com.vamp.haron.domain.repository.FileRepository
import com.vamp.haron.domain.repository.IndexProgress
import com.vamp.haron.domain.repository.SearchFilter
import com.vamp.haron.domain.repository.SearchRepository
import com.vamp.haron.domain.repository.SteganographyRepository
import com.vamp.haron.domain.repository.TrashRepository
import com.vamp.haron.domain.model.StegoDetectResult
import com.vamp.haron.domain.model.StegoProgress
import com.vamp.haron.domain.model.RemoteInputEvent
import com.vamp.haron.domain.repository.IndexMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for delegating use cases that simply forward to repositories.
 */
class DelegatingUseCasesTest {

    // region Fakes

    private class FakeFileRepository : FileRepository {
        var copyResult: Result<Int> = Result.success(0)
        var moveResult: Result<Int> = Result.success(0)
        var deleteResult: Result<Int> = Result.success(0)
        var renameResult: Result<String> = Result.success("")
        var createDirResult: Result<String> = Result.success("")
        var createFileResult: Result<String> = Result.success("")

        var lastCopyPaths: List<String> = emptyList()
        var lastCopyDest: String = ""
        var lastCopyResolution: ConflictResolution = ConflictResolution.RENAME
        var lastMovePaths: List<String> = emptyList()
        var lastMoveDest: String = ""
        var lastMoveResolution: ConflictResolution = ConflictResolution.RENAME
        var lastDeletePaths: List<String> = emptyList()

        override suspend fun getFiles(path: String): Result<List<FileEntry>> = Result.success(emptyList())
        override fun getStorageRoots(): List<FileEntry> = emptyList()
        override fun getParentPath(path: String): String? = null

        override suspend fun copyFiles(sourcePaths: List<String>, destinationDir: String, conflictResolution: ConflictResolution): Result<Int> {
            lastCopyPaths = sourcePaths
            lastCopyDest = destinationDir
            lastCopyResolution = conflictResolution
            return copyResult
        }

        override suspend fun moveFiles(sourcePaths: List<String>, destinationDir: String, conflictResolution: ConflictResolution): Result<Int> {
            lastMovePaths = sourcePaths
            lastMoveDest = destinationDir
            lastMoveResolution = conflictResolution
            return moveResult
        }

        override suspend fun deleteFiles(paths: List<String>): Result<Int> {
            lastDeletePaths = paths
            return deleteResult
        }

        override suspend fun renameFile(path: String, newName: String): Result<String> = renameResult
        override suspend fun createDirectory(parentPath: String, name: String): Result<String> = createDirResult
        override suspend fun createFile(parentPath: String, name: String, content: String): Result<String> = createFileResult
        override suspend fun copyFilesWithResolutions(sourcePaths: List<String>, destinationDir: String, resolutions: Map<String, ConflictResolution>): Result<Int> = Result.success(0)
        override suspend fun moveFilesWithResolutions(sourcePaths: List<String>, destinationDir: String, resolutions: Map<String, ConflictResolution>): Result<Int> = Result.success(0)
    }

    private class FakeTrashRepository : TrashRepository {
        var moveResult: Result<Int> = Result.success(0)
        var restoreResult: Result<Int> = Result.success(0)
        var emptyResult: Result<Int> = Result.success(0)
        var cleanResult: Result<Int> = Result.success(0)
        var evictResult: Int = 0

        var lastRestoreIds: List<String> = emptyList()

        override suspend fun moveToTrash(paths: List<String>): Result<Int> = moveResult
        override suspend fun getTrashEntries() = Result.success(emptyList<com.vamp.haron.domain.model.TrashEntry>())
        override suspend fun restoreFromTrash(ids: List<String>): Result<Int> {
            lastRestoreIds = ids
            return restoreResult
        }
        override suspend fun deleteFromTrash(ids: List<String>): Result<Int> = Result.success(0)
        override suspend fun emptyTrash(): Result<Int> = emptyResult
        override suspend fun cleanExpired(): Result<Int> = cleanResult
        override suspend fun getTrashSize(): Long = 0L
        override suspend fun evictToFitSize(maxSizeBytes: Long): Int = evictResult
    }

    private class FakeSearchRepository : SearchRepository {
        var searchResult: List<FileIndexEntity> = emptyList()
        var lastFilter: SearchFilter? = null
        var indexCalled = false
        var lastProgressCallback: ((IndexProgress) -> Unit)? = null

        override suspend fun searchFiles(filter: SearchFilter): List<FileIndexEntity> {
            lastFilter = filter
            return searchResult
        }

        override suspend fun indexAllFiles(onProgress: (IndexProgress) -> Unit) {
            indexCalled = true
            lastProgressCallback = onProgress
            onProgress(IndexProgress(totalFiles = 10, processedFiles = 10, isRunning = false))
        }

        override suspend fun indexByMode(mode: IndexMode, onProgress: (IndexProgress) -> Unit) {}
        override fun startIndexByMode(mode: IndexMode) {}
        override suspend fun getIndexedCount(): Int = 0
        override suspend fun getLastIndexedTime(): Long? = null
        override suspend fun getContentIndexSize(): Long = 0L
        override suspend fun clearIndex() {}
        override fun indexProgressFlow(): Flow<IndexProgress> = emptyFlow()
        override val indexCompleted: StateFlow<Boolean> = MutableStateFlow(false)
        override fun dismissIndexCompleted() {}
        override suspend fun searchContentInFolder(folderPath: String, query: String): Map<String, String> = emptyMap()
        override suspend fun indexFolderContent(folderPath: String, force: Boolean, onProgress: (Int, Int) -> Unit) {}
    }

    private class FakeSteganographyRepository : SteganographyRepository {
        var hideResult: StegoResult = StegoResult.Hidden("/output", 100)
        var extractResult: StegoResult = StegoResult.Extracted("/output", "payload.bin")

        var lastCarrier: String = ""
        var lastPayload: String = ""
        var lastOutput: String = ""
        var lastExtractPath: String = ""
        var lastExtractOutputDir: String = ""

        override fun hidePayload(carrierPath: String, payloadPath: String, outputPath: String): Flow<StegoProgress> = emptyFlow()

        override suspend fun hidePayloadComplete(carrierPath: String, payloadPath: String, outputPath: String): StegoResult {
            lastCarrier = carrierPath
            lastPayload = payloadPath
            lastOutput = outputPath
            return hideResult
        }

        override suspend fun detectHiddenData(filePath: String): StegoDetectResult = StegoDetectResult(false)

        override suspend fun extractPayload(filePath: String, outputDir: String): StegoResult {
            lastExtractPath = filePath
            lastExtractOutputDir = outputDir
            return extractResult
        }
    }

    private class FakeCastRepository : CastRepository {
        var castCalled = false
        var disconnectCalled = false
        var lastDevice: CastDevice? = null
        var lastMediaUrl: String = ""
        var lastMimeType: String = ""

        override fun discoverCastDevices(): Flow<List<CastDevice>> = emptyFlow()

        override suspend fun castMedia(device: CastDevice, mediaUrl: String, mimeType: String) {
            castCalled = true
            lastDevice = device
            lastMediaUrl = mediaUrl
            lastMimeType = mimeType
        }

        override fun sendRemoteInput(event: RemoteInputEvent) {}

        override fun disconnect() {
            disconnectCalled = true
        }

        override fun isConnected(): Boolean = false
    }

    // endregion

    private lateinit var fileRepo: FakeFileRepository
    private lateinit var trashRepo: FakeTrashRepository
    private lateinit var searchRepo: FakeSearchRepository
    private lateinit var stegoRepo: FakeSteganographyRepository
    private lateinit var castRepo: FakeCastRepository

    @Before
    fun setUp() {
        fileRepo = FakeFileRepository()
        trashRepo = FakeTrashRepository()
        searchRepo = FakeSearchRepository()
        stegoRepo = FakeSteganographyRepository()
        castRepo = FakeCastRepository()
    }

    // region CopyFilesUseCase

    @Test
    fun `CopyFiles - empty list returns 0`() = runTest {
        val useCase = CopyFilesUseCase(fileRepo)
        val result = useCase(emptyList(), "/dest")
        assertEquals(0, result.getOrThrow())
    }

    @Test
    fun `CopyFiles - delegates to repository`() = runTest {
        fileRepo.copyResult = Result.success(3)
        val useCase = CopyFilesUseCase(fileRepo)
        val paths = listOf("/a", "/b", "/c")
        val result = useCase(paths, "/dest")
        assertEquals(3, result.getOrThrow())
        assertEquals(paths, fileRepo.lastCopyPaths)
        assertEquals("/dest", fileRepo.lastCopyDest)
    }

    @Test
    fun `CopyFiles - passes conflict resolution`() = runTest {
        fileRepo.copyResult = Result.success(1)
        val useCase = CopyFilesUseCase(fileRepo)
        useCase(listOf("/a"), "/dest", ConflictResolution.REPLACE)
        assertEquals(ConflictResolution.REPLACE, fileRepo.lastCopyResolution)
    }

    @Test
    fun `CopyFiles - default conflict resolution is RENAME`() = runTest {
        fileRepo.copyResult = Result.success(1)
        val useCase = CopyFilesUseCase(fileRepo)
        useCase(listOf("/a"), "/dest")
        assertEquals(ConflictResolution.RENAME, fileRepo.lastCopyResolution)
    }

    // endregion

    // region MoveFilesUseCase

    @Test
    fun `MoveFiles - empty list returns 0`() = runTest {
        val useCase = MoveFilesUseCase(fileRepo)
        val result = useCase(emptyList(), "/dest")
        assertEquals(0, result.getOrThrow())
    }

    @Test
    fun `MoveFiles - delegates to repository`() = runTest {
        fileRepo.moveResult = Result.success(2)
        val useCase = MoveFilesUseCase(fileRepo)
        val paths = listOf("/x", "/y")
        val result = useCase(paths, "/dest")
        assertEquals(2, result.getOrThrow())
        assertEquals(paths, fileRepo.lastMovePaths)
        assertEquals("/dest", fileRepo.lastMoveDest)
    }

    @Test
    fun `MoveFiles - passes conflict resolution`() = runTest {
        fileRepo.moveResult = Result.success(1)
        val useCase = MoveFilesUseCase(fileRepo)
        useCase(listOf("/a"), "/dest", ConflictResolution.SKIP)
        assertEquals(ConflictResolution.SKIP, fileRepo.lastMoveResolution)
    }

    // endregion

    // region DeleteFilesUseCase

    @Test
    fun `DeleteFiles - empty list returns 0`() = runTest {
        val useCase = DeleteFilesUseCase(fileRepo)
        val result = useCase(emptyList())
        assertEquals(0, result.getOrThrow())
    }

    @Test
    fun `DeleteFiles - delegates to repository`() = runTest {
        fileRepo.deleteResult = Result.success(5)
        val useCase = DeleteFilesUseCase(fileRepo)
        val paths = listOf("/a", "/b", "/c", "/d", "/e")
        val result = useCase(paths)
        assertEquals(5, result.getOrThrow())
        assertEquals(paths, fileRepo.lastDeletePaths)
    }

    // endregion

    // region RestoreFromTrashUseCase

    @Test
    fun `RestoreFromTrash - empty list returns 0`() = runTest {
        val useCase = RestoreFromTrashUseCase(trashRepo)
        val result = useCase(emptyList())
        assertEquals(0, result.getOrThrow())
    }

    @Test
    fun `RestoreFromTrash - delegates to repository`() = runTest {
        trashRepo.restoreResult = Result.success(2)
        val useCase = RestoreFromTrashUseCase(trashRepo)
        val ids = listOf("id1", "id2")
        val result = useCase(ids)
        assertEquals(2, result.getOrThrow())
        assertEquals(ids, trashRepo.lastRestoreIds)
    }

    // endregion

    // region EmptyTrashUseCase

    @Test
    fun `EmptyTrash - delegates to repository`() = runTest {
        trashRepo.emptyResult = Result.success(10)
        val useCase = EmptyTrashUseCase(trashRepo)
        val result = useCase()
        assertEquals(10, result.getOrThrow())
    }

    // endregion

    // region CleanExpiredTrashUseCase

    @Test
    fun `CleanExpiredTrash - delegates to repository`() = runTest {
        trashRepo.cleanResult = Result.success(3)
        val useCase = CleanExpiredTrashUseCase(trashRepo)
        val result = useCase()
        assertEquals(3, result.getOrThrow())
    }

    // endregion

    // region SearchFilesUseCase

    @Test
    fun `SearchFiles - delegates with filter`() = runTest {
        val entity = FileIndexEntity(
            path = "/test.txt", name = "test.txt", extension = "txt",
            size = 100, lastModified = 0, mimeType = "text/plain",
            parentPath = "/", isDirectory = false, isHidden = false
        )
        searchRepo.searchResult = listOf(entity)
        val useCase = SearchFilesUseCase(searchRepo)
        val filter = SearchFilter(query = "test")
        val result = useCase(filter)
        assertEquals(1, result.size)
        assertEquals("test.txt", result[0].name)
        assertEquals(filter, searchRepo.lastFilter)
    }

    @Test
    fun `SearchFiles - empty result`() = runTest {
        searchRepo.searchResult = emptyList()
        val useCase = SearchFilesUseCase(searchRepo)
        val result = useCase(SearchFilter(query = "nothing"))
        assertTrue(result.isEmpty())
    }

    // endregion

    // region IndexFilesUseCase

    @Test
    fun `IndexFiles - delegates to repository`() = runTest {
        val useCase = IndexFilesUseCase(searchRepo)
        useCase()
        assertTrue(searchRepo.indexCalled)
    }

    @Test
    fun `IndexFiles - passes progress callback`() = runTest {
        val progresses = mutableListOf<IndexProgress>()
        val useCase = IndexFilesUseCase(searchRepo)
        useCase { progresses.add(it) }
        assertTrue(progresses.isNotEmpty())
        assertEquals(10, progresses.last().totalFiles)
    }

    // endregion

    // region HideInFileUseCase

    @Test
    fun `HideInFile - delegates to stego repository`() = runTest {
        stegoRepo.hideResult = StegoResult.Hidden("/out.png", 200)
        val useCase = HideInFileUseCase(stegoRepo)
        val result = useCase("/carrier.png", "/payload.bin", "/out.png")
        assertTrue(result is StegoResult.Hidden)
        assertEquals("/out.png", (result as StegoResult.Hidden).outputPath)
        assertEquals("/carrier.png", stegoRepo.lastCarrier)
        assertEquals("/payload.bin", stegoRepo.lastPayload)
        assertEquals("/out.png", stegoRepo.lastOutput)
    }

    @Test
    fun `HideInFile - returns error from repository`() = runTest {
        stegoRepo.hideResult = StegoResult.Error("too large")
        val useCase = HideInFileUseCase(stegoRepo)
        val result = useCase("/c", "/p", "/o")
        assertTrue(result is StegoResult.Error)
        assertEquals("too large", (result as StegoResult.Error).message)
    }

    // endregion

    // region ExtractHiddenUseCase

    @Test
    fun `ExtractHidden - delegates to stego repository`() = runTest {
        stegoRepo.extractResult = StegoResult.Extracted("/output/secret.bin", "secret.bin")
        val useCase = ExtractHiddenUseCase(stegoRepo)
        val result = useCase("/stego.png", "/output")
        assertTrue(result is StegoResult.Extracted)
        assertEquals("secret.bin", (result as StegoResult.Extracted).payloadName)
        assertEquals("/stego.png", stegoRepo.lastExtractPath)
        assertEquals("/output", stegoRepo.lastExtractOutputDir)
    }

    @Test
    fun `ExtractHidden - returns error from repository`() = runTest {
        stegoRepo.extractResult = StegoResult.Error("no hidden data")
        val useCase = ExtractHiddenUseCase(stegoRepo)
        val result = useCase("/file.png", "/out")
        assertTrue(result is StegoResult.Error)
    }

    // endregion

    // region StartCastUseCase

    @Test
    fun `StartCast - delegates to cast repository`() = runTest {
        val useCase = StartCastUseCase(castRepo)
        val device = CastDevice("id1", "Living Room", CastType.CHROMECAST)
        useCase(device, "http://media.mp4", "video/mp4")
        assertTrue(castRepo.castCalled)
        assertEquals(device, castRepo.lastDevice)
        assertEquals("http://media.mp4", castRepo.lastMediaUrl)
        assertEquals("video/mp4", castRepo.lastMimeType)
    }

    @Test
    fun `StartCast - disconnect delegates to repository`() {
        val useCase = StartCastUseCase(castRepo)
        useCase.disconnect()
        assertTrue(castRepo.disconnectCalled)
    }

    // endregion
}
