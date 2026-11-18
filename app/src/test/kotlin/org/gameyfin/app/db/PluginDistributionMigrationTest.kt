package org.gameyfin.app.db

import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assumptions
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PluginDistributionMigrationTest {
    @Test
    fun `backup restoration retains pending onboarding and both completed and failed recovery`() {
        val source = org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:source-${UUID.randomUUID()};DB_CLOSE_DELAY=-1", "sa", "")
        val target = org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:target-${UUID.randomUUID()};DB_CLOSE_DELAY=-1", "sa", "")
        source.connection.use { connection ->
            Liquibase("db/changelog/db.changelog-master.yaml", ClassLoaderResourceAccessor(), JdbcConnection(connection)).update(Contexts(), LabelExpression())
            connection.createStatement().use { sql ->
                sql.executeUpdate("INSERT INTO PLUGIN_ONBOARDING_STATE (ID, PENDING) VALUES (1, TRUE)")
                sql.executeUpdate("INSERT INTO PLUGIN_RECOVERY_CANDIDATE (PLUGIN_ID, COMPLETED, LAST_ERROR) VALUES ('org.gameyfin.plugins.metadata.igdb', TRUE, NULL), ('org.gameyfin.plugins.download.direct', FALSE, 'offline')")
            }
            connection.commit()
        }
        val archive = java.nio.file.Files.createTempFile("plugin-state-backup-", ".zip")
        try {
            val fingerprint = org.gameyfin.app.core.backup.AppKeyFingerprint.of(java.util.Base64.getEncoder().encodeToString(ByteArray(16)))
            java.nio.file.Files.newOutputStream(archive).use {
                org.gameyfin.app.core.backup.DatabaseExporter().writeArchive(source, it, java.time.Instant.now(), fingerprint, "test", null)
            }
            org.gameyfin.app.core.backup.DatabaseRestorer(target, "db/changelog/db.changelog-master.yaml", ClassLoaderResourceAccessor()).restore(archive, fingerprint, null)
            target.connection.use { connection -> connection.createStatement().use { sql ->
                sql.executeQuery("SELECT PENDING FROM PLUGIN_ONBOARDING_STATE").use { it.next(); kotlin.test.assertTrue(it.getBoolean(1)) }
                sql.executeQuery("SELECT COUNT(*) FROM PLUGIN_RECOVERY_CANDIDATE WHERE COMPLETED=TRUE").use { it.next(); assertEquals(1, it.getInt(1)) }
                sql.executeQuery("SELECT LAST_ERROR FROM PLUGIN_RECOVERY_CANDIDATE WHERE COMPLETED=FALSE").use { it.next(); assertEquals("offline", it.getString(1)) }
            } }
        } finally { java.nio.file.Files.deleteIfExists(archive) }
    }

    @Test
    fun `snapshot is restricted and one time and completion survives reconnect`() {
        val url = "jdbc:h2:mem:plugin-migration-${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
        verifySnapshot(url, "sa", "")
    }

    @Test
    fun `PostgreSQL snapshot and completed state persist`() {
        val url = System.getenv("GAMEYFIN_TEST_POSTGRES_URL")
        Assumptions.assumeTrue(url != null, "Local PostgreSQL URL required")
        verifySnapshot(url!!, System.getenv("GAMEYFIN_TEST_POSTGRES_USER") ?: "postgres", System.getenv("GAMEYFIN_TEST_POSTGRES_PASSWORD") ?: "")
    }

    private fun verifySnapshot(url: String, username: String, password: String) {
        DriverManager.getConnection(url, username, password).use { connection ->
            val migration = Liquibase("db/changelog/db.changelog-master.yaml", ClassLoaderResourceAccessor(), JdbcConnection(connection))
            val changesBeforeSnapshot = migration.databaseChangeLog.changeSets.indexOfFirst {
                it.id == "2.7.0-marketplace-only-plugins" && it.author == "gameyfin"
            }
            check(changesBeforeSnapshot > 0) { "Marketplace plugin snapshot changeset has no preceding schema" }
            migration.update(changesBeforeSnapshot, Contexts(), LabelExpression())
            connection.createStatement().use { sql ->
                sql.executeUpdate("""INSERT INTO PLUGIN_MANAGEMENT_ENTRY (PLUGIN_ID, ENABLED, PRIORITY, TRUST_LEVEL, SOURCE_MARKETPLACE_ID)
                    VALUES ('org.gameyfin.plugins.metadata.igdb', FALSE, 42, 1, NULL),
                    ('org.gameyfin.plugins.metadata.steam', TRUE, 3, 1, 'third-party'),
                    ('org.gameyfin.plugins.metadata.steamgriddb', TRUE, 4, 2, NULL),
                    ('org.gameyfin.plugins.download.direct', TRUE, 5, 1, NULL),
                    ('org.gameyfin.plugins.store.gog', TRUE, 6, 1, NULL)""")
            }
            migration.update(Contexts(), LabelExpression())
            connection.createStatement().use { sql ->
                sql.executeQuery("SELECT PLUGIN_ID FROM PLUGIN_RECOVERY_CANDIDATE ORDER BY PLUGIN_ID").use { rows ->
                    val ids = buildList { while (rows.next()) add(rows.getString(1)) }
                    assertEquals(listOf("org.gameyfin.plugins.download.direct", "org.gameyfin.plugins.metadata.igdb"), ids)
                }
                sql.executeQuery("SELECT PENDING FROM PLUGIN_ONBOARDING_STATE").use { assertFalse(it.next()) }
                sql.executeUpdate("UPDATE PLUGIN_RECOVERY_CANDIDATE SET COMPLETED=TRUE WHERE PLUGIN_ID='org.gameyfin.plugins.download.direct'")
                sql.executeUpdate("INSERT INTO PLUGIN_MANAGEMENT_ENTRY (PLUGIN_ID, ENABLED, PRIORITY, TRUST_LEVEL) VALUES ('org.gameyfin.plugins.download.torrent', TRUE, 7, 1)")
            }
            migration.update(Contexts(), LabelExpression())
        }
        DriverManager.getConnection(url, username, password).use { connection ->
            connection.createStatement().use { sql ->
                sql.executeQuery("SELECT COUNT(*) FROM PLUGIN_RECOVERY_CANDIDATE").use { it.next(); assertEquals(2, it.getInt(1)) }
                sql.executeQuery("SELECT COUNT(*) FROM PLUGIN_RECOVERY_CANDIDATE WHERE COMPLETED=TRUE").use { it.next(); assertEquals(1, it.getInt(1)) }
                sql.executeQuery("SELECT ENABLED, PRIORITY FROM PLUGIN_MANAGEMENT_ENTRY WHERE PLUGIN_ID='org.gameyfin.plugins.metadata.igdb'").use {
                    it.next(); assertFalse(it.getBoolean(1)); assertEquals(42, it.getInt(2))
                }
            }
        }
    }
}
