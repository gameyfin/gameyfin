package de.grimsi.gameyfin.core.filesystem

import org.springframework.stereotype.Service
import java.io.File

@Service
class FilesystemService {
    /**
     * Lists all files and directories in the given path.
     * If the path is null or empty, it lists all root directories.
     *
     * @param path The path to list files and directories from.
     * @return A list of FileDto objects representing the files and directories.
     */
    fun listContents(path: String?): List<FileDto> {
        val file = if (path.isNullOrEmpty()) File.listRoots().toList() else listOf(File(path))
        return file.flatMap { it.listFiles()?.toList() ?: emptyList() }
            .map { FileDto(it.name, if (it.isDirectory) FileType.DIRECTORY else FileType.FILE, it.hashCode()) }
    }

    fun listSubDirectories(path: String?): List<FileDto> {
        return listContents(path).filter { it.type == FileType.DIRECTORY }
    }
}