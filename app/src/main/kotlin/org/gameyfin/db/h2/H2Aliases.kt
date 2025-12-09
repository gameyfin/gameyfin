package org.gameyfin.db.h2

import java.sql.Connection
import java.sql.SQLException

/**
 * H2 helper methods exposed as SQL ALIASes.
 * <p>
 * Kotlin implementation replacing the former Java version so a JDK (javac) is not
 * required at runtime for defining aliases in migration scripts.
 */
object H2Aliases {
    /**
     * Renames a constraint if it exists, swallowing only H2 error code 90057 (constraint not found).
     */
    @JvmStatic
    @Throws(SQLException::class)
    fun renameConstraintIfExists(conn: Connection, table: String, oldName: String, newName: String) {
        conn.createStatement().use { st ->
            try {
                st.execute("ALTER TABLE $table RENAME CONSTRAINT $oldName TO $newName")
            } catch (e: SQLException) {
                if (e.errorCode != 90057) { // ignore only 'constraint not found'
                    throw e
                }
            }
        }
    }
}

