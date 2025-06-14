package org.gameyfin.app.config.persistence

import org.gameyfin.app.config.entities.ConfigEntry
import org.springframework.data.jpa.repository.JpaRepository

interface ConfigRepository : JpaRepository<ConfigEntry, String>
