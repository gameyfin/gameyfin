package org.gameyfin.app.core.download.files

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.io.path.invariantSeparatorsPathString

data class DownloadFileEntry(val path: String, val size: Long)

internal object DownloadSelection {
    // ZIP names use forward slashes on every host. Avoid ambiguous Windows path names.
    private fun safeName(name: String) = name.isNotEmpty() && name != "." && name != ".." &&
            '\\' !in name && ':' !in name

    fun contents(root: Path): List<DownloadFileEntry> {
        val entries = mutableListOf<DownloadFileEntry>()
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                GameDownloadLifecycle.checkCancelled()
                if (attrs.isRegularFile) {
                    val path = root.relativize(file)
                    if (path.all { safeName(it.toString()) }) {
                        entries.add(DownloadFileEntry(path.invariantSeparatorsPathString, attrs.size()))
                    }
                }
                return FileVisitResult.CONTINUE
            }
        })
        return entries.sortedBy { it.path }
    }

    /** Entries are sorted by path. NUL cannot occur in a filesystem path. */
    fun contentsETag(entries: List<DownloadFileEntry>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for ((path, size) in entries) {
            digest.update(path.toByteArray())
            digest.update(0)
            digest.update(size.toString().toByteArray())
            digest.update(0)
        }
        return "\"" + HexFormat.of().formatHex(digest.digest()) + "\""
    }

    fun validate(root: Path, files: List<String>): List<Path> {
        require(files.isNotEmpty()) { "Select at least one file" }
        return files.map { name ->
            GameDownloadLifecycle.checkCancelled()
            require(name.split('/').all(::safeName)) { "Invalid file path" }
            Path.of(name)
        }.distinct().onEach { relative ->
            GameDownloadLifecycle.checkCancelled()
            val file = root.resolve(relative)
            require(file.toRealPath() == file && Files.isRegularFile(file, NOFOLLOW_LINKS)) {
                "Selected file is unavailable"
            }
        }
    }
}
