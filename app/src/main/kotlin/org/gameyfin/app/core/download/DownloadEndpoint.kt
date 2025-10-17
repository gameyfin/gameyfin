package org.gameyfin.app.core.download

import com.vaadin.flow.server.auth.AnonymousAllowed
import org.gameyfin.app.core.annotations.DynamicPublicAccess
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
        @RequestParam provider: String
    ): ResponseEntity<StreamingResponseBody> {
        val game = gameService.getById(gameId)
        gameService.incrementDownloadCount(game)
        return when (val download = downloadService.getDownload(game.metadata.path, provider)) {
            is FileDownload -> {
                ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"${game.title}.${download.fileExtension}\"")
                    .body(StreamingResponseBody { outputStream ->
                        downloadService.processDownload(outputStream, download.data)
                    })
            }

            is LinkDownload -> {
                TODO("Handle download link")
            }
        }
    }
}