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
import org.gameyfin.app.core.database.*
import org.gameyfin.app.core.security.EncryptionUtils
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
    private lateinit var databaseStore: DatabaseSettingsStore

    private val postgres = DatabaseSettings.of(DatabaseType.POSTGRESQL, "localhost", 1, "gameyfin", "user", "secret")

    @BeforeEach
    fun setUp() {
        mockkObject(AppKeyFingerprint)
        every { AppKeyFingerprint.current() } returns FINGERPRINT
        mockkObject(EncryptionUtils)
        every { EncryptionUtils.encrypt(any()) } answers { "enc:" + firstArg<String>() }
        every { EncryptionUtils.decrypt(any()) } answers { firstArg<String>().removePrefix("enc:") }

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
            .withProperty(DatabaseSettingsInitializer.SETTINGS_FILE_PROPERTY, (tempDir / "db" / "database.json").toString())
        databaseStore = DatabaseSettingsInitializer.store(environment)
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

    @Test
    fun `completes a database switch`() {
        createBackup("a.zip")
        storage.schedulePendingRestore("a.zip")
        // Switching from PostgreSQL to the embedded database (which is configured in the environment)
        databaseStore.schedulePendingSwitch(PendingDatabaseSwitch(postgres, DatabaseSettings.EMBEDDED, "a.zip"))
        databaseStore.save(DatabaseSettings.EMBEDDED)

        run()

        assertTrue(storage.readRestoreStatus()!!.success)
        assertNull(databaseStore.pendingSwitch())
        assertEquals(DatabaseSettings.EMBEDDED, databaseStore.load())
        val status = databaseStore.readSwitchStatus()!!
        assertTrue(status.success, status.message)
        assertEquals(DatabaseType.POSTGRESQL, status.previousType)
        assertEquals(DatabaseType.H2, status.newType)
    }

    @Test
    fun `reverts to the previous database when the data can not be transferred`() {
        createBackup("a.zip")
        storage.schedulePendingRestore("a.zip")
        databaseStore.schedulePendingSwitch(PendingDatabaseSwitch(DatabaseSettings.EMBEDDED, postgres, "a.zip"))
        databaseStore.save(postgres)
        DatabaseSettingsInitializer.apply(environment, postgres)
        assertEquals(postgres.jdbcUrl(), environment.getProperty("spring.datasource.url"))

        run()

        assertFalse(storage.readRestoreStatus()!!.success)
        assertNull(databaseStore.pendingSwitch())
        assertEquals(DatabaseSettings.EMBEDDED, databaseStore.load())
        assertEquals(url, environment.getProperty("spring.datasource.url"))
        val status = databaseStore.readSwitchStatus()!!
        assertFalse(status.success)
        assertTrue(status.message.contains("still used"), status.message)
    }

    @Test
    fun `reverts a database switch without pending restore`() {
        databaseStore.schedulePendingSwitch(PendingDatabaseSwitch(DatabaseSettings.EMBEDDED, postgres, "a.zip"))
        databaseStore.save(postgres)
        DatabaseSettingsInitializer.apply(environment, postgres)

        run()

        assertNull(databaseStore.pendingSwitch())
        assertEquals(DatabaseSettings.EMBEDDED, databaseStore.load())
        assertEquals(url, environment.getProperty("spring.datasource.url"))
        assertFalse(databaseStore.readSwitchStatus()!!.success)
    }
}
