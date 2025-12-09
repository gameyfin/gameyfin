package org.gameyfin.app.games.repositories

import org.gameyfin.app.media.Image
import org.springframework.data.jpa.repository.JpaRepository

interface ImageRepository : JpaRepository<Image, Long> {
    fun findAllByOriginalUrl(originalUrl: String): List<Image>
}