package org.gameyfin.app.system.backup

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.*

class BackupStorageTest {

    @TempDir
    lateinit var dir: Path

    private fun storage() = BackupStorage(dir).also { it.ensureExists() }

    @Test
    fun `isValidName accepts only plain zip file names`() {
        val s = storage()
        assertTrue(s.isValidName("backup-1.zip"))
        assertFalse(s.isValidName("../backup.zip"))
        assertFalse(s.isValidName("a/b.zip"))
        assertFalse(s.isValidName("backup.txt"))
        assertFalse(s.isValidName(".hidden.zip"))
    }

    @Test
    fun `resolve returns existing backup from the backup directory`() {
        val s = storage()
        (s.root / "backup.zip").writeText("x")

        assertEquals(s.root / "backup.zip", s.resolve("backup.zip"))
    }

    @Test
    fun `resolve rejects invalid and missing names with the same message`() {
        val s = storage()
        val invalid = assertThrows(IllegalArgumentException::class.java) { s.resolve("../etc/passwd") }
        val missing = assertThrows(IllegalArgumentException::class.java) { s.resolve("missing.zip") }

        assertEquals(invalid.message, missing.message)
    }

    @Test
    fun `resolve ignores directories named like backups`() {
        val s = storage()
        (s.root / "dir.zip").createDirectory()

        assertThrows(IllegalArgumentException::class.java) { s.resolve("dir.zip") }
    }

    @Test
    fun `list returns only valid backup files`() {
        val s = storage()
        (s.root / "a.zip").writeText("x")
        (s.root / ".tmp-1.zip").writeText("x")
        (s.root / "notes.txt").writeText("x")

        assertEquals(listOf("a.zip"), s.list().map { it.name })
    }

    @Test
    fun `list returns empty list when directory is missing`() {
        assertTrue(BackupStorage(dir / "missing").list().isEmpty())
    }

    @Test
    fun `delete removes the backup`() {
        val s = storage()
        (s.root / "a.zip").writeText("x")
        s.delete("a.zip")
        assertFalse((s.root / "a.zip").exists())
    }

    @Test
    fun `newBackupName avoids collisions`() {
        val s = storage()
        val now = Instant.parse("2026-01-02T03:04:05Z")
        val first = s.newBackupName(now)
        (s.root / first).writeText("x")

        assertEquals("gameyfin-backup-20260102-030405.zip", first)
        assertEquals("gameyfin-backup-20260102-030405-1.zip", s.newBackupName(now))
        assertEquals("gameyfin-backup-20260102-030405-pre.zip", s.newBackupName(now, "pre"))
    }

    @Test
    fun `publish moves temp file and cleanup removes leftovers`() {
        val s = storage()
        val temp = s.createTempFile()
        temp.writeText("data")

        val target = s.publish(temp, "final.zip")

        assertEquals("data", target.readText())
        assertFalse(temp.exists())
        assertThrows(IllegalArgumentException::class.java) { s.publish(s.createTempFile(), "../x.zip") }
        s.cleanupTempFiles()
        assertEquals(listOf("final.zip"), s.root.listDirectoryEntries().map { it.name })
    }

    @Test
    fun `pending restore lifecycle`() {
        val s = storage()
        assertNull(s.pendingRestore())
        assertThrows(IllegalArgumentException::class.java) { s.schedulePendingRestore("none.zip") }

        (s.root / "a.zip").writeText("x")
        s.schedulePendingRestore("a.zip")
        assertEquals("a.zip", s.pendingRestore())

        s.clearPendingRestore()
        assertNull(s.pendingRestore())
    }

    @Test
    fun `image recovery marker lifecycle`() {
        val s = storage()
        assertFalse(s.isImageRecoveryPending())
        s.scheduleImageRecovery()
        assertTrue(s.isImageRecoveryPending())
        s.clearPendingImageRecovery()
        assertFalse(s.isImageRecoveryPending())
    }

    @Test
    fun `restore status roundtrip`() {
        val s = storage()
        assertNull(s.readRestoreStatus())

        val status = RestoreStatusDto("a.zip", true, "ok", Instant.parse("2026-01-02T03:04:05Z"))
        s.writeRestoreStatus(status)
        assertEquals(status, s.readRestoreStatus())

        (s.root / ".last-restore.json").writeText("not json")
        assertNull(s.readRestoreStatus())
    }
}
