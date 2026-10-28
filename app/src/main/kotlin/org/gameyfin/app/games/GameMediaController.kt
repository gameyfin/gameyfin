package org.gameyfin.app.games

import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.core.Role
import org.gameyfin.app.core.security.isCurrentUserAdmin
import org.gameyfin.app.games.dto.GameUpdateDto
import org.gameyfin.app.libraries.LibraryCoreService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartHttpServletRequest
import org.springframework.web.server.ResponseStatusException

/** Files stay in the browser until the complete metadata form is submitted. */
@RestController
@RequestMapping("/game-media")
class GameMediaController(private val gameService: GameService, private val libraryCoreService: LibraryCoreService) {
    @RolesAllowed(Role.Names.ADMIN, Role.Names.SUPERADMIN)
    @PostMapping("/import", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional(rollbackFor = [Exception::class])
    fun importGame(
        @RequestPart("metadata") metadata: GameUpdateDto,
        @RequestParam path: String,
        @RequestParam libraryId: Long,
        request: MultipartHttpServletRequest
    ) {
        if (!isCurrentUserAdmin()) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access required")
        try {
            val library = libraryCoreService.getWithGamesAndIgnoredPaths(libraryId)
                ?: throw IllegalArgumentException("Library with id $libraryId not found")
            val game = gameService.createManually(metadata.title.orEmpty(), path, library)
            save(metadata.copy(id = game.id!!), request)
            libraryCoreService.addGamesToLibrary(listOf(game), library, true)
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message, e)
        }
    }

    @RolesAllowed(Role.Names.ADMIN, Role.Names.SUPERADMIN)
    @PostMapping("/metadata", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun save(@RequestPart("metadata") metadata: GameUpdateDto, request: MultipartHttpServletRequest) {
        if (!isCurrentUserAdmin()) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access required")
        val files = request.multiFileMap.filterKeys { it != "metadata" }
        if (files.values.any { it.size != 1 }) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload keys must be unique")
        }
        try {
            gameService.edit(metadata, files.mapValues { it.value.single() })
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message, e)
        }
    }
}
