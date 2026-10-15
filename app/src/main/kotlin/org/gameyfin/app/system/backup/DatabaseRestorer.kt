package org.gameyfin.app.system.backup

import io.github.oshai.kotlinlogging.KotlinLogging
import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.Scope
import liquibase.changelog.ChangeLogParameters
import liquibase.database.Database
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.parser.ChangeLogParserFactory
import liquibase.resource.ResourceAccessor
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.databind.JsonNode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.sql.Connection
import java.util.zip.ZipException
import java.util.zip.ZipFile
import javax.sql.DataSource

/**
 * Restores a backup archive created by [DatabaseExporter] into a (possibly different type of) database.
 *
 * The restore works as follows:
 * 1. All objects of the target schema are dropped.
 * 2. The Liquibase changesets which were applied to the source database are applied to the target database,
 *    so the schema exactly matches the one the backup was created from.
 * 3. The table data is imported (in foreign key dependency order) within a single transaction.
 * 4. Sequences and identity columns are moved past the highest restored ID.
 * 5. Media files (if included) are extracted into the media directory.
 *
 * Changesets introduced by newer Gameyfin versions are *not* applied here; they are applied by the regular
 * Liquibase migration afterward, so data migrations contained in them run on the restored data as usual.
 */
class DatabaseRestorer(
    private val dataSource: DataSource,
    private val changeLog: String,
    private val resourceAccessor: ResourceAccessor,
    private val contexts: String? = null,
    private val labels: String? = null,
    private val databaseCustomizer: (Database) -> Unit = {}
) {

    companion object {
        private val log = KotlinLogging.logger {}
        private const val BATCH_SIZE = 500

        fun readManifest(archive: Path): BackupManifest = openArchive(archive).use { readManifest(it) }

        fun openArchive(archive: Path): ZipFile = try {
            ZipFile(archive.toFile())
        } catch (e: ZipException) {
            throw InvalidBackupException("The file is not a valid Gameyfin backup (not a ZIP archive)", e)
        }

        fun readManifest(zip: ZipFile): BackupManifest {
            val entry = zip.getEntry(BackupFormat.MANIFEST_ENTRY)
                ?: throw InvalidBackupException("The file is not a valid Gameyfin backup (manifest is missing)")
            val node = try {
                zip.getInputStream(entry).use { BackupFormat.jsonMapper.readTree(it) }
            } catch (e: Exception) {
                throw InvalidBackupException("The backup manifest could not be read: ${e.message}", e)
            }
            return BackupManifest.fromJson(node)
        }
    }

    data class RestoreSummary(val tables: Int, val rows: Long, val mediaFiles: Int)

    /**
     * Returns the changesets of the changelog shipped with this version of Gameyfin, in changelog order.
     */
    fun currentChangeSets(): List<BackupChangeSet> = changeLogChangeSets

    private val changeLogChangeSets: List<BackupChangeSet> by lazy {
        withLiquibaseScope {
            val parser = ChangeLogParserFactory.getInstance().getParser(changeLog, resourceAccessor)
            parser.parse(changeLog, ChangeLogParameters(), resourceAccessor).changeSets
                .map { BackupChangeSet(it.id, it.author) }
        }
    }

    /**
     * Checks whether the backup described by [manifest] can be restored by this Gameyfin instance.
     *
     * @throws InvalidBackupException if the backup can not be restored
     */
    fun validate(manifest: BackupManifest, appKeyFingerprint: String) {
        if (manifest.formatVersion > BackupFormat.FORMAT_VERSION) {
            throw InvalidBackupException(
                "The backup format (version ${manifest.formatVersion}) is not supported by this version of Gameyfin. " +
                        "Please update Gameyfin."
            )
        }

        if (manifest.appKeyFingerprint != appKeyFingerprint) {
            throw InvalidBackupException(
                "The backup was created by a Gameyfin instance using a different APP_KEY. " +
                        "Set APP_KEY to the value used when the backup was created and try again."
            )
        }

        val current = currentChangeSets()
        val backup = manifest.changeSets
        if (backup.isEmpty()) {
            throw InvalidBackupException("The backup does not contain any database migration history")
        }
        if (backup.size > current.size || current.subList(0, backup.size) != backup) {
            throw InvalidBackupException(
                "The backup was created by an incompatible (probably newer) version of Gameyfin " +
                        "(${manifest.gameyfinVersion ?: "unknown version"}). Please update Gameyfin."
            )
        }
    }

    /**
     * Replaces the content of the database with the content of [archive].
     *
     * @param mediaRoot directory to extract media files into, or `null` to skip media files
     */
    fun restore(archive: Path, appKeyFingerprint: String, mediaRoot: Path?): RestoreSummary {
        openArchive(archive).use { zip ->
            val manifest = readManifest(zip)
            validate(manifest, appKeyFingerprint)

            log.info { "Restoring backup ${archive.fileName} (created ${manifest.createdAt} on ${manifest.databaseProduct})" }

            var rows = 0L
            var tables = 0
            dataSource.connection.use { connection ->
                recreateSchema(connection, manifest)

                connection.autoCommit = false
                try {
                    val schema = SchemaInspector(connection)
                    val targetTables = schema.tables()
                    val backupTables = manifest.tables.associateBy { it.name.uppercase() }

                    val unknownTables = backupTables.keys - targetTables.map { it.uppercase() }.toSet()
                    if (unknownTables.isNotEmpty()) {
                        log.warn { "Skipping tables from backup which do not exist in the database: $unknownTables" }
                    }

                    for (table in schema.sortByDependencies(targetTables)) {
                        val backupTable = backupTables[table.uppercase()] ?: continue
                        rows += importTable(connection, schema, table, zip, backupTable)
                        tables++
                    }
                    connection.commit()
                } catch (e: Exception) {
                    connection.rollback()
                    throw e
                } finally {
                    connection.autoCommit = true
                }

                resetSequences(connection)
            }

            val mediaFiles = if (mediaRoot != null && manifest.includesMedia) restoreMedia(zip, mediaRoot) else 0

            log.info { "Restored $rows rows in $tables tables and $mediaFiles media files" }
            return RestoreSummary(tables, rows, mediaFiles)
        }
    }

    private fun recreateSchema(connection: Connection, manifest: BackupManifest) {
        withLiquibaseScope {
            val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(connection))
            databaseCustomizer(database)
            val liquibase = Liquibase(changeLog, resourceAccessor, database)

            log.info { "Dropping all database objects" }
            liquibase.dropAll()

            log.info { "Recreating database schema (${manifest.changeSets.size} changesets)" }
            liquibase.update(manifest.changeSets.size, Contexts(contexts), LabelExpression(labels))
        }
    }

    private fun importTable(
        connection: Connection,
        schema: SchemaInspector,
        table: String,
        zip: ZipFile,
        backupTable: BackupTable
    ): Long {
        if (!backupTable.entry.startsWith(BackupFormat.DATABASE_PREFIX)) {
            throw InvalidBackupException("Invalid entry '${backupTable.entry}' for table ${backupTable.name}")
        }
        val entry = zip.getEntry(backupTable.entry)
            ?: throw InvalidBackupException("Data for table ${backupTable.name} is missing in the backup")

        val targetColumns = schema.columns(table).associateBy { it.name.uppercase() }
        var columnMapping: List<SchemaInspector.Column?>? = null
        var rowCount = 0L

        zip.getInputStream(entry).use { input ->
            BackupFormat.jsonMapper.createParser(input).use { parser ->
                if (parser.nextToken() != JsonToken.START_OBJECT) {
                    throw InvalidBackupException("Data for table ${backupTable.name} is malformed")
                }
                while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                    val property = parser.currentName()
                    parser.nextToken()
                    when (property) {
                        "columns" -> {
                            val columns = parser.readValueAsTree<JsonNode>().toList().map { it.get("name").asString() }
                            val unknownColumns = columns.filter { it.uppercase() !in targetColumns }
                            if (unknownColumns.isNotEmpty()) {
                                log.warn { "Skipping columns of table $table which do not exist in the database: $unknownColumns" }
                            }
                            columnMapping = columns.map { targetColumns[it.uppercase()] }
                        }

                        "rows" -> {
                            val mapping = columnMapping
                                ?: throw InvalidBackupException("Data for table ${backupTable.name} is malformed")
                            rowCount = insertRows(connection, schema, table, mapping, parser)
                        }

                        else -> parser.skipChildren()
                    }
                }
            }
        }

        log.debug { "Imported $rowCount rows into table $table" }
        return rowCount
    }

    private fun insertRows(
        connection: Connection,
        schema: SchemaInspector,
        table: String,
        columnMapping: List<SchemaInspector.Column?>,
        parser: JsonParser
    ): Long {
        val usedColumns = columnMapping.withIndex().mapNotNull { (index, column) -> column?.let { index to it } }
        if (usedColumns.isEmpty()) {
            parser.skipChildren()
            return 0
        }

        val sql = "INSERT INTO ${schema.quote(table)} " +
                "(${usedColumns.joinToString { schema.quote(it.second.name) }}) " +
                "VALUES (${usedColumns.joinToString { "?" }})"

        var rowCount = 0L
        connection.prepareStatement(sql).use { ps ->
            while (parser.nextToken() == JsonToken.START_ARRAY) {
                val row = parser.readValueAsTree<JsonNode>()
                usedColumns.forEachIndexed { parameterIndex, (sourceIndex, column) ->
                    JdbcValues.bind(ps, parameterIndex + 1, column.jdbcType, row.get(sourceIndex))
                }
                ps.addBatch()
                if (++rowCount % BATCH_SIZE == 0L) ps.executeBatch()
            }
            ps.executeBatch()
        }
        return rowCount
    }

    /**
     * Moves all sequences (Hibernate uses one sequence named `<TABLE>_SEQ` per entity table) and identity columns
     * past the highest restored ID, so newly created entities do not collide with restored ones.
     */
    private fun resetSequences(connection: Connection) {
        val schema = SchemaInspector(connection)
        val tables = schema.tables()
        val tablesByName = tables.associateBy { it.uppercase() }

        for (sequence in schema.sequences()) {
            if (!sequence.name.endsWith("_SEQ", ignoreCase = true)) continue
            val table = tablesByName[sequence.name.dropLast(4).uppercase()] ?: continue
            val idColumn = schema.columns(table).firstOrNull { it.name.equals("ID", ignoreCase = true) } ?: continue
            val max = maxValue(connection, schema, table, idColumn.name) ?: continue
            // Hibernate's pooled optimizer uses the IDs (value - increment, value], so skip a whole increment
            val next = max + maxOf(sequence.increment, 1) + 1
            execute(connection, "ALTER SEQUENCE ${schema.quote(sequence.name)} RESTART WITH $next")
        }

        for (table in tables) {
            for (column in schema.columns(table).filter { it.autoIncrement }) {
                val max = maxValue(connection, schema, table, column.name) ?: continue
                execute(
                    connection,
                    "ALTER TABLE ${schema.quote(table)} ALTER COLUMN ${schema.quote(column.name)} RESTART WITH ${max + 1}"
                )
            }
        }
    }

    private fun maxValue(connection: Connection, schema: SchemaInspector, table: String, column: String): Long? =
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT MAX(${schema.quote(column)}) FROM ${schema.quote(table)}").use { rs ->
                if (rs.next()) rs.getLong(1).takeUnless { rs.wasNull() } else null
            }
        }

    private fun execute(connection: Connection, sql: String) {
        try {
            connection.createStatement().use { it.execute(sql) }
        } catch (e: Exception) {
            log.warn { "Could not execute '$sql': ${e.message}" }
        }
    }

    private fun restoreMedia(zip: ZipFile, mediaRoot: Path): Int {
        val root = mediaRoot.toAbsolutePath().normalize()
        Files.createDirectories(root)

        var count = 0
        for (entry in zip.entries().asSequence()) {
            if (entry.isDirectory || !entry.name.startsWith(BackupFormat.MEDIA_PREFIX)) continue

            val fileName = entry.name.removePrefix(BackupFormat.MEDIA_PREFIX)
            val target = root.resolve(fileName).normalize()
            if (fileName.isBlank() || fileName.contains('/') || fileName.contains('\\') || target.parent != root) {
                log.warn { "Skipping invalid media entry '${entry.name}'" }
                continue
            }

            zip.getInputStream(entry).use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }
            count++
        }
        return count
    }

    private fun <T> withLiquibaseScope(block: () -> T): T =
        Scope.child(
            mapOf<String, Any>(Scope.Attr.resourceAccessor.name to resourceAccessor),
            Scope.ScopedRunnerWithReturn { block() }
        )
}
