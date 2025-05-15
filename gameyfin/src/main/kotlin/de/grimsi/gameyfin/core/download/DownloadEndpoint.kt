package de.grimsi.gameyfin.core.download

import de.grimsi.gameyfin.core.annotations.DynamicPublicAccess
import de.grimsi.gameyfin.games.GameService
import de.grimsi.gameyfin.pluginapi.download.FileDownload
import de.grimsi.gameyfin.pluginapi.download.LinkDownload
import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/download")
@DynamicPublicAccess
class DownloadEndpoint(
    private val downloadService: DownloadService,
    private val gameService: GameService
) {
    fun getProviders(): List<String> {
        return downloadService.getProviders()
    }

    @GetMapping("/{gameId}")
    fun downloadGame(@PathVariable gameId: Long, @RequestParam provider: String): ResponseEntity<Resource> {
        val game = gameService.getGame(gameId)
        val downloadElement = downloadService.getDownloadElement(game.path, provider)

        return when (downloadElement) {
            is FileDownload -> {
                val resource = InputStreamResource(downloadElement.data)
                ResponseEntity.ok()
                    .header(
                        "Content-Disposition",
                        "attachment; filename=\"${game.title}.${downloadElement.fileExtension}\""
                    )
                    .body(resource)
            }

            is LinkDownload -> {
                TODO("Handle download link")
            }
        }
    }
}