package org.gameyfin.app.games.repositories

import org.gameyfin.app.games.entities.Image
import org.springframework.data.jpa.repository.JpaRepository
import java.net.URL

interface ImageRepository : JpaRepository<Image, Long> {
    fun findByOriginalUrl(originalUrl: URL): Image?
}