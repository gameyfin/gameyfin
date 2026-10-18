package org.gameyfin.app.system.backup

import liquibase.integration.spring.SpringLiquibase
import liquibase.integration.spring.SpringResourceAccessor
import org.gameyfin.app.core.backup.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.io.TempDir
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.PostgreSQLContainer
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Blob
import java.sql.Clob
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.sql.DataSource
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Creates backups of a database and restores them into databases of the same and of different types.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatabaseBackupIntegrationTest {

    companion object {
        private const val CHANGELOG = "classpath:/db/changelog/db.changelog-master.yaml"
        private val APP_KEY_FINGERPRINT =
            AppKeyFingerprint.of(Base64.getEncoder().encodeToString(ByteArray(16) { it.toByte() }))
        private val BLOB_CONTENT = byteArrayOf(0, 1, 2, -1, 127, -128)
        private const val UNICODE_TEXT = "Ünïcødé 🎮\nwith 'quotes' and \"double quotes\""
    }

    private val postgres = PostgreSQLContainer<Nothing>("postgres:alpine")

    @TempDir
    lateinit var tempDir: Path

    @BeforeAll
    fun startPostgres() = postgres.start()

    @AfterAll
    fun stopPostgres() = postgres.stop()

    private fun h2(name: String): DataSource =
        DriverManagerDataSource("jdbc:h2:file:${tempDir.resolve(name).toAbsolutePath()}", "sa", "")

    private fun postgres(): DataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)

    /** Runs the database migrations the same way as on application startup */
    private fun migrate(dataSource: DataSource) {
        SpringLiquibase().apply {
            this.dataSource = dataSource
            changeLog = CHANGELOG
            resourceLoader = DefaultResourceLoader()
            afterPropertiesSet()
        }
    }

    private fun restorer(dataSource: DataSource) =
        DatabaseRestorer(dataSource, CHANGELOG, SpringResourceAccessor(DefaultResourceLoader()))

    private fun backup(dataSource: DataSource, mediaRoot: Path? = null): Path {
        val file = Files.createTempFile(tempDir, "backup", ".zip")
        Files.newOutputStream(file).use {
            DatabaseExporter().writeArchive(dataSource, it, Instant.now(), APP_KEY_FINGERPRINT, "test", mediaRoot)
        }
        return file
    }

    /** Quoted names of the `key` and `value` columns, whose case depends on the database */
    private fun keyValueColumns(c: Connection, table: String): String {
        val schema = SchemaInspector(c)
        val columns = schema.columns(table).associateBy { it.name.uppercase() }
        return "${schema.quote(columns.getValue("KEY").name)}, ${schema.quote(columns.getValue("VALUE").name)}"
    }

    private fun insertSampleData(dataSource: DataSource) = dataSource.connection.use { c ->
        val appConfig = keyValueColumns(c, "APP_CONFIG")
        val userPreference = keyValueColumns(c, "USER_PREFERENCE")
        c.createStatement().use { s ->
            s.execute("INSERT INTO APP_CONFIG ($appConfig) VALUES ('library.scan.enable', 'true')")
            s.execute("INSERT INTO APP_CONFIG ($appConfig) VALUES ('sso.client-secret', 'ZW5jcnlwdGVk')")
            s.execute("INSERT INTO IMAGE (ID, CONTENT_ID, CONTENT_LENGTH, MIME_TYPE, ORIGINAL_URL, TYPE) VALUES (1, 'content-1', 1234, 'image/png', 'https://example.org/1.png', 0)")
            s.execute("INSERT INTO IMAGE (ID, CONTENT_ID, TYPE) VALUES (51, 'content-2', 2)")
            s.execute("INSERT INTO LIBRARY (ID, CREATED_AT, NAME, UPDATED_AT, DISPLAY_ON_HOMEPAGE, DISPLAY_ORDER) VALUES (1, TIMESTAMP '2024-01-02 03:04:05.123456', 'Library', TIMESTAMP '2024-02-03 04:05:06', FALSE, 3)")
            s.execute("INSERT INTO USERS (ID, EMAIL, EMAIL_CONFIRMED, ENABLED, PASSWORD, USERNAME, AVATAR_ID) VALUES (1, 'admin@example.org', TRUE, TRUE, 'hash', 'admin', 51)")
            s.execute("INSERT INTO USER_ROLES (USER_ID, ROLES) VALUES (1, 'ROLE_SUPERADMIN')")
            s.execute("INSERT INTO USER_PREFERENCE ($userPreference, USER_ID) VALUES ('preferred-theme', 'dark', 1)")
        }
        c.prepareStatement(
            "INSERT INTO GAME (ID, CREATED_AT, UPDATED_AT, TITLE, PATH, SUMMARY, FILE_SIZE, MATCH_CONFIRMED, COVER_IMAGE_ID, LIBRARY_ID) " +
                    "VALUES (101, ?, ?, 'Game', '/games/game', ?, 123456789012, TRUE, 1, 1)"
        ).use { ps ->
            ps.setObject(1, LocalDateTime.of(2024, 5, 6, 7, 8, 9))
            ps.setObject(2, LocalDateTime.of(2024, 5, 6, 7, 8, 10))
            ps.setString(3, UNICODE_TEXT)
            ps.executeUpdate()
        }
        c.prepareStatement("INSERT INTO GAME_VIDEO_URLS (GAME_ID, VIDEO_URLS) VALUES (101, ?)").use { ps ->
            ps.setBytes(1, BLOB_CONTENT)
            ps.executeUpdate()
        }
    }

    /** Reads the content of all application tables in a vendor-neutral representation */
    private fun dump(dataSource: DataSource): Map<String, List<Map<String, String?>>> = dataSource.connection.use { c ->
        SchemaInspector(c).tables().associate { table ->
            val rows = c.createStatement().use { s ->
                s.executeQuery("SELECT * FROM ${SchemaInspector(c).quote(table)}").use { rs ->
                    buildList {
                        while (rs.next()) {
                            add((1..rs.metaData.columnCount).associate { i ->
                                rs.metaData.getColumnName(i).uppercase() to normalize(rs.getObject(i))
                            })
                        }
                    }
                }
            }
            table.uppercase() to rows.sortedBy { it.toSortedMap().toString() }
        }
    }

    private fun normalize(value: Any?): String? = when (value) {
        null -> null
        is ByteArray -> HexFormat.of().formatHex(value)
        is Blob -> HexFormat.of().formatHex(value.getBytes(1, value.length().toInt()))
        is Clob -> value.getSubString(1, value.length().toInt())
        is Timestamp -> value.toLocalDateTime().toString()
        is Number -> BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
        else -> value.toString()
    }

    private fun query(dataSource: DataSource, sql: String): Long = dataSource.connection.use { c ->
        c.createStatement().use { s -> s.executeQuery(sql).use { rs -> rs.next(); rs.getLong(1) } }
    }

    private fun assertSequencesWork(dataSource: DataSource, nextValueSql: (String) -> String) {
        // Hibernate's pooled optimizer uses the IDs (value - 49, value], which must not collide with restored IDs
        assertTrue(query(dataSource, nextValueSql("GAME_SEQ")) - 49 > 101)
        assertTrue(query(dataSource, nextValueSql("IMAGE_SEQ")) - 49 > 51)
        assertTrue(query(dataSource, nextValueSql("USERS_SEQ")) - 49 > 1)
    }

    private fun Connection.changeLogRows(): Int =
        createStatement().use { s ->
            s.executeQuery("SELECT COUNT(*) FROM DATABASECHANGELOG").use { it.next(); it.getInt(1) }
        }

    @Test
    fun `backup of H2 database can be restored on Postgres and back on H2`() {
        val source = h2("source")
        migrate(source)
        insertSampleData(source)
        val expected = dump(source)

        // H2 -> Postgres
        val target = postgres()
        restorer(target).restore(backup(source), APP_KEY_FINGERPRINT, null)
        migrate(target)

        assertEquals(expected, dump(target))
        assertEquals(restorer(target).currentChangeSets().size, target.connection.use { it.changeLogRows() })
        assertSequencesWork(target) { "SELECT nextval('${it.lowercase()}')" }

        // Postgres -> H2
        val roundTrip = h2("round-trip")
        restorer(roundTrip).restore(backup(target), APP_KEY_FINGERPRINT, null)
        migrate(roundTrip)

        assertEquals(expected, dump(roundTrip))
        assertSequencesWork(roundTrip) { "SELECT NEXT VALUE FOR $it" }

        val game = roundTrip.connection.use { c ->
            c.createStatement().use { s ->
                s.executeQuery("SELECT g.SUMMARY, v.VIDEO_URLS FROM GAME g JOIN GAME_VIDEO_URLS v ON v.GAME_ID = g.ID")
                    .use {
                        it.next(); it.getString(1) to it.getBytes(2)
                    }
            }
        }
        assertEquals(UNICODE_TEXT, game.first)
        assertContentEquals(BLOB_CONTENT, game.second)
    }

    @Test
    fun `backup of an older schema version is restored and migrated afterwards`() {
        val source = h2("source-old")
        migrate(source)
        insertSampleData(source)
        val expected = dump(source)

        // Pretend the source database has only been migrated up to the first changeset
        val firstChangeSet = restorer(source).currentChangeSets().first()
        source.connection.use { c ->
            c.createStatement().use { it.execute("DELETE FROM DATABASECHANGELOG WHERE ID <> '${firstChangeSet.id}'") }
        }
        val archive = backup(source)
        assertEquals(listOf(firstChangeSet), DatabaseRestorer.readManifest(archive).changeSets)

        val target = postgres()
        restorer(target).restore(archive, APP_KEY_FINGERPRINT, null)
        assertEquals(1, target.connection.use { it.changeLogRows() })

        // The remaining changesets are applied by the regular migration on startup
        migrate(target)
        assertEquals(restorer(target).currentChangeSets().size, target.connection.use { it.changeLogRows() })
        assertEquals(expected, dump(target))
    }

    @Test
    fun `restore replaces existing data`() {
        val source = h2("source-replace")
        migrate(source)
        insertSampleData(source)
        val archive = backup(source)

        val target = h2("target-replace")
        migrate(target)
        target.connection.use { c ->
            c.createStatement().use {
                it.execute(
                    "INSERT INTO APP_CONFIG (${
                        keyValueColumns(
                            c,
                            "APP_CONFIG"
                        )
                    }) VALUES ('only.in.target', 'x')"
                )
                it.execute("INSERT INTO IMAGE (ID, CONTENT_ID) VALUES (1, 'conflicting')")
            }
        }

        restorer(target).restore(archive, APP_KEY_FINGERPRINT, null)

        assertEquals(dump(source), dump(target))
    }

    @Test
    fun `media files are restored`() {
        val mediaSource = Files.createDirectories(tempDir.resolve("media-source"))
        mediaSource.resolve("content-1").writeText("image 1")
        mediaSource.resolve("content-2").writeText("image 2")

        val source = h2("source-media")
        migrate(source)
        val archive = backup(source, mediaSource)

        val mediaTarget = tempDir.resolve("media-target")
        val summary = restorer(h2("target-media")).restore(archive, APP_KEY_FINGERPRINT, mediaTarget)

        assertEquals(2, summary.mediaFiles)
        assertEquals("image 1", mediaTarget.resolve("content-1").readText())
        assertEquals("image 2", mediaTarget.resolve("content-2").readText())
    }

    @Test
    fun `backup created with a different APP_KEY is rejected`() {
        val source = h2("source-key")
        migrate(source)
        val manifest = DatabaseRestorer.readManifest(backup(source))

        val otherKey = AppKeyFingerprint.of(Base64.getEncoder().encodeToString(ByteArray(16)))
        val e = assertThrows<InvalidBackupException> { restorer(source).validate(manifest, otherKey) }
        assertTrue(e.message!!.contains("APP_KEY"))
    }

    @Test
    fun `backup of a newer Gameyfin version is rejected`() {
        val source = h2("source-version")
        migrate(source)
        val manifest = DatabaseRestorer.readManifest(backup(source))
        val newer = manifest.copy(changeSets = manifest.changeSets + BackupChangeSet("9.9.9-future", "someone"))

        assertThrows<InvalidBackupException> { restorer(source).validate(newer, APP_KEY_FINGERPRINT) }
        assertThrows<InvalidBackupException> {
            restorer(source).validate(
                manifest.copy(formatVersion = BackupFormat.FORMAT_VERSION + 1),
                APP_KEY_FINGERPRINT
            )
        }
    }

    @Test
    fun `invalid archives are rejected without touching the database`() {
        val notAZip = tempDir.resolve("not-a-zip.zip").apply { writeText("hello") }
        assertThrows<InvalidBackupException> { DatabaseRestorer.readManifest(notAZip) }

        val noManifest = tempDir.resolve("no-manifest.zip")
        ZipOutputStream(Files.newOutputStream(noManifest)).use {
            it.putNextEntry(ZipEntry("something.txt"))
            it.closeEntry()
        }
        assertThrows<InvalidBackupException> { DatabaseRestorer.readManifest(noManifest) }

        val target = h2("target-invalid")
        migrate(target)
        target.connection.use { c -> c.createStatement().use { it.execute("INSERT INTO IMAGE (ID) VALUES (1)") } }
        assertThrows<InvalidBackupException> { restorer(target).restore(noManifest, APP_KEY_FINGERPRINT, null) }
        assertEquals(1, query(target, "SELECT COUNT(*) FROM IMAGE"))
    }

    @Test
    fun `media entries escaping the media directory are ignored`() {
        val source = h2("source-traversal")
        migrate(source)
        val archive = backup(source, Files.createDirectories(tempDir.resolve("empty-media")))

        // Add malicious entries to the archive
        val manipulated = tempDir.resolve("manipulated.zip")
        java.util.zip.ZipFile(archive.toFile()).use { zip ->
            ZipOutputStream(Files.newOutputStream(manipulated)).use { out ->
                zip.entries().asSequence().forEach { entry ->
                    out.putNextEntry(ZipEntry(entry.name))
                    zip.getInputStream(entry).use { it.copyTo(out) }
                    out.closeEntry()
                }
                for (name in listOf("media/../evil", "media/sub/evil", "media/..")) {
                    out.putNextEntry(ZipEntry(name))
                    out.write("evil".toByteArray())
                    out.closeEntry()
                }
            }
        }

        val mediaTarget = tempDir.resolve("traversal").resolve("media")
        val summary = restorer(h2("target-traversal")).restore(manipulated, APP_KEY_FINGERPRINT, mediaTarget)

        assertEquals(0, summary.mediaFiles)
        assertFalse(tempDir.resolve("traversal").resolve("evil").exists())
        assertFalse(mediaTarget.resolve("sub").exists())
    }
}
