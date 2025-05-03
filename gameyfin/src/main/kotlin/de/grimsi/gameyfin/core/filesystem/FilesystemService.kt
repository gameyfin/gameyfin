package de.grimsi.gameyfin.core.filesystem

import de.grimsi.gameyfin.config.ConfigProperties
import de.grimsi.gameyfin.config.ConfigService
import de.grimsi.gameyfin.libraries.Library
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.io.FilenameUtils
import org.springframework.stereotype.Service
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.*

@Service
class FilesystemService(
    private val config: ConfigService
) {

    private val log = KotlinLogging.logger {}

    private val gameFileExtensions
        get() = config.get(ConfigProperties.Libraries.Scan.GameFileExtensions)!!.map { it.trim().lowercase() }

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

        val path = FilenameUtils.separatorsToSystem(path)

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

    /**
     * Scans the given library for files and directories potentially containing games.
     *
     * @param library The library to scan.
     * @return A list of paths representing game files and directories.
     */
    fun scanLibraryForGamefiles(library: Library): List<Path> {
        // Cache the game file extensions to avoid reading them multiple times in the same scan
        val gamefileExtensions = gameFileExtensions

        // Filter out invalid directories (directories could have been changed externally after the library was created)
        val validDirectories = library.directories.map { Path(it) }
            .filter { path ->
                if (!path.isDirectory()) {
                    log.warn { "Invalid directory '$path' in library '${library.name}'" }
                    false
                } else {
                    true
                }
            }

        // Return all paths that are directories or match the game file extensions
        return validDirectories.flatMap { validDirectory ->
            safeReadDirectoryContents(validDirectory)
                .filter { it.isDirectory() || it.extension.lowercase() in gamefileExtensions }
        }
    }

    private fun safeReadDirectoryContents(path: String): List<FileDto> {
        return safeReadDirectoryContents(Path(path))
            .map { FileDto(it.name, if (it.isDirectory()) FileType.DIRECTORY else FileType.FILE, it.hashCode()) }
    }

    private fun safeReadDirectoryContents(path: Path): List<Path> {
        return try {
            path.listDirectoryEntries()
                .filter { !it.isHidden() }
        } catch (_: Exception) {
            log.warn { "Error reading directory contents of $path" }
            emptyList()
        }
    }
}