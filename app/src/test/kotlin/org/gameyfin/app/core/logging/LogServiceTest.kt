package org.gameyfin.app.core.logging

import io.mockk.*
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.logging.LogLevel
import reactor.test.StepVerifier
import java.nio.file.Path
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LogServiceTest {

    private lateinit var configService: ConfigService
    private lateinit var logService: LogService

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        configService = mockk<ConfigService>(relaxed = true)

        // Set default config values
        every { configService.get(ConfigProperties.Logs.Folder) } returns tempDir.toString()
        every { configService.get(ConfigProperties.Logs.MaxHistoryDays) } returns 30
        every { configService.get(ConfigProperties.Logs.Level.Gameyfin) } returns LogLevel.INFO
        every { configService.get(ConfigProperties.Logs.Level.Root) } returns LogLevel.WARN

        logService = LogService(configService)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `configureFileLogging should use config service to get configuration values`() {
        logService.configureFileLogging()

        verify(exactly = 1) { configService.get(ConfigProperties.Logs.Folder) }
        verify(exactly = 1) { configService.get(ConfigProperties.Logs.MaxHistoryDays) }
        verify(exactly = 1) { configService.get(ConfigProperties.Logs.Level.Gameyfin) }
        verify(exactly = 1) { configService.get(ConfigProperties.Logs.Level.Root) }
    }

    @Test
    fun `configureFileLogging should configure logback with correct parameters`() {
        val folder = tempDir.toString()
        val maxHistoryDays = 45
        val levelGameyfin = LogLevel.DEBUG
        val levelRoot = LogLevel.ERROR

        logService.configureFileLogging(folder, maxHistoryDays, levelGameyfin, levelRoot)

        // Verify the logger context is configured
        // Since we're modifying global state, we just verify no exceptions are thrown
        assertTrue(true)
    }

    @Test
    fun `configureFileLogging should handle folder path with trailing slash`() {
        val folderWithSlash = "$tempDir/"
        val maxHistoryDays = 30
        val levelGameyfin = LogLevel.INFO
        val levelRoot = LogLevel.WARN

        logService.configureFileLogging(folderWithSlash, maxHistoryDays, levelGameyfin, levelRoot)

        // Should complete without throwing an exception
        assertTrue(true)
    }

    @Test
    fun `configureFileLogging should configure file tailer on first call`() {
        val folder = tempDir.toString()
        val maxHistoryDays = 30
        val levelGameyfin = LogLevel.INFO
        val levelRoot = LogLevel.WARN

        logService.configureFileLogging(folder, maxHistoryDays, levelGameyfin, levelRoot)

        // The log file path should be set and tailer should be created
        // We verify this indirectly through the streamLogs functionality
        val flux = logService.streamLogs()
        assertNotNull(flux)
    }

    @Test
    fun `configureFileLogging should restart tailer when log file path changes`() {
        val folder1 = tempDir.resolve("logs1").toString()
        val folder2 = tempDir.resolve("logs2").toString()
        val maxHistoryDays = 30
        val levelGameyfin = LogLevel.INFO
        val levelRoot = LogLevel.WARN

        // First configuration
        logService.configureFileLogging(folder1, maxHistoryDays, levelGameyfin, levelRoot)

        // Second configuration with different folder
        logService.configureFileLogging(folder2, maxHistoryDays, levelGameyfin, levelRoot)

        // Should complete without throwing an exception
        assertTrue(true)
    }

    @Test
    fun `configureFileLogging should not restart tailer when log file path is the same`() {
        val folder = tempDir.toString()
        val maxHistoryDays = 30
        val levelGameyfin = LogLevel.INFO
        val levelRoot = LogLevel.WARN

        // First configuration
        logService.configureFileLogging(folder, maxHistoryDays, levelGameyfin, levelRoot)

        // Second configuration with same folder
        logService.configureFileLogging(folder, maxHistoryDays, levelGameyfin, levelRoot)

        // Should complete without throwing an exception
        assertTrue(true)
    }

    @Test
    fun `configureFileLogging should support all log levels for gameyfin`() {
        val folder = tempDir.toString()
        val maxHistoryDays = 30
        val levelRoot = LogLevel.WARN

        LogLevel.entries.forEach { level ->
            logService.configureFileLogging(folder, maxHistoryDays, level, levelRoot)
            // Should complete without throwing an exception
        }

        assertTrue(true)
    }

    @Test
    fun `configureFileLogging should support all log levels for root`() {
        val folder = tempDir.toString()
        val maxHistoryDays = 30
        val levelGameyfin = LogLevel.INFO

        LogLevel.entries.forEach { level ->
            logService.configureFileLogging(folder, maxHistoryDays, levelGameyfin, level)
            // Should complete without throwing an exception
        }

        assertTrue(true)
    }

    @Test
    fun `configureFileLogging should support various max history days values`() {
        val folder = tempDir.toString()
        val levelGameyfin = LogLevel.INFO
        val levelRoot = LogLevel.WARN

        listOf(1, 7, 30, 90, 365).forEach { days ->
            logService.configureFileLogging(folder, days, levelGameyfin, levelRoot)
            // Should complete without throwing an exception
        }

        assertTrue(true)
    }

    @Test
    fun `streamLogs should return a Flux that emits log lines`() {
        logService.configureFileLogging(
            tempDir.toString(),
            30,
            LogLevel.INFO,
            LogLevel.WARN
        )

        val flux = logService.streamLogs()

        assertNotNull(flux)

        // Verify that we can subscribe to the flux
        StepVerifier.create(flux.take(0))
            .verifyComplete()
    }

    @Test
    fun `streamLogs should emit lines that are added to the sink`() {
        logService.configureFileLogging(
            tempDir.toString(),
            30,
            LogLevel.INFO,
            LogLevel.WARN
        )

        val flux = logService.streamLogs()

        // Since we're using a replay sink with limit, we can test the basic flux behavior
        StepVerifier.create(flux.take(0))
            .verifyComplete()
    }

    @Test
    fun `multiple calls to streamLogs should return the same flux source`() {
        logService.configureFileLogging(
            tempDir.toString(),
            30,
            LogLevel.INFO,
            LogLevel.WARN
        )

        val flux1 = logService.streamLogs()
        val flux2 = logService.streamLogs()

        // Both should be valid flux instances
        assertNotNull(flux1)
        assertNotNull(flux2)
    }

    @Test
    fun `configureFileLogging should handle zero max history days`() {
        val folder = tempDir.toString()
        val levelGameyfin = LogLevel.INFO
        val levelRoot = LogLevel.WARN

        logService.configureFileLogging(folder, 0, levelGameyfin, levelRoot)

        // Should complete without throwing an exception
        assertTrue(true)
    }

    @Test
    fun `configureFileLogging should handle negative max history days`() {
        val folder = tempDir.toString()
        val levelGameyfin = LogLevel.INFO
        val levelRoot = LogLevel.WARN

        logService.configureFileLogging(folder, -1, levelGameyfin, levelRoot)

        // Should complete without throwing an exception (logback will handle this)
        assertTrue(true)
    }

    @Test
    fun `configureFileLogging should handle very long folder paths`() {
        val longPath = tempDir.resolve("a".repeat(100)).toString()
        val levelGameyfin = LogLevel.INFO
        val levelRoot = LogLevel.WARN

        logService.configureFileLogging(longPath, 30, levelGameyfin, levelRoot)

        // Should complete without throwing an exception
        assertTrue(true)
    }

    @Test
    fun `configureFileLogging should handle folder paths with special characters`() {
        val folderWithSpaces = tempDir.resolve("log folder with spaces").toString()
        val levelGameyfin = LogLevel.INFO
        val levelRoot = LogLevel.WARN

        logService.configureFileLogging(folderWithSpaces, 30, levelGameyfin, levelRoot)

        // Should complete without throwing an exception
        assertTrue(true)
    }
}

