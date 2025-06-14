package org.gameyfin.app.core.download

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class DownloadProviderDto(
    val key: String,
    val name: String,
    val priority: Int,
    val description: String,
    val shortDescription: String? = null
)
