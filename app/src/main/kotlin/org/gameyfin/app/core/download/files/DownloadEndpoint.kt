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
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.context.request.async.DeferredResult
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.net.URLEncoder
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
) {

    private val downloadExecutor: Executor = Executors.newVirtualThreadPerTaskExecutor()

    @GetMapping("/{gameId}")
    fun downloadGame(
        @PathVariable gameId: Long,
        @RequestParam provider: String,
        request: HttpServletRequest
    ): DeferredResult<ResponseEntity<StreamingResponseBody>> {
        val username = getCurrentAuth()?.name
        val deferredResult = DeferredResult<ResponseEntity<StreamingResponseBody>>()

        downloadExecutor.execute {
            try {
                val game = gameService.getById(gameId)
                val sessionId = request.session.id
                val remoteIp = request.getRemoteIp(LookupPolicy.IPV4_PREFERRED)

                val baseFilename = game.title?.replace("[\\\\/:*?\"<>|\r\n]".toRegex(), "")
                    ?.ifBlank { "download" } ?: "download"

                fun disposition(extension: String?) = createContentDispositionHeader(
                    baseFilename + (extension?.let { ".$it" } ?: "")
                )

                val onStart = { gameService.incrementDownloadCount(game); }
                val transfer = { input: java.io.InputStream, output: java.io.OutputStream ->
                    downloadService.processDownload(input, output, game, username, sessionId, remoteIp)
                }
                val resource = downloadService.getRangeDownload(game.metadata.path, provider)
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
                } else when (val download = downloadService.getDownload(game.metadata.path, provider)) {
                    is FileDownload -> DownloadResponses.file(
                        download,
                        request,
                        disposition(download.fileExtension),
                        onStart,
                        transfer
                    )

                    is LinkDownload -> TODO("Handle download link")
                }
                deferredResult.onCompletion { (result.body as? java.io.Closeable)?.close() }
                if (!deferredResult.setResult(result)) (result.body as? java.io.Closeable)?.close()
            } catch (e: Exception) {
                deferredResult.setErrorResult(e)
            }
        }

        return deferredResult
    }

    /**
     * Converts a string to a safe ASCII fallback filename by replacing non-ASCII characters.
     * Characters with code points > 127 and common invalid chars for filenames are removed, and if the result is empty or only whitespace,
     * returns "download" as a fallback.
     */
    private fun String.safeDownloadFileName(): String {
        val asciiOnly = filter { it.code in 0..255 } // Printable ASCII only
            .trim()

        return asciiOnly.ifBlank { "download" }
    }

    /**
     * URL-encodes a string according to RFC 5987.
     */
    private fun String.encodeRfc5987(): String {
        return URLEncoder.encode(this, StandardCharsets.UTF_8)
            .replace("+", "%20") // URLEncoder uses + for space, but RFC 5987 requires %20
    }

    /**
     * Creates a Content-Disposition header value with both ASCII fallback and RFC 5987 Unicode support.
     *
     * Example output:
     *   attachment; filename="Game_Title.zip"; filename*=UTF-8''Game%E2%84%A2%20Title.zip
     *
     * @param filename The original filename (may contain Unicode characters)
     * @param disposition The disposition type (default: "attachment")
     * @return A properly formatted Content-Disposition header value
     */
    private fun createContentDispositionHeader(filename: String, disposition: String = "attachment"): String {
        val asciiFallback = filename.safeDownloadFileName()
        val encodedFilename = filename.encodeRfc5987()

        return buildString {
            append(disposition)
            append("; filename=\"")
            append(asciiFallback)
            append("\"")
            // Only add filename* if there are non-ASCII characters
            if (filename != asciiFallback) {
                append("; filename*=utf-8''")
                append(encodedFilename)
            }
        }
    }
}
