package de.grimsi.gameyfin.games.repositories

import de.grimsi.gameyfin.games.entities.Screenshot
import org.springframework.data.jpa.repository.JpaRepository
import java.net.URL

interface ScreenshotRepository : JpaRepository<Screenshot, Long> {
    fun findByOriginalUrl(originalUrl: URL): Screenshot?
}