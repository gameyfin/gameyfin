package org.gameyfin.app.core.database

import java.time.Instant

/**
 * Database connection entered by an admin.
 */
data class DatabaseConnectionDto(
    val type: DatabaseType,
    val host: String? = null,
    val port: Int? = null,
    val database: String? = null,
    val username: String? = null,
    /** `null` keeps the password of the currently configured PostgreSQL database */
    val password: String? = null
)

data class DatabaseInfoDto(
    /** Type of the database currently in use, `null` if it is not one of the supported types */
    val type: DatabaseType?,
    val productName: String?,
    val productVersion: String?,
    /** Whether the database is configured via environment variables (and therefore can not be changed in the UI) */
    val managedExternally: Boolean,
    /** JDBC URL (without parameters) if the database is configured via environment variables */
    val url: String?,
    val host: String?,
    val port: Int?,
    val database: String?,
    val username: String?,
    val passwordSet: Boolean,
    /** File in which the database settings configured in the UI are stored */
    val settingsFile: String,
    val lastSwitch: DatabaseSwitchStatusDto?
)

data class DatabaseConnectionTestResultDto(
    val success: Boolean,
    val message: String,
    val productName: String? = null,
    val productVersion: String? = null,
    /** Number of tables in the target database; all of them are deleted when switching to this database */
    val existingTables: Int = 0
)

data class DatabaseSwitchStatusDto(
    val success: Boolean,
    val message: String,
    val previousType: DatabaseType,
    val newType: DatabaseType,
    val finishedAt: Instant
)
