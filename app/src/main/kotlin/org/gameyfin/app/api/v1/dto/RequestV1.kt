package org.gameyfin.app.api.v1.dto

import java.time.Instant
import java.time.LocalDate

data class RequestV1(
    val id: String, val title: String, val platform: String, val release: LocalDate?, val status: String,
    val requesterId: String?, val voteCount: Int, val voted: Boolean?, val linkedGameId: String?,
    val createdAt: Instant?, val updatedAt: Instant?
)
