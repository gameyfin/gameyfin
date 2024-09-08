package de.grimsi.gameyfin.config.persistence

import de.grimsi.gameyfin.config.entities.ConfigEntry
import org.springframework.data.jpa.repository.JpaRepository

interface ConfigRepository : JpaRepository<ConfigEntry, String>
