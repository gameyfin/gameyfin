package org.gameyfin.app.system.backup

import io.mockk.*
import liquibase.integration.spring.SpringLiquibase
import org.gameyfin.app.core.backup.AppKeyFingerprint
import org.gameyfin.app.core.backup.BackupDto
import org.gameyfin.app.core.backup.BackupService
import org.gameyfin.app.core.backup.InvalidBackupException
import org.gameyfin.app.system.SystemService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.info.BuildProperties
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.io.ByteArrayInputStream
import java.nio.file.Path
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.io.path.*

class BackupServiceTest {

    companion object {
        private const val CHANGELOG = "classpath:/db/changelog/db.changelog-master.yaml"
        private val FINGERPRINT =
            AppKeyFingerprint.of(Base64.getEncoder().encodeToString(ByteArray(16) { it.toByte() }))
    }

    @TempDir
    lateinit var tempDir: Path

    private val systemService = mockk<SystemService>(relaxed = true)
    private lateinit var service: BackupService
    private lateinit var backupDir: Path

    @BeforeEach
    fun setUp() {
        mockkObject(AppKeyFingerprint)
        every { AppKeyFingerprint.current() } returns FINGERPRINT

        val dataSource = DriverManagerDataSource("jdbc:h2:file:${(tempDir / "db").toAbsolutePath()}", "sa", "")
        SpringLiquibase().apply {
            this.dataSource = dataSource
            changeLog = CHANGELOG
            resourceLoader = DefaultResourceLoader()
            afterPropertiesSet()
        }

        backupDir = tempDir / "backups"
        val buildProperties = mockk<ObjectProvider<BuildProperties>>()
        every { buildProperties.ifAvailable } returns null
        service = BackupService(
            dataSource, systemService, buildProperties, DefaultResourceLoader(),
            backupDir.toString(), (tempDir / "media").toString(), CHANGELOG
        )
    }

    @AfterEach
    fun tearDown() {
        service.shutdown()
        unmockkAll()
    }

    private fun awaitBackups(count: Int): List<BackupDto> {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60)
        while (System.nanoTime() < deadline) {
            val backups = service.getBackups()
            if (backups.size >= count) return backups
            Thread.sleep(50)
        }
        fail<Unit>("Backup was not created in time")
        return emptyList()
    }

    private fun createBackupAndWait(): BackupDto {
        service.createBackup(includeMedia = false)
        val backup = awaitBackups(1).single()
        // wait until the operation permit has been released
        repeat(200) {
            if (runCatching { service.getLastRestoreStatus() }.isSuccess && permitFree()) return backup
            Thread.sleep(25)
        }
        return backup
    }

    private fun permitFree(): Boolean = try {
        service.deleteBackup("nonexistent.zip")
        true
    } catch (_: IllegalArgumentException) {
        true
    } catch (_: IllegalStateException) {
        false
    }

    @Test
    fun `createBackup creates a restorable backup`() {
        val backup = createBackupAndWait()

        assertTrue(backup.restorable)
        assertNull(backup.problem)
        assertTrue(backup.sizeBytes > 0)
        assertEquals(backup.name, service.getBackupFile(backup.name).name)
    }

    @Test
    fun `createBackup rejects concurrent operations`() {
        service.createBackup(includeMedia = false)

        // either still running or finished; a second immediate call must not corrupt state
        runCatching { service.createBackup(includeMedia = false) }
            .onFailure { assertTrue(it is IllegalStateException) }
        awaitBackups(1)
    }

    @Test
    fun `deleteBackup removes backup and rejects unknown names`() {
        val backup = createBackupAndWait()

        service.deleteBackup(backup.name)

        assertTrue(service.getBackups().isEmpty())
        assertThrows(IllegalArgumentException::class.java) { service.deleteBackup(backup.name) }
        assertThrows(IllegalArgumentException::class.java) { service.getBackupFile("../x.zip") }
    }

    @Test
    fun `storeUploadedBackup accepts valid archives and rejects invalid ones`() {
        val backup = createBackupAndWait()
        val bytes = service.getBackupFile(backup.name).readBytes()

        val stored = service.storeUploadedBackup(ByteArrayInputStream(bytes))

        assertTrue(stored.name.contains("uploaded"))
        assertTrue(stored.restorable)
        assertThrows(InvalidBackupException::class.java) {
            service.storeUploadedBackup(ByteArrayInputStream("garbage".toByteArray()))
        }
        assertEquals(2, service.getBackups().size)
        assertTrue(backupDir.listDirectoryEntries(".tmp-*").isEmpty())
    }

    @Test
    fun `getBackups flags corrupt archives and wrong app keys`() {
        val backup = createBackupAndWait()
        (backupDir / "corrupt.zip").writeText("not a zip")

        every { AppKeyFingerprint.current() } returns AppKeyFingerprint.of(
            Base64.getEncoder().encodeToString(ByteArray(16))
        )
        val backups = service.getBackups().associateBy { it.name }

        assertFalse(backups.getValue("corrupt.zip").restorable)
        assertNotNull(backups.getValue("corrupt.zip").problem)
        assertFalse(backups.getValue(backup.name).restorable)
        assertTrue(backups.getValue(backup.name).problem!!.contains("APP_KEY"))
    }

    @Test
    fun `restoreBackup schedules restore, creates safety backup and restarts`() {
        val backup = createBackupAndWait()

        service.restoreBackup(backup.name, createSafetyBackup = true)

        verify { systemService.restart() }
        assertEquals(backup.name, (backupDir / ".pending-restore").readText())
        assertTrue(service.getBackups().any { it.name.contains("pre-restore") })
        val e = assertThrows(IllegalStateException::class.java) { service.createBackup(includeMedia = false) }
        assertTrue(e.message!!.contains("restore is pending"))
    }

    @Test
    fun `restoreBackup without safety backup does not create another backup`() {
        val backup = createBackupAndWait()

        service.restoreBackup(backup.name, createSafetyBackup = false)

        assertEquals(1, service.getBackups().size)
    }

    @Test
    fun `restoreBackup rejects backups of a different app key`() {
        val backup = createBackupAndWait()
        every { AppKeyFingerprint.current() } returns AppKeyFingerprint.of(
            Base64.getEncoder().encodeToString(ByteArray(16))
        )

        assertThrows(InvalidBackupException::class.java) { service.restoreBackup(backup.name, false) }
        verify(exactly = 0) { systemService.restart() }
    }

    @Test
    fun `getLastRestoreStatus is empty without a restore`() {
        assertNull(service.getLastRestoreStatus())
    }

    @Test
    fun `createBackup fails when shutting down`() {
        service.shutdown()

        assertThrows(IllegalStateException::class.java) { service.createBackup(includeMedia = false) }
        // permit was released again
        assertTrue(permitFree())
    }
}
