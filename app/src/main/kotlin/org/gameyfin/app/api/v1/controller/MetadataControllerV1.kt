package org.gameyfin.app.api.v1.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.gameyfin.app.api.security.ApiAccess
import org.gameyfin.app.api.security.ApiScope
import org.gameyfin.app.api.v1.apiEnum
import org.gameyfin.app.api.v1.dto.ExternalIdV1
import org.gameyfin.app.api.v1.dto.MetadataSearchResultV1
import org.gameyfin.app.api.v1.dto.MetadataUrlV1
import org.gameyfin.app.games.GameService
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.ZoneOffset

@Tag(name = "Metadata", description = "Search enabled external metadata providers.")
@RestController
@RequestMapping("/v1/metadata")
class MetadataControllerV1(private val access: ApiAccess, private val games: GameService) {
    @GetMapping("/search")
    @Operation(
        operationId = "searchMetadata",
        description = "Requires games:read. Searches enabled providers and merges matches."
    )
    fun search(
        @Parameter(description = "Nonblank search term, maximum 255 characters.") @RequestParam q: String,
        @Parameter(description = "Platform codes. Repeat this parameter for multiple platforms; omit to search all platforms.")
        @RequestParam(required = false) platform: List<String>?
    ): List<MetadataSearchResultV1> {
        access.require(ApiScope.GAMES_READ)
        require(q.isNotBlank() && q.length <= 255) { "Search term must be nonblank and at most 255 characters" }
        val platforms = platform.orEmpty().map { apiEnum<Platform>(it) }.toSet()
        return games.getPotentialMatches(q, platforms).map { result ->
            MetadataSearchResultV1(
                result.title, result.platforms?.map { it.name },
                result.release?.atZone(ZoneOffset.UTC)?.toLocalDate(),
                result.publishers?.toList(), result.developers?.toList(),
                result.coverUrls?.map { MetadataUrlV1(it.url, it.pluginId) },
                result.headerUrls?.map { MetadataUrlV1(it.url, it.pluginId) },
                result.screenshotUrls?.map { MetadataUrlV1(it.url, it.pluginId) },
                result.originalIds.values.map { ExternalIdV1(it.pluginId, it.externalProviderId) }.distinct()
            )
        }
    }
}
