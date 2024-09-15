package de.grimsi.gameyfin.users.avatar

import de.grimsi.gameyfin.meta.Roles
import de.grimsi.gameyfin.users.UserService
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile


@RestController
class AvatarController(
    private val userService: UserService
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

    @RolesAllowed(Roles.Names.SUPERADMIN, Roles.Names.ADMIN)
    @PostMapping("/avatar/deleteByName")
    fun deleteAvatarByName(@RequestParam("name") name: String) {
        userService.deleteAvatar(name)
    }

    @PermitAll
    @GetMapping("/images/avatar")
    fun getAvatar(
        @RequestParam("username") username: String,
        response: HttpServletResponse
    ): ResponseEntity<InputStreamResource>? {
        val avatar = userService.getAvatar(username) ?: return ResponseEntity.notFound().build()

        val file = avatar.let { userService.getAvatarFile(it) }

        val inputStreamResource = InputStreamResource(file)
        val headers = HttpHeaders()
        headers.contentLength = avatar.contentLength!!
        headers.contentType = MediaType.parseMediaType(avatar.mimeType!!)

        return ResponseEntity.ok()
            .headers(headers)
            .body(inputStreamResource)
    }
}