package org.gameyfin.app.system.backup

import liquibase.integration.spring.SpringLiquibase
import liquibase.integration.spring.SpringResourceAccessor
import org.gameyfin.app.core.backup.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.jdbc.datasource.DriverManagerDataSource
import tools.jackson.databind.node.ObjectNode
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.sql.DataSource
import kotlin.io.path.div
import kotlin.io.path.writeText

/** Error handling and edge cases of the restore, using H2 only */
class DatabaseRestorerTest {

    companion object {
        private const val CHANGELOG = "classpath:/db/changelog/db.changelog-master.yaml"
        private val FINGERPRINT =
            AppKeyFingerprint.of(Base64.getEncoder().encodeToString(ByteArray(16) { it.toByte() }))
    }

    @TempDir
    lateinit var tempDir: Path

    private lateinit var source: DataSource
    private lateinit var archive: Path

    @BeforeEach
    fun setUp() {
        source = h2("source")
        migrate(source)
        source.connection.use { c ->
            c.createStatement().use {
                it.execute("INSERT INTO IMAGE (ID, CONTENT_ID, TYPE) VALUES (1, 'content-1', 0)")
            }
        }
        archive = tempDir / "backup.zip"
        Files.newOutputStream(archive).use {
            DatabaseExporter().writeArchive(source, it, Instant.now(), FINGERPRINT, "test", null)
        }
    }

    private fun h2(name: String): DataSource =
        DriverManagerDataSource("jdbc:h2:file:${(tempDir / name).toAbsolutePath()}", "sa", "")

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

    private fun count(dataSource: DataSource, table: String): Long = dataSource.connection.use { c ->
        c.createStatement().use { s -> s.executeQuery("SELECT COUNT(*) FROM $table").use { it.next(); it.getLong(1) } }
    }

    /** Copies the archive, optionally changing the manifest and replacing/adding entries */
    private fun rewrite(
        name: String,
        manifest: (ObjectNode) -> Unit = {},
        entries: Map<String, String> = emptyMap(),
        rawManifest: String? = null
    ): Path {
        val target = tempDir / name
        ZipFile(archive.toFile()).use { zip ->
            ZipOutputStream(Files.newOutputStream(target)).use { out ->
                for (entry in zip.entries().asSequence()) {
                    if (entry.name in entries) continue
                    out.putNextEntry(ZipEntry(entry.name))
                    val bytes = zip.getInputStream(entry).readAllBytes()
                    if (entry.name == BackupFormat.MANIFEST_ENTRY) {
                        val node = BackupFormat.jsonMapper.readTree(bytes) as ObjectNode
                        manifest(node)
                        out.write((rawManifest ?: BackupFormat.jsonMapper.writeValueAsString(node)).toByteArray())
                    } else {
                        out.write(bytes)
                    }
                    out.closeEntry()
                }
                entries.forEach { (entryName, content) ->
                    out.putNextEntry(ZipEntry(entryName))
                    out.write(content.toByteArray())
                    out.closeEntry()
                }
            }
        }
        return target
    }

    private fun restoreInto(backup: Path, target: DataSource = h2("target-${UUID.randomUUID()}")) =
        restorer(target).restore(backup, FINGERPRINT, null)

    @Test
    fun `restore roundtrip imports rows in batches and resets sequences`() {
        source.connection.use { c ->
            c.prepareStatement("INSERT INTO JOB_RUN_RESULT (JOB_NAME, STARTED_AT, STATUS) VALUES ('scan', TIMESTAMP '2024-01-01 00:00:00', 'SUCCESS')")
                .use { ps -> repeat(1200) { ps.addBatch() }; ps.executeBatch() }
        }
        Files.newOutputStream(archive).use {
            DatabaseExporter().writeArchive(source, it, Instant.now(), FINGERPRINT, "test", null)
        }
        val target = h2("target-roundtrip")

        val summary = restoreInto(archive, target)

        assertTrue(summary.rows >= 1201)
        assertEquals(1200, count(target, "JOB_RUN_RESULT"))
        assertEquals(1, count(target, "IMAGE"))
    }

    @Test
    fun `manifest problems are reported`() {
        val malformed = rewrite("malformed.zip", rawManifest = "{not json")
        assertTrue(assertThrows(InvalidBackupException::class.java) { DatabaseRestorer.readManifest(malformed) }
            .message!!.contains("could not be read"))

        val missingField = rewrite("missing.zip", manifest = { it.remove("databaseProduct") })
        assertThrows(InvalidBackupException::class.java) { DatabaseRestorer.readManifest(missingField) }

        val notArray = rewrite("notarray.zip", manifest = { it.put("tables", "x") })
        assertThrows(InvalidBackupException::class.java) { DatabaseRestorer.readManifest(notArray) }

        val badValue = rewrite("badvalue.zip", manifest = { it.put("createdAt", "yesterday") })
        assertTrue(assertThrows(InvalidBackupException::class.java) { DatabaseRestorer.readManifest(badValue) }
            .message!!.contains("malformed"))

        val notZip = tempDir / "notzip.zip"
        notZip.writeText("x")
        assertThrows(InvalidBackupException::class.java) { DatabaseRestorer.readManifest(notZip) }
    }

