package org.gameyfin.app.db

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.getBean
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.context.annotation.Configuration
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File
import java.nio.file.Files
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Testcontainers
class MigrationIntegrationTest {

    @Configuration
    @EnableAutoConfiguration
    class Config

    private val contextRunner = WebApplicationContextRunner()
        .withUserConfiguration(Config::class.java)
        .withPropertyValues("spring.liquibase.change-log=classpath:/db/changelog/db.changelog-master.yaml")

    @Test
    fun `test migration against new Postgres database`() {
        val postgres = PostgreSQLContainer<Nothing>("postgres:alpine")
        postgres.start()

        contextRunner.withPropertyValues(
            "spring.datasource.url=${postgres.jdbcUrl}",
            "spring.datasource.username=${postgres.username}",
            "spring.datasource.password=${postgres.password}",
            "spring.datasource.driverClassName=${postgres.driverClassName}"
        ).run { context ->
            assertTrue(context.isRunning)
            require(context.startupFailure == null) { "Context failed to start: ${context.startupFailure}" }
            assertKeyValueColumnsMatchEntities(context.getBean<DataSource>())
        }

        postgres.stop()
    }

    @Test
    fun `test migration against new H2 database`() {
        val tempDir = Files.createTempDirectory("h2-new")
        val dbPath = tempDir.resolve("testdb").toAbsolutePath().toString()

        contextRunner.withPropertyValues(
            "spring.datasource.url=jdbc:h2:file:$dbPath;CACHE_SIZE=16384",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.datasource.driverClassName=org.h2.Driver"
        ).run { context ->
            assertTrue(context.isRunning)
            require(context.startupFailure == null) { "Context failed to start: ${context.startupFailure}" }
            assertKeyValueColumnsMatchEntities(context.getBean<DataSource>())
        }
    }

    @Test
    fun `test migration against existing H2 database`() {
        val tempDir = Files.createTempDirectory("h2-existing")

        var sourceDbFile = File("src/test/resources/testdb/gameyfin_db.mv.db")
        if (!sourceDbFile.exists()) {
            sourceDbFile = File("app/src/test/resources/testdb/gameyfin_db.mv.db")
        }
        require(sourceDbFile.exists()) { "Existing H2 database file not found at ${sourceDbFile.absolutePath}" }

        val targetDbFile = tempDir.resolve("gameyfin_db.mv.db").toFile()
        sourceDbFile.copyTo(targetDbFile, overwrite = true)

        val dbPath = tempDir.resolve("gameyfin_db").toAbsolutePath().toString()

        contextRunner.withPropertyValues(
            "spring.datasource.url=jdbc:h2:file:$dbPath;CACHE_SIZE=16384",
            "spring.datasource.username=gfadmin",
            "spring.datasource.password=gameyfin",
            "spring.datasource.driverClassName=org.h2.Driver"
        ).run { context ->
            assertTrue(context.isRunning)
            require(context.startupFailure == null) { "Context failed to start: ${context.startupFailure}" }
        }
    }

    /**
     * The JPA entities reference these reserved-word columns as quoted lowercase identifiers,
     * so they must exist with exactly that (case-sensitive) name on every database.
     */
    private fun assertKeyValueColumnsMatchEntities(dataSource: DataSource) {
        val tables = setOf("APP_CONFIG", "PLUGIN_CONFIG", "USER_PREFERENCE")
        val columns = mutableMapOf<String, MutableSet<String>>()
        dataSource.connection.use { connection ->
            connection.metaData.getColumns(null, null, "%", "%").use { rs ->
                while (rs.next()) {
                    val table = rs.getString("TABLE_NAME").uppercase()
                    if (table in tables) columns.getOrPut(table) { mutableSetOf() }.add(rs.getString("COLUMN_NAME"))
                }
            }
        }
        for (table in tables) {
            val names = columns[table].orEmpty()
            assertEquals(
                setOf("key", "value"),
                names.filter { it.equals("key", true) || it.equals("value", true) }.toSet(),
                "Unexpected key/value column names in $table: $names"
            )
        }
    }
}
