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
        val deferredResult = DeferredResult<ResponseEntity<StreamingResponseBody>>()

        downloadExecutor.execute {
            try {
                val game = gameService.getById(gameId)
                gameService.incrementDownloadCount(game)
                val sessionId = request.session.id
                val remoteIp = request.getRemoteIp(LookupPolicy.IPV4_PREFERRED)

                val result = when (val download = downloadService.getDownload(game.metadata.path, provider)) {
                    is FileDownload -> {
                        val responseBuilder = ResponseEntity.ok()
                            .header(
                                "Content-Disposition",
                                "attachment; filename=\"${game.title}.${download.fileExtension}\""
                            )

                        responseBuilder.body(StreamingResponseBody { outputStream ->
                            downloadService.processDownload(
                                download.data,
                                outputStream,
                                game,
                                getCurrentAuth()?.name,
                                sessionId,
                                remoteIp
                            )
                        })
                    }

                    is LinkDownload -> {
                        TODO("Handle download link")
                    }
                }

                deferredResult.setResult(result)
            } catch (e: Exception) {
                deferredResult.setErrorResult(e)
            }
        }

        return deferredResult
    }
}