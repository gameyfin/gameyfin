package org.gameyfin.app.system.backup

import io.github.oshai.kotlinlogging.KotlinLogging
import liquibase.integration.spring.SpringLiquibase
import liquibase.integration.spring.SpringResourceAccessor
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.core.env.Environment
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.time.Instant

/**
 * Executes a restore scheduled by [BackupService.restoreBackup].
 *
 * The restore runs right before Liquibase migrates the database during startup. At this point no other component
 * (Hibernate, plugins, jobs, ...) has accessed the database yet, so the content can safely be replaced.
 * Afterward Liquibase applies any changesets that are newer than the backup.
 *
 * If the restore fails, the error is recorded in the restore status and Gameyfin continues to start
 * (a backup of the previous database state is created before an admin-triggered restore).
 */
@Component
class PendingRestoreExecutor(private val environment: Environment) : BeanPostProcessor {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    override fun postProcessBeforeInitialization(bean: Any, beanName: String): Any {
        if (bean is SpringLiquibase) executePendingRestore(bean)
        return bean
    }

    private fun executePendingRestore(liquibase: SpringLiquibase) {
        val storage = BackupStorage(Path.of(environment.getProperty("gameyfin.backup.directory", "./backups")))
        val backupName = storage.pendingRestore() ?: return

        // Remove the marker first, so a failing restore is never retried on every startup
        storage.clearPendingRestore()

        val status = try {
            val restorer = DatabaseRestorer(
                dataSource = liquibase.dataSource,
                changeLog = liquibase.changeLog,
                resourceAccessor = SpringResourceAccessor(liquibase.resourceLoader ?: DefaultResourceLoader()),
                contexts = liquibase.contexts,
                labels = liquibase.labelFilter
            ) { database ->
                liquibase.defaultSchema?.let { database.defaultSchemaName = it }
                liquibase.liquibaseSchema?.let { database.liquibaseSchemaName = it }
                liquibase.liquibaseTablespace?.let { database.liquibaseTablespaceName = it }
                liquibase.databaseChangeLogTable?.let { database.databaseChangeLogTableName = it }
                liquibase.databaseChangeLogLockTable?.let { database.databaseChangeLogLockTableName = it }
            }

            val mediaRoot = Path.of(environment.getProperty("spring.content.fs.filesystem-root", "./data/"))
            val summary = restorer.restore(storage.resolve(backupName), AppKeyFingerprint.current(), mediaRoot)

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
}
