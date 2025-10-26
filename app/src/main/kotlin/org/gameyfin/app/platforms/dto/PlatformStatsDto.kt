package org.gameyfin.app.platforms.dto

import com.fasterxml.jackson.annotation.JsonInclude
import org.gameyfin.pluginapi.gamemetadata.Platform

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PlatformStatsDto(
    val available: Set<Platform>? = null,
    val inUseByGames: Set<Platform>? = null,
    val inUseByLibraries: Set<Platform>? = null
)