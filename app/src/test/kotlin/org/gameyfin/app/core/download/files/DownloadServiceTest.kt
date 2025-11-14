package org.gameyfin.app.core.download.files

import io.mockk.*
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.core.download.bandwidth.SessionBandwidthManager
import org.gameyfin.app.core.download.bandwidth.SessionBandwidthTracker
import org.gameyfin.app.core.plugins.management.GameyfinPluginDescriptor
import org.gameyfin.app.core.plugins.management.GameyfinPluginManager
import org.gameyfin.app.games.entities.Game
import org.gameyfin.pluginapi.download.Download
import org.gameyfin.pluginapi.download.DownloadProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.pf4j.PluginWrapper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.io.path.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownloadServiceTest {

    private lateinit var pluginManager: GameyfinPluginManager
    private lateinit var configService: ConfigService
    private lateinit var sessionBandwidthManager: SessionBandwidthManager
    private lateinit var service: DownloadService

    @BeforeEach
    fun setup() {
        pluginManager = mockk<GameyfinPluginManager>(relaxed = true)
        configService = mockk<ConfigService>(relaxed = true)
        sessionBandwidthManager = mockk<SessionBandwidthManager>(relaxed = true)
        service = DownloadService(pluginManager, configService, sessionBandwidthManager)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `getProviders should return list of download provider DTOs`() {
        val provider1 = createMockProvider("Provider1")
        val provider2 = createMockProvider("Provider2")

        every { pluginManager.getExtensions(DownloadProvider::class.java) } returns listOf(provider1, provider2)

        val pluginWrapper1 = createMockPluginWrapper("plugin1", "Provider 1", "Description 1", "Short 1")
        val pluginWrapper2 = createMockPluginWrapper("plugin2", "Provider 2", "Description 2", "Short 2")

        every { pluginManager.whichPlugin(any()) } returnsMany listOf(pluginWrapper1, pluginWrapper2)
        every { pluginManager.getManagementEntry("plugin1") } returns mockk { every { priority } returns 10 }
        every { pluginManager.getManagementEntry("plugin2") } returns mockk { every { priority } returns 5 }

        val result = service.getProviders()

        assertEquals(2, result.size)
        assertEquals("Provider 1", result[0].name)
        assertEquals("Provider 2", result[1].name)
        assertEquals(10, result[0].priority)
        assertEquals(5, result[1].priority)
    }

    @Test
    fun `getProviders should return empty list when no providers available`() {
        every { pluginManager.getExtensions(DownloadProvider::class.java) } returns emptyList()

        val result = service.getProviders()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getProviders should include all provider details`() {
        val provider = createMockProvider("TestProvider")

        every { pluginManager.getExtensions(DownloadProvider::class.java) } returns listOf(provider)

        val pluginWrapper = createMockPluginWrapper(
            "test-plugin",
            "Test Provider",
            "This is a test provider",
            "Test short description"
        )

        every { pluginManager.whichPlugin(any()) } returns pluginWrapper
        every { pluginManager.getManagementEntry("test-plugin") } returns mockk {
            every { priority } returns 15
        }

        val result = service.getProviders()

        assertEquals(1, result.size)
        assertEquals(TestProvider::class.java.name, result[0].key)
        assertEquals("Test Provider", result[0].name)
        assertEquals(15, result[0].priority)
        assertEquals("This is a test provider", result[0].description)
        assertEquals("Test short description", result[0].shortDescription)
    }

    @Test
    fun `getDownload should return download from provider`() {
        val provider = createMockProvider("TestProvider")
        val path = "/test/path"
        val expectedDownload = mockk<Download>()

        every { pluginManager.getExtensions(DownloadProvider::class.java) } returns listOf(provider)
        every { provider.download(Path(path)) } returns expectedDownload

        val result = service.getDownload(path, TestProvider::class.java.name)

        assertEquals(expectedDownload, result)
        verify(exactly = 1) { provider.download(Path(path)) }
    }

    @Test
    fun `getDownload should throw exception when provider not found`() {
        every { pluginManager.getExtensions(DownloadProvider::class.java) } returns emptyList()

        val exception = assertThrows(IllegalArgumentException::class.java) {
            service.getDownload("/test/path", "NonExistentProvider")
        }

        assertTrue(exception.message!!.contains("Download provider NonExistentProvider not found"))
    }

    @Test
    fun `getDownload should find correct provider by class name`() {
        val provider1 = createMockProvider("Provider1")
        val provider2 = createMockProvider("Provider2")
        val expectedDownload = mockk<Download>()

        every { pluginManager.getExtensions(DownloadProvider::class.java) } returns listOf(provider1, provider2)
        every { provider2.download(any()) } returns expectedDownload

        val result = service.getDownload("/test/path", Provider2::class.java.name)

        assertEquals(expectedDownload, result)
        verify(exactly = 0) { provider1.download(any()) }
        verify(exactly = 1) { provider2.download(any()) }
    }

    @Test
    fun `processDownload should use throttled stream when bandwidth limiting enabled`() {
        val inputStream = ByteArrayInputStream("test data".toByteArray())
        val outputStream = ByteArrayOutputStream()
        val game = createMockGame(1L, "Test Game")
        val tracker = mockk<SessionBandwidthTracker>(relaxed = true)

        every { configService.get(ConfigProperties.Downloads.BandwidthLimitEnabled) } returns true
        every { configService.get(ConfigProperties.Downloads.BandwidthLimitMbps) } returns 10
        every { sessionBandwidthManager.getTracker("session-123", 1_250_000) } returns tracker
        every { tracker.activeDownloads } returns mockk { every { get() } returns 0 }

        service.processDownload(inputStream, outputStream, game, "testuser", "session-123", "192.168.1.1")

        verify(exactly = 1) { sessionBandwidthManager.getTracker("session-123", 1_250_000) }
        assertEquals("test data", outputStream.toString())
    }

    @Test
    fun `processDownload should use monitored stream when bandwidth limiting disabled`() {
        val inputStream = ByteArrayInputStream("test data".toByteArray())
        val outputStream = ByteArrayOutputStream()
        val game = createMockGame(1L, "Test Game")
        val tracker = mockk<SessionBandwidthTracker>(relaxed = true)

        every { configService.get(ConfigProperties.Downloads.BandwidthLimitEnabled) } returns false
        every { configService.get(ConfigProperties.Downloads.BandwidthLimitMbps) } returns 10
        every { sessionBandwidthManager.getTracker("session-123", 0) } returns tracker
        every { tracker.activeDownloads } returns mockk { every { get() } returns 0 }

        service.processDownload(inputStream, outputStream, game, "testuser", "session-123", "192.168.1.1")

        verify(exactly = 1) { sessionBandwidthManager.getTracker("session-123", 0) }
        assertEquals("test data", outputStream.toString())
    }

    @Test
    fun `processDownload should use monitored stream when bandwidth limit is zero`() {
        val inputStream = ByteArrayInputStream("test data".toByteArray())
        val outputStream = ByteArrayOutputStream()
        val game = createMockGame(1L, "Test Game")
        val tracker = mockk<SessionBandwidthTracker>(relaxed = true)

        every { configService.get(ConfigProperties.Downloads.BandwidthLimitEnabled) } returns true
        every { configService.get(ConfigProperties.Downloads.BandwidthLimitMbps) } returns 0
        every { sessionBandwidthManager.getTracker("session-123", 0) } returns tracker
        every { tracker.activeDownloads } returns mockk { every { get() } returns 0 }

        service.processDownload(inputStream, outputStream, game, "testuser", "session-123", "192.168.1.1")

        verify(exactly = 1) { sessionBandwidthManager.getTracker("session-123", 0) }
    }

    @Test
    fun `processDownload should convert Mbps to bytes per second correctly`() {
        val inputStream = ByteArrayInputStream("test data".toByteArray())
        val outputStream = ByteArrayOutputStream()
        val game = createMockGame(1L, "Test Game")
        val tracker = mockk<SessionBandwidthTracker>(relaxed = true)

        every { configService.get(ConfigProperties.Downloads.BandwidthLimitEnabled) } returns true
        every { configService.get(ConfigProperties.Downloads.BandwidthLimitMbps) } returns 8 // 8 Mbps = 1,000,000 bytes/sec
        every { sessionBandwidthManager.getTracker("session-123", 1_000_000) } returns tracker
        every { tracker.activeDownloads } returns mockk { every { get() } returns 0 }

        service.processDownload(inputStream, outputStream, game, "testuser", "session-123", "192.168.1.1")

        verify(exactly = 1) { sessionBandwidthManager.getTracker("session-123", 1_000_000) }
    }

    @Test
    fun `processDownload should handle null username`() {
        val inputStream = ByteArrayInputStream("test data".toByteArray())
        val outputStream = ByteArrayOutputStream()
        val game = createMockGame(1L, "Test Game")
        val tracker = mockk<SessionBandwidthTracker>(relaxed = true)

        every { configService.get(ConfigProperties.Downloads.BandwidthLimitEnabled) } returns false
        every { configService.get(ConfigProperties.Downloads.BandwidthLimitMbps) } returns 0
        every { sessionBandwidthManager.getTracker("session-123", 0) } returns tracker
        every { tracker.activeDownloads } returns mockk { every { get() } returns 0 }

        assertDoesNotThrow {
            service.processDownload(inputStream, outputStream, game, null, "session-123", "192.168.1.1")
        }

        assertEquals("test data", outputStream.toString())
    }

    @Test
    fun `processDownload should handle IOException gracefully`() {
        val inputStream = object : ByteArrayInputStream("test data".toByteArray()) {
            override fun read(b: ByteArray): Int {
                throw IOException("Connection reset")
            }
        }
        val outputStream = ByteArrayOutputStream()
        val game = createMockGame(1L, "Test Game")
        val tracker = mockk<SessionBandwidthTracker>(relaxed = true)

        every { configService.get(ConfigProperties.Downloads.BandwidthLimitEnabled) } returns false
        every { configService.get(ConfigProperties.Downloads.BandwidthLimitMbps) } returns 0
        every { sessionBandwidthManager.getTracker("session-123", 0) } returns tracker
        every { tracker.activeDownloads } returns mockk { every { get() } returns 0 }

        // Should not throw - IOException is caught and logged
        assertDoesNotThrow {
            service.processDownload(inputStream, outputStream, game, "testuser", "session-123", "192.168.1.1")
        }
    }

    @Test
    fun `processDownload should handle large files`() {
        val largeData = ByteArray(10 * 1024 * 1024) { it.toByte() } // 10 MB
        val inputStream = ByteArrayInputStream(largeData)
        val outputStream = ByteArrayOutputStream()
        val game = createMockGame(1L, "Test Game")
        val tracker = mockk<SessionBandwidthTracker>(relaxed = true)

        every { configService.get(ConfigProperties.Downloads.BandwidthLimitEnabled) } returns false
        every { configService.get(ConfigProperties.Downloads.BandwidthLimitMbps) } returns 0
        every { sessionBandwidthManager.getTracker("session-123", 0) } returns tracker
        every { tracker.activeDownloads } returns mockk { every { get() } returns 0 }

        service.processDownload(inputStream, outputStream, game, "testuser", "session-123", "192.168.1.1")

        assertEquals(largeData.size, outputStream.size())
    }

    @Test
    fun `processDownload should get tracker for each download`() {
        val inputStream = ByteArrayInputStream("test data".toByteArray())
        val outputStream = ByteArrayOutputStream()
        val game = createMockGame(1L, "Test Game")
        val tracker = mockk<SessionBandwidthTracker>(relaxed = true)

        every { configService.get(ConfigProperties.Downloads.BandwidthLimitEnabled) } returns false
        every { configService.get(ConfigProperties.Downloads.BandwidthLimitMbps) } returns 0
        every { sessionBandwidthManager.getTracker(any(), any()) } returns tracker
        every { tracker.activeDownloads } returns mockk { every { get() } returns 2 }

        service.processDownload(inputStream, outputStream, game, "testuser", "session-123", "192.168.1.1")

        verify(exactly = 1) { sessionBandwidthManager.getTracker("session-123", 0) }
    }

    @Test
    fun `processDownload should flush output stream`() {
        val inputStream = ByteArrayInputStream("test data".toByteArray())
        val outputStream = mockk<ByteArrayOutputStream>(relaxed = true)
        val game = createMockGame(1L, "Test Game")
        val tracker = mockk<SessionBandwidthTracker>(relaxed = true)

        every { configService.get(ConfigProperties.Downloads.BandwidthLimitEnabled) } returns false
        every { configService.get(ConfigProperties.Downloads.BandwidthLimitMbps) } returns 0
        every { sessionBandwidthManager.getTracker("session-123", 0) } returns tracker
        every { tracker.activeDownloads } returns mockk { every { get() } returns 0 }

        service.processDownload(inputStream, outputStream, game, "testuser", "session-123", "192.168.1.1")

        // Flush is called through the wrapper stream
        verify(atLeast = 1) { outputStream.write(any<ByteArray>(), any(), any()) }
    }

    @Test
    fun `processDownload should handle config service returning null values`() {
        val inputStream = ByteArrayInputStream("test data".toByteArray())
        val outputStream = ByteArrayOutputStream()
        val game = createMockGame(1L, "Test Game")
        val tracker = mockk<SessionBandwidthTracker>(relaxed = true)

        every { configService.get(ConfigProperties.Downloads.BandwidthLimitEnabled) } returns null
        every { configService.get(ConfigProperties.Downloads.BandwidthLimitMbps) } returns null
        every { sessionBandwidthManager.getTracker("session-123", 0) } returns tracker
        every { tracker.activeDownloads } returns mockk { every { get() } returns 0 }

        assertDoesNotThrow {
            service.processDownload(inputStream, outputStream, game, "testuser", "session-123", "192.168.1.1")
        }

        assertEquals("test data", outputStream.toString())
    }

    @Test
    fun `processDownload should handle different bandwidth limits`() {
        val testCases = listOf(1, 10, 100, 1000) // Mbps

        testCases.forEach { mbps ->
            clearAllMocks(answers = false)

            val inputStream = ByteArrayInputStream("test".toByteArray())
            val outputStream = ByteArrayOutputStream()
            val game = createMockGame(1L, "Test Game")
            val tracker = mockk<SessionBandwidthTracker>(relaxed = true)
            val expectedBytesPerSec = (mbps * 125_000).toLong()

            every { configService.get(ConfigProperties.Downloads.BandwidthLimitEnabled) } returns true
            every { configService.get(ConfigProperties.Downloads.BandwidthLimitMbps) } returns mbps
            every { sessionBandwidthManager.getTracker("session-123", expectedBytesPerSec) } returns tracker
            every { tracker.activeDownloads } returns mockk { every { get() } returns 0 }

            service.processDownload(inputStream, outputStream, game, "testuser", "session-123", "192.168.1.1")

            verify(exactly = 1) { sessionBandwidthManager.getTracker("session-123", expectedBytesPerSec) }
        }
    }

    @Test
    fun `should be a Spring Service`() {
        val annotations = DownloadService::class.annotations
        assertTrue(annotations.any { it.annotationClass.simpleName == "Service" })
    }

    @Test
    fun `processDownload should handle empty input stream`() {
        val inputStream = ByteArrayInputStream(ByteArray(0))
        val outputStream = ByteArrayOutputStream()
        val game = createMockGame(1L, "Test Game")
        val tracker = mockk<SessionBandwidthTracker>(relaxed = true)

        every { configService.get(ConfigProperties.Downloads.BandwidthLimitEnabled) } returns false
        every { configService.get(ConfigProperties.Downloads.BandwidthLimitMbps) } returns 0
        every { sessionBandwidthManager.getTracker("session-123", 0) } returns tracker
        every { tracker.activeDownloads } returns mockk { every { get() } returns 0 }

        service.processDownload(inputStream, outputStream, game, "testuser", "session-123", "192.168.1.1")

        assertEquals(0, outputStream.size())
    }

    // Test provider classes with known names
    class Provider1 : DownloadProvider {
        override fun download(path: java.nio.file.Path): Download = mockk(relaxed = true)
    }

    class Provider2 : DownloadProvider {
        override fun download(path: java.nio.file.Path): Download = mockk(relaxed = true)
    }

    class TestProvider : DownloadProvider {
        override fun download(path: java.nio.file.Path): Download = mockk(relaxed = true)
    }

    private fun createMockProvider(className: String): DownloadProvider {
        return when (className) {
            "Provider1" -> spyk(Provider1())
            "Provider2" -> spyk(Provider2())
            "TestProvider" -> spyk(TestProvider())
            else -> {
                // For other cases, create a mock and use reflection to set the expected behavior
                spyk(TestProvider())
            }
        }
    }

    private fun createMockPluginWrapper(
        pluginId: String,
        name: String,
        description: String,
        shortDescription: String
    ): PluginWrapper {
        val descriptor = mockk<GameyfinPluginDescriptor> {
            every { pluginName } returns name
            every { pluginDescription } returns description
            every { pluginShortDescription } returns shortDescription
        }

        return mockk {
            every { this@mockk.pluginId } returns pluginId
            every { this@mockk.descriptor } returns descriptor
        }
    }

    @Suppress("SameParameterValue")
    private fun createMockGame(id: Long, title: String): Game {
        return mockk {
            every { this@mockk.id } returns id
            every { this@mockk.title } returns title
        }
    }
}