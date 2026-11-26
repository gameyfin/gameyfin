package org.gameyfin.app.db

import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.Types
import java.util.UUID
import kotlin.test.assertEquals

class LegacyTextMigrationTest {
    @Test
    fun `upgrading released H2 text columns preserves long values`() {
        val source = DriverManagerDataSource("jdbc:h2:mem:${UUID.randomUUID()}", "sa", "")
        val jdbc = JdbcTemplate(source)
        val contexts = Contexts()
        val labels = LabelExpression()
        val columns = mapOf(
            "APP_NOTIFICATION" to listOf("BODY", "ACTIONS"),
            "PLUGIN_UPDATE_NOTICE" to listOf("VERSION"),
            "GAME_REVIEW" to listOf("SUBMITTED_TEXT", "PUBLISHED_TEXT")
        )
        Liquibase("db/changelog/db.changelog-master.yaml", ClassLoaderResourceAccessor(), JdbcConnection(source.connection)).use { migration ->
            val beforeRepair = migration.listUnrunChangeSets(contexts, labels).indexOfFirst {
                it.id == "2.8.0-normalize-notification-and-review-text"
            }
            check(beforeRepair > 0)
            migration.update(beforeRepair, contexts, labels)
            for ((table, names) in columns) for (name in names) {
                jdbc.execute("ALTER TABLE $table ALTER COLUMN $name SET DATA TYPE TEXT")
            }
            val longText = "Legacy text ä🎮".repeat(800)
            jdbc.update("""INSERT INTO APP_NOTIFICATION (ID, SOURCE, TITLE, BODY, ACTIONS, SEVERITY, CREATED_AT)
                VALUES ('notice', 'test', 'Title', ?, ?, 'INFO', CURRENT_TIMESTAMP)""", longText, longText)
            jdbc.update("INSERT INTO PLUGIN_UPDATE_NOTICE (PLUGIN_ID, VERSION, NOTIFICATION_ID) VALUES ('plugin', ?, 'notice')", longText)
            jdbc.update("INSERT INTO USERS (ID, USERNAME, EMAIL, EMAIL_CONFIRMED, ENABLED) VALUES (1, 'user', 'user@example.com', TRUE, TRUE)")
            jdbc.update("INSERT INTO GAME (ID, TITLE, CREATED_AT, UPDATED_AT) VALUES (1, 'Game', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
            jdbc.update("INSERT INTO GAME_REVIEW (GAME_ID, MEMBER_ID, SUBMITTED_TEXT, PUBLISHED_TEXT) VALUES (1, 1, ?, ?)", longText, longText)

            migration.update(contexts, labels)
            for ((table, names) in columns) for (name in names) {
                val value = jdbc.queryForObject("SELECT $name FROM $table") { row, _ ->
                    assertEquals(Types.CLOB, row.metaData.getColumnType(1), "$table.$name")
                    row.getString(1)
                }
                assertEquals(longText, value)
            }
        }
    }
}
