package org.gameyfin.app.system.backup

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Instant

/**
 * Layout of a Gameyfin backup archive (ZIP):
 *
 * - `manifest.json`              metadata about the backup (see [BackupManifest])
 * - `database/<TABLE>.json`      one file per table: `{"table": "...", "columns": [...], "rows": [[...], ...]}`
 * - `media/<contentId>`          (optional) files from the media storage directory
 *
 * The database content is stored in a vendor-neutral JSON format so that a backup created on one supported
 * database (e.g. H2) can be restored on another one (e.g. PostgreSQL).
 */
object BackupFormat {
    const val FORMAT_VERSION = 1
    const val MANIFEST_ENTRY = "manifest.json"
    const val DATABASE_PREFIX = "database/"
    const val MEDIA_PREFIX = "media/"
    const val FILE_EXTENSION = ".zip"

    /** Tables managed by the migration tooling which must never be exported or imported */
    private val EXCLUDED_TABLES = setOf("DATABASECHANGELOG", "DATABASECHANGELOGLOCK", "FLYWAY_SCHEMA_HISTORY")

    fun isExcludedTable(name: String) = name.uppercase() in EXCLUDED_TABLES

    val jsonMapper: JsonMapper = JsonMapper.builder().build()
}

data class BackupChangeSet(val id: String, val author: String)

data class BackupTable(val name: String, val entry: String, val rowCount: Long)

data class BackupManifest(
    val formatVersion: Int,
    val createdAt: Instant,
    val gameyfinVersion: String?,
    val databaseProduct: String,
    val appKeyFingerprint: String,
    val includesMedia: Boolean,
    val mediaFileCount: Int,
    val changeSets: List<BackupChangeSet>,
    val tables: List<BackupTable>
) {
    fun toJson(): JsonNode {
        val root = BackupFormat.jsonMapper.createObjectNode()
        root.put("formatVersion", formatVersion)
        root.put("createdAt", createdAt.toString())
        root.put("gameyfinVersion", gameyfinVersion)
        root.put("databaseProduct", databaseProduct)
        root.put("appKeyFingerprint", appKeyFingerprint)
        root.put("includesMedia", includesMedia)
        root.put("mediaFileCount", mediaFileCount)
        val changeSetsNode = root.putArray("changeSets")
        changeSets.forEach { changeSetsNode.addObject().put("id", it.id).put("author", it.author) }
        val tablesNode = root.putArray("tables")
        tables.forEach {
            tablesNode.addObject().put("name", it.name).put("entry", it.entry).put("rowCount", it.rowCount)
        }
        return root
    }

    companion object {
        fun fromJson(node: JsonNode): BackupManifest {
            fun required(field: String): JsonNode =
                node.get(field)?.takeUnless { it.isNull }
                    ?: throw InvalidBackupException("Backup manifest is missing '$field'")

            fun requiredArray(field: String): List<JsonNode> =
                required(field).takeIf { it.isArray }?.toList()
                    ?: throw InvalidBackupException("Backup manifest field '$field' is not an array")

            fun optional(field: String): JsonNode? = node.get(field)?.takeUnless { it.isNull }

            return try {
                BackupManifest(
                    formatVersion = required("formatVersion").asInt(),
                    createdAt = Instant.parse(required("createdAt").asString()),
                    gameyfinVersion = optional("gameyfinVersion")?.asString(),
                    databaseProduct = required("databaseProduct").asString(),
                    appKeyFingerprint = required("appKeyFingerprint").asString(),
                    includesMedia = optional("includesMedia")?.asBoolean() ?: false,
                    mediaFileCount = optional("mediaFileCount")?.asInt() ?: 0,
                    changeSets = requiredArray("changeSets").map {
                        BackupChangeSet(it.get("id").asString(), it.get("author").asString())
                    },
                    tables = requiredArray("tables").map {
                        BackupTable(it.get("name").asString(), it.get("entry").asString(), it.get("rowCount").asLong())
                    }
                )
            } catch (e: InvalidBackupException) {
                throw e
            } catch (e: Exception) {
                throw InvalidBackupException("Backup manifest is malformed: ${e.message}", e)
            }
        }
    }
}

class InvalidBackupException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
