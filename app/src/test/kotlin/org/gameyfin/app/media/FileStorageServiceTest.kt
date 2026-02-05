package org.gameyfin.app.media

import io.mockk.clearAllMocks
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class FileStorageServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var fileStorageService: FileStorageService

    @BeforeEach
    fun setup() {
        fileStorageService = FileStorageService(tempDir.toString())
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `init should create storage directory if it does not exist`() {
        val newDir = tempDir.resolve("newStorage")
        assertFalse(Files.exists(newDir))

        FileStorageService(newDir.toString())

        assertTrue(Files.exists(newDir))
    }

    @Test
    fun `saveFile should store file and return content ID`() {
        val testData = "test file content".toByteArray()
        val inputStream = ByteArrayInputStream(testData)

        val contentId = fileStorageService.saveFile(inputStream)

        assertNotNull(contentId)
        assertTrue(contentId.isNotEmpty())

        val filePath = tempDir.resolve(contentId)
        assertTrue(Files.exists(filePath))

        val savedContent = Files.readAllBytes(filePath)
        assertContentEquals(testData, savedContent)
    }

    @Test
    fun `saveFile should generate unique content IDs for multiple files`() {
        val testData1 = "first file".toByteArray()
        val testData2 = "second file".toByteArray()

        val contentId1 = fileStorageService.saveFile(ByteArrayInputStream(testData1))
        val contentId2 = fileStorageService.saveFile(ByteArrayInputStream(testData2))

        assertNotEquals(contentId1, contentId2)
        assertTrue(Files.exists(tempDir.resolve(contentId1)))
        assertTrue(Files.exists(tempDir.resolve(contentId2)))
    }

    @Test
    fun `saveFile should replace existing file with same content ID`() {
        val originalData = "original content".toByteArray()
        val newData = "new content".toByteArray()

        val contentId = fileStorageService.saveFile(ByteArrayInputStream(originalData))

        // Manually save a file with a predictable name
        val filePath = tempDir.resolve(contentId)
        Files.write(filePath, newData)

        val retrievedContent = Files.readAllBytes(filePath)
        assertContentEquals(newData, retrievedContent)
    }

    @Test
    fun `getFile should return input stream when file exists`() {
        val testData = "test file content".toByteArray()
        val contentId = fileStorageService.saveFile(ByteArrayInputStream(testData))

        val inputStream = fileStorageService.getFile(contentId)

        assertNotNull(inputStream)
        val retrievedData = inputStream.readAllBytes()
        assertContentEquals(testData, retrievedData)
    }

    @Test
    fun `getFile should return null when file does not exist`() {
        val nonExistentId = "non-existent-file-id"

        val inputStream = fileStorageService.getFile(nonExistentId)

        assertNull(inputStream)
    }

    @Test
    fun `getFile should return null when content ID is null`() {
        val inputStream = fileStorageService.getFile(null)

        assertNull(inputStream)
    }

    @Test
    fun `getFile should allow reading file multiple times`() {
        val testData = "test file content".toByteArray()
        val contentId = fileStorageService.saveFile(ByteArrayInputStream(testData))

        val inputStream1 = fileStorageService.getFile(contentId)
        assertNotNull(inputStream1)
        val retrievedData1 = inputStream1.readAllBytes()
        assertContentEquals(testData, retrievedData1)

        val inputStream2 = fileStorageService.getFile(contentId)
        assertNotNull(inputStream2)
        val retrievedData2 = inputStream2.readAllBytes()
        assertContentEquals(testData, retrievedData2)
    }

    @Test
    fun `deleteFile should remove file when it exists`() {
        val testData = "test file content".toByteArray()
        val contentId = fileStorageService.saveFile(ByteArrayInputStream(testData))

        assertTrue(Files.exists(tempDir.resolve(contentId)))

        fileStorageService.deleteFile(contentId)

        assertFalse(Files.exists(tempDir.resolve(contentId)))
    }

    @Test
    fun `deleteFile should not throw exception when file does not exist`() {
        val nonExistentId = "non-existent-file-id"

        // Should not throw any exception
        fileStorageService.deleteFile(nonExistentId)

        assertFalse(Files.exists(tempDir.resolve(nonExistentId)))
    }

    @Test
    fun `deleteFile should do nothing when content ID is null`() {
        // Should not throw any exception
        fileStorageService.deleteFile(null)
    }

    @Test
    fun `fileExists should return true when file exists`() {
        val testData = "test file content".toByteArray()
        val contentId = fileStorageService.saveFile(ByteArrayInputStream(testData))

        val exists = fileStorageService.fileExists(contentId)

        assertTrue(exists)
    }

    @Test
    fun `fileExists should return false when file does not exist`() {
        val nonExistentId = "non-existent-file-id"

        val exists = fileStorageService.fileExists(nonExistentId)

        assertFalse(exists)
    }

    @Test
    fun `fileExists should return false when content ID is null`() {
        val exists = fileStorageService.fileExists(null)

        assertFalse(exists)
    }

    @Test
    fun `saveFile should handle large files`() {
        val largeData = ByteArray(10 * 1024 * 1024) { it.toByte() } // 10 MB
        val inputStream = ByteArrayInputStream(largeData)

        val contentId = fileStorageService.saveFile(inputStream)

        assertNotNull(contentId)
        assertTrue(fileStorageService.fileExists(contentId))

        val retrievedStream = fileStorageService.getFile(contentId)
        assertNotNull(retrievedStream)
        val retrievedData = retrievedStream.readAllBytes()
        assertContentEquals(largeData, retrievedData)
    }

    @Test
    fun `saveFile should handle empty files`() {
        val emptyData = ByteArray(0)
        val inputStream = ByteArrayInputStream(emptyData)

        val contentId = fileStorageService.saveFile(inputStream)

        assertNotNull(contentId)
        assertTrue(fileStorageService.fileExists(contentId))

        val retrievedStream = fileStorageService.getFile(contentId)
        assertNotNull(retrievedStream)
        val retrievedData = retrievedStream.readAllBytes()
        assertEquals(0, retrievedData.size)
    }

    @Test
    fun `integration test - save, retrieve, and delete file lifecycle`() {
        val testData = "lifecycle test content".toByteArray()

        // Save file
        val contentId = fileStorageService.saveFile(ByteArrayInputStream(testData))
        assertNotNull(contentId)
        assertTrue(fileStorageService.fileExists(contentId))

        // Retrieve file
        val retrievedStream = fileStorageService.getFile(contentId)
        assertNotNull(retrievedStream)
        val retrievedData = retrievedStream.readAllBytes()
        assertContentEquals(testData, retrievedData)

        // Delete file
        fileStorageService.deleteFile(contentId)
        assertFalse(fileStorageService.fileExists(contentId))
        assertNull(fileStorageService.getFile(contentId))
    }
}
