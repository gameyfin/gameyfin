package org.gameyfin.app.system.backup

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import liquibase.integration.spring.SpringLiquibase
import org.gameyfin.app.core.backup.AppKeyFingerprint
import org.gameyfin.app.core.backup.BackupStorage
import org.gameyfin.app.core.backup.DatabaseExporter
import org.gameyfin.app.core.backup.PendingRestoreExecutor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.mock.env.MockEnvironment
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.*
import kotlin.io.path.div

class PendingRestoreExecutorTest {

    companion object {
        private const val CHANGELOG = "classpath:/db/changelog/db.changelog-master.yaml"
        private val FINGERPRINT =
            AppKeyFingerprint.of(Base64.getEncoder().encodeToString(ByteArray(16) { it.toByte() }))
    }

    @TempDir
    lateinit var tempDir: Path

    private lateinit var backupDir: Path
    private lateinit var storage: BackupStorage
    private lateinit var environment: MockEnvironment
    private lateinit var url: String

    @BeforeEach
    fun setUp() {
        mockkObject(AppKeyFingerprint)
        every { AppKeyFingerprint.current() } returns FINGERPRINT

        backupDir = tempDir / "backups"
        storage = BackupStorage(backupDir).also { it.ensureExists() }
        url = "jdbc:h2:file:${(tempDir / "db").toAbsolutePath()}"
        environment = MockEnvironment()
            .withProperty("gameyfin.backup.directory", backupDir.toString())
            .withProperty("spring.content.fs.filesystem-root", (tempDir / "media").toString())
            .withProperty("spring.datasource.url", url)
            .withProperty("spring.datasource.username", "sa")
            .withProperty("spring.datasource.password", "")
            .withProperty("spring.liquibase.change-log", CHANGELOG)
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    private fun createBackup(name: String): DriverManagerDataSource {
        val dataSource = DriverManagerDataSource(url, "sa", "")
        SpringLiquibase().apply {
            this.dataSource = dataSource
            changeLog = CHANGELOG
            resourceLoader = DefaultResourceLoader()
            afterPropertiesSet()
        }
        Files.newOutputStream(backupDir / name).use {
            DatabaseExporter().writeArchive(dataSource, it, Instant.now(), FINGERPRINT, "test", null)
        }
        return dataSource
    }

    private fun run() {
        val context = mockk<ConfigurableApplicationContext>()
        every { context.environment } returns environment
        every { context.classLoader } returns javaClass.classLoader
        PendingRestoreExecutor().initialize(context)
    }

    @Test
    fun `does nothing without pending restore`() {
        run()

        assertNull(storage.readRestoreStatus())
    }

    @Test
    fun `restores the pending backup and records success`() {
        createBackup("a.zip")
        storage.schedulePendingRestore("a.zip")

        run()

        val status = storage.readRestoreStatus()!!
        assertTrue(status.success, status.message)
        assertEquals("a.zip", status.backupName)
        assertTrue(status.message.startsWith("Restored"))
        assertNull(storage.pendingRestore())
        assertTrue(storage.isImageRecoveryPending())
    }

    @Test
    fun `keeps pending restore when liquibase is disabled`() {
        createBackup("a.zip")
        storage.schedulePendingRestore("a.zip")
        environment.setProperty("spring.liquibase.enabled", "false")

        run()

        assertEquals("a.zip", storage.pendingRestore())
        assertNull(storage.readRestoreStatus())
    }

    @Test
    fun `records failure and clears marker when restore fails`() {
        createBackup("a.zip")
        storage.schedulePendingRestore("a.zip")
        every { AppKeyFingerprint.current() } returns AppKeyFingerprint.of(
            Base64.getEncoder().encodeToString(ByteArray(16))
        )

        run()

        val status = storage.readRestoreStatus()!!
        assertFalse(status.success)
        assertTrue(status.message.contains("APP_KEY"))
        assertNull(storage.pendingRestore())
        assertFalse(storage.isImageRecoveryPending())
    }

    @Test
    fun `uses liquibase specific connection settings when configured`() {
        createBackup("a.zip")
        storage.schedulePendingRestore("a.zip")
        environment.setProperty("spring.datasource.url", "jdbc:h2:mem:unused")
        environment.setProperty("spring.liquibase.url", url)
        environment.setProperty("spring.liquibase.user", "sa")
        environment.setProperty("spring.liquibase.password", "")
        environment.setProperty("spring.liquibase.driver-class-name", "org.h2.Driver")

        run()

        assertTrue(storage.readRestoreStatus()!!.success)
    }
}
