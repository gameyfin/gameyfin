package org.gameyfin.app.api.v1.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import org.gameyfin.app.api.security.ApiAccess
import org.gameyfin.app.api.security.ApiScope
import org.gameyfin.app.api.v1.apiId
import org.gameyfin.app.api.v1.dto.DownloadLinkV1
import org.gameyfin.app.api.v1.dto.DownloadProviderV1
import org.gameyfin.app.api.v1.service.CatalogServiceV1
import org.gameyfin.app.core.LookupPolicy
import org.gameyfin.app.core.download.files.DownloadService
import org.gameyfin.app.core.getRemoteIp
import org.gameyfin.app.games.GameService
import org.gameyfin.app.media.ImageService
import org.gameyfin.pluginapi.download.FileDownload
import org.gameyfin.pluginapi.download.LinkDownload
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.*
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.nio.charset.StandardCharsets
import java.time.Duration

@Tag(name = "Downloads", description = "Download providers, game downloads and game images.")
@RestController
@RequestMapping("/v1")
class DownloadControllerV1(
    private val access: ApiAccess, private val downloads: DownloadService,
    private val catalog: CatalogServiceV1, private val games: GameService, private val images: ImageService
) {

    @GetMapping("/download-providers")
    @Operation(
        operationId = "listDownloadProviders",
        description = "Requires downloads:read. Provider IDs are opaque; discover them rather than constructing or hard-coding them."
    )
    fun providers(): List<DownloadProviderV1> {
        access.require(ApiScope.DOWNLOADS_READ)
        return downloads.getProviders().sortedByDescending { it.priority }
            .map { DownloadProviderV1(it.key, it.name, it.description, it.priority) }
    }

    @GetMapping("/games/{id}/download")
    @Operation(
        operationId = "downloadGame",
        description = "Requires downloads:read. Streams a file or returns a JSON link descriptor. Follow links explicitly without forwarding X-API-Key. Range/resume is not supported; requests receive the complete stream.",
        responses = [ApiResponse(
            responseCode = "200",
            content = [Content(
                mediaType = "application/octet-stream",
                schema = Schema(type = "string", format = "binary")
            ), Content(mediaType = "application/json", schema = Schema(implementation = DownloadLinkV1::class))]
        )]
    )
    fun download(
        @Parameter(description = "Game ID.") @PathVariable id: String,
        @Parameter(description = "Download provider key, as returned by GET /v1/download-providers.")
        @RequestParam provider: String,
        request: HttpServletRequest
    ): ResponseEntity<*> {
        val actor = access.require(ApiScope.DOWNLOADS_READ)
        val game = catalog.gameEntity(id)
        require(downloads.getProviders().any { it.key == provider }) { "Unknown download provider" }
        val ip = request.getRemoteIp(LookupPolicy.IPV4_PREFERRED)
        return when (val download = downloads.getDownload(game.metadata.path, provider)) {
            is FileDownload -> {
                try {
                    games.incrementDownloadCount(game)
                    val safeTitle =
                        game.title.orEmpty().replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "").ifBlank { "download" }
                    val filename = safeTitle + (download.fileExtension?.let { ".$it" } ?: "")
                    val response = ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .cacheControl(CacheControl.noStore())
                        .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build()
                                .toString()
                        )
                    download.size?.let { response.contentLength(it) }
                    response.body(StreamingResponseBody { output ->
                        downloads.processDownload(
                            download.data,
                            output,
                            game,
                            actor.name,
                            "api:${actor.credentialId}",
                            ip
                        )
                    })
                } catch (error: Exception) {
                    download.data.close(); throw error
                }
            }

            is LinkDownload -> {
                games.incrementDownloadCount(game)
                ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_JSON)
                    .body(DownloadLinkV1(url = download.url))
            }
        }
    }

    @GetMapping("/games/{id}/images/{imageId}")
    @Transactional(readOnly = true)
    @Operation(
        operationId = "getGameImage",
        description = "Requires games:read. Supports ETag conditional requests. The image must belong to the specified game."
    )
    @ApiResponse(
        responseCode = "200", description = "OK",
        content = [Content(mediaType = "image/*", schema = Schema(type = "string", format = "binary"))]
    )
    @ApiResponse(responseCode = "304", description = "Not modified", content = [Content()])
    fun image(
        @Parameter(description = "Game ID.") @PathVariable id: String,
        @Parameter(description = "ID of a cover, header or gallery image of the game.") @PathVariable imageId: String,
        request: WebRequest
    ): ResponseEntity<Resource> {
        access.require(ApiScope.GAMES_READ)
        val game = catalog.gameEntity(id)
        val image =
            (listOfNotNull(game.coverImage, game.headerImage) + game.images).firstOrNull { it.id == apiId(imageId) }
                ?: throw NoSuchElementException("Game image not found")
        val path = images.getFilePath(image) ?: throw NoSuchElementException("Image content not found")
        val etag = image.contentId?.let { "\"$it\"" }
        val cache = CacheControl.maxAge(Duration.ofDays(7)).cachePrivate().mustRevalidate()
        if (etag != null && request.checkNotModified(etag)) return ResponseEntity.status(304).eTag(etag)
            .cacheControl(cache).build()
        val response = ResponseEntity.ok().cacheControl(cache)
            .header(HttpHeaders.VARY, "X-API-Key")
            .header("X-Content-Type-Options", "nosniff")
        etag?.let { response.eTag(it) }
        image.mimeType?.let { response.contentType(MediaType.parseMediaType(it)) }
        image.contentLength?.let { response.contentLength(it) }
        return response.body(FileSystemResource(path))
    }
}
