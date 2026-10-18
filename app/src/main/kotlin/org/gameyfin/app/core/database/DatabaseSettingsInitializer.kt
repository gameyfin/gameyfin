package org.gameyfin.app.core.database

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.Ordered
import org.springframework.core.env.CommandLinePropertySource
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.Environment
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import java.nio.file.Path

/**
 * Applies the database settings configured in the UI ([DatabaseSettingsStore]) to the environment before the
 * application context is refreshed (registered in `META-INF/spring.factories`, so it also runs when Gameyfin restarts
 * itself). It runs before [org.gameyfin.app.core.backup.PendingRestoreExecutor], so a pending restore is executed on
 * the configured database.
 *
 * Datasource properties set via environment variables, system properties or command line arguments take precedence:
 * in this case the settings configured in the UI are ignored and the database can not be changed in the UI.
 */
class DatabaseSettingsInitializer : ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {

    companion object {
        private val log = KotlinLogging.logger {}

        const val ORDER = Ordered.HIGHEST_PRECEDENCE + 10
        const val SETTINGS_FILE_PROPERTY = "gameyfin.database.settings-file"
        const val DEFAULT_SETTINGS_FILE = "./db/database.json"

        private const val PROPERTY_SOURCE_NAME = "gameyfinDatabaseSettings"
        private const val EMBEDDED_PROPERTY_SOURCE_NAME = "gameyfinEmbeddedDatabase"
        private const val EMBEDDED_PREFIX = "gameyfin.database.embedded."

        private const val URL = "spring.datasource.url"
        private const val USERNAME = "spring.datasource.username"
        private const val PASSWORD = "spring.datasource.password"
        private const val DRIVER = "spring.datasource.driver-class-name"
        private val DATASOURCE_PROPERTIES = listOf(URL, USERNAME, PASSWORD, DRIVER, "spring.datasource.driverClassName")

        /** Property sources which are not part of the configuration shipped with Gameyfin */
        private val EXTERNAL_PROPERTY_SOURCES = listOf(
            CommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME,
            StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            "spring.application.json"
        )

        fun store(environment: Environment) =
            DatabaseSettingsStore(Path.of(environment.getProperty(SETTINGS_FILE_PROPERTY, DEFAULT_SETTINGS_FILE)))

        /** Whether the datasource is configured via environment variables, system properties or command line arguments */
        fun isManagedExternally(environment: ConfigurableEnvironment): Boolean =
            environment.propertySources
                .filter { it.name in EXTERNAL_PROPERTY_SOURCES }
                .any { source -> DATASOURCE_PROPERTIES.any { source.containsProperty(it) } }

        /**
         * Applies [settings] to the environment, replacing previously applied settings.
         * The embedded database uses the default configuration, so nothing is overridden for it.
         */
        fun apply(environment: ConfigurableEnvironment, settings: DatabaseSettings) {
            val sources = environment.propertySources
            sources.remove(PROPERTY_SOURCE_NAME)
            val url = settings.jdbcUrl() ?: return

            val source = MapPropertySource(
                PROPERTY_SOURCE_NAME,
                mapOf(
                    URL to url,
                    USERNAME to settings.username.orEmpty(),
                    PASSWORD to settings.password.orEmpty(),
                    DRIVER to settings.type.driverClassName
                )
            )
            // Lower precedence than environment variables etc., higher precedence than application.yml
            if (sources.contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
                sources.addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, source)
            } else {
                sources.addFirst(source)
            }
        }

        /** Connection properties of the embedded database (see [captureEmbeddedDatabase]) */
        fun embeddedDatabase(environment: Environment): EmbeddedDatabase = EmbeddedDatabase(
            url = environment.getProperty("${EMBEDDED_PREFIX}url")
                ?: environment.getRequiredProperty(URL),
            username = environment.getProperty("${EMBEDDED_PREFIX}username"),
            password = environment.getProperty("${EMBEDDED_PREFIX}password"),
            driverClassName = environment.getProperty("${EMBEDDED_PREFIX}driver-class-name")
        )

        /**
         * Remembers the default datasource configuration (the embedded database), as it is hidden once settings
         * for a different database are applied.
         */
        private fun captureEmbeddedDatabase(environment: ConfigurableEnvironment) {
            if (environment.propertySources.contains(EMBEDDED_PROPERTY_SOURCE_NAME)) return
            val properties = buildMap {
                environment.getProperty(URL)?.let { put("${EMBEDDED_PREFIX}url", it) }
                environment.getProperty(USERNAME)?.let { put("${EMBEDDED_PREFIX}username", it) }
                environment.getProperty(PASSWORD)?.let { put("${EMBEDDED_PREFIX}password", it) }
                (environment.getProperty(DRIVER) ?: environment.getProperty("spring.datasource.driverClassName"))
                    ?.let { put("${EMBEDDED_PREFIX}driver-class-name", it) }
            }
            environment.propertySources.addLast(MapPropertySource(EMBEDDED_PROPERTY_SOURCE_NAME, properties))
        }
    }

    data class EmbeddedDatabase(
        val url: String,
        val username: String?,
        val password: String?,
        val driverClassName: String?
    )

    override fun getOrder(): Int = ORDER

    override fun initialize(context: ConfigurableApplicationContext) {
        val environment = context.environment
        captureEmbeddedDatabase(environment)

        val store = store(environment)
        val settings = store.load() ?: return

        if (isManagedExternally(environment)) {
            if (settings.type != DatabaseType.H2) {
                log.warn {
                    "Ignoring the database configured in ${store.settingsFile} (${settings.describe()}), " +
                            "because the database is configured via environment variables"
                }
            }
            return
        }

        apply(environment, settings)
        if (settings.type != DatabaseType.H2) {
            log.info {
                "Using database ${settings.describe()} configured in ${store.settingsFile} " +
                        "(delete this file to revert to the embedded H2 database)"
            }
        }
    }
}
