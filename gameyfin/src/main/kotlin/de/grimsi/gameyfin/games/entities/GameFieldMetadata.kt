package de.grimsi.gameyfin.games.entities

import de.grimsi.gameyfin.core.plugins.management.PluginManagementEntry
import jakarta.persistence.*
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@Entity
class GameFieldMetadata(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,

    @ManyToOne
    val source: PluginManagementEntry,

    @UpdateTimestamp
    var updatedAt: Instant? = Instant.now()
)
