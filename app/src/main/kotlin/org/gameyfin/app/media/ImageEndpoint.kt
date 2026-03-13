package org.gameyfin.app.media

import com.vaadin.flow.server.auth.AnonymousAllowed
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
import jakarta.servlet.http.HttpServletRequest
import org.gameyfin.app.core.Role
import org.gameyfin.app.core.Utils
import org.gameyfin.app.core.annotations.DynamicPublicAccess
import org.gameyfin.app.core.plugins.PluginService
import org.gameyfin.app.core.security.getCurrentAuth
import org.gameyfin.app.users.UserService
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.*
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.util.concurrent.TimeUnit

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
    fun getScreenshot(@PathVariable id: Long, request: HttpServletRequest): ResponseEntity<Resource>? {
        return getImageContent(id, request)
    }

    @GetMapping("/cover/{id}")
    fun getCover(@PathVariable id: Long, request: HttpServletRequest): ResponseEntity<Resource>? {
        return getImageContent(id, request)
    }

    @GetMapping("/header/{id}")
    fun getHeader(@PathVariable id: Long, request: HttpServletRequest): ResponseEntity<Resource>? {
        return getImageContent(id, request)
    }

    @GetMapping("/plugins/{pluginId}/logo")
    fun getPluginLogo(@PathVariable pluginId: String): ResponseEntity<ByteArrayResource>? {
        val logo = pluginService.getLogo(pluginId)
        return Utils.inputStreamToResponseEntity(logo)
    }

    @GetMapping("/avatar")
    fun getAvatarByUsername(@RequestParam username: String, request: HttpServletRequest): ResponseEntity<Resource>? {
        val avatar = userService.getAvatar(username) ?: return ResponseEntity.notFound().build()
        if (avatar.id == null) return ResponseEntity.notFound().build()
        return getImageContent(avatar.id!!, request)
    }

    @PermitAll
    @PostMapping("/avatar/upload")
    fun uploadAvatar(@RequestParam("file") file: MultipartFile) {
        val auth = getCurrentAuth() ?: error("No authentication found")

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
        val auth = getCurrentAuth() ?: error("No authentication found")
        userService.deleteAvatar(auth.name)
    }

    @RolesAllowed(Role.Names.ADMIN)
    @PostMapping("/avatar/deleteByName")
    fun deleteAvatarByName(@RequestParam("name") name: String) {
        userService.deleteAvatar(name)
    }

    private fun getImageContent(id: Long, request: HttpServletRequest): ResponseEntity<Resource> {
        val image = imageService.getImage(id) ?: return ResponseEntity.notFound().build()

        // Use contentId as ETag — it changes whenever the file content changes
        val etag = image.contentId?.let { "\"$it\"" }

        // Check If-None-Match for conditional requests (304 Not Modified)
        if (etag != null) {
            val ifNoneMatch = request.getHeader(HttpHeaders.IF_NONE_MATCH)
            if (ifNoneMatch != null && (ifNoneMatch == etag || ifNoneMatch == "W/$etag")) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS))
                    .build()
            }
        }

        // Resolve the file path on disk for efficient zero-copy serving
        val filePath = imageService.getFilePath(image) ?: return ResponseEntity.notFound().build()

        val resource = FileSystemResource(filePath)

        val headers = HttpHeaders()
        image.contentLength?.let { headers.contentLength = it }
        image.mimeType?.let { headers.contentType = MediaType.parseMediaType(it) }
        headers.cacheControl = CacheControl.maxAge(7, TimeUnit.DAYS).headerValue
        etag?.let { headers.eTag = it }

        // Add Last-Modified from the file's modification time
        try {
            val lastModified = Files.getLastModifiedTime(filePath).toInstant()
            headers.lastModified = lastModified.toEpochMilli()
        } catch (_: Exception) {
            // Ignore — Last-Modified is optional
        }

        return ResponseEntity.ok()
            .headers(headers)
            .body(resource)
    }
}