package org.gameyfin.app.core.database

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.gameyfin.app.core.security.EncryptionUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.env.SystemEnvironmentPropertySource
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.div
import kotlin.io.path.readText
import kotlin.io.path.writeText

class DatabaseSettingsTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var store: DatabaseSettingsStore
    private lateinit var environment: StandardEnvironment

    private val postgres = DatabaseSettings.of(DatabaseType.POSTGRESQL, "db", null, "gameyfin_db", "gfadmin", "secret")

    companion object {
        private const val EMBEDDED_URL = "jdbc:h2:file:./db/gameyfin_db"
    }

    @BeforeEach
    fun setUp() {
        mockkObject(EncryptionUtils)
        every { EncryptionUtils.encrypt(any()) } answers { "enc:" + firstArg<String>() }
        every { EncryptionUtils.decrypt(any()) } answers { firstArg<String>().removePrefix("enc:") }

        environment = environment()
        store = DatabaseSettingsInitializer.store(environment)
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    /** Environment resembling the one of the application, with the given environment variables */
    private fun environment(variables: Map<String, Any> = emptyMap()) = StandardEnvironment().apply {
        propertySources.replace(
            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables)
        )
        propertySources.addLast(
            MapPropertySource(
                "application.yml",
                mapOf(
                    "spring.datasource.url" to EMBEDDED_URL,
                    "spring.datasource.username" to "gfadmin",
                    "spring.datasource.password" to "gameyfin",
                    "spring.datasource.driverClassName" to "org.h2.Driver",
                    DatabaseSettingsInitializer.SETTINGS_FILE_PROPERTY to (tempDir / "db" / "database.json").toString()
                )
            )
        )
    }

    private fun initialize(environment: StandardEnvironment) {
        val context = mockk<ConfigurableApplicationContext>()
        every { context.environment } returns environment
        DatabaseSettingsInitializer().initialize(context)
    }

    @Test
    fun `validates and normalizes settings`() {
        assertEquals(DatabaseSettings.EMBEDDED, DatabaseSettings.of(DatabaseType.H2, "x", 1, "y", "z", "p"))
        assertEquals(DatabaseSettings.DEFAULT_POSTGRESQL_PORT, postgres.port)
        assertEquals("jdbc:postgresql://db:5432/gameyfin_db", postgres.jdbcUrl())
        assertEquals(
            "jdbc:postgresql://[::1]:5433/db",
            DatabaseSettings.of(DatabaseType.POSTGRESQL, " [::1] ", 5433, "db", "u", null).jdbcUrl()
        )

        listOf(
            { DatabaseSettings.of(DatabaseType.POSTGRESQL, "", null, "db", "u", null) },
            { DatabaseSettings.of(DatabaseType.POSTGRESQL, "db/x?ssl=false", null, "db", "u", null) },
            { DatabaseSettings.of(DatabaseType.POSTGRESQL, "db", 0, "db", "u", null) },
            { DatabaseSettings.of(DatabaseType.POSTGRESQL, "db", null, "db?ssl=false", "u", null) },
            { DatabaseSettings.of(DatabaseType.POSTGRESQL, "db", null, "db", " ", null) }
        ).forEach { assertThrows(IllegalArgumentException::class.java) { it() } }

        assertFalse(postgres.toString().contains("secret"))
    }

    @Test
    fun `stores settings with encrypted password`() {
        assertNull(store.load())

        store.save(postgres)

        assertEquals(postgres, store.load())
        val json = store.settingsFile.readText()
        assertTrue(json.contains("enc:secret"))

        store.save(DatabaseSettings.EMBEDDED)
        assertEquals(DatabaseSettings.EMBEDDED, store.load())
    }

    @Test
    fun `fails on corrupt settings file`() {
        store.save(postgres)
        store.settingsFile.writeText("{corrupt")

        assertThrows(IllegalStateException::class.java) { store.load() }
    }

    @Test
    fun `stores pending switch and status`() {
        assertNull(store.pendingSwitch())
        assertNull(store.readSwitchStatus())

        store.schedulePendingSwitch(PendingDatabaseSwitch(DatabaseSettings.EMBEDDED, postgres, "a.zip"))
        assertEquals(PendingDatabaseSwitch(DatabaseSettings.EMBEDDED, postgres, "a.zip"), store.pendingSwitch())
        store.clearPendingSwitch()
        assertNull(store.pendingSwitch())

        val status = DatabaseSwitchStatusDto(true, "ok", DatabaseType.H2, DatabaseType.POSTGRESQL, Instant.now())
        store.writeSwitchStatus(status)
        assertEquals(status, store.readSwitchStatus())
    }

    @Test
    fun `uses the default configuration without settings`() {
        initialize(environment)

        assertEquals(EMBEDDED_URL, environment.getProperty("spring.datasource.url"))
        assertEquals(EMBEDDED_URL, DatabaseSettingsInitializer.embeddedDatabase(environment).url)
        assertFalse(DatabaseSettingsInitializer.isManagedExternally(environment))
    }

    @Test
    fun `applies stored settings with precedence over the application configuration`() {
        store.save(postgres)

        initialize(environment)

        assertEquals(postgres.jdbcUrl(), environment.getProperty("spring.datasource.url"))
        assertEquals("gfadmin", environment.getProperty("spring.datasource.username"))
        assertEquals("secret", environment.getProperty("spring.datasource.password"))
        assertEquals("org.postgresql.Driver", environment.getProperty("spring.datasource.driver-class-name"))
        val embedded = DatabaseSettingsInitializer.embeddedDatabase(environment)
        assertEquals(EMBEDDED_URL, embedded.url)
        assertEquals("gameyfin", embedded.password)
        assertEquals("org.h2.Driver", embedded.driverClassName)

        DatabaseSettingsInitializer.apply(environment, DatabaseSettings.EMBEDDED)
        assertEquals(EMBEDDED_URL, environment.getProperty("spring.datasource.url"))
    }

    @Test
    fun `environment variables take precedence over stored settings`() {
        store.save(postgres)
        val environment = environment(mapOf("SPRING_DATASOURCE_URL" to "jdbc:postgresql://external/db"))

        initialize(environment)

        assertTrue(DatabaseSettingsInitializer.isManagedExternally(environment))
        assertEquals("jdbc:postgresql://external/db", environment.getProperty("spring.datasource.url"))
        assertEquals("gameyfin", environment.getProperty("spring.datasource.password"))
    }

    @Test
    fun `any externally configured datasource property prevents changes in the UI`() {
        val environment = environment(mapOf("SPRING_DATASOURCE_PASSWORD" to "other"))

        assertTrue(DatabaseSettingsInitializer.isManagedExternally(environment))
    }
}
