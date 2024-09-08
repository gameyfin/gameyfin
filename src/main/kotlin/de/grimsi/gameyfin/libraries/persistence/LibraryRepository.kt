package de.grimsi.gameyfin.libraries.persistence

import de.grimsi.gameyfin.libraries.entities.Library
import org.springframework.data.jpa.repository.JpaRepository

interface LibraryRepository : JpaRepository<Library, Long>