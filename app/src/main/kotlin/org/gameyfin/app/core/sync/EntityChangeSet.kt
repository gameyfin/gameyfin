package org.gameyfin.app.core.sync

import org.gameyfin.app.collections.dto.CollectionField
import org.gameyfin.app.games.dto.GameField
import org.gameyfin.app.games.entities.GameMetadata
import org.gameyfin.app.libraries.dto.LibraryField

/**
 * Changes of a single entity within one or more transactions.
 */
class EntityChange<F : Enum<F>> {
    var created: Boolean = false
    var deleted: Boolean = false

    /** Whether Hibernate issued an UPDATE for the entity itself (which refreshes its `updatedAt` timestamp) */
    var rowUpdated: Boolean = false

    /** Whether a collection or child entity changed, which requires the owner's `updatedAt` timestamp to be bumped */
    var requiresTimestampBump: Boolean = false

    val fields: MutableSet<F> = mutableSetOf()

    /** Changed fields that are not covered by an UPDATE of the entity row */
    fun addAssociationFields(changedFields: Collection<F>) {
        if (changedFields.isEmpty()) return
        fields.addAll(changedFields)
        requiresTimestampBump = true
    }

    fun mergeFrom(other: EntityChange<F>) {
        created = created || other.created
        deleted = deleted || other.deleted
        rowUpdated = rowUpdated || other.rowUpdated
        requiresTimestampBump = requiresTimestampBump || other.requiresTimestampBump
        fields.addAll(other.fields)
    }

    /**
     * Called once the transaction has been committed:
     * If the row has been updated within the same transaction, the timestamp is already up to date.
     */
    fun finalizeTransaction() {
        if (rowUpdated) requiresTimestampBump = false
    }

    /** Created and deleted before any client could see it */
    val isTransient: Boolean get() = created && deleted
}

/**
 * Collects all changes relevant for real-time synchronization within a transaction.
 */
class EntityChangeSet {
    val games: MutableMap<Long, EntityChange<GameField>> = mutableMapOf()
    val libraries: MutableMap<Long, EntityChange<LibraryField>> = mutableMapOf()
    val collections: MutableMap<Long, EntityChange<CollectionField>> = mutableMapOf()

    /* Child entities whose owners have to be resolved after the commit */
    val gameFieldMetadataIds: MutableSet<Long> = mutableSetOf()
    val directoryMappingIds: MutableSet<Long> = mutableSetOf()
    val imageIds: MutableSet<Long> = mutableSetOf()

    fun game(id: Long) = games.getOrPut(id) { EntityChange() }
    fun library(id: Long) = libraries.getOrPut(id) { EntityChange() }
    fun collection(id: Long) = collections.getOrPut(id) { EntityChange() }

    fun isEmpty(): Boolean = games.isEmpty() && libraries.isEmpty() && collections.isEmpty() &&
            gameFieldMetadataIds.isEmpty() && directoryMappingIds.isEmpty() && imageIds.isEmpty()

    fun finalizeTransaction(): EntityChangeSet {
        games.values.forEach { it.finalizeTransaction() }
        libraries.values.forEach { it.finalizeTransaction() }
        collections.values.forEach { it.finalizeTransaction() }
        return this
    }

    fun mergeFrom(other: EntityChangeSet) {
        other.games.forEach { (id, change) -> game(id).mergeFrom(change) }
        other.libraries.forEach { (id, change) -> library(id).mergeFrom(change) }
        other.collections.forEach { (id, change) -> collection(id).mergeFrom(change) }
        gameFieldMetadataIds.addAll(other.gameFieldMetadataIds)
        directoryMappingIds.addAll(other.directoryMappingIds)
        imageIds.addAll(other.imageIds)
    }

    companion object {
        fun merge(changeSets: Collection<EntityChangeSet>): EntityChangeSet {
            val merged = EntityChangeSet()
            changeSets.forEach { merged.mergeFrom(it) }
            return merged
        }
    }
}

/**
 * Maps (dirty) entity properties and collection roles to the fields of the respective patch DTOs.
 * Property paths of collections within embeddables are dot-separated (e.g. `metadata.fields`).
 */
object EntityFieldMappings {

    val GAME_MEDIA_FIELDS = listOf(GameField.IMAGES, GameField.VIDEO_URLS, GameField.MEDIA_ORDER)

