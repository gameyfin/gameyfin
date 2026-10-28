package org.gameyfin.app.api.v1.dto

import java.time.Instant
import java.time.LocalDate

data class GameV1(
    val id: String, val libraryId: String, val title: String?, val createdAt: Instant?, val updatedAt: Instant?,
    val platforms: List<String>, val release: LocalDate?, val summary: String?, val comment: String?,
    val userRating: Int?, val criticRating: Int?, val publishers: List<String>, val developers: List<String>,
    val genres: List<String>, val themes: List<String>, val features: List<String>, val perspectives: List<String>,
    val keywords: List<String>, val cover: ImageV1?, val header: ImageV1?, val images: List<ImageV1>,
    val videoUrls: List<String>, val mediaOrder: List<String>, val externalIds: List<ExternalIdV1>, val fileSize: Long?
)
