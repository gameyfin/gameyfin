package org.gameyfin.app.api.v1.dto

import java.time.Instant

data class LibraryV1(
    val id: String,
    val name: String,
    val platforms: List<String>,
    val createdAt: Instant?,
    val updatedAt: Instant?
)
