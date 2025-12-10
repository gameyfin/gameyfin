package org.gameyfin.app.collections.repositories

import org.gameyfin.app.collections.entities.Collection
import org.springframework.data.jpa.repository.JpaRepository

interface CollectionRepository : JpaRepository<Collection, Long> {
    fun findByName(name: String): Collection?
}