    val GAME_PROPERTIES: Map<String, GameField> = mapOf(
        "library" to GameField.LIBRARY_ID,
        "title" to GameField.TITLE,
        "platforms" to GameField.PLATFORMS,
        "coverImage" to GameField.COVER,
        "headerImage" to GameField.HEADER,
        "comment" to GameField.COMMENT,
        "summary" to GameField.SUMMARY,
        "release" to GameField.RELEASE,
        "userRating" to GameField.USER_RATING,
        "criticRating" to GameField.CRITIC_RATING,
        "publishers" to GameField.PUBLISHERS,
        "developers" to GameField.DEVELOPERS,
        "genres" to GameField.GENRES,
        "themes" to GameField.THEMES,
        "keywords" to GameField.KEYWORDS,
        "features" to GameField.FEATURES,
        "perspectives" to GameField.PERSPECTIVES,
        "metadata.fields" to GameField.FIELD_METADATA,
        "metadata.originalIds" to GameField.ORIGINAL_IDS
    )

    /** Fields stored in the embedded [GameMetadata] (excluding its collections) */
    val GAME_METADATA_FIELDS = listOf(
        GameField.PATH,
        GameField.FILE_SIZE,
        GameField.DOWNLOAD_COUNT,
        GameField.MATCH_CONFIRMED
    )

    val LIBRARY_PROPERTIES: Map<String, LibraryField> = mapOf(
        "name" to LibraryField.NAME,
        "metadata" to LibraryField.METADATA,
        "platforms" to LibraryField.PLATFORMS,
        "directories" to LibraryField.DIRECTORIES,
        "ignoredPaths" to LibraryField.IGNORED_PATHS
    )

    val COLLECTION_PROPERTIES: Map<String, CollectionField> = mapOf(
        "name" to CollectionField.NAME,
        "description" to CollectionField.DESCRIPTION,
        "games" to CollectionField.GAME_IDS,
        "metadata" to CollectionField.METADATA,
        "metadata.gamesAddedAt" to CollectionField.METADATA
    )

    /**
     * Determines which fields of the embedded [GameMetadata] changed.
     * If the previous state is unknown, all fields are considered changed.
     */
    fun changedGameMetadataFields(previous: Any?, current: Any?): List<GameField> {
        if (previous !is GameMetadata || current !is GameMetadata) return GAME_METADATA_FIELDS

        return buildList {
            if (previous.path != current.path) add(GameField.PATH)
            if (previous.fileSize != current.fileSize) add(GameField.FILE_SIZE)
            if (previous.downloadCount != current.downloadCount) add(GameField.DOWNLOAD_COUNT)
            if (previous.matchConfirmed != current.matchConfirmed) add(GameField.MATCH_CONFIRMED)
        }
    }

    /**
     * Maps the dirty properties of a game to the changed fields.
     *
     * @param dirtyProperties Names of the dirty properties, `null` if unknown (all fields are considered changed)
     */
    fun changedGameFields(
        dirtyProperties: Collection<String>?,
        previousMetadata: () -> Any?,
        currentMetadata: () -> Any?
    ): Set<GameField> {
        if (dirtyProperties == null) return GameField.entries.toSet()

        return buildSet {
            dirtyProperties.forEach { property ->
                if (property == "media") {
                    addAll(GAME_MEDIA_FIELDS)
                } else if (property == "metadata") {
                    addAll(changedGameMetadataFields(previousMetadata(), currentMetadata()))
                } else {
                    GAME_PROPERTIES[property]?.let { add(it) }
                }
            }
        }
    }

    fun changedLibraryFields(dirtyProperties: Collection<String>?): Set<LibraryField> {
        if (dirtyProperties == null) return LibraryField.entries.toSet()
        return dirtyProperties.mapNotNull { LIBRARY_PROPERTIES[it] }.toSet()
    }

    fun changedCollectionFields(dirtyProperties: Collection<String>?): Set<CollectionField> {
        if (dirtyProperties == null) return CollectionField.entries.toSet()
        return dirtyProperties.mapNotNull { COLLECTION_PROPERTIES[it] }.toSet()
    }

    /**
     * Extracts the property path from a collection role, e.g.
     * `org.gameyfin.app.games.entities.Game.metadata.fields` -> `metadata.fields`
     */
    fun propertyPathFromRole(role: String, ownerEntityName: String?): String {
        if (ownerEntityName != null && role.startsWith("$ownerEntityName.")) {
            return role.removePrefix("$ownerEntityName.")
        }
        return role.substringAfterLast('.')
    }
}
