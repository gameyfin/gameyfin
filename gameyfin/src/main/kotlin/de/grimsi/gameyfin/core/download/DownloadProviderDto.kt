package de.grimsi.gameyfin.core.download

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class DownloadProviderDto(
    val key: String,
    val name: String,
    val description: String,
    val shortDescription: String? = null
)
