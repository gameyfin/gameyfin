package de.grimsi.gameyfin.core.plugins.management

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull

@Entity
@Table(name = "plugin_management")
data class PluginManagementEntry(
    @Id
    @Column(name = "plugin_id")
    val pluginId: String,

    @NotNull
    @Column(name = "enabled")
    var enabled: Boolean = true
)