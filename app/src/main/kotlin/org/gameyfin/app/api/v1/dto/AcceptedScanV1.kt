package org.gameyfin.app.api.v1.dto

import io.swagger.v3.oas.annotations.media.Schema

data class AcceptedScanV1(
    val libraryId: String,
    val type: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val status: String = "QUEUED"
)
