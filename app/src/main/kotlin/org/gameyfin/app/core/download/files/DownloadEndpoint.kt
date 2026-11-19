package org.gameyfin.app.core.download.files

import com.vaadin.flow.server.auth.AnonymousAllowed
import jakarta.servlet.http.HttpServletRequest
import org.gameyfin.app.core.LookupPolicy
import org.gameyfin.app.core.annotations.DynamicPublicAccess
import org.gameyfin.app.core.getRemoteIp
import org.gameyfin.app.core.security.getCurrentAuth
import org.gameyfin.app.games.GameService
import org.gameyfin.pluginapi.download.FileDownload
import org.gameyfin.pluginapi.download.LinkDownload
import org.springframework.http.ContentDisposition
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.context.request.async.DeferredResult
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import tools.jackson.databind.ObjectMapper
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@RestController
@RequestMapping("/download")
@DynamicPublicAccess
@AnonymousAllowed
class DownloadEndpoint(
    private val downloadService: DownloadService,
    private val gameService: GameService,
    private val objectMapper: ObjectMapper,
) {

    private val downloadExecutor: Executor = Executors.newVirtualThreadPerTaskExecutor()

    @GetMapping("/{gameId}")
    fun downloadGame(
        @PathVariable gameId: Long,
        @RequestParam provider: String,
        request: HttpServletRequest
    ) = startDownload(gameId, provider, request, null)

    @GetMapping("/{gameId}/contents")
    fun listContents(@PathVariable gameId: Long): ResponseEntity<List<DownloadFileEntry>> = selectionRequest {
        val contents = downloadService.listContents(gameService.getDownloadFolder(gameId))
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).eTag(DownloadSelection.contentsETag(contents)).body(contents)
    }

    @PostMapping("/{gameId}/selection", consumes = ["application/x-www-form-urlencoded"])
    fun downloadSelection(
        @PathVariable gameId: Long,
        @RequestParam provider: String,
        @RequestParam(required = false) files: String?,
        @RequestParam(required = false) contentsETag: String?,
        request: HttpServletRequest
    ): DeferredResult<ResponseEntity<StreamingResponseBody>> {
        if (contentsETag != null && files == null) {
            return startDownload(gameId, provider, request) {
                downloadService.getFullSelection(gameService.getDownloadFolder(gameId), provider, contentsETag)
            }
        }
        val paths = try {
            require(files != null && contentsETag == null)
            val nodes = objectMapper.readTree(files)
            require(nodes.isArray && !nodes.isEmpty && nodes.all { it.isString }) { "Expected a list of files" }
            nodes.values().map { it.asString() }
        } catch (e: RuntimeException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file selection", e)
        }
        return startDownload(gameId, provider, request) {
            downloadService.getSelection(gameService.getDownloadFolder(gameId), provider, paths)
        }
    }

    private fun startDownload(
        gameId: Long, provider: String, request: HttpServletRequest, selection: (() -> FileDownload)?
    ): DeferredResult<ResponseEntity<StreamingResponseBody>> {
        val username = getCurrentAuth()?.name
        val deferredResult = DeferredResult<ResponseEntity<StreamingResponseBody>>()

        downloadExecutor.execute {
            try {
                val result = prepareDownload(gameId, provider, request, username, selection)
                deferredResult.onCompletion { (result.body as? java.io.Closeable)?.close() }
                if (!deferredResult.setResult(result)) (result.body as? java.io.Closeable)?.close()
            } catch (e: Exception) {
                deferredResult.setErrorResult(e)
            }
        }

        return deferredResult
    }

    private fun prepareDownload(
        gameId: Long,
        provider: String,
        request: HttpServletRequest,
        username: String?,
        selection: (() -> FileDownload)?
    ): ResponseEntity<StreamingResponseBody> =
        GameDownloadLifecycle.prepare(gameId, request, { gameService.getById(gameId) }) { game, lifetime ->
            val sessionId = request.session.id
            val remoteIp = request.getRemoteIp(LookupPolicy.IPV4_PREFERRED)

            val baseFilename = game.title?.replace("[\\\\/:*?\"<>|\r\n]".toRegex(), "")
                ?.ifBlank { "download" } ?: "download"

            fun disposition(extension: String?) = createContentDispositionHeader(
                baseFilename + (extension?.let { ".$it" } ?: "")
            )

            val onStart = { lifetime.whileActive { gameService.incrementDownloadCount(game) }; }
            val transfer = { input: java.io.InputStream, output: java.io.OutputStream ->
                downloadService.processDownload(input, output, game, username, sessionId, remoteIp)
            }
            val resource = if (selection != null) null else downloadService.getRangeDownload(game.metadata.path, provider)
                ?.also { lifetime.own(it) }
            val result = if (resource != null) {
                DownloadResponses.range(
                    resource,
                    request,
                    disposition(resource.metadata.fileExtension),
                    onStart,
                    transfer
                )
            } else if (request.method == "HEAD") {
                val metadata = downloadService.describeDownload(game.metadata.path, provider)
                DownloadResponses.head(metadata, disposition(metadata?.fileExtension))
            } else {
                val download = if (selection != null) selectionRequest(selection)
                    else downloadService.getDownload(game.metadata.path, provider)
                when (download) {
                    is FileDownload -> DownloadResponses.file(
                        download.also { lifetime.own(it.data) },
                        request,
                        download.fileName?.let { createContentDispositionHeader(it.substringAfterLast('/').substringAfterLast('\\')) }
                            ?: disposition(download.fileExtension),
                        onStart,
                        transfer
                    )

                    is LinkDownload -> {
                        val response = DownloadResponses.link(download, request)
                        onStart()
                        response
                    }
                }
            }
            if (request.getParameter("client") == "browser" && result.body != null &&
                result.headers.containsHeader(HttpHeaders.CONTENT_DISPOSITION)
            ) {
                ResponseEntity.status(result.statusCode).headers(result.headers)
                    .header("X-Frame-Options", "SAMEORIGIN").body(result.body)
            } else result
        }

    private inline fun <T> selectionRequest(action: () -> T): T = try {
        action()
    } catch (e: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Selection is unavailable", e)
    } catch (e: IOException) {
        throw ResponseStatusException(HttpStatus.CONFLICT, "Game contents have changed or are unavailable", e)
    } catch (e: UncheckedIOException) {
        throw ResponseStatusException(HttpStatus.CONFLICT, "Game contents have changed or are unavailable", e)
    }

    private fun createContentDispositionHeader(filename: String): String {
        val charset = if (filename.all { it.code in 32..126 }) StandardCharsets.US_ASCII else StandardCharsets.UTF_8
        return ContentDisposition.attachment().filename(filename, charset).build().toString()
    }
}
