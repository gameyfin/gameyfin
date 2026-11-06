package org.gameyfin.app.api.v1.dto

import java.time.LocalDate

data class MetadataSearchResultV1(
    val title: String,
    val platforms: List<String>?,
    val release: LocalDate?,
    val publishers: List<String>?,
    val developers: List<String>?,
    val coverUrls: List<MetadataUrlV1>?,
    val headerUrls: List<MetadataUrlV1>?,
    val screenshotUrls: List<MetadataUrlV1>?,
    val externalIds: List<ExternalIdV1>
)

data class MetadataUrlV1(val url: String, val pluginId: String)
