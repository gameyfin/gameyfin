package de.grimsi.gameyfin.games.entities

import de.grimsi.gameyfin.core.plugins.management.PluginManagementEntry
import jakarta.persistence.*

@Embeddable
class GameMetadata(
    @Column(unique = true)
    val path: String,

    var fileSize: Long? = null,

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    var fields: Map<String, GameFieldMetadata> = emptyMap(),

    @ElementCollection
    var originalIds: Map<PluginManagementEntry, String> = emptyMap(),

    var downloadCount: Int = 0,

    var matchConfirmed: Boolean = false
)