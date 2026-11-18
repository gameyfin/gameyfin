package org.gameyfin.app.core.plugins.config

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional

interface PluginConfigRepository : JpaRepository<PluginConfigEntry, PluginConfigEntryKey> {

    @Query("SELECT p FROM PluginConfigEntry p WHERE p.id.pluginId = :pluginId")
    fun findAllByPluginId(pluginId: String): List<PluginConfigEntry>

    @Transactional
    @Modifying
    @Query("DELETE FROM PluginConfigEntry p WHERE p.id.pluginId = :pluginId")
    fun deleteAllByPluginId(pluginId: String)
}
