package org.gameyfin.app.core.database

import io.mockk.*
import org.gameyfin.app.core.backup.BackupService
import org.gameyfin.app.core.security.EncryptionUtils
import org.gameyfin.app.system.SystemService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.env.SystemEnvironmentPropertySource
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.mock.env.MockEnvironment
import java.nio.file.Path
import kotlin.io.path.div

class DatabaseServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private val backupService = mockk<BackupService>()
    private val systemService = mockk<SystemService>(relaxed = true)
    private lateinit var environment: MockEnvironment
    private lateinit var store: DatabaseSettingsStore
    private lateinit var service: DatabaseService
    private lateinit var url: String

    /** A PostgreSQL database which is not reachable */
    private val unreachable = DatabaseConnectionDto(DatabaseType.POSTGRESQL, "localhost", 1, "gameyfin", "user", "pw")

    @BeforeEach
    fun setUp() {
        mockkObject(EncryptionUtils)
        every { EncryptionUtils.encrypt(any()) } answers { "enc:" + firstArg<String>() }
        every { EncryptionUtils.decrypt(any()) } answers { firstArg<String>().removePrefix("enc:") }

        url = "jdbc:h2:file:${(tempDir / "h2").toAbsolutePath()}"
        environment = MockEnvironment()
            .withProperty("spring.datasource.url", url)
            .withProperty("spring.datasource.username", "sa")
            .withProperty("spring.datasource.password", "")
            .withProperty(DatabaseSettingsInitializer.SETTINGS_FILE_PROPERTY, (tempDir / "db" / "database.json").toString())
        store = DatabaseSettingsInitializer.store(environment)
        service = service(environment)
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    private fun service(environment: org.springframework.core.env.ConfigurableEnvironment) =
        DatabaseService(environment, DriverManagerDataSource(url, "sa", ""), backupService, systemService)

    @Test
    fun `reports the embedded database`() {
        val info = service.getDatabaseInfo()

        assertEquals(DatabaseType.H2, info.type)
        assertEquals("H2", info.productName)
        assertFalse(info.managedExternally)
        assertNull(info.url)
        assertFalse(info.passwordSet)
        assertNull(info.lastSwitch)
    }

    @Test
    fun `reports the stored database without password`() {
        store.save(DatabaseSettings.of(DatabaseType.POSTGRESQL, "db", 5432, "gameyfin", "user", "secret"))

        val info = service.getDatabaseInfo()

        assertEquals(DatabaseType.POSTGRESQL, info.type)
        assertEquals("db", info.host)
        assertEquals("user", info.username)
        assertTrue(info.passwordSet)
    }

    @Test
    fun `tests connections`() {
        val embedded = service.testConnection(DatabaseConnectionDto(DatabaseType.H2))
        assertTrue(embedded.success, embedded.message)
        assertEquals("H2", embedded.productName)

        val invalid = service.testConnection(unreachable.copy(host = "db?x=y"))
        assertFalse(invalid.success)
        assertEquals("Invalid host name", invalid.message)

        assertFalse(service.testConnection(unreachable).success)
    }

    @Test
    fun `rejects switching to the current or an unreachable database`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.switchDatabase(DatabaseConnectionDto(DatabaseType.H2))
        }
        val e = assertThrows(IllegalArgumentException::class.java) { service.switchDatabase(unreachable) }
        assertTrue(e.message!!.startsWith("Could not connect"))

        verify(exactly = 0) { backupService.transferDatabase(any()) }
        assertNull(store.load())
    }

    @Test
    fun `switches to a different database by transferring the data`() {
        val previous = DatabaseSettings.of(DatabaseType.POSTGRESQL, "db", 5432, "gameyfin", "user", "secret")
        store.save(previous)
        val switchDatabase = slot<(String) -> Unit>()
        every { backupService.transferDatabase(capture(switchDatabase)) } just Runs

        service.switchDatabase(DatabaseConnectionDto(DatabaseType.H2))

        // Settings are only changed once the backup has been created
        assertEquals(previous, store.load())
        switchDatabase.captured("a.zip")
        assertEquals(DatabaseSettings.EMBEDDED, store.load())
        assertEquals(PendingDatabaseSwitch(previous, DatabaseSettings.EMBEDDED, "a.zip"), store.pendingSwitch())
    }

    @Test
    fun `can not be changed when configured via environment variables`() {
        val environment = StandardEnvironment().apply {
            propertySources.replace(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                SystemEnvironmentPropertySource(
                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                    mapOf("SPRING_DATASOURCE_URL" to "jdbc:postgresql://external:5432/db?password=secret")
                )
            )
        }
        val service = service(environment)

        val info = service.getDatabaseInfo()
        assertTrue(info.managedExternally)
        assertEquals(DatabaseType.POSTGRESQL, info.type)
        assertEquals("jdbc:postgresql://external:5432/db", info.url)

        assertThrows(IllegalStateException::class.java) { service.switchDatabase(unreachable) }
    }
}
