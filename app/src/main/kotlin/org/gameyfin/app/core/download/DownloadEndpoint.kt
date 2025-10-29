package org.gameyfin.app.core.download

import com.vaadin.flow.server.auth.AnonymousAllowed
import jakarta.servlet.http.HttpServletRequest
import org.gameyfin.app.core.annotations.DynamicPublicAccess
import org.gameyfin.app.core.security.getCurrentAuth
import org.gameyfin.app.games.GameService
import org.gameyfin.pluginapi.download.FileDownload
import org.gameyfin.pluginapi.download.LinkDownload
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody

@RestController
@RequestMapping("/download")
@DynamicPublicAccess
@AnonymousAllowed
class DownloadEndpoint(
    private val downloadService: DownloadService,
    private val gameService: GameService
) {
    @GetMapping("/{gameId}")
    fun downloadGame(
        @PathVariable gameId: Long,
        @RequestParam provider: String,
        request: HttpServletRequest
    ): ResponseEntity<StreamingResponseBody> {
        val game = gameService.getById(gameId)
        gameService.incrementDownloadCount(game)
        val sessionId = request.session.id

        return when (val download = downloadService.getDownload(game.metadata.path, provider)) {
            is FileDownload -> {
                val responseBuilder = ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"${game.title}.${download.fileExtension}\"")

                // Add Content-Length header if file size is available for download progress/ETA
                val fileSize = game.metadata.fileSize
                if (fileSize != null && fileSize > 0) {
                    responseBuilder.header("Content-Length", fileSize.toString())
                }

                responseBuilder.body(StreamingResponseBody { outputStream ->
                    downloadService.processDownload(
                        download.data,
                        outputStream,
                        game,
                        getCurrentAuth()?.name,
                        sessionId
                    )
                })
            }

            is LinkDownload -> {
                TODO("Handle download link")
            }
        }
    }
}