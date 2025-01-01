package de.grimsi.gameyfin.games.entities

import de.grimsi.gameyfin.core.plugins.management.PluginManagementEntry
import jakarta.persistence.*
import java.time.Instant

@Entity
class FieldMetadata(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,

    @ManyToOne
    val source: PluginManagementEntry,

    val lastUpdated: Instant
) {
    constructor(source: PluginManagementEntry) : this(null, source, Instant.now())
}
