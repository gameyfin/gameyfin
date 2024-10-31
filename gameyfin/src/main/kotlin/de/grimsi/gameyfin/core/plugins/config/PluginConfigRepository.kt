package de.grimsi.gameyfin.core.plugins.config

import org.springframework.data.jpa.repository.JpaRepository

interface PluginConfigRepository : JpaRepository<PluginConfigEntry, PluginConfigEntryKey> {
    fun findAllById_PluginId(pluginId: String): List<PluginConfigEntry>
    fun findById_PluginIdAndId_Key(pluginId: String, key: String): PluginConfigEntry?
}