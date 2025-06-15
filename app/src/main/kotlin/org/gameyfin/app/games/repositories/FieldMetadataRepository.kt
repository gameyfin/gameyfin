package org.gameyfin.app.games.repositories

import org.gameyfin.app.games.entities.GameFieldMetadata
import org.springframework.data.jpa.repository.JpaRepository

interface FieldMetadataRepository : JpaRepository<GameFieldMetadata, Long>