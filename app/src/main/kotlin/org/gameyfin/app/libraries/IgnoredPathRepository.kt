package org.gameyfin.app.libraries

import org.gameyfin.app.libraries.entities.IgnoredPath
import org.springframework.data.jpa.repository.JpaRepository

interface IgnoredPathRepository : JpaRepository<IgnoredPath, Long> {
    fun findByPath(path: String): IgnoredPath?
}

