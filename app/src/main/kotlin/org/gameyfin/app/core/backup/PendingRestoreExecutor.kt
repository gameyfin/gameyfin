package org.gameyfin.app.core.backup

import io.github.oshai.kotlinlogging.KotlinLogging
import liquibase.integration.spring.SpringResourceAccessor
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.liquibase.autoconfigure.LiquibaseProperties
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.Environment
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.nio.file.Path
import java.time.Instant

/**
 * Executes a restore scheduled by [BackupService.restoreBackup].
 *
 * The restore runs before the application context is refreshed (registered in `META-INF/spring.factories`, so it also
 * runs when Gameyfin restarts itself). At this point no other component has accessed the database yet, so the content
 * can safely be replaced. This includes conditions which are evaluated while the configuration is parsed and read the
 * database directly (e.g. [org.gameyfin.app.core.security.SsoEnabledCondition]), so they already see the restored data.
 * Afterward Liquibase applies any changesets that are newer than the backup.
 * Once Gameyfin has started, images missing after the restore are recovered in the background ([ImageRecoveryService]).
 *
 * If the restore fails, the error is recorded in the restore status and Gameyfin continues to start
 * (a backup of the previous database state is created before an admin-triggered restore).
 */
class PendingRestoreExecutor : ApplicationContextInitializer<ConfigurableApplicationContext> {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    override fun initialize(context: ConfigurableApplicationContext) {
        executePendingRestore(context.environment, context.classLoader)
    }

    private fun executePendingRestore(environment: Environment, classLoader: ClassLoader?) {
        val storage = BackupStorage(Path.of(environment.getProperty("gameyfin.backup.directory", "./backups")))
        val backupName = storage.pendingRestore() ?: return

        val liquibase = Binder.get(environment).bindOrCreate("spring.liquibase", LiquibaseProperties::class.java)
        if (!liquibase.isEnabled) {
            log.warn { "Not restoring backup $backupName because Liquibase is disabled" }
            return
        }

        // Remove the marker first, so a failing restore is never retried on every startup
        storage.clearPendingRestore()

        val status = try {
            val restorer = DatabaseRestorer(
                dataSource = dataSource(environment, liquibase),
                changeLog = liquibase.changeLog,
                resourceAccessor = SpringResourceAccessor(DefaultResourceLoader(classLoader)),
                contexts = liquibase.contexts?.joinToString(","),
                labels = liquibase.labelFilter?.joinToString(",")
            ) { database ->
                liquibase.defaultSchema?.let { database.defaultSchemaName = it }
                liquibase.liquibaseSchema?.let { database.liquibaseSchemaName = it }
                liquibase.liquibaseTablespace?.let { database.liquibaseTablespaceName = it }
                liquibase.databaseChangeLogTable?.let { database.databaseChangeLogTableName = it }
                liquibase.databaseChangeLogLockTable?.let { database.databaseChangeLogLockTableName = it }
            }

            val mediaRoot = Path.of(environment.getProperty("spring.content.fs.filesystem-root", "./data/"))
            val summary = restorer.restore(storage.resolve(backupName), AppKeyFingerprint.current(), mediaRoot)
            // Images which are not part of the backup are downloaded again once Gameyfin has started
            try {
                storage.scheduleImageRecovery()
            } catch (e: Exception) {
                log.warn(e) { "Could not schedule the recovery of missing images" }
            }

            RestoreStatusDto(
                backupName = backupName,
                success = true,
                message = "Restored ${summary.rows} rows in ${summary.tables} tables" +
                        if (summary.mediaFiles > 0) " and ${summary.mediaFiles} media files" else "",
                finishedAt = Instant.now()
            )
        } catch (e: Exception) {
            log.error(e) { "Restoring backup $backupName failed" }
            RestoreStatusDto(
                backupName = backupName,
                success = false,
                message = e.message ?: e.javaClass.simpleName,
                finishedAt = Instant.now()
            )
        }

        try {
            storage.writeRestoreStatus(status)
        } catch (e: Exception) {
            log.warn(e) { "Could not write restore status" }
        }
    }

    /** Connects to the database the same way Liquibase does (the DataSource bean does not exist yet) */
    private fun dataSource(environment: Environment, liquibase: LiquibaseProperties): DriverManagerDataSource {
        val url = liquibase.url ?: environment.getRequiredProperty("spring.datasource.url")
        val ownConnection = liquibase.url != null
        return DriverManagerDataSource(url).apply {
            (liquibase.user ?: environment.getProperty("spring.datasource.username"))?.let { username = it }
            (liquibase.password ?: environment.getProperty("spring.datasource.password"))?.let { password = it }
            val driver = if (ownConnection) liquibase.driverClassName
            else environment.getProperty("spring.datasource.driver-class-name")
            driver?.let { setDriverClassName(it) }
        }
    }
}
