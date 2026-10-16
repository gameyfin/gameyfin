package org.gameyfin.app.core.database

/**
 * Database management systems which can be configured in the UI.
 */
enum class DatabaseType(val displayName: String, val driverClassName: String, val urlPrefix: String) {
    /** The embedded database, configured by `spring.datasource.*` in application.yml */
    H2("H2 (embedded)", "org.h2.Driver", "jdbc:h2:"),
    POSTGRESQL("PostgreSQL", "org.postgresql.Driver", "jdbc:postgresql:")
}

/** Determines the [DatabaseType] from a JDBC URL (top-level, as a companion object would leak into the generated TS enum) */
fun databaseTypeOf(url: String?): DatabaseType? =
    DatabaseType.entries.firstOrNull { url?.startsWith(it.urlPrefix, ignoreCase = true) == true }

/**
 * Database connection settings configured in the UI (see [DatabaseSettingsStore]).
 */
data class DatabaseSettings(
    val type: DatabaseType,
    val host: String? = null,
    val port: Int? = null,
    val database: String? = null,
    val username: String? = null,
    val password: String? = null
) {
    companion object {
        val EMBEDDED = DatabaseSettings(DatabaseType.H2)
        const val DEFAULT_POSTGRESQL_PORT = 5432

        // Restrictive patterns, as host and database name are part of the JDBC URL
        private val HOST = Regex("^(\\[[0-9A-Fa-f:.]+]|[A-Za-z0-9][A-Za-z0-9._-]{0,252})$")
        private val DATABASE = Regex("^[A-Za-z0-9_][A-Za-z0-9_.-]{0,62}$")

        /**
         * Creates validated and normalized settings.
         *
         * @throws IllegalArgumentException if the settings are invalid
         */
        fun of(
            type: DatabaseType,
            host: String?,
            port: Int?,
            database: String?,
            username: String?,
            password: String?
        ): DatabaseSettings = when (type) {
            DatabaseType.H2 -> EMBEDDED
            DatabaseType.POSTGRESQL -> {
                val normalizedHost = host?.trim().orEmpty()
                val normalizedDatabase = database?.trim().orEmpty()
                val normalizedUsername = username?.trim().orEmpty()
                val normalizedPort = port ?: DEFAULT_POSTGRESQL_PORT
                require(HOST.matches(normalizedHost)) { "Invalid host name" }
                require(normalizedPort in 1..65535) { "Invalid port" }
                require(DATABASE.matches(normalizedDatabase)) {
                    "Invalid database name (allowed are letters, digits, '_', '-' and '.')"
                }
                require(normalizedUsername.isNotEmpty()) { "Username is required" }
                DatabaseSettings(type, normalizedHost, normalizedPort, normalizedDatabase, normalizedUsername, password)
            }
        }
    }

    /** JDBC URL, or `null` for the embedded database (which uses the default configuration) */
    fun jdbcUrl(): String? = when (type) {
        DatabaseType.H2 -> null
        DatabaseType.POSTGRESQL -> "jdbc:postgresql://$host:$port/$database"
    }

    /** Whether both settings point to the same database (but possibly with different credentials) */
    fun isSameDatabase(other: DatabaseSettings): Boolean =
        type == other.type && host.equals(other.host, ignoreCase = true) && port == other.port &&
                database == other.database

    /** Short description without credentials, e.g. for log messages */
    fun describe(): String = when (type) {
        DatabaseType.H2 -> type.displayName
        else -> "${type.displayName} ($host:$port/$database)"
    }

    override fun toString(): String =
        "DatabaseSettings(type=$type, host=$host, port=$port, database=$database, username=$username, password=***)"
}
