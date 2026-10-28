package org.gameyfin.app.api.v1.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.gameyfin.app.api.security.ApiAccess
import org.gameyfin.app.api.security.ApiScope
import org.gameyfin.app.api.v1.dto.OptionV1
import org.gameyfin.app.platforms.PlatformService
import org.gameyfin.pluginapi.gamemetadata.GameFeature
import org.gameyfin.pluginapi.gamemetadata.Genre
import org.gameyfin.pluginapi.gamemetadata.PlayerPerspective
import org.gameyfin.pluginapi.gamemetadata.Theme
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Catalog", description = "Platforms and metadata options supported by the instance.")
@RestController
@RequestMapping("/v1")
class CatalogControllerV1(
    private val access: ApiAccess,
    private val platforms: PlatformService
) {
    @GetMapping("/platforms")
    @Operation(
        operationId = "listPlatforms",
        description = "Requires games:read. Returns platforms supported by active metadata providers."
    )
    fun platforms(): List<OptionV1> {
        access.require(ApiScope.GAMES_READ)
        return platforms.availablePlatforms.map { OptionV1(it.name, it.displayName) }.sortedBy { it.code }
    }

    @GetMapping("/metadata-options")
    @Operation(
        operationId = "getMetadataOptions",
        description = "Requires games:read. Codes are extensible; clients must tolerate new values."
    )
    fun options(): Map<String, List<OptionV1>> {
        access.require(ApiScope.GAMES_READ)
        return mapOf(
            "genres" to Genre.entries.map { OptionV1(it.name, it.displayName) },
            "themes" to Theme.entries.map { OptionV1(it.name, it.displayName) },
            "features" to GameFeature.entries.map { OptionV1(it.name, it.displayName) },
            "perspectives" to PlayerPerspective.entries.map { OptionV1(it.name, it.displayName) })
    }
}
