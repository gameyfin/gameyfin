package de.grimsi.gameyfin.media

import de.grimsi.gameyfin.core.Role
import de.grimsi.gameyfin.core.Utils
import de.grimsi.gameyfin.core.annotations.DynamicPublicAccess
import de.grimsi.gameyfin.core.plugins.management.PluginManagementService
import de.grimsi.gameyfin.games.entities.Image
import de.grimsi.gameyfin.games.entities.ImageType
import de.grimsi.gameyfin.users.UserService
import jakarta.annotation.security.RolesAllowed
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@DynamicPublicAccess
@RestController
@RequestMapping("/images")
class ImageEndpoint(
    private val imageService: ImageService,
    private val userService: UserService,
    private val pluginManagementService: PluginManagementService
) {

    @GetMapping("/screenshot/{id}")
    fun getScreenshot(@PathVariable("id") id: Long): ResponseEntity<InputStreamResource>? {
        return getImageContent(id)
    }

    @GetMapping("/cover/{id}")
    fun getCover(@PathVariable("id") id: Long): ResponseEntity<InputStreamResource>? {
        return getImageContent(id)
    }

    @GetMapping("/plugins/{id}/logo")
    fun getPluginLogo(@PathVariable("id") pluginId: String): ResponseEntity<ByteArrayResource>? {
        val logo = pluginManagementService.getLogo(pluginId)
        return Utils.inputStreamToResponseEntity(logo)
    }

    @GetMapping("/avatar")
    fun getAvatarByUsername(@RequestParam username: String): ResponseEntity<InputStreamResource>? {
        val avatar = userService.getAvatar(username) ?: return ResponseEntity.notFound().build()
        if (avatar.id == null) return ResponseEntity.notFound().build()
        return getImageContent(avatar.id!!)
    }

    @PostMapping("/avatar/upload")
    fun uploadAvatar(@RequestParam("file") file: MultipartFile) {
        val auth: Authentication = SecurityContextHolder.getContext().authentication

        val image: Image = if (!userService.hasAvatar(auth.name)) {
            imageService.createFile(ImageType.AVATAR, file.inputStream, file.contentType!!)
        } else {
            val existingAvatar = userService.getAvatar(auth.name)!!
            imageService.updateFileContent(existingAvatar, file.inputStream, file.contentType!!)
        }

        userService.updateAvatar(auth.name, image)
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

    private fun getImageContent(id: Long): ResponseEntity<InputStreamResource>? {
        val image = imageService.getImage(id) ?: return ResponseEntity.notFound().build()

        val file = image.let { imageService.getFileContent(it) }

        if (file == null) return ResponseEntity.notFound().build()

        val inputStreamResource = InputStreamResource(file)

        val headers = HttpHeaders()
        image.contentLength?.let { headers.contentLength = it }
        image.mimeType?.let { headers.contentType = MediaType.parseMediaType(it) }

        return ResponseEntity.ok()
            .headers(headers)
            .body(inputStreamResource)
    }
}