package org.gameyfin.app.core.filesystem

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.io.FilenameUtils
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.libraries.entities.Library
import org.springframework.stereotype.Service
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.*

internal const val GAME_UPLOAD_DIRECTORY = ".gameyfin-uploads"

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
        if (path.isNullOrEmpty()) {
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
            .mapNotNull { runCatching { it.toRealPath() }.getOrNull() }
            .distinct()

        // Get all paths that are directories or match the game file extensions
        // Also check if the directory is empty and if empty directories should be included
        val currentFilesystemPaths = validPaths.flatMap { validDirectory ->
            safeReadDirectoryContents(validDirectory)
                .filter { it.fileName.toString() != GAME_UPLOAD_DIRECTORY }
                .filter { it.isDirectory() || it.extension.lowercase() in gamefileExtensions }
                .filter {
                    if (!it.isDirectory()) return@filter true

                    val contents = safeReadDirectoryContents(it)
                    return@filter if (contents.isEmpty() && !config.get(ConfigProperties.Libraries.Scan.ScanEmptyDirectories)!!) {
                        log.debug { "Directory '$it' is empty and will be ignored" }
                        false
                    } else {
                        true
                    }
                }
        }

        // Get all paths already in the library as game files or as ignored paths
        val currentLibraryGamePaths = library.games.map { Path(it.metadata.path) }
        val currentLibraryIgnoredPaths = library.ignoredPaths.map { Path(it.path) }
        val allCurrentLibraryPaths = (currentLibraryGamePaths + currentLibraryIgnoredPaths)
            .map { runCatching { it.toRealPath() }.getOrDefault(it) }

        //Get all paths that are on the filesystem, but not in the library (either as game or as ignored path)
        val newPaths = currentFilesystemPaths.filter { path ->
            val isInLibrary = allCurrentLibraryPaths.any { it == path }
            !isInLibrary
        }

        //Get all paths that are in the library (either as game or as ignored path), but not on the filesystem
        val removedGamePaths = currentLibraryGamePaths.filter { path ->
            // Registered games need not match the discovery extension filter.
            val isOnFilesystem = Files.exists(path)
            !isOnFilesystem
        }

        val removedIgnoredPaths = library.ignoredPaths.filter { ignoredPath ->
            val path = Path(ignoredPath.path)
            val canonicalPath = runCatching { path.toRealPath() }.getOrDefault(path)
            val isOnFilesystem = currentFilesystemPaths.any { it == canonicalPath }
            !isOnFilesystem
        }

        return FilesystemScanResult(
            newPaths = newPaths,
            removedGamePaths = removedGamePaths,
            removedIgnoredPaths = removedIgnoredPaths
        )
    }

    /** Resolve and validate a client path against canonical library directories. */
    fun validateGamePath(path: String, library: Library): Path {
        val input = Path(path)
        require(input.isAbsolute) { "Path must be absolute" }

        // Resolve the original path so links are resolved before any '..' components.
        // Check and return the same canonical path that callers will use. Path.startsWith
        // compares complete components, preventing sibling-prefix matches.
        try {
            val canonicalPath = input.toRealPath()
            for (directory in library.directories) {
                val canonicalRoot = Path(directory.internalPath).toRealPath()
                if (canonicalPath != canonicalRoot && canonicalPath.startsWith(canonicalRoot)) {
                    return canonicalPath
                }
            }
        } catch (e: IOException) {
            throw IllegalArgumentException("Path must refer to an accessible file or directory inside the library", e)
        } catch (e: SecurityException) {
            throw IllegalArgumentException("Path must refer to an accessible file or directory inside the library", e)
        }
        throw IllegalArgumentException("Path must refer to an accessible file or directory inside the library")
    }

    /** Delete only a validated game path; never follow symlinks during recursive deletion. */
    fun deleteGameFiles(path: String, library: Library) {
        require(!Files.isSymbolicLink(Path(path))) { "Cannot delete game files through a symbolic link" }
        val target = validateGamePath(path, library)
        require(library.directories.none {
            Path(it.internalPath).toRealPath().startsWith(target)
        }) { "Cannot delete a library directory" }
        require(library.games.count { it.metadata.path == path } <= 1) {
            "Cannot delete files shared with another game"
        }
        require(library.games.none { game ->
            game.metadata.path != path && runCatching {
                val other = Path(game.metadata.path).toRealPath()
                other.startsWith(target) || target.startsWith(other)
            }.getOrDefault(false)
        }) { "Cannot delete files shared with another game" }

        Files.walkFileTree(target, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                if (exc != null) throw exc
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }

    /** Recheck the library boundary at use time, including paths loaded from stored game metadata. */
    fun calculateFileSize(path: String, library: Library): Long {
        return try {
            val file = validateGamePath(path, library)
            val attributes = Files.readAttributes(file, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            if (attributes.isRegularFile) {
                attributes.size()
            } else if (attributes.isDirectory) {
                // Files.walkFileTree does not follow symlinks, so cyclic links can't cause endless traversal
                var total = 0L
                Files.walkFileTree(file, object : SimpleFileVisitor<Path>() {
                    override fun visitFile(
                        f: Path,
                        attrs: BasicFileAttributes
                    ): FileVisitResult {
                        if (attrs.isRegularFile) total += attrs.size()
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(f: Path, exc: IOException): FileVisitResult =
                        FileVisitResult.CONTINUE
                })
                total
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
