package org.gameyfin.app.core.db

import org.flywaydb.core.api.callback.Callback
import org.flywaydb.core.api.callback.Context
import org.flywaydb.core.api.callback.Event
import org.slf4j.LoggerFactory

class ClearLegacyChecksumsCallback : Callback {

    private val log = LoggerFactory.getLogger(javaClass)

    private val legacyVersions = setOf(
        "2.0.0", "2.1.0.1", "2.1.0.2", "2.1.1", "2.1.2.1", "2.1.2.2",
        "2.2.0.1", "2.2.0.2", "2.2.0.3", "2.2.0.4", "2.3.0.1", "2.3.0.2",
        "2.3.0.3", "2.3.0.4", "2.3.0.5", "2.3.0.6", "2.3.0.7",
        "2.4.0.1", "2.4.0.2"
    )

    override fun supports(event: Event?, context: Context?): Boolean =
        event == Event.BEFORE_VALIDATE

    override fun canHandleInTransaction(event: Event?, context: Context?): Boolean = true

    override fun handle(event: Event?, context: Context?) {
        val connection = context?.connection ?: return

        val actualTableName = findTableName(connection, "flyway_schema_history")
            ?: findTableName(connection, "FLYWAY_SCHEMA_HISTORY")

        if (actualTableName == null) {
            log.debug("flyway_schema_history does not exist yet (fresh install) — skipping legacy checksum clear")
            return
        }

        val sql = """
            UPDATE "$actualTableName"
            SET "checksum" = NULL
            WHERE "success" = TRUE
            AND "checksum" IS NOT NULL
            AND "version" IN (${legacyVersions.joinToString(",") { "?" }})
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            legacyVersions.forEachIndexed { i, version -> stmt.setString(i + 1, version) }
            val updated = stmt.executeUpdate()
            if (updated > 0) {
                log.info("Cleared checksums for $updated legacy unified migration(s)")
            }
        }
    }

    private fun findTableName(connection: java.sql.Connection, name: String): String? =
        connection.metaData
            .getTables(null, null, name, arrayOf("TABLE"))
            .use { rs -> if (rs.next()) rs.getString("TABLE_NAME") else null }

    override fun getCallbackName(): String = "ClearLegacyChecksumsCallback"
}