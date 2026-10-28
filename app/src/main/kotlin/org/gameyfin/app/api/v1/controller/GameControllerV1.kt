package org.gameyfin.app.api.v1.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Encoding
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.gameyfin.app.api.security.ApiAccess
import org.gameyfin.app.api.security.ApiScope
import org.gameyfin.app.api.v1.apiId
import org.gameyfin.app.api.v1.apiPage
import org.gameyfin.app.api.v1.dto.GameMediaV1
import org.gameyfin.app.api.v1.dto.GamePatchV1
import org.gameyfin.app.api.v1.dto.GameV1
import org.gameyfin.app.api.v1.dto.PageV1
import org.gameyfin.app.api.v1.service.CatalogServiceV1
import org.gameyfin.app.games.GameService
import org.gameyfin.app.libraries.LibraryCoreService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartHttpServletRequest

@Tag(name = "Games", description = "Browse, update and delete games in the catalog.")
@RestController
@RequestMapping("/v1/games")
class GameControllerV1(
    private val access: ApiAccess, private val catalog: CatalogServiceV1,
    private val games: GameService, private val libraries: LibraryCoreService
) {
    @GetMapping
    @Operation(
        operationId = "listGames",
        description = "Requires games:read. Filter by externalProvider and externalId together. sort: id, title, createdAt or updatedAt, optionally followed by ,asc or ,desc."
    )
    fun games(
        @Parameter(description = "Zero-based page index.") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size.") @RequestParam(defaultValue = "50") size: Int,
        @Parameter(description = "Sort field, optionally followed by ,asc or ,desc. Allowed fields: id, title, createdAt, updatedAt.")
        @RequestParam(defaultValue = "id") sort: String,
        @Parameter(description = "Search term. Maximum 255 characters.") @RequestParam(required = false) q: String?,
        @Parameter(description = "Only include games in this library.") @RequestParam(required = false) libraryId: String?,
        @Parameter(description = "Only include games in this collection.") @RequestParam(required = false) collectionId: String?,
        @Parameter(description = "Only include games for this platform code.") @RequestParam(required = false) platform: String?,
        @Parameter(description = "External metadata provider. Must be supplied together with externalId.")
        @RequestParam(required = false) externalProvider: String?,
        @Parameter(description = "ID of the game at the external provider. Must be supplied together with externalProvider.")
        @RequestParam(required = false) externalId: String?
    ): PageV1<GameV1> {
        access.require(ApiScope.GAMES_READ)
        return catalog.games(
            apiPage(page, size, sort, setOf("id", "title", "createdAt", "updatedAt")),
            q,
            libraryId,
            collectionId,
            platform,
            externalProvider,
            externalId
        )
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getGame", description = "Requires games:read.")
    fun game(@Parameter(description = "Game ID.") @PathVariable id: String): GameV1 {
        access.require(ApiScope.GAMES_READ); return catalog.game(id)
    }

    @PatchMapping("/{id}", consumes = ["application/merge-patch+json"])
    @Operation(operationId = "patchGame", description = "Requires games:write or an administrator personal token.")
    fun patchGame(
        @Parameter(description = "Game ID.") @PathVariable id: String,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(content = [Content(schema = Schema(implementation = GamePatchV1::class))])
        @RequestBody patch: Map<String, Any?>
    ): GameV1 {
        access.require(ApiScope.GAMES_WRITE); return catalog.patchGame(id, patch)
    }

    @PutMapping("/{id}/media", consumes = ["multipart/form-data"])
    @Operation(
        operationId = "replaceGameMedia",
        description = "Requires games:write or an administrator personal token. The metadata JSON part describes the complete artwork/gallery. Each image selects exactly one existing id, URL or named file uploadKey. Null cover/header removes it. Gallery arrays replace existing content."
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        content = [Content(
            mediaType = "multipart/form-data",
            encoding = [Encoding(name = "metadata", contentType = "application/json")]
        )]
    )
    fun media(
        @Parameter(description = "Game ID.") @PathVariable id: String,
        @Parameter(description = "JSON metadata describing the complete cover, header and gallery. Image entries reference an existing image id, a URL, or an uploaded file via uploadKey.")
        @RequestPart metadata: GameMediaV1,
        request: MultipartHttpServletRequest
    ): GameV1 {
        access.require(ApiScope.GAMES_WRITE)
        val files = request.multiFileMap.filterKeys { it != "metadata" }
        require(files.values.all { it.size == 1 }) { "Upload keys must be unique" }
        return catalog.replaceMedia(id, metadata, files.mapValues { it.value.single() })
    }

    @DeleteMapping("/{id}")
    @Operation(
        operationId = "deleteGame",
        description = "Requires games:delete or an administrator personal token. Removes the catalog entry, not the game files."
    )
    @ApiResponse(responseCode = "204", description = "No content")
    fun deleteGame(@Parameter(description = "Game ID.") @PathVariable id: String): ResponseEntity<Void> {
        access.require(ApiScope.GAMES_DELETE); catalog.game(id)
        libraries.deleteGameFromLibrary(apiId(id)); games.delete(apiId(id))
        return ResponseEntity.noContent().build()
    }
}
