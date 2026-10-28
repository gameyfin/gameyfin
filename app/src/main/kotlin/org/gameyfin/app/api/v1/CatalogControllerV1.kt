package org.gameyfin.app.api.v1

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Encoding
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.validation.Valid
import org.gameyfin.app.api.security.ApiAccess
import org.gameyfin.app.api.security.ApiScope
import org.gameyfin.app.api.v1.dto.*
import org.gameyfin.app.core.tasks.DeletionTaskService
import org.gameyfin.app.games.GameService
import org.gameyfin.app.libraries.LibraryCoreService
import org.gameyfin.app.libraries.LibraryScanService
import org.gameyfin.app.libraries.enums.ScanType
import org.gameyfin.app.platforms.PlatformService
import org.gameyfin.pluginapi.gamemetadata.GameFeature
import org.gameyfin.pluginapi.gamemetadata.Genre
import org.gameyfin.pluginapi.gamemetadata.PlayerPerspective
import org.gameyfin.pluginapi.gamemetadata.Theme
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartHttpServletRequest
import java.net.URI

@RestController
@RequestMapping("/v1")
class CatalogControllerV1(
    private val access: ApiAccess, private val catalog: CatalogServiceV1,
    private val games: GameService, private val libraries: LibraryCoreService,
    private val scans: LibraryScanService, private val deletions: DeletionTaskService,
    private val platforms: PlatformService
) {
    @GetMapping("/games")
    @Operation(
        operationId = "listGames",
        description = "Requires games:read. Filter by externalProvider and externalId together. sort: id, title, createdAt or updatedAt, optionally followed by ,asc or ,desc."
    )
    fun games(
        @RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(defaultValue = "id") sort: String, @RequestParam(required = false) q: String?,
        @RequestParam(required = false) libraryId: String?, @RequestParam(required = false) collectionId: String?,
        @RequestParam(required = false) platform: String?, @RequestParam(required = false) externalProvider: String?,
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

    @GetMapping("/games/{id}")
    @Operation(operationId = "getGame", description = "Requires games:read.")
    fun game(@PathVariable id: String): GameV1 {
        access.require(ApiScope.GAMES_READ); return catalog.game(id)
    }

    @PatchMapping("/games/{id}", consumes = ["application/merge-patch+json"])
    @Operation(operationId = "patchGame", description = "Requires games:write or an administrator personal token.")
    fun patchGame(
        @PathVariable id: String,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(content = [Content(schema = Schema(implementation = GamePatchV1::class))])
        @RequestBody patch: Map<String, Any?>
    ): GameV1 {
        access.require(ApiScope.GAMES_WRITE); return catalog.patchGame(id, patch)
    }

    @PutMapping("/games/{id}/media", consumes = ["multipart/form-data"])
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
        @PathVariable id: String,
        @RequestPart metadata: GameMediaV1,
        request: MultipartHttpServletRequest
    ): GameV1 {
        access.require(ApiScope.GAMES_WRITE)
        val files = request.multiFileMap.filterKeys { it != "metadata" }
        require(files.values.all { it.size == 1 }) { "Upload keys must be unique" }
        return catalog.replaceMedia(id, metadata, files.mapValues { it.value.single() })
    }

    @DeleteMapping("/games/{id}")
    @Operation(
        operationId = "deleteGame",
        description = "Requires games:delete or an administrator personal token. Removes the catalog entry, not the game files."
    )
    @ApiResponse(responseCode = "204", description = "No content")
    fun deleteGame(@PathVariable id: String): ResponseEntity<Void> {
        access.require(ApiScope.GAMES_DELETE); catalog.game(id)
        libraries.deleteGameFromLibrary(apiId(id)); games.delete(apiId(id))
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/libraries")
    @Operation(
        operationId = "listLibraries",
        description = "Requires libraries:read. sort: id, name, createdAt or updatedAt."
    )
    fun libraries(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(defaultValue = "id") sort: String
    ): PageV1<LibraryV1> {
        access.require(ApiScope.LIBRARIES_READ); return catalog.libraries(
            apiPage(
                page,
                size,
                sort,
                setOf("id", "name", "createdAt", "updatedAt")
            )
        )
    }

    @GetMapping("/libraries/{id}")
    @Operation(
        operationId = "getLibrary",
        description = "Requires libraries:read. Does not include server filesystem paths."
    )
    fun library(@PathVariable id: String): LibraryV1 {
        access.require(ApiScope.LIBRARIES_READ); return catalog.library(id)
    }

    @GetMapping("/libraries/{id}/configuration")
    @Operation(
        operationId = "getLibraryConfiguration",
        description = "Requires libraries:write or an administrator personal token. Includes server directory mappings."
    )
    fun configuration(@PathVariable id: String): LibraryConfigurationV1 {
        access.require(ApiScope.LIBRARIES_WRITE); return catalog.configuration(id)
    }

    @PostMapping("/libraries")
    @Operation(
        operationId = "createLibrary",
        description = "Requires libraries:write or an administrator personal token. scanAfterCreation also requires libraries:scan."
    )
    @ApiResponse(responseCode = "201", description = "Created")
    fun createLibrary(@Valid @RequestBody input: CreateLibraryV1): ResponseEntity<LibraryV1> {
        access.require(ApiScope.LIBRARIES_WRITE)
        if (input.scanAfterCreation) access.require(ApiScope.LIBRARIES_SCAN)
        val library = catalog.createLibrary(input)
        // The catalog transaction has committed before the asynchronous scan can start.
        if (input.scanAfterCreation) scans.triggerScan(ScanType.QUICK, listOf(apiId(library.id)))
        return ResponseEntity.created(URI.create("/v1/libraries/${library.id}")).body(library)
    }

    @PatchMapping("/libraries/{id}", consumes = ["application/merge-patch+json"])
    @Operation(
        operationId = "patchLibrary",
        description = "Requires libraries:write or an administrator personal token. Removing directory mappings also requires games:delete because it removes catalog entries."
    )
    fun patchLibrary(
        @PathVariable id: String,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(content = [Content(schema = Schema(implementation = LibraryPatchV1::class))])
        @RequestBody patch: Map<String, Any?>
    ): LibraryV1 {
        access.require(ApiScope.LIBRARIES_WRITE); return catalog.patchLibrary(id, patch)
    }

    @DeleteMapping("/libraries/{id}")
    @Operation(
        operationId = "deleteLibrary",
        description = "Requires libraries:delete or an administrator personal token. Queues catalog deletion, including this library's games; does not delete game files."
    )
    @ApiResponse(responseCode = "202", description = "Accepted")
    fun deleteLibrary(@PathVariable id: String): ResponseEntity<TaskRunV1> {
        access.require(ApiScope.LIBRARIES_DELETE); catalog.library(id)
        val run = deletions.deleteLibrary(apiId(id))
        return ResponseEntity.accepted().location(URI.create("/v1/task-runs/${run.id}"))
            .body(TaskRunV1(run.id, run.taskKey, run.status.name, run.startedAt, run.finishedAt))
    }

    @PostMapping("/libraries/{id}/scans")
    @Operation(
        operationId = "scanLibrary",
        description = "Requires libraries:scan or an administrator personal token. Acknowledges queue acceptance; task runs are created when the queue processes the scan."
    )
    @ApiResponse(responseCode = "202", description = "Accepted")
    fun scan(@PathVariable id: String, @RequestBody input: ScanV1): ResponseEntity<AcceptedScanV1> {
        access.require(ApiScope.LIBRARIES_SCAN); catalog.library(id)
        require(input.type in setOf("QUICK", "FULL")) { "Scan type must be QUICK or FULL" }
        scans.triggerScan(ScanType.valueOf(input.type), listOf(apiId(id)))
        return ResponseEntity.accepted().body(AcceptedScanV1(id, input.type))
    }

    @GetMapping("/collections")
    @Operation(
        operationId = "listCollections",
        description = "Requires collections:read. sort: id, name, createdAt or updatedAt."
    )
    fun collections(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(defaultValue = "id") sort: String
    ): PageV1<CollectionV1> {
        access.require(ApiScope.COLLECTIONS_READ); return catalog.collections(
            apiPage(
                page,
                size,
                sort,
                setOf("id", "name", "createdAt", "updatedAt")
            )
        )
    }

    @GetMapping("/collections/{id}")
    @Operation(operationId = "getCollection", description = "Requires collections:read.")
    fun collection(@PathVariable id: String): CollectionV1 {
        access.require(ApiScope.COLLECTIONS_READ); return catalog.collection(id)
    }

    @PostMapping("/collections")
    @Operation(
        operationId = "createCollection",
        description = "Requires collections:write or an administrator personal token."
    )
    @ApiResponse(responseCode = "201", description = "Created")
    fun createCollection(@Valid @RequestBody input: CreateCollectionV1): ResponseEntity<CollectionV1> {
        access.require(ApiScope.COLLECTIONS_WRITE)
        val collection = catalog.createCollection(input)
        return ResponseEntity.created(URI.create("/v1/collections/${collection.id}")).body(collection)
    }

    @PatchMapping("/collections/{id}", consumes = ["application/merge-patch+json"])
    @Operation(
        operationId = "patchCollection",
        description = "Requires collections:write or an administrator personal token."
    )
    fun patchCollection(
        @PathVariable id: String,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(content = [Content(schema = Schema(implementation = CollectionPatchV1::class))])
        @RequestBody patch: Map<String, Any?>
    ): CollectionV1 {
        access.require(ApiScope.COLLECTIONS_WRITE); return catalog.patchCollection(id, patch)
    }


    @DeleteMapping("/collections/{id}")
    @Operation(
        operationId = "deleteCollection",
        description = "Requires collections:delete or an administrator personal token. Does not delete member games."
    )
    @ApiResponse(responseCode = "202", description = "Accepted")
    fun deleteCollection(@PathVariable id: String): ResponseEntity<TaskRunV1> {
        access.require(ApiScope.COLLECTIONS_DELETE); catalog.collection(id)
        val run = deletions.deleteCollection(apiId(id))
        return ResponseEntity.accepted().location(URI.create("/v1/task-runs/${run.id}"))
            .body(TaskRunV1(run.id, run.taskKey, run.status.name, run.startedAt, run.finishedAt))
    }

    @GetMapping("/collections/{id}/games")
    @Operation(operationId = "listCollectionGames", description = "Requires collections:read and games:read.")
    fun collectionGames(
        @PathVariable id: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int
    ): PageV1<GameV1> {
        access.require(ApiScope.COLLECTIONS_READ); access.require(ApiScope.GAMES_READ); catalog.collection(id)
        return catalog.games(apiPage(page, size, "id"), null, null, id, null, null, null)
    }

    @PutMapping("/collections/{id}/games/{gameId}")
    @Operation(
        operationId = "addCollectionGame",
        description = "Requires collections:write or an administrator personal token. Idempotent."
    )
    @ApiResponse(responseCode = "204", description = "No content")
    fun addGame(@PathVariable id: String, @PathVariable gameId: String): ResponseEntity<Void> {
        access.require(ApiScope.COLLECTIONS_WRITE); catalog.membership(
            id,
            gameId,
            true
        ); return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/collections/{id}/games/{gameId}")
    @Operation(
        operationId = "removeCollectionGame",
        description = "Requires collections:write or an administrator personal token. Idempotent; does not delete the game."
    )
    @ApiResponse(responseCode = "204", description = "No content")
    fun removeGame(@PathVariable id: String, @PathVariable gameId: String): ResponseEntity<Void> {
        access.require(ApiScope.COLLECTIONS_WRITE); catalog.membership(
            id,
            gameId,
            false
        ); return ResponseEntity.noContent().build()
    }

    @GetMapping("/platforms")
    @Operation(
        operationId = "listPlatforms",
        description = "Requires games:read. Returns platforms supported by active metadata providers."
    )
    fun platforms(): List<OptionV1> {
        access.require(ApiScope.GAMES_READ); return platforms.availablePlatforms.map {
            OptionV1(
                it.name,
                it.displayName
            )
        }.sortedBy { it.code }
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
