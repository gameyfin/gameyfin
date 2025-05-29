package de.grimsi.gameyfin.games.repositories

import de.grimsi.gameyfin.games.entities.GameFieldMetadata
import org.springframework.data.jpa.repository.JpaRepository

interface FieldMetadataRepository : JpaRepository<GameFieldMetadata, Long>