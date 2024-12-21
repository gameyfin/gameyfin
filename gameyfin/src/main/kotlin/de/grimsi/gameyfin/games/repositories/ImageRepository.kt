package de.grimsi.gameyfin.games.repositories

import de.grimsi.gameyfin.games.entities.Image
import org.springframework.data.jpa.repository.JpaRepository
import java.net.URL

interface ImageRepository : JpaRepository<Image, Long> {
    fun findByOriginalUrl(originalUrl: URL): Image?
}