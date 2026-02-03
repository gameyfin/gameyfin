package org.gameyfin.app.media

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists

private val logger = KotlinLogging.logger {}

/**
 * Service for handling file storage operations.
 * Files are stored in the filesystem under the specified root directory.
 * The content ID is a UUID string used as the filename.
 * Files are stored without extensions; MIME type is managed separately.
 *
 * Note: This is a drop-in replacement for Spring Content's filesystem storage (which has been discontinued).
 */
@Service
class FileStorageService(
    @param:Value($$"${spring.content.fs.filesystem-root:./data/}") private val storageRoot: String
) {
    private val rootPath: Path = Path.of(storageRoot)

    init {
        // Ensure storage directory exists
        if (!rootPath.exists()) {
            rootPath.createDirectories()
            logger.info { "Created file storage directory: $rootPath" }
        }
    }

    /**
     * Stores a file and returns the generated content ID (UUID).
     */
    fun saveFile(inputStream: InputStream): String {
        val contentId = UUID.randomUUID().toString()
        val filePath = rootPath.resolve(contentId)

        inputStream.use { input ->
            Files.copy(input, filePath, StandardCopyOption.REPLACE_EXISTING)
        }

        logger.debug { "Saved file with contentId: $contentId" }
        return contentId
    }

    /**
     * Retrieves a file by content ID.
     * Returns null if the file doesn't exist.
     */
    fun getFile(contentId: String?): InputStream? {
        if (contentId == null) return null

        val filePath = rootPath.resolve(contentId)
        return if (filePath.exists()) {
            Files.newInputStream(filePath)
        } else {
            logger.warn { "File not found for contentId: $contentId" }
            null
        }
    }

    /**
     * Deletes a file by content ID.
     */
    fun deleteFile(contentId: String?) {
        if (contentId == null) return

        val filePath = rootPath.resolve(contentId)
        if (filePath.exists()) {
            filePath.deleteExisting()
            logger.debug { "Deleted file with contentId: $contentId" }
        }
    }

    /**
     * Checks if a file exists for the given content ID.
     */
    fun fileExists(contentId: String?): Boolean {
        if (contentId == null) return false
        return rootPath.resolve(contentId).exists()
    }
}