    @Test
    fun `validate rejects unsupported backups`() {
        val restorer = restorer(h2("validate"))
        val manifest = DatabaseRestorer.readManifest(archive)

        restorer.validate(manifest, FINGERPRINT)
        assertTrue(restorer.currentChangeSets().isNotEmpty())

        assertTrue(assertThrows(InvalidBackupException::class.java) {
            restorer.validate(manifest.copy(formatVersion = BackupFormat.FORMAT_VERSION + 1), FINGERPRINT)
        }.message!!.contains("format"))
        assertTrue(assertThrows(InvalidBackupException::class.java) {
            restorer.validate(manifest, "sha256:other")
        }.message!!.contains("APP_KEY"))
        assertTrue(assertThrows(InvalidBackupException::class.java) {
            restorer.validate(manifest.copy(changeSets = emptyList()), FINGERPRINT)
        }.message!!.contains("migration history"))
        assertTrue(assertThrows(InvalidBackupException::class.java) {
            restorer.validate(
                manifest.copy(changeSets = manifest.changeSets + BackupChangeSet("future", "me")),
                FINGERPRINT
            )
        }.message!!.contains("incompatible"))
    }

    @Test
    fun `table entries must be valid`() {
        val badPrefix = rewrite("prefix.zip", manifest = {
            (it.get("tables")[0] as ObjectNode).put("entry", "elsewhere/x.json")
        })
        assertTrue(assertThrows(InvalidBackupException::class.java) { restoreInto(badPrefix) }.message!!.contains("Invalid entry"))

        val missingEntry = rewrite("missing-entry.zip", manifest = {
            (it.get("tables")[0] as ObjectNode).put("entry", "database/DOES_NOT_EXIST.json")
        })
        assertTrue(assertThrows(InvalidBackupException::class.java) { restoreInto(missingEntry) }.message!!.contains("missing"))
    }

    @Test
    fun `malformed table data is rejected and rolled back`() {
        val notObject = rewrite("not-object.zip", entries = mapOf("database/IMAGE.json" to "[]"))
        assertTrue(assertThrows(InvalidBackupException::class.java) { restoreInto(notObject) }.message!!.contains("malformed"))

        val rowsFirst =
            rewrite("rows-first.zip", entries = mapOf("database/IMAGE.json" to """{"rows": [[1]], "columns": []}"""))
        assertThrows(InvalidBackupException::class.java) { restoreInto(rowsFirst) }

        val badValue = rewrite(
            "bad-value.zip",
            entries = mapOf(
                "database/IMAGE.json" to """{"columns": [{"name": "ID"}, {"name": "TYPE"}], "rows": [["not a number", 0]]}"""
            )
        )
        assertThrows(Exception::class.java) { restoreInto(badValue) }
    }

    @Test
    fun `unknown tables, columns and properties are skipped`() {
        val lenient = rewrite(
            "lenient.zip",
            manifest = {
                (it.get("tables") as tools.jackson.databind.node.ArrayNode).addObject()
                    .put("name", "REMOVED_TABLE").put("entry", "database/REMOVED_TABLE.json").put("rowCount", 0)
            },
            entries = mapOf(
                "database/IMAGE.json" to """{"extra": {"a": [1]}, "columns": [{"name": "ID"}, {"name": "GONE"}, {"name": "TYPE"}], "rows": [[7, "x", 1], [8, "y", 2]]}"""
            )
        )
        val target = h2("target-lenient")

        restoreInto(lenient, target)

        assertEquals(2, count(target, "IMAGE"))
    }

    @Test
    fun `table with only unknown columns is skipped`() {
        val onlyUnknown = rewrite(
            "only-unknown.zip",
            entries = mapOf("database/IMAGE.json" to """{"columns": [{"name": "GONE"}], "rows": [["x"]]}""")
        )
        val target = h2("target-unknown")

        restoreInto(onlyUnknown, target)

        assertEquals(0, count(target, "IMAGE"))
    }

    @Test
    fun `media files are restored when included`() {
        val withMedia = rewrite(
            "media.zip",
            manifest = { it.put("includesMedia", true) },
            entries = mapOf("media/file-1" to "data")
        )
        val mediaRoot = tempDir / "media"

        val summary = restorer(h2("target-media")).restore(withMedia, FINGERPRINT, mediaRoot)

        assertEquals(1, summary.mediaFiles)
        assertEquals("data", Files.readString(mediaRoot / "file-1"))
    }
}
