package de.grimsi.gameyfin.media

import de.grimsi.gameyfin.core.Role
import de.grimsi.gameyfin.users.UserService
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile


@RestController
@RequestMapping("/images")
class ImageController(
    private val userService: UserService,
    private val imageService: ImageService
) {

    @PostMapping("/avatar/upload")
    fun uploadAvatar(@RequestParam("file") file: MultipartFile) {
        val auth: Authentication = SecurityContextHolder.getContext().authentication
        userService.setAvatar(auth.name, file)
    }

    @PostMapping("/avatar/delete")
    fun deleteAvatar() {
        val auth: Authentication = SecurityContextHolder.getContext().authentication
        userService.deleteAvatar(auth.name)
    }

    @RolesAllowed(Role.Names.ADMIN)
    @PostMapping("/avatar/deleteByName")
    fun deleteAvatarByName(@RequestParam("name") name: String) {
        userService.deleteAvatar(name)
    }

    @PermitAll
    @GetMapping("/avatar")
    fun getAvatar(
        @RequestParam("username") username: String
    ): ResponseEntity<InputStreamResource>? {
        val avatar = userService.getAvatar(username) ?: return ResponseEntity.notFound().build()

        val file = avatar.let { imageService.getFileContent(it) }

        val inputStreamResource = InputStreamResource(file)
        val headers = HttpHeaders()
        headers.contentLength = avatar.contentLength!!
        headers.contentType = MediaType.parseMediaType(avatar.mimeType!!)

        return ResponseEntity.ok()
            .headers(headers)
            .body(inputStreamResource)
    }
}