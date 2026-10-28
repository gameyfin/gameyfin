package org.gameyfin.app.core.database

import org.gameyfin.app.core.security.EncryptionUtils
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import kotlin.io.path.*

/**
 * A switch to a different database, scheduled to be completed on the next startup (see
 * [org.gameyfin.app.core.backup.PendingRestoreExecutor]): the data is transferred by restoring [backupName] into the
 * new database. If this fails, Gameyfin reverts to the [previous] database.
 */
data class PendingDatabaseSwitch(
    val previous: DatabaseSettings,
    val target: DatabaseSettings,
    val backupName: String
)

/**
 * Manages the database settings configured in the UI.
 *
 * The settings can not be stored in the database itself, so they are stored in a JSON file (by default next to the
 * embedded H2 database). Passwords are encrypted using the APP_KEY. Besides the settings, the directory contains:
 * - a marker file for a database switch which is completed on the next startup
 * - a status file with the result of the last database switch
 */
class DatabaseSettingsStore(
    settingsFile: Path,
    private val encrypt: (String) -> String = EncryptionUtils::encrypt,
    private val decrypt: (String) -> String = EncryptionUtils::decrypt
) {

    companion object {
        private const val PENDING_SWITCH_FILE = ".pending-database-switch.json"
        private const val SWITCH_STATUS_FILE = ".last-database-switch.json"
        private val jsonMapper: JsonMapper = JsonMapper.builder().build()
    }

    val settingsFile: Path = settingsFile.toAbsolutePath().normalize()
    private val directory: Path get() = settingsFile.parent
    private val pendingSwitchFile: Path get() = directory.resolve(PENDING_SWITCH_FILE)
    private val switchStatusFile: Path get() = directory.resolve(SWITCH_STATUS_FILE)

    /**
     * Returns the stored settings, or `null` if no settings have been configured in the UI.
     *
     * @throws IllegalStateException if the settings file can not be read
     */
    fun load(): DatabaseSettings? {
        if (!settingsFile.exists()) return null
        return try {
            fromJson(jsonMapper.readTree(settingsFile.readText()))
        } catch (e: Exception) {
            throw IllegalStateException("Could not read database settings from $settingsFile: ${e.message}", e)
        }
    }

    fun save(settings: DatabaseSettings) {
        write(settingsFile, toJson(settings))
    }

    fun schedulePendingSwitch(pendingSwitch: PendingDatabaseSwitch) {
        val node = jsonMapper.createObjectNode()
        node.set("previous", toJson(pendingSwitch.previous))
        node.set("target", toJson(pendingSwitch.target))
        node.put("backupName", pendingSwitch.backupName)
        write(pendingSwitchFile, node)
    }

    /**
     * @throws IllegalStateException if the marker file can not be read
     */
    fun pendingSwitch(): PendingDatabaseSwitch? {
        if (!pendingSwitchFile.isRegularFile()) return null
        return try {
            val node = jsonMapper.readTree(pendingSwitchFile.readText())
            PendingDatabaseSwitch(
                previous = fromJson(node.required("previous")),
                target = fromJson(node.required("target")),
                backupName = node.required("backupName").asString()
            )
        } catch (e: Exception) {
            throw IllegalStateException(
                "Could not read pending database switch from $pendingSwitchFile: ${e.message}",
                e
            )
        }
    }

    fun clearPendingSwitch() {
        pendingSwitchFile.deleteIfExists()
    }

    fun writeSwitchStatus(status: DatabaseSwitchStatusDto) {
        val node = jsonMapper.createObjectNode()
            .put("success", status.success)
            .put("message", status.message)
            .put("previousType", status.previousType.name)
            .put("newType", status.newType.name)
            .put("finishedAt", status.finishedAt.toString())
        write(switchStatusFile, node)
    }

    fun readSwitchStatus(): DatabaseSwitchStatusDto? {
        if (!switchStatusFile.isRegularFile()) return null
        return try {
            val node = jsonMapper.readTree(switchStatusFile.readText())
            DatabaseSwitchStatusDto(
                success = node.required("success").asBoolean(),
                message = node.required("message").asString(),
                previousType = DatabaseType.valueOf(node.required("previousType").asString()),
                newType = DatabaseType.valueOf(node.required("newType").asString()),
                finishedAt = Instant.parse(node.required("finishedAt").asString())
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun toJson(settings: DatabaseSettings): ObjectNode {
        val node = jsonMapper.createObjectNode().put("type", settings.type.name)
        settings.host?.let { node.put("host", it) }
        settings.port?.let { node.put("port", it) }
        settings.database?.let { node.put("database", it) }
        settings.username?.let { node.put("username", it) }
        settings.password?.let { node.put("password", encrypt(it)) }
        return node
    }

    private fun fromJson(node: JsonNode): DatabaseSettings {
        fun optional(name: String) = node.get(name)?.takeUnless { it.isNull }
        val type = DatabaseType.valueOf(node.required("type").asString())
        if (type == DatabaseType.H2) return DatabaseSettings.EMBEDDED
        return DatabaseSettings.of(
            type = type,
            host = optional("host")?.asString(),
            port = optional("port")?.asInt(),
            database = optional("database")?.asString(),
            username = optional("username")?.asString(),
            password = optional("password")?.asString()?.let(decrypt)
        )
    }

    /** Writes the file atomically and (if supported) only readable by the owner, as it may contain credentials */
    private fun write(target: Path, node: JsonNode) {
        directory.createDirectories()
        val tempFile = Files.createTempFile(directory, ".tmp-", ".json")
        try {
            try {
                Files.setPosixFilePermissions(tempFile, PosixFilePermissions.fromString("rw-------"))
            } catch (_: UnsupportedOperationException) {
                // Not supported on this file system
            }
            tempFile.writeText(jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node))
            try {
                Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            tempFile.deleteIfExists()
        }
    }
}
