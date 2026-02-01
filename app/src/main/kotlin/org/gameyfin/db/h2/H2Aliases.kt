package org.gameyfin.db.h2

import org.gameyfin.app.core.security.EncryptionUtils
import tools.jackson.databind.ObjectMapper
import java.sql.Connection
import java.sql.SQLException

/**
 * H2 helper methods exposed as SQL ALIASes.
 * <p>
 * Kotlin implementation replacing the former Java version so a JDK (javac) is not
 * required at runtime for defining aliases in migration scripts.
 */
object H2Aliases {

    private val objectMapper = ObjectMapper()

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

    /**
     * Convert a plain string to JSON string format (wrapped in quotes).
     * Returns the input unchanged if it's already a JSON string.
     */
    @JvmStatic
    fun toJsonString(value: String?): String? {
        if (value == null) return null
        val decryptedValue = EncryptionUtils.decrypt(value)
        // Check if already JSON string
        if (decryptedValue.startsWith("\"") && decryptedValue.endsWith("\"")) return value
        val jsonValue = objectMapper.writeValueAsString(decryptedValue)
        return EncryptionUtils.encrypt(jsonValue)
    }

    /**
     * Convert a boolean string to JSON boolean format.
     * Returns the input unchanged if it's already "true" or "false".
     */
    @JvmStatic
    fun toJsonBoolean(value: String?): String? {
        if (value == null) return null
        val decryptedValue = EncryptionUtils.decrypt(value)
        // Already correct JSON format
        if (decryptedValue == "true" || decryptedValue == "false") return value
        return EncryptionUtils.encrypt(decryptedValue.lowercase())
    }

    /**
     * Convert an integer string to JSON integer format.
     * Returns the input unchanged if it's already a valid integer.
     */
    @JvmStatic
    fun toJsonInt(value: String?): String? {
        if (value == null) return null
        val decryptedValue = EncryptionUtils.decrypt(value)
        return try {
            EncryptionUtils.encrypt(decryptedValue.toInt().toString())
        } catch (_: NumberFormatException) {
            value
        }
    }

    /**
     * Convert a comma-separated string to JSON array format.
     * Returns the input unchanged if it's already a JSON array.
     */
    @JvmStatic
    fun toJsonArray(value: String?): String? {
        if (value == null) return null
        val decryptedValue = EncryptionUtils.decrypt(value)
        // Check if already JSON array
        if (decryptedValue.startsWith("[") && decryptedValue.endsWith("]")) return value

        val elements = if (decryptedValue.isBlank()) {
            emptyArray<String>()
        } else {
            decryptedValue.split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toTypedArray()
        }

        val jsonValue = objectMapper.writeValueAsString(elements)
        return EncryptionUtils.encrypt(jsonValue)
    }
}

