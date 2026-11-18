package org.gameyfin.app.users

import liquibase.integration.spring.SpringLiquibase
import org.gameyfin.app.games.entities.GameFieldMetadata
import org.gameyfin.app.games.entities.GameFieldUserSource
import org.gameyfin.app.games.entities.GameMetadata
import org.gameyfin.app.games.extensions.toAdminDto
import org.gameyfin.app.libraries.entities.IgnoredPath
import org.gameyfin.app.libraries.entities.IgnoredPathUserSource
import org.gameyfin.app.libraries.extensions.toDto
import org.junit.jupiter.api.Test
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserDeletionMigrationTest {
    @Test
    fun `migrated database preserves contributions when their author is deleted`() {
        val dataSource = DriverManagerDataSource("jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1", "sa", "")
        SpringLiquibase().apply {
            this.dataSource = dataSource
            changeLog = "classpath:/db/changelog/db.changelog-master.yaml"
            resourceLoader = DefaultResourceLoader()
            afterPropertiesSet()
        }
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("INSERT INTO USERS (ID, USERNAME, EMAIL, EMAIL_CONFIRMED, ENABLED) VALUES (1, 'author', 'author@example.com', true, true)")
                statement.executeUpdate("INSERT INTO GAME_FIELD_SOURCE (ID, DTYPE, USER_ID) VALUES (1, 'GameFieldUserSource', 1)")
                statement.executeUpdate("INSERT INTO GAME_FIELD_METADATA (ID, SOURCE_ID) VALUES (1, 1)")
                statement.executeUpdate("INSERT INTO IGNORED_PATH_SOURCE (ID, DTYPE, USER_ID) VALUES (1, 'IgnoredPathUserSource', 1)")
                statement.executeUpdate("INSERT INTO IGNORED_PATH (ID, PATH, SOURCE_ID) VALUES (1, '/ignored', 1)")
                assertEquals(1, statement.executeUpdate("DELETE FROM USERS WHERE ID = 1"))
                for (table in listOf("GAME_FIELD_SOURCE", "IGNORED_PATH_SOURCE")) {
                    statement.executeQuery("SELECT USER_ID FROM $table WHERE ID = 1").use {
                        assertTrue(it.next())
                        assertNull(it.getObject(1))
                    }
                }
                for (table in listOf("GAME_FIELD_METADATA", "IGNORED_PATH")) {
                    statement.executeQuery("SELECT COUNT(*) FROM $table").use {
                        assertTrue(it.next())
                        assertEquals(1, it.getInt(1))
                    }
                }
            }
        }
    }

    @Test
    fun `contributions with deleted authors can still be rendered`() {
        val metadata = GameMetadata(
            path = "/game", fields = mutableMapOf(
                "title" to GameFieldMetadata(source = GameFieldUserSource(null))
            )
        )
        assertEquals("Deleted user", metadata.toAdminDto().fields!!.getValue("title").source)
        assertEquals("", IgnoredPath(id = 1, path = "/ignored", source = IgnoredPathUserSource(null)).toDto().source)
    }
}
