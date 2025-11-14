package org.gameyfin.app.core.filesystem

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FilesystemEndpointTest {

    private lateinit var filesystemService: FilesystemService
    private lateinit var filesystemEndpoint: FilesystemEndpoint

    @BeforeEach
    fun setup() {
        filesystemService = mockk()
        filesystemEndpoint = FilesystemEndpoint(filesystemService)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `listContents should delegate to filesystemService`() {
        val path = "/some/path"
        val expectedResult = listOf(
            FileDto("file1.txt", FileType.FILE, 1),
            FileDto("dir1", FileType.DIRECTORY, 2)
        )

        every { filesystemService.listContents(path) } returns expectedResult

        val result = filesystemEndpoint.listContents(path)

        assertEquals(expectedResult, result)
        verify(exactly = 1) { filesystemService.listContents(path) }
    }

    @Test
    fun `listContents should handle empty path`() {
        val expectedResult = emptyList<FileDto>()

        every { filesystemService.listContents("") } returns expectedResult

        val result = filesystemEndpoint.listContents("")

        assertEquals(expectedResult, result)
        verify(exactly = 1) { filesystemService.listContents("") }
    }

    @Test
    fun `listContents should handle paths with special characters`() {
        val path = "/path with spaces/and-special_chars"
        val expectedResult = listOf(FileDto("file.exe", FileType.FILE, 123))

        every { filesystemService.listContents(path) } returns expectedResult

        val result = filesystemEndpoint.listContents(path)

        assertEquals(expectedResult, result)
        verify(exactly = 1) { filesystemService.listContents(path) }
    }

    @Test
    fun `listSubDirectories should delegate to filesystemService`() {
        val path = "/some/path"
        val expectedResult = listOf(
            FileDto("dir1", FileType.DIRECTORY, 1),
            FileDto("dir2", FileType.DIRECTORY, 2)
        )

        every { filesystemService.listSubDirectories(path) } returns expectedResult

        val result = filesystemEndpoint.listSubDirectories(path)

        assertEquals(expectedResult, result)
        verify(exactly = 1) { filesystemService.listSubDirectories(path) }
    }

    @Test
    fun `listSubDirectories should handle empty result`() {
        val path = "/empty/path"
        val expectedResult = emptyList<FileDto>()

        every { filesystemService.listSubDirectories(path) } returns expectedResult

        val result = filesystemEndpoint.listSubDirectories(path)

        assertEquals(expectedResult, result)
        verify(exactly = 1) { filesystemService.listSubDirectories(path) }
    }

    @Test
    fun `getHostOperatingSystem should delegate to filesystemService and return WINDOWS`() {
        every { filesystemService.getHostOperatingSystem() } returns OperatingSystemType.WINDOWS

        val result = filesystemEndpoint.getHostOperatingSystem()

        assertEquals(OperatingSystemType.WINDOWS, result)
        verify(exactly = 1) { filesystemService.getHostOperatingSystem() }
    }

    @Test
    fun `getHostOperatingSystem should delegate to filesystemService and return MAC`() {
        every { filesystemService.getHostOperatingSystem() } returns OperatingSystemType.MAC

        val result = filesystemEndpoint.getHostOperatingSystem()

        assertEquals(OperatingSystemType.MAC, result)
        verify(exactly = 1) { filesystemService.getHostOperatingSystem() }
    }

    @Test
    fun `getHostOperatingSystem should delegate to filesystemService and return LINUX`() {
        every { filesystemService.getHostOperatingSystem() } returns OperatingSystemType.LINUX

        val result = filesystemEndpoint.getHostOperatingSystem()

        assertEquals(OperatingSystemType.LINUX, result)
        verify(exactly = 1) { filesystemService.getHostOperatingSystem() }
    }

    @Test
    fun `getHostOperatingSystem should delegate to filesystemService and return UNKNOWN`() {
        every { filesystemService.getHostOperatingSystem() } returns OperatingSystemType.UNKNOWN

        val result = filesystemEndpoint.getHostOperatingSystem()

        assertEquals(OperatingSystemType.UNKNOWN, result)
        verify(exactly = 1) { filesystemService.getHostOperatingSystem() }
    }
}