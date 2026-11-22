package org.gameyfin.app.db

import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.LiquibaseException
import liquibase.resource.ClassLoaderResourceAccessor
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SocialMigrationTest {
    @Test
    fun `forward and rollback preserve curated collections and guard new data`() {
        val source = DriverManagerDataSource("jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1", "sa", "")
        val jdbc = JdbcTemplate(source)
        val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(source.connection))
        val contexts = Contexts()
        val labels = LabelExpression()
        Liquibase("db/changelog/db.changelog-master.yaml", ClassLoaderResourceAccessor(), database).use { liquibase ->
            val changes = liquibase.listUnrunChangeSets(contexts, labels)
            val changesBeforeSocial = changes.indexOfFirst { it.id == "game-social-features" }
            check(changesBeforeSocial > 0) { "Social changeset has no preceding schema" }
            liquibase.update(changesBeforeSocial, contexts, labels)
            jdbc.update("INSERT INTO COLLECTION (ID, NAME, CREATED_AT, UPDATED_AT, DISPLAY_ORDER) VALUES (1, 'Legacy', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 5)")
            jdbc.update("INSERT INTO GAME (ID, TITLE, CREATED_AT, UPDATED_AT) VALUES (2, 'Game', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
            jdbc.update("INSERT INTO COLLECTION_GAMES (COLLECTIONS_ID, GAMES_ID) VALUES (1, 2)")
            jdbc.update("INSERT INTO USERS (ID, USERNAME, EMAIL, EMAIL_CONFIRMED, ENABLED) VALUES (3, 'member', 'member@example.com', TRUE, TRUE)")

            liquibase.update(1, contexts, labels)
            assertEquals("CURATED", jdbc.queryForObject("SELECT KIND FROM COLLECTION WHERE ID = 1", String::class.java))
            assertEquals("PUBLIC", jdbc.queryForObject("SELECT VISIBILITY FROM COLLECTION WHERE ID = 1", String::class.java))
            jdbc.update(
                """INSERT INTO COLLECTION (ID, NAME, CREATED_AT, UPDATED_AT, KIND, VISIBILITY, OWNER_ID, MEMBER_NAME, FAVOURITES_OWNER_ID)
                   VALUES (4, 'storage-key', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'FAVOURITES', 'PRIVATE', 3, 'Your favourites', 3)"""
            )
            assertFailsWith<DataIntegrityViolationException> {
                jdbc.update(
                    """INSERT INTO COLLECTION (ID, NAME, CREATED_AT, UPDATED_AT, KIND, VISIBILITY, OWNER_ID, MEMBER_NAME, FAVOURITES_OWNER_ID)
                       VALUES (5, 'different-key', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'FAVOURITES', 'PRIVATE', 3, 'Your favourites', 3)"""
                )
            }
            jdbc.update("INSERT INTO MEMBER_GAME_STATE (GAME_ID, MEMBER_ID, RATING, PLAY_STATUS) VALUES (2, 3, 4, 'PLAYED')")
            jdbc.update("INSERT INTO GAME_REVIEW (GAME_ID, MEMBER_ID, SUBMITTED_TEXT) VALUES (2, 3, 'Pending')")
            assertFailsWith<LiquibaseException> { liquibase.rollback(1, contexts, labels) }
            assertEquals(1, count(jdbc, "GAME_REVIEW"))

            jdbc.update("DELETE FROM USERS WHERE ID = 3")
            assertEquals(0, count(jdbc, "GAME_REVIEW"))
            assertEquals(0, count(jdbc, "MEMBER_GAME_STATE"))
            assertEquals(1, count(jdbc, "COLLECTION"))

            jdbc.update("INSERT INTO USERS (ID, USERNAME, EMAIL, EMAIL_CONFIRMED, ENABLED) VALUES (4, 'other', 'other@example.com', TRUE, TRUE)")
            jdbc.update("INSERT INTO GAME (ID, TITLE, CREATED_AT, UPDATED_AT) VALUES (5, 'Other game', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
            jdbc.update("INSERT INTO MEMBER_GAME_STATE (GAME_ID, MEMBER_ID, RATING) VALUES (5, 4, 1)")
            jdbc.update("INSERT INTO GAME_REVIEW (GAME_ID, MEMBER_ID, SUBMITTED_TEXT) VALUES (5, 4, 'Pending')")
            jdbc.update("DELETE FROM GAME WHERE ID = 5")
            assertEquals(0, count(jdbc, "GAME_REVIEW"))
            assertEquals(0, count(jdbc, "MEMBER_GAME_STATE"))

            liquibase.rollback(1, contexts, labels)
            assertEquals("Legacy", jdbc.queryForObject("SELECT NAME FROM COLLECTION WHERE ID = 1", String::class.java))
            assertEquals(5, jdbc.queryForObject("SELECT DISPLAY_ORDER FROM COLLECTION WHERE ID = 1", Int::class.java))
            assertEquals(1, count(jdbc, "COLLECTION_GAMES"))
            liquibase.update(1, contexts, labels)
            assertEquals("PUBLIC", jdbc.queryForObject("SELECT VISIBILITY FROM COLLECTION WHERE ID = 1", String::class.java))
        }
    }

    private fun count(jdbc: JdbcTemplate, table: String) = jdbc.queryForObject("SELECT COUNT(*) FROM $table", Int::class.java)
}
