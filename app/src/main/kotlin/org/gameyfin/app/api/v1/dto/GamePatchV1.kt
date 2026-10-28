package org.gameyfin.app.api.v1.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(description = "JSON Merge Patch: omitted fields stay unchanged, null clears summary, comment or release; arrays replace their contents. Other null values are rejected.")
data class GamePatchV1(
    val title: String? = null, val platforms: Set<String>? = null, val release: LocalDate? = null,
    val summary: String? = null, val comment: String? = null, val publishers: List<String>? = null,
    val developers: List<String>? = null, val genres: List<String>? = null, val themes: List<String>? = null,
    val features: List<String>? = null, val perspectives: List<String>? = null, val keywords: List<String>? = null
)
