package org.gameyfin.plugins.download.direct

import java.io.InterruptedIOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.attribute.BasicFileAttributes

/** Visits files incrementally, without following directory symlinks or retaining the tree. */
internal object ArchiveFiles {
    fun visit(root: Path, consume: (Path, Long) -> Unit) {
        fun checkCancelled() {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Archive traversal cancelled")
        }

        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                checkCancelled()
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                checkCancelled()
                when {
                    attrs.isRegularFile -> consume(file, attrs.size())
                    // Match the previous walker: include links to regular files, but not directory links.
                    attrs.isSymbolicLink && Files.isRegularFile(file) -> consume(file, Files.size(file))
                }
                return FileVisitResult.CONTINUE
            }
        })
    }
}
