package de.grimsi.gameyfin.games.repositories

import de.grimsi.gameyfin.games.entities.FieldMetadata
import org.springframework.data.jpa.repository.JpaRepository

interface FieldMetadataRepository : JpaRepository<FieldMetadata, Long>