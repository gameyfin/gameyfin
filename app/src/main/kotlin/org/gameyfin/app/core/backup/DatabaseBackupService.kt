package org.gameyfin.app.core.backup

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.sql.DataSource
import kotlin.io.path.deleteIfExists
import kotlin.io.path.fileSize
import kotlin.io.path.name

/**
 * Creates consistent, online backups of the embedded H2 database.
 *
 * Uses H2's `BACKUP TO` statement, which produces a zip archive of the database
 * while the application keeps running. This is the only safe way to copy an H2
 * file store: copying `.mv.db` from the filesystem while the store is open can
 * capture a half-written chunk and yield an unrecoverable file.
 */
@Service
class DatabaseBackupService(
    private val dataSource: DataSource
) {
    companion object {
        private val log = KotlinLogging.logger {}

        /** Relative to the application working directory, so it lands in the mounted `db` volume. */
        private val BACKUP_DIR: Path = Paths.get("./db/backups")

        private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

        /** How many backups to keep before the oldest ones are deleted. */
        private const val KEEP = 5
    }

    /**
     * Writes a backup archive and prunes older ones.
     *
     * Never throws: a failed backup must not prevent the operation that requested it.
     * The caller gets `null` and a logged warning instead.
     */
    fun backup(reason: String): Path? {
        return try {
            Files.createDirectories(BACKUP_DIR)

            val target = BACKUP_DIR.resolve("gameyfin_db_${LocalDateTime.now().format(TIMESTAMP)}_$reason.zip")

            val started = System.currentTimeMillis()
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    // BACKUP TO does not accept bind parameters, and the path is built
                    // from a fixed directory plus a timestamp, never from user input.
                    statement.execute("BACKUP TO '${target.toAbsolutePath()}'")
                }
            }
            val elapsed = System.currentTimeMillis() - started

            log.info { "Database backup written to ${target.name} (${target.fileSize() / 1024} KiB, ${elapsed} ms)" }

            prune()
            target
        } catch (e: Exception) {
            log.warn { "Database backup failed ($reason): ${e.message}" }
            log.debug(e) {}
            null
        }
    }

    /** Keeps the [KEEP] most recent archives, deletes the rest. */
    private fun prune() {
        try {
            Files.list(BACKUP_DIR).use { stream ->
                stream.filter { it.name.startsWith("gameyfin_db_") && it.name.endsWith(".zip") }
                    .sorted(compareByDescending { it.name })
                    .skip(KEEP.toLong())
                    .forEach { stale ->
                        stale.deleteIfExists()
                        log.debug { "Deleted stale backup ${stale.name}" }
                    }
            }
        } catch (e: Exception) {
            log.warn { "Could not prune old database backups: ${e.message}" }
        }
    }
}
