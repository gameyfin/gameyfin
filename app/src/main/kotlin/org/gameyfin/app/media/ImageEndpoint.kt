package org.gameyfin.app.media

import com.vaadin.flow.server.auth.AnonymousAllowed
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.core.Role
import org.gameyfin.app.core.Utils
import org.gameyfin.app.core.annotations.DynamicPublicAccess
import org.gameyfin.app.core.plugins.PluginService
import org.gameyfin.app.core.security.getCurrentAuth
import org.gameyfin.app.games.entities.Image
import org.gameyfin.app.games.entities.ImageType
import org.gameyfin.app.users.UserService
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/images")
@DynamicPublicAccess
@AnonymousAllowed
class ImageEndpoint(
    private val imageService: ImageService,
    private val userService: UserService,
    private val pluginService: PluginService
) {

    @GetMapping("/screenshot/{id}")
    fun getScreenshot(@PathVariable("id") id: Long): ResponseEntity<InputStreamResource>? {
        return getImageContent(id)
    }

    @GetMapping("/cover/{id}")
    fun getCover(@PathVariable("id") id: Long): ResponseEntity<InputStreamResource>? {
        return getImageContent(id)
    }

    @GetMapping("/header/{id}")
    fun getHeader(@PathVariable("id") id: Long): ResponseEntity<InputStreamResource>? {
        return getImageContent(id)
    }

    @GetMapping("/plugins/{id}/logo")
    fun getPluginLogo(@PathVariable("id") pluginId: String): ResponseEntity<ByteArrayResource>? {
        val logo = pluginService.getLogo(pluginId)
        return Utils.Companion.inputStreamToResponseEntity(logo)
    }

    @GetMapping("/avatar")
    fun getAvatarByUsername(@RequestParam username: String): ResponseEntity<InputStreamResource>? {
        val avatar = userService.getAvatar(username) ?: return ResponseEntity.notFound().build()
        if (avatar.id == null) return ResponseEntity.notFound().build()
        return getImageContent(avatar.id!!)
    }

    @PermitAll
    @PostMapping("/avatar/upload")
    fun uploadAvatar(@RequestParam("file") file: MultipartFile) {
        val auth = getCurrentAuth() ?: throw IllegalStateException("No authentication found")

        val image: Image = if (!userService.hasAvatar(auth.name)) {
            imageService.createFromInputStream(ImageType.AVATAR, file.inputStream, file.contentType!!)
        } else {
            val existingAvatar = userService.getAvatar(auth.name)!!
            imageService.updateFileContent(existingAvatar, file.inputStream, file.contentType!!)
        }

        userService.updateAvatar(auth.name, image)
    }

    @PermitAll
    @PostMapping("/avatar/delete")
    fun deleteAvatar() {
        val auth = getCurrentAuth() ?: throw IllegalStateException("No authentication found")
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