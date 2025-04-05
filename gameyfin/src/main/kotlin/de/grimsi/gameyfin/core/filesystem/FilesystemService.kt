package de.grimsi.gameyfin.core.filesystem

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.io.FilenameUtils
import org.springframework.stereotype.Service
import java.nio.file.FileSystems
import kotlin.io.path.Path
import kotlin.io.path.isDirectory

@Service
class FilesystemService {

    private val log = KotlinLogging.logger {}

    /**
     * Lists all files and directories in the given path.
     * If the path is null or empty, it lists all root directories.
     *
     * @param path The path to list files and directories from.
     * @return A list of FileDto objects representing the files and directories.
     */
    fun listContents(path: String?): List<FileDto> {
        if (path == null || path.isEmpty()) {
            val roots = FileSystems.getDefault().rootDirectories.toList()

            if (getHostOperatingSystem() == OperatingSystemType.WINDOWS) return roots.map {
                FileDto(
                    it.root.toString(),
                    if (it.isDirectory()) FileType.DIRECTORY else FileType.FILE,
                    it.hashCode()
                )
            }

            // UNIX file systems only have one root, so return its contents directly
            return safeReadDirectoryContents(roots.first().toString())
        }

        var path = FilenameUtils.separatorsToSystem(path)

        return safeReadDirectoryContents(path)
    }

    /**
     * Lists all subdirectories in the given path.
     * If the path is null or empty, it lists all root directories.
     *
     * @param path The path to list subdirectories from.
     * @return A list of FileDto objects representing the subdirectories.
     */
    fun listSubDirectories(path: String?): List<FileDto> {
        return listContents(path).filter { it.type == FileType.DIRECTORY }
    }

    fun getHostOperatingSystem(): OperatingSystemType {
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("win") -> OperatingSystemType.WINDOWS
            os.contains("mac") -> OperatingSystemType.MAC
            os.contains("nux") -> OperatingSystemType.LINUX
            else -> OperatingSystemType.UNKNOWN
        }
    }

    private fun safeReadDirectoryContents(path: String): List<FileDto> {
        return try {
            Path(path).toFile().listFiles()
                .filter { !it.isHidden }
                .map { FileDto(it.name, if (it.isDirectory) FileType.DIRECTORY else FileType.FILE, it.hashCode()) }
        } catch (_: Exception) {
            log.warn { "Error reading directory contents of $path" }
            emptyList()
        }
    }
}