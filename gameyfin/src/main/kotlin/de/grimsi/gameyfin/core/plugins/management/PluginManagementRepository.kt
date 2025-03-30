package de.grimsi.gameyfin.core.plugins.management

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface PluginManagementRepository : JpaRepository<PluginManagementEntry, String> {
    @Query("SELECT MAX(p.priority) FROM PluginManagementEntry p")
    fun findMaxPriority(): Int?
}