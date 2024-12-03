package de.grimsi.gameyfin.core.plugins.management

import org.springframework.data.jpa.repository.JpaRepository

interface PluginManagementRepository : JpaRepository<PluginManagementEntry, String>