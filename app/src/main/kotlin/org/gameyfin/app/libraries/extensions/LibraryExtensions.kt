package org.gameyfin.app.libraries.extensions

import org.gameyfin.app.libraries.dto.*
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.app.libraries.entities.LibraryMetadata


fun Library.toSummaryDto(admin: Boolean): LibrarySummaryDto {
    return LibrarySummaryDto(
        id = this.id!!,
        name = this.name,
        createdAt = this.createdAt!!,
        updatedAt = this.updatedAt!!,
        metadata = this.metadata.toDto(),
        platforms = if (admin) this.platforms.toList() else null
    )
}

fun Library.toAdminDto(): LibraryAdminDto {
    return LibraryAdminDto(
        id = this.id!!,
        name = this.name,
        createdAt = this.createdAt!!,
        updatedAt = this.updatedAt,
        directories = this.directories.map { DirectoryMappingDto(it.internalPath, it.externalPath) },
        platforms = this.platforms.toList(),
        ignoredPaths = this.ignoredPaths.toDtos(),
        metadata = this.metadata.toDto()
    )
}

/**
 * Builds a patch containing only the given fields. Admin-only fields are dropped for non-admins.
 * Returns `null` if no field is visible for the given audience.
 */
fun Library.toPatchDto(fields: Set<LibraryField>, admin: Boolean): LibraryPatchDto? {
    val visibleFields = fields.filter { admin || !it.adminOnly }.sorted()
    if (visibleFields.isEmpty()) return null

    var values = LibraryPatchValuesDto()
    for (field in visibleFields) {
        values = when (field) {
            LibraryField.NAME -> values.copy(name = name)
            LibraryField.METADATA -> values.copy(metadata = metadata.toDto())
            LibraryField.PLATFORMS -> values.copy(platforms = platforms.toList())
            LibraryField.DIRECTORIES -> values.copy(
                directories = directories.map { DirectoryMappingDto(it.internalPath, it.externalPath) }
            )

            LibraryField.IGNORED_PATHS -> values.copy(ignoredPaths = ignoredPaths.toDtos())
        }
    }

    return LibraryPatchDto(updatedAt = updatedAt!!, fields = visibleFields, values = values)
}

fun LibraryMetadata.toDto(): LibraryMetadataDto {
    return LibraryMetadataDto(
        displayOnHomepage = this.displayOnHomepage,
        displayOrder = this.displayOrder
    )
}

fun LibraryMetadataDto.toEntity(): LibraryMetadata {
    return LibraryMetadata(
        displayOnHomepage = this.displayOnHomepage,
        displayOrder = this.displayOrder
    )
}