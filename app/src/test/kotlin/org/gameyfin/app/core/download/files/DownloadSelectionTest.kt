package org.gameyfin.app.core.download.files

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DownloadSelectionTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `only regular files below the game root are selectable`() {
        val root = Files.createDirectory(directory.resolve("game")).toRealPath()
        val updates = Files.createDirectory(root.resolve("updates"))
        val file = Files.writeString(updates.resolve("patch, 1.bin"), "update")
        val outside = Files.writeString(directory.resolve("other.bin"), "other game")
        Files.createSymbolicLink(root.resolve("linked"), outside)
        Files.createSymbolicLink(root.resolve("folder-link"), updates)
        val path = "updates/patch, 1.bin"
        Files.createFile(root.resolve("empty"))
        assertEquals(listOf(DownloadFileEntry("empty", 0), DownloadFileEntry(path, 6)), DownloadSelection.contents(root))
        assertEquals(listOf(root.relativize(file)), DownloadSelection.validate(root, listOf(path, path)))
        for (invalid in listOf("", "../other.bin", outside.toString(), "updates", "linked", "folder-link/patch, 1.bin",
                "C:/other.bin", "updates\\patch, 1.bin")) {
            assertFailsWith<IllegalArgumentException> { DownloadSelection.validate(root, listOf(invalid)) }
        }
        assertFailsWith<IllegalArgumentException> { DownloadSelection.validate(root, emptyList()) }
        Files.delete(file)
        assertFailsWith<java.nio.file.NoSuchFileException> { DownloadSelection.validate(root, listOf(path)) }
    }
}
