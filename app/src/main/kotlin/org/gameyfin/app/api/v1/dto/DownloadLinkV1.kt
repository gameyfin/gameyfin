package org.gameyfin.app.api.v1.dto

import io.swagger.v3.oas.annotations.media.Schema

data class DownloadLinkV1(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val kind: String = "link",
    val url: String
)
