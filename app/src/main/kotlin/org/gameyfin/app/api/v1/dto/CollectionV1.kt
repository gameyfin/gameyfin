package org.gameyfin.app.api.v1.dto

import java.time.Instant

data class CollectionV1(
    val id: String,
    val name: String,
    val description: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)
