package de.grimsi.gameyfin.core.download

import de.grimsi.gameyfin.core.annotations.DynamicPublicAccess
import de.grimsi.gameyfin.games.GameService
import de.grimsi.gameyfin.pluginapi.download.FileDownload
import de.grimsi.gameyfin.pluginapi.download.LinkDownload
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody

@RestController
@RequestMapping("/download")
@DynamicPublicAccess
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
        val download = downloadService.getDownload(game.path, provider)

        return when (download) {
            is FileDownload -> {
                ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"${game.title}.${download.fileExtension}\"")
                    .body(StreamingResponseBody { outputStream ->
                        download.data.copyTo(outputStream)
                    })
            }

            is LinkDownload -> {
                TODO("Handle download link")
            }
        }
    }
}