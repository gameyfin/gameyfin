package org.gameyfin.app.libraries.extensions

import org.gameyfin.app.core.plugins.management.PluginManagementEntry
import org.gameyfin.app.libraries.dto.IgnoredPathDto
import org.gameyfin.app.libraries.dto.IgnoredPathSourceTypeDto
import org.gameyfin.app.libraries.entities.IgnoredPath
import org.gameyfin.app.libraries.entities.IgnoredPathPluginSource
import org.gameyfin.app.libraries.entities.IgnoredPathSourceType
import org.gameyfin.app.libraries.entities.IgnoredPathUserSource
import org.gameyfin.app.users.entities.User

fun IgnoredPath.toDto(): IgnoredPathDto {
    return IgnoredPathDto(
        id = this.id!!,
        path = this.path,
        sourceType = this.getType().toDto(),
        source = when (val source = this.source) {
            is IgnoredPathPluginSource -> source.plugins.joinToString("\", \"", "[\"", "\"]") { it.pluginId }
            is IgnoredPathUserSource -> source.user.id.toString()
            else -> throw IllegalStateException("Unknown IgnoredPathSource type")
        }
    )
}

fun Collection<IgnoredPath>.toDtos(): List<IgnoredPathDto> {
    return this.map { it.toDto() }
}

fun IgnoredPathSourceType.toDto(): IgnoredPathSourceTypeDto {
    return when (this) {
        IgnoredPathSourceType.PLUGIN -> IgnoredPathSourceTypeDto.PLUGIN
        IgnoredPathSourceType.USER -> IgnoredPathSourceTypeDto.USER
    }
}

fun IgnoredPathDto.toEntity(user: User? = null, plugins: List<PluginManagementEntry>? = null): IgnoredPath {
    val source = when (this.sourceType) {
        IgnoredPathSourceTypeDto.PLUGIN -> {
            requireNotNull(plugins) { "Plugins must provided for PLUGINS source type" }
            require(plugins.isNotEmpty()) { "Plugins must provided for PLUGINS source type" }
            IgnoredPathPluginSource(plugins.toMutableList())
        }

        IgnoredPathSourceTypeDto.USER -> {
            requireNotNull(user) { "User must be provided for USER source type" }
            IgnoredPathUserSource(user)
        }
    }

    return IgnoredPath(
        id = this.id,
        path = this.path,
        source = source
    )
}