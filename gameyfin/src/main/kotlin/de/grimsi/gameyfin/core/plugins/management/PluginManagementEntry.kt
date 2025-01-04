package de.grimsi.gameyfin.core.plugins.management

import jakarta.persistence.Entity
import jakarta.persistence.Id

@Entity
data class PluginManagementEntry(
    @Id
    val pluginId: String,

    var enabled: Boolean = true,

    var priority: Int = Int.MAX_VALUE
)