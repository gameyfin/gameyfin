package org.gameyfin.app.api.v1.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

data class InstanceV1(
    val version: String,
    val buildTime: Instant?,
    val gitCommit: String?,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val apiVersions: List<String> = listOf("v1"),
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val capabilities: List<String> = listOf(
        "catalog",
        "downloads",
        "metadata-write",
        "media-write",
        "library-scans",
        "requests"
    )
)
