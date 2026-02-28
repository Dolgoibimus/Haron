package com.vamp.haron.domain.usecase

import com.vamp.haron.domain.model.DiscoveredDevice
import com.vamp.haron.domain.model.TransferProgressInfo
import com.vamp.haron.domain.model.TransferProtocol
import com.vamp.haron.domain.repository.TransferRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Tests for DiscoverDevicesUseCase and SendFilesUseCase.
 */
class TransferUseCasesTest {

    private lateinit var transferRepo: TransferRepository

    @Before
    fun setUp() {
        transferRepo = mockk(relaxed = true)
    }

    // region DiscoverDevicesUseCase

    @Test
    fun `DiscoverDevices - returns flow from repository`() = runTest {
        val device = DiscoveredDevice(
            id = "1", name = "Phone", address = "192.168.1.1",
            supportedProtocols = setOf(TransferProtocol.HTTP)
        )
        every { transferRepo.discoverDevices() } returns flowOf(listOf(device))

        val useCase = DiscoverDevicesUseCase(transferRepo)
        val result = useCase().toList()
        assertEquals(1, result.size)
        assertEquals(1, result[0].size)
        assertEquals("Phone", result[0][0].name)
    }

    @Test
    fun `DiscoverDevices - stop delegates to repository`() {
        val useCase = DiscoverDevicesUseCase(transferRepo)
        useCase.stop()
        verify { transferRepo.stopDiscovery() }
    }

    // endregion

    // region SendFilesUseCase

    @Test
    fun `SendFiles - returns progress flow`() = runTest {
        val device = DiscoveredDevice(
            id = "1", name = "Phone", address = "192.168.1.1",
            supportedProtocols = setOf(TransferProtocol.HTTP)
        )
        val progress = TransferProgressInfo(
            bytesTransferred = 500, totalBytes = 1000,
            currentFileIndex = 0, totalFiles = 1,
            currentFileName = "file.txt"
        )
        coEvery { transferRepo.sendFiles(any(), any(), any()) } returns flowOf(progress)

        val useCase = SendFilesUseCase(transferRepo)
        val tempFile = File.createTempFile("test", ".txt")
        try {
            val result = useCase(listOf(tempFile), device, TransferProtocol.HTTP).toList()
            assertEquals(1, result.size)
            assertEquals(500L, result[0].bytesTransferred)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `SendFiles - cancel delegates to repository`() {
        val useCase = SendFilesUseCase(transferRepo)
        useCase.cancel()
        verify { transferRepo.cancelTransfer() }
    }

    // endregion
}
