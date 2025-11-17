package org.gameyfin.app.core.filesystem

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.io.FilenameUtils
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.libraries.entities.Library
import org.springframework.stereotype.Service
import java.io.File
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
    fun scanLibraryForGamefiles(library: Library): FilesystemScanResult {
        // Cache the game file extensions to avoid reading them multiple times in the same scan
        val gamefileExtensions = gameFileExtensions

        // Filter out invalid directories (directories could have been changed externally after the library was created)
        val validPaths = library.directories.map { Path(it.internalPath) }
            .filter { path ->
                if (!path.isDirectory()) {
                    log.warn { "Invalid directory '$path' in library '${library.name}'" }
                    false
                } else {
                    true
                }
            }

        // Get all paths that are directories or match the game file extensions
        // Also check if the directory is empty and if empty directories should be included
        val currentFilesystemPaths = validPaths.flatMap { validDirectory ->
            safeReadDirectoryContents(validDirectory)
                .filter { it.isDirectory() || it.extension.lowercase() in gamefileExtensions }
                .filter {
                    if (!it.isDirectory()) return@filter true

                    val contents = safeReadDirectoryContents(it)
                    if (contents.isEmpty() && !config.get(ConfigProperties.Libraries.Scan.ScanEmptyDirectories)!!) {
                        log.debug { "Directory '$it' is empty and will be ignored" }
                        return@filter false
                    } else {
                        return@filter true
                    }
                }
        }

        // Get all paths already in the library as game files or as ignored paths
        val currentLibraryGamePaths = library.games.map { Path(it.metadata.path) }
        val currentLibraryIgnoredPaths = library.ignoredPaths.map { Path(it.path) }
        val allCurrentLibraryPaths = currentLibraryGamePaths + currentLibraryIgnoredPaths

        //Get all paths that are on the filesystem, but not in the library (either as game or as ignored path)
        val newPaths = currentFilesystemPaths.filter { path ->
            val isInLibrary = allCurrentLibraryPaths.any { it == path }
            !isInLibrary
        }

        //Get all paths that are in the library (either as game or as ignored path), but not on the filesystem
        val removedGamePaths = currentLibraryGamePaths.filter { path ->
            val isOnFilesystem = currentFilesystemPaths.any { it == path }
            !isOnFilesystem
        }

        val removedIgnoredPaths = library.ignoredPaths.filter { ignoredPath ->
            val path = Path(ignoredPath.path)
            val isOnFilesystem = currentFilesystemPaths.any { it == path }
            !isOnFilesystem
        }

        return FilesystemScanResult(
            newPaths = newPaths,
            removedGamePaths = removedGamePaths,
            removedIgnoredPaths = removedIgnoredPaths
        )
    }

    fun calculateFileSize(path: String): Long {
        return try {
            val file = File(path)
            if (file.isFile) {
                file.length()
            } else if (file.isDirectory) {
                File(path).walkTopDown().filter { it.isFile }.map { it.length() }.sum()
            } else {
                0L
            }
        } catch (e: Exception) {
            log.warn { "Error calculating file size for $path: ${e.message}" }
            0L
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