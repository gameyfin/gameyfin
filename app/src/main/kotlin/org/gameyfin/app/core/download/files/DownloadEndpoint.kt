package org.gameyfin.app.core.download.files

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
        val remoteIp = getPreferredRemoteIp(request)

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
                        sessionId,
                        remoteIp
                    )
                })
            }

            is LinkDownload -> {
                TODO("Handle download link")
            }
        }
    }

    /**
     * Get the remote IP address, preferring IPv4 over IPv6.
     * Checks X-Forwarded-For header first (for proxied requests), then falls back to remoteAddr.
     */
    private fun getPreferredRemoteIp(request: HttpServletRequest): String {
        val candidateIps = mutableListOf<String>()

        // Check X-Forwarded-For header (for requests behind proxies/load balancers)
        request.getHeader("X-Forwarded-For")?.let { forwardedFor ->
            // X-Forwarded-For can contain multiple IPs: "client, proxy1, proxy2"
            candidateIps.addAll(forwardedFor.split(",").map { it.trim() })
        }

        // Add the direct remote address
        request.remoteAddr?.let { candidateIps.add(it) }

        // Filter and separate IPv4 and IPv6 addresses
        val ipv4Addresses = candidateIps.filter { isIpv4(it) }
        val ipv6Addresses = candidateIps.filter { isIpv6(it) }

        // Prefer IPv4, fall back to IPv6, or return "unknown"
        return ipv4Addresses.firstOrNull() ?: ipv6Addresses.firstOrNull() ?: "unknown"
    }

    /**
     * Check if an IP address is IPv4 format
     */
    private fun isIpv4(ip: String): Boolean {
        return ip.matches(Regex("""^(\d{1,3}\.){3}\d{1,3}$"""))
    }

    /**
     * Check if an IP address is IPv6 format
     */
    private fun isIpv6(ip: String): Boolean {
        return ip.contains(":")
    }
}