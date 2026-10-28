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
import org.gameyfin.app.libraries.LibraryScanService
import org.gameyfin.app.libraries.enums.ScanType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URI

@Tag(name = "Libraries", description = "Manage libraries, their configuration and scans.")
@RestController
@RequestMapping("/v1/libraries")
class LibraryControllerV1(
    private val access: ApiAccess, private val catalog: CatalogServiceV1,
    private val scans: LibraryScanService, private val deletions: DeletionTaskService
) {
    @GetMapping
    @Operation(
        operationId = "listLibraries",
        description = "Requires libraries:read. sort: id, name, createdAt or updatedAt."
    )
    fun libraries(
        @Parameter(description = "Zero-based page index.") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size.") @RequestParam(defaultValue = "50") size: Int,
        @Parameter(description = "Sort field, optionally followed by ,asc or ,desc. Allowed fields: id, name, createdAt, updatedAt.")
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

    @GetMapping("/{id}")
    @Operation(
        operationId = "getLibrary",
        description = "Requires libraries:read. Does not include server filesystem paths."
    )
    fun library(@Parameter(description = "Library ID.") @PathVariable id: String): LibraryV1 {
        access.require(ApiScope.LIBRARIES_READ); return catalog.library(id)
    }

    @GetMapping("/{id}/configuration")
    @Operation(
        operationId = "getLibraryConfiguration",
        description = "Requires libraries:write or an administrator personal token. Includes server directory mappings."
    )
    fun configuration(@Parameter(description = "Library ID.") @PathVariable id: String): LibraryConfigurationV1 {
        access.require(ApiScope.LIBRARIES_WRITE); return catalog.configuration(id)
    }

    @PostMapping
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

    @PatchMapping("/{id}", consumes = ["application/merge-patch+json"])
    @Operation(
        operationId = "patchLibrary",
        description = "Requires libraries:write or an administrator personal token. Removing directory mappings also requires games:delete because it removes catalog entries."
    )
    fun patchLibrary(
        @Parameter(description = "Library ID.") @PathVariable id: String,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(content = [Content(schema = Schema(implementation = LibraryPatchV1::class))])
        @RequestBody patch: Map<String, Any?>
    ): LibraryV1 {
        access.require(ApiScope.LIBRARIES_WRITE); return catalog.patchLibrary(id, patch)
    }

    @DeleteMapping("/{id}")
    @Operation(
        operationId = "deleteLibrary",
        description = "Requires libraries:delete or an administrator personal token. Queues catalog deletion, including this library's games; does not delete game files."
    )
    @ApiResponse(responseCode = "202", description = "Accepted")
    fun deleteLibrary(@Parameter(description = "Library ID.") @PathVariable id: String): ResponseEntity<TaskRunV1> {
        access.require(ApiScope.LIBRARIES_DELETE); catalog.library(id)
        val run = deletions.deleteLibrary(apiId(id))
        return ResponseEntity.accepted().location(URI.create("/v1/task-runs/${run.id}"))
            .body(TaskRunV1(run.id, run.taskKey, run.status.name, run.startedAt, run.finishedAt))
    }

    @PostMapping("/{id}/scans")
    @Operation(
        operationId = "scanLibrary",
        description = "Requires libraries:scan or an administrator personal token. Acknowledges queue acceptance; task runs are created when the queue processes the scan."
    )
    @ApiResponse(responseCode = "202", description = "Accepted")
    fun scan(@Parameter(description = "Library ID.") @PathVariable id: String, @RequestBody input: ScanV1): ResponseEntity<AcceptedScanV1> {
        access.require(ApiScope.LIBRARIES_SCAN); catalog.library(id)
        require(input.type in setOf("QUICK", "FULL")) { "Scan type must be QUICK or FULL" }
        scans.triggerScan(ScanType.valueOf(input.type), listOf(apiId(id)))
        return ResponseEntity.accepted().body(AcceptedScanV1(id, input.type))
    }
}
