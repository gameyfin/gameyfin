package org.gameyfin.app.core.database

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.app.core.backup.BackupService
import org.gameyfin.app.system.SystemService
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.stereotype.Service
import java.sql.Connection
import java.sql.Driver
import java.util.*
import javax.sql.DataSource

/**
 * Manages the database Gameyfin uses.
 *
 * Switching to a different database transfers all data using a backup which is restored into the new database on
 * the next startup (see [BackupService.transferDatabase]).
 */
@Service
class DatabaseService(
    private val environment: ConfigurableEnvironment,
    private val dataSource: DataSource,
    private val backupService: BackupService,
    private val systemService: SystemService
) {

    companion object {
        private val log = KotlinLogging.logger {}
        private const val CONNECT_TIMEOUT_SECONDS = 10
    }

    private val store = DatabaseSettingsInitializer.store(environment)

    fun isManagedExternally(): Boolean = DatabaseSettingsInitializer.isManagedExternally(environment)

    fun getDatabaseInfo(): DatabaseInfoDto {
        val managedExternally = isManagedExternally()
        val settings = if (managedExternally) null else currentSettings()
        val url = environment.getProperty("spring.datasource.url")

        val (productName, productVersion) = try {
            dataSource.connection.use { it.metaData.databaseProductName to it.metaData.databaseProductVersion }
        } catch (e: Exception) {
            log.warn { "Could not determine database product: ${e.message}" }
            null to null
        }

        return DatabaseInfoDto(
            type = settings?.type ?: databaseTypeOf(url),
            productName = productName,
            productVersion = productVersion,
            managedExternally = managedExternally,
            // Parameters are removed, as they may contain credentials
            url = if (managedExternally) url?.substringBefore('?')?.substringBefore(';') else null,
            host = settings?.host,
            port = settings?.port,
            database = settings?.database,
            username = settings?.username,
            passwordSet = !settings?.password.isNullOrEmpty(),
            settingsFile = store.settingsFile.toString(),
            lastSwitch = store.readSwitchStatus()
        )
    }

    /**
     * Checks whether a connection to the given database can be established.
     */
    fun testConnection(connection: DatabaseConnectionDto): DatabaseConnectionTestResultDto {
        val settings = try {
            resolve(connection)
        } catch (e: IllegalArgumentException) {
            return DatabaseConnectionTestResultDto(success = false, message = e.message ?: "Invalid settings")
        }
        return test(settings)
    }

    /**
     * Switches to the given database and restarts Gameyfin.
     * All data is transferred to the new database; existing data in the new database is deleted.
     */
    fun switchDatabase(connection: DatabaseConnectionDto) {
        val target = prepareSwitch(connection)
            ?: throw IllegalArgumentException("Gameyfin is already using this database")
        performSwitch(target)
    }

    /**
     * Validates the given database and checks whether a connection can be established.
     *
     * @return the settings to pass to [performSwitch], or `null` if Gameyfin is already using this database
     * @throws IllegalStateException if the database is configured via environment variables
     * @throws IllegalArgumentException if the settings are invalid or no connection can be established
     */
    fun prepareSwitch(connection: DatabaseConnectionDto): DatabaseSettings? {
        check(!isManagedExternally()) {
            "The database is configured via environment variables and can not be changed in the UI"
        }
        val target = resolve(connection)
        if (target == currentSettings()) return null

        val result = test(target)
        require(result.success) { "Could not connect to the database: ${result.message}" }
        return target
    }

    fun performSwitch(target: DatabaseSettings) {
        val current = currentSettings()

        if (target.isSameDatabase(current)) {
            // Only the credentials have changed, so the data does not need to be transferred
            store.save(target)
            log.info { "Updated the credentials of database ${target.describe()}, restarting Gameyfin" }
            systemService.restart()
            return
        }

        log.info { "Switching database from ${current.describe()} to ${target.describe()}" }
        backupService.transferDatabase { backupName ->
            store.schedulePendingSwitch(PendingDatabaseSwitch(current, target, backupName))
            try {
                store.save(target)
            } catch (e: Exception) {
                store.clearPendingSwitch()
                throw e
            }
        }
    }

    private fun currentSettings(): DatabaseSettings = store.load() ?: DatabaseSettings.EMBEDDED

    /** Converts the input to validated settings; a missing password is taken from the current settings */
    private fun resolve(connection: DatabaseConnectionDto): DatabaseSettings {
        val current = currentSettings()
        val password = connection.password
            ?: current.password.takeIf { current.type == connection.type }
        return DatabaseSettings.of(
            type = connection.type,
            host = connection.host,
            port = connection.port,
            database = connection.database,
            username = connection.username,
            password = password
        )
    }

    private fun test(settings: DatabaseSettings): DatabaseConnectionTestResultDto = try {
        connect(settings).use { connection ->
            val metaData = connection.metaData
            val tables = metaData.getTables(connection.catalog, connection.schema, "%", arrayOf("TABLE"))
                .use { rs -> generateSequence { if (rs.next()) Unit else null }.count() }
            DatabaseConnectionTestResultDto(
                success = true,
                message = "Connected to ${metaData.databaseProductName} ${metaData.databaseProductVersion}",
                productName = metaData.databaseProductName,
                productVersion = metaData.databaseProductVersion,
                existingTables = tables
            )
        }
    } catch (e: Exception) {
        log.debug(e) { "Could not connect to ${settings.describe()}" }
        DatabaseConnectionTestResultDto(success = false, message = e.message ?: e.javaClass.simpleName)
    }

    private fun connect(settings: DatabaseSettings): Connection {
        val properties = Properties()
        val url: String
        val driverClassName: String

        if (settings.type == DatabaseType.H2) {
            val embedded = DatabaseSettingsInitializer.embeddedDatabase(environment)
            url = embedded.url
            driverClassName = embedded.driverClassName ?: DatabaseType.H2.driverClassName
            embedded.username?.let { properties["user"] = it }
            embedded.password?.let { properties["password"] = it }
        } else {
            url = requireNotNull(settings.jdbcUrl())
            driverClassName = settings.type.driverClassName
            settings.username?.let { properties["user"] = it }
            settings.password?.let { properties["password"] = it }
            properties["connectTimeout"] = CONNECT_TIMEOUT_SECONDS.toString()
            properties["loginTimeout"] = CONNECT_TIMEOUT_SECONDS.toString()
        }

        val driver = Class.forName(driverClassName).getDeclaredConstructor().newInstance() as Driver
        return driver.connect(url, properties)
            ?: throw IllegalArgumentException("The JDBC driver does not accept the URL")
    }
}
