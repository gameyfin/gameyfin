package org.gameyfin.app.api.v1.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.validation.Valid
import org.gameyfin.app.api.security.ApiAccess
import org.gameyfin.app.api.security.ApiScope
import org.gameyfin.app.api.v1.apiId
import org.gameyfin.app.api.v1.apiPage
import org.gameyfin.app.api.v1.dto.*
import org.gameyfin.app.api.v1.service.CatalogServiceV1
import org.gameyfin.app.core.tasks.DeletionTaskService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URI

@Tag(name = "Collections", description = "Manage game collections and their members.")
@RestController
@RequestMapping("/v1/collections")
class CollectionControllerV1(
    private val access: ApiAccess, private val catalog: CatalogServiceV1,
    private val deletions: DeletionTaskService
) {
    @GetMapping
    @Operation(
        operationId = "listCollections",
        description = "Requires collections:read. sort: id, name, createdAt or updatedAt."
    )
    fun collections(
        @Parameter(description = "Zero-based page index.") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size.") @RequestParam(defaultValue = "50") size: Int,
        @Parameter(description = "Sort field, optionally followed by ,asc or ,desc. Allowed fields: id, name, createdAt, updatedAt.")
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

    @GetMapping("/{id}")
    @Operation(operationId = "getCollection", description = "Requires collections:read.")
    fun collection(@Parameter(description = "Collection ID.") @PathVariable id: String): CollectionV1 {
        access.require(ApiScope.COLLECTIONS_READ); return catalog.collection(id)
    }

    @PostMapping
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

    @PatchMapping("/{id}", consumes = ["application/merge-patch+json"])
    @Operation(
        operationId = "patchCollection",
        description = "Requires collections:write or an administrator personal token."
    )
    fun patchCollection(
        @Parameter(description = "Collection ID.") @PathVariable id: String,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(content = [Content(schema = Schema(implementation = CollectionPatchV1::class))])
        @RequestBody patch: Map<String, Any?>
    ): CollectionV1 {
        access.require(ApiScope.COLLECTIONS_WRITE); return catalog.patchCollection(id, patch)
    }

    @DeleteMapping("/{id}")
    @Operation(
        operationId = "deleteCollection",
        description = "Requires collections:delete or an administrator personal token. Does not delete member games."
    )
    @ApiResponse(responseCode = "202", description = "Accepted")
    fun deleteCollection(@Parameter(description = "Collection ID.") @PathVariable id: String): ResponseEntity<TaskRunV1> {
        access.require(ApiScope.COLLECTIONS_DELETE); catalog.collection(id)
        val run = deletions.deleteCollection(apiId(id))
        return ResponseEntity.accepted().location(URI.create("/v1/task-runs/${run.id}"))
            .body(TaskRunV1(run.id, run.taskKey, run.status.name, run.startedAt, run.finishedAt))
    }

    @GetMapping("/{id}/games")
    @Operation(operationId = "listCollectionGames", description = "Requires collections:read and games:read.")
    fun collectionGames(
        @Parameter(description = "Collection ID.") @PathVariable id: String,
        @Parameter(description = "Zero-based page index.") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size.") @RequestParam(defaultValue = "50") size: Int
    ): PageV1<GameV1> {
        access.require(ApiScope.COLLECTIONS_READ); access.require(ApiScope.GAMES_READ); catalog.collection(id)
        return catalog.games(apiPage(page, size, "id"), null, null, id, null, null, null)
    }

    @PutMapping("/{id}/games/{gameId}")
    @Operation(
        operationId = "addCollectionGame",
        description = "Requires collections:write or an administrator personal token. Idempotent."
    )
    @ApiResponse(responseCode = "204", description = "No content")
    fun addGame(
        @Parameter(description = "Collection ID.") @PathVariable id: String,
        @Parameter(description = "Game ID.") @PathVariable gameId: String
    ): ResponseEntity<Void> {
        access.require(ApiScope.COLLECTIONS_WRITE); catalog.membership(id, gameId, true)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{id}/games/{gameId}")
    @Operation(
        operationId = "removeCollectionGame",
        description = "Requires collections:write or an administrator personal token. Idempotent; does not delete the game."
    )
    @ApiResponse(responseCode = "204", description = "No content")
    fun removeGame(
        @Parameter(description = "Collection ID.") @PathVariable id: String,
        @Parameter(description = "Game ID.") @PathVariable gameId: String
    ): ResponseEntity<Void> {
        access.require(ApiScope.COLLECTIONS_WRITE); catalog.membership(id, gameId, false)
        return ResponseEntity.noContent().build()
    }
}
