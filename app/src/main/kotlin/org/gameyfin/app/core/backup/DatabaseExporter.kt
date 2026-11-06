package org.gameyfin.app.core.backup

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.io.output.CloseShieldOutputStream
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.sql.DataSource
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/**
 * Writes the content of a Gameyfin database (and optionally the media files) into a backup archive.
 */
class DatabaseExporter {

    companion object {
        private val log = KotlinLogging.logger {}
        private const val FETCH_SIZE = 500
        private const val EXPORT_DATABASE_STEP = "Exporting database tables"
        private const val EXPORT_MEDIA_STEP = "Exporting images"
    }

    /**
     * Writes a complete backup archive of the database (and optionally the media files) to [output].
     *
     * All tables are exported within a read-only transaction with REPEATABLE READ isolation (if supported),
     * so the backup is consistent even though Gameyfin keeps running while it is created.
     *
     * @param mediaRoot directory containing the media files to include, or `null` to exclude media files
     * @param progress called whenever the export advances
     */
    fun writeArchive(
        dataSource: DataSource,
        output: OutputStream,
        createdAt: Instant,
        appKeyFingerprint: String,
        gameyfinVersion: String?,
        mediaRoot: Path?,
        progress: (BackupStep) -> Unit = {}
    ): BackupManifest {
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            val (databaseProduct, changeSets, tables) = dataSource.connection.use { connection ->
                inSnapshotTransaction(connection) {
                    Triple(
                        connection.metaData.databaseProductName,
                        readChangeSets(connection),
                        exportTables(connection, zip, progress)
                    )
                }
            }

            val mediaFileCount = mediaRoot?.let { exportMedia(it, zip, progress) } ?: 0

            progress(BackupStep("Finishing backup"))

            val manifest = BackupManifest(
                formatVersion = BackupFormat.FORMAT_VERSION,
                createdAt = createdAt,
                gameyfinVersion = gameyfinVersion,
                databaseProduct = databaseProduct,
                appKeyFingerprint = appKeyFingerprint,
                includesMedia = mediaRoot != null,
                mediaFileCount = mediaFileCount,
                changeSets = changeSets,
                tables = tables
            )
            zip.putNextEntry(ZipEntry(BackupFormat.MANIFEST_ENTRY))
            zip.write(BackupFormat.jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest.toJson()))
            zip.closeEntry()
            return manifest
        }
    }

    private fun <T> inSnapshotTransaction(connection: Connection, action: () -> T): T {
        val autoCommit = connection.autoCommit
        val readOnly = connection.isReadOnly
        val isolation = connection.transactionIsolation
        try {
            connection.isReadOnly = true
            try {
                connection.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
            } catch (e: Exception) {
                log.debug { "Could not set transaction isolation for backup: ${e.message}" }
            }
            connection.autoCommit = false
            return action()
        } finally {
            connection.rollback()
            connection.autoCommit = autoCommit
            connection.transactionIsolation = isolation
            connection.isReadOnly = readOnly
        }
    }

    /**
     * Returns the Liquibase changesets applied to the database, in the order they were executed.
     */
    fun readChangeSets(connection: Connection): List<BackupChangeSet> = try {
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT ID, AUTHOR FROM DATABASECHANGELOG ORDER BY ORDEREXECUTED").use { rs ->
                buildList { while (rs.next()) add(BackupChangeSet(rs.getString(1), rs.getString(2))) }
            }
        }
    } catch (e: Exception) {
        throw IllegalStateException("Could not read the database migration history: ${e.message}", e)
    }

    /**
     * Exports all application tables into [zip]. Should be called within a read-only transaction
     * (ideally with REPEATABLE READ isolation) to get a consistent snapshot.
     */
    fun exportTables(
        connection: Connection,
        zip: ZipOutputStream,
        progress: (BackupStep) -> Unit = {}
    ): List<BackupTable> {
        val schema = SchemaInspector(connection)
        val tables = schema.tables()
        return tables.mapIndexed { index, table ->
            progress(BackupStep(EXPORT_DATABASE_STEP, index, tables.size))
            exportTable(connection, schema, table, zip)
        }.also { progress(BackupStep(EXPORT_DATABASE_STEP, tables.size, tables.size)) }
    }

    private fun exportTable(
        connection: Connection,
        schema: SchemaInspector,
        table: String,
        zip: ZipOutputStream
    ): BackupTable {
        val entryName = "${BackupFormat.DATABASE_PREFIX}$table.json"
        zip.putNextEntry(ZipEntry(entryName))

        var rowCount = 0L
        connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY).use { stmt ->
            stmt.fetchSize = FETCH_SIZE
            stmt.executeQuery("SELECT * FROM ${schema.quote(table)}").use { rs ->
                val meta = rs.metaData
                val kinds = (1..meta.columnCount).map { ValueKind.fromJdbcType(meta.getColumnType(it)) }

                BackupFormat.jsonMapper.createGenerator(CloseShieldOutputStream.wrap(zip)).use { gen ->
                    gen.writeStartObject()
                    gen.writeStringProperty("table", table)
                    gen.writeName("columns")
                    gen.writeStartArray()
                    for (i in 1..meta.columnCount) {
                        gen.writeStartObject()
                        gen.writeStringProperty("name", meta.getColumnName(i))
                        gen.writeStringProperty("type", meta.getColumnTypeName(i))
                        gen.writeStringProperty("kind", kinds[i - 1].name)
                        gen.writeEndObject()
                    }
                    gen.writeEndArray()

                    gen.writeName("rows")
                    gen.writeStartArray()
                    while (rs.next()) {
                        gen.writeStartArray()
                        for (i in 1..meta.columnCount) JdbcValues.write(rs, i, kinds[i - 1], gen)
                        gen.writeEndArray()
                        rowCount++
                    }
                    gen.writeEndArray()
                    gen.writeEndObject()
                }
            }
        }

        zip.closeEntry()
        log.debug { "Exported $rowCount rows from table $table" }
        return BackupTable(table, entryName, rowCount)
    }

    /**
     * Adds all files of the media storage directory to [zip].
     * Media files are usually already compressed, so they are stored with the fastest compression level.
     *
     * @return the number of exported files
     */
    fun exportMedia(mediaRoot: Path, zip: ZipOutputStream, progress: (BackupStep) -> Unit = {}): Int {
        if (!Files.isDirectory(mediaRoot)) return 0

        progress(BackupStep(EXPORT_MEDIA_STEP))
        val files = Files.list(mediaRoot)
            .use { stream -> buildList { stream.filter { it.isRegularFile() }.sorted().forEach(::add) } }

        var count = 0
        zip.setLevel(Deflater.BEST_SPEED)
        try {
            files.forEachIndexed { index, file ->
                progress(BackupStep(EXPORT_MEDIA_STEP, index, files.size))
                val input = try {
                    Files.newInputStream(file)
                } catch (_: NoSuchFileException) {
                    // Gameyfin keeps running while the backup is created, so files may be deleted in the meantime
                    log.debug { "Skipping media file ${file.name} which has been deleted" }
                    return@forEachIndexed
                }
                input.use {
                    zip.putNextEntry(ZipEntry("${BackupFormat.MEDIA_PREFIX}${file.name}"))
                    it.copyTo(zip)
                    zip.closeEntry()
                }
                count++
            }
        } finally {
            zip.setLevel(Deflater.DEFAULT_COMPRESSION)
        }
        progress(BackupStep(EXPORT_MEDIA_STEP, files.size, files.size))
        log.debug { "Exported $count media files" }
        return count
    }
}
