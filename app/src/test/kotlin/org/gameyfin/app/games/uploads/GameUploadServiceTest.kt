package org.gameyfin.app.games.uploads

import io.mockk.*
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.core.filesystem.FilesystemService
import org.gameyfin.app.core.filesystem.GAME_UPLOAD_DIRECTORY
import org.gameyfin.app.games.GameService
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.libraries.LibraryCoreService
import org.gameyfin.app.libraries.LibraryOperationLock
import org.gameyfin.app.libraries.LibraryRepository
import org.gameyfin.app.libraries.entities.DirectoryMapping
import org.gameyfin.app.libraries.entities.Library
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.HttpStatus
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.web.server.ResponseStatusException
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.time.Instant
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

class GameUploadServiceTest {
    @TempDir lateinit var temp: Path
    private val libraries = mockk<LibraryCoreService>()
    private val repository = mockk<LibraryRepository>()
    private val games = mockk<GameService>()
    private val transactions = mockk<PlatformTransactionManager>(relaxed = true)
    private lateinit var service: GameUploadService
    private lateinit var root: Path
    private lateinit var second: Path
    private lateinit var library: Library

    @BeforeEach
    fun setup() {
        root = Files.createDirectory(temp.resolve("first")).toRealPath()
        second = Files.createDirectory(temp.resolve("second")).toRealPath()
        library = Library(id = 1, name = "Games", directories = mutableListOf(
            DirectoryMapping(internalPath = root.toString()), DirectoryMapping(internalPath = second.toString())
        ))
        every { libraries.getWithGamesAndIgnoredPaths(1) } returns library
        every { repository.findAll() } returns listOf(library)
        every { transactions.getTransaction(any()) } answers { SimpleTransactionStatus() }
        every { games.prepareMetadata(any(), any(), any()) } answers {
            GameService.PreparedMetadata(emptyMap(), Game(secondArg<Path>(), library).apply { title = "Draft" })
        }
        every { games.createPrepared(any(), any(), any()) } answers {
            Game(secondArg<Path>(), thirdArg()).apply { id = 42; title = "Draft" }
        }
        every { games.edit(any(), any()) } just Runs
        every { libraries.addGamesToLibrary(any(), library, true) } answers {
            library.games.addAll(firstArg<Collection<Game>>())
            library
        }
        service = GameUploadService(libraries, repository, games, transactions)
    }

    private fun request(name: String = "Original Game.iso", directory: Path = root) =
        GameUploadRequest(1, directory.toString(), name, false, listOf(GameUploadFileDto(name, 3)))

    private fun prepare(request: GameUploadRequest = request()): Pair<UUID, org.gameyfin.app.games.dto.GameUpdateDto> {
        val id = service.create("admin", request).id
        return id to service.prepare("admin", id, emptyMap())
    }

    @Test
    fun `destination preflight detects files directories and broken links without creating staging`() {
        val check = GameUploadDestinationDto(1, root.toString(), "Original Game.iso")
        service.validateDestination(check)
        assertFalse(Files.exists(root.resolve(GAME_UPLOAD_DIRECTORY)))
        val existing = Files.writeString(root.resolve(check.name), "keep")
        assertEquals(HttpStatus.CONFLICT, assertFailsWith<ResponseStatusException> {
            service.validateDestination(check)
        }.statusCode)
        assertEquals("keep", Files.readString(existing))
        Files.delete(existing)
        Files.createDirectory(existing)
        assertEquals(HttpStatus.CONFLICT, assertFailsWith<ResponseStatusException> {
            service.validateDestination(check)
        }.statusCode)
        Files.delete(existing)
        Files.createSymbolicLink(existing, temp.resolve("missing"))
        assertEquals(HttpStatus.CONFLICT, assertFailsWith<ResponseStatusException> {
            service.validateDestination(check)
        }.statusCode)
        assertFalse(Files.exists(root.resolve(GAME_UPLOAD_DIRECTORY)))
    }

    @Test
    fun `destination preflight rejects paths outside library and creation rechecks later collisions`() {
        assertFailsWith<IllegalArgumentException> {
            service.validateDestination(GameUploadDestinationDto(1, temp.toString(), "game.iso"))
        }
        assertFailsWith<IllegalArgumentException> {
            service.validateDestination(GameUploadDestinationDto(1, root.toString(), "../game.iso"))
        }
        service.validateDestination(GameUploadDestinationDto(1, root.toString(), "Original Game.iso"))
        Files.writeString(root.resolve("Original Game.iso"), "keep")
        assertEquals(HttpStatus.CONFLICT, assertFailsWith<ResponseStatusException> {
            service.create("admin", request())
        }.statusCode)
        assertFalse(Files.exists(root.resolve(GAME_UPLOAD_DIRECTORY)))
    }

    @Test
    fun `file keeps original name and bytes in selected library directory and completion is idempotent`() {
        val (id, metadata) = prepare(request(directory = second))
        service.transfer("admin", id, 0, byteArrayOf(1, 2, 3).inputStream())
        assertFalse(Files.exists(second.resolve("Original Game.iso")))
        assertEquals(42L, service.complete("admin", id, metadata, emptyMap()).gameId)
        assertContentEquals(byteArrayOf(1, 2, 3), Files.readAllBytes(second.resolve("Original Game.iso")))
        assertFalse(Files.exists(root.resolve("Original Game.iso")))
        assertEquals(42L, service.complete("admin", id, metadata, emptyMap()).gameId)
        assertEquals(42L, service.status("admin", id).gameId)
        verify(exactly = 1) { games.createPrepared(any(), any(), any()) }
        verify(exactly = 1) { transactions.commit(any()) }
    }

    @Test
    fun `folder preserves root name nested paths unicode hidden files and zero byte files`() {
        val request = GameUploadRequest(1, root.toString(), "My Game", true, listOf(
            GameUploadFileDto("data/日本語.dat", 3), GameUploadFileDto(".config", 0)
        ))
        val (id, metadata) = prepare(request)
        service.transfer("admin", id, 0, "abc".byteInputStream())
        service.transfer("admin", id, 1, byteArrayOf().inputStream())
        service.complete("admin", id, metadata, emptyMap())
        assertEquals("abc", Files.readString(root.resolve("My Game/data/日本語.dat")))
        assertEquals(0L, Files.size(root.resolve("My Game/.config")))
    }

    @Test
    fun `folder transfer rejects linked staging directories before creating nested directories`() {
        val request = request(name = "My Game").copy(
            folder = true, files = listOf(GameUploadFileDto("data/nested/file.dat", 3))
        )
        val (id, _) = prepare(request)
        val payload = Files.createDirectory(root.resolve("$GAME_UPLOAD_DIRECTORY/$id/payload"))
        Files.createSymbolicLink(payload.resolve("data"), second)

        assertFailsWith<IllegalArgumentException> { service.transfer("admin", id, 0, "abc".byteInputStream()) }
        assertFalse(Files.exists(second.resolve("nested")))
        assertEquals(0, service.status("admin", id).completedFiles)
    }

    @Test
    fun `invalid manifests cannot escape destination or conflict within a folder`() {
        val invalid = listOf("../escape", "/absolute", "nested/../../escape", "C:\\escape", "a//b", "a/./b", "a/", "a\u0000b")
        invalid.forEach { path ->
            assertFailsWith<IllegalArgumentException>(path) {
                service.create("admin", request().copy(folder = true, files = listOf(GameUploadFileDto(path, 1))))
            }
        }
        listOf(
            listOf(GameUploadFileDto("a", 1), GameUploadFileDto("a", 2)),
            listOf(GameUploadFileDto("a", 1), GameUploadFileDto("a/b", 2)),
            emptyList(), listOf(GameUploadFileDto("a", -1))
        ).forEach { manifest ->
            assertFailsWith<IllegalArgumentException> { service.create("admin", request().copy(folder = true, files = manifest)) }
        }
        assertFailsWith<IllegalArgumentException> { service.create("admin", request(directory = temp)) }
        assertFailsWith<IllegalArgumentException> { service.create("admin", request(name = "..")) }
    }

    @Test
    fun `existing destinations and symlinks are never replaced`() {
        val existing = Files.writeString(root.resolve("Original Game.iso"), "keep")
        assertEquals(HttpStatus.CONFLICT, assertFailsWith<ResponseStatusException> {
            service.create("admin", request())
        }.statusCode)
        Files.delete(existing)
        val (id, metadata) = prepare()
        service.transfer("admin", id, 0, "new".byteInputStream())
        Files.createSymbolicLink(existing, temp.resolve("outside"))
        assertEquals(HttpStatus.CONFLICT, assertFailsWith<ResponseStatusException> {
            service.complete("admin", id, metadata, emptyMap())
        }.statusCode)
        assertTrue(Files.isSymbolicLink(existing))
        verify(exactly = 0) { games.createPrepared(any(), any(), any()) }
    }

    @Test
    fun `staging symlink and replaced library root are rejected`() {
        Files.createSymbolicLink(root.resolve(GAME_UPLOAD_DIRECTORY), second)
        assertFailsWith<IllegalArgumentException> { service.create("admin", request()) }
        Files.delete(root.resolve(GAME_UPLOAD_DIRECTORY))
        val (id, _) = prepare()
        library.directories[0].internalPath = second.toString()
        assertFailsWith<IllegalArgumentException> { service.transfer("admin", id, 0, "abc".byteInputStream()) }
        assertFalse(Files.exists(second.resolve("Original Game.iso")))
    }

    @Test
    fun `truncated oversized and failed streams clear staged files and restart transfers everything`() {
        val (id, metadata) = prepare()
        for (data in listOf("ab", "abcd")) {
            assertFailsWith<IllegalArgumentException> { service.transfer("admin", id, 0, data.byteInputStream()) }
            assertEquals(0, service.status("admin", id).completedFiles)
        }
        assertFailsWith<IOException> {
            service.transfer("admin", id, 0, object : InputStream() {
                override fun read(): Int = throw IOException("disk or network failure")
            })
        }
        assertFalse(Files.exists(root.resolve("$GAME_UPLOAD_DIRECTORY/$id/payload")))
        assertFailsWith<IllegalArgumentException> { service.complete("admin", id, metadata, emptyMap()) }
        service.transfer("admin", id, 0, "abc".byteInputStream())
        service.restart("admin", id)
        assertEquals(0, service.status("admin", id).completedFiles)
        service.transfer("admin", id, 0, "xyz".byteInputStream())
        service.complete("admin", id, metadata, emptyMap())
        assertEquals("xyz", Files.readString(root.resolve("Original Game.iso")))
    }

    @Test
    fun `failed persistence rolls back and returns published files to staging`() {
        val (id, metadata) = prepare()
        service.transfer("admin", id, 0, "abc".byteInputStream())
        every { games.edit(any(), any()) } throws IllegalArgumentException("invalid artwork")
        assertFailsWith<IllegalArgumentException> { service.complete("admin", id, metadata, emptyMap()) }
        assertFalse(Files.exists(root.resolve("Original Game.iso")))
        assertEquals("abc", Files.readString(root.resolve("$GAME_UPLOAD_DIRECTORY/$id/payload")))
        verify(exactly = 1) { transactions.rollback(any()) }
        every { games.edit(any(), any()) } just Runs
        assertEquals(42L, service.complete("admin", id, metadata, emptyMap()).gameId)
    }

    @Test
    fun `blank edited titles cannot publish even when provider title exists`() {
        val (id, metadata) = prepare()
        service.transfer("admin", id, 0, "abc".byteInputStream())
        assertFailsWith<IllegalArgumentException> { service.complete("admin", id, metadata.copy(title = "  "), emptyMap()) }
        assertFalse(Files.exists(root.resolve("Original Game.iso")))
        verify(exactly = 0) { games.createPrepared(any(), any(), any()) }
    }

    @Test
    fun `sessions belong to creating user and cancellation removes staging`() {
        val (id, _) = prepare()
        assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<ResponseStatusException> { service.status("other", id) }.statusCode)
        assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<ResponseStatusException> { service.cancel("other", id) }.statusCode)
        service.transfer("admin", id, 0, "abc".byteInputStream())
        service.cancel("admin", id)
        assertFalse(Files.exists(root.resolve("$GAME_UPLOAD_DIRECTORY/$id")))
        assertFailsWith<ResponseStatusException> { service.status("admin", id) }
    }

    @Test
    fun `replaced staging symlinks cannot redirect writes or cancellation cleanup`() {
        val (id, _) = prepare()
        val staging = root.resolve("$GAME_UPLOAD_DIRECTORY/$id")
        Files.move(staging, temp.resolve("original-staging"))
        val outside = Files.createDirectory(temp.resolve("outside"))
        Files.writeString(outside.resolve("keep"), "keep")
        Files.createSymbolicLink(staging, outside)
        assertFailsWith<IllegalArgumentException> { service.transfer("admin", id, 0, "abc".byteInputStream()) }
        assertFailsWith<IllegalArgumentException> { service.cancel("admin", id) }
        assertEquals("keep", Files.readString(outside.resolve("keep")))
        assertFalse(Files.exists(outside.resolve("payload")))
    }

    @Test
    fun `restart cleanup removes only abandoned staging and keeps active uploads and published games`() {
        val (id, _) = prepare()
        val abandoned = Files.createDirectory(root.resolve("$GAME_UPLOAD_DIRECTORY/${UUID.randomUUID()}"))
        Files.writeString(abandoned.resolve("partial"), "abc")
        val game = Files.writeString(root.resolve("Published.iso"), "keep")
        service.cleanInterruptedUploads()
        assertFalse(Files.exists(abandoned))
        assertTrue(Files.exists(root.resolve("$GAME_UPLOAD_DIRECTORY/$id")))
        assertEquals("keep", Files.readString(game))
    }

    @Test
    fun `idle sessions expire after 24 hours without deleting published files`() {
        val (id, _) = prepare()
        service.transfer("admin", id, 0, "abc".byteInputStream())
        val session = (ReflectionTestUtils.getField(service, "sessions") as Map<*, *>)[id]!!
        ReflectionTestUtils.setField(session, "touched", Instant.now().minus(Duration.ofHours(25)))
        val published = Files.writeString(root.resolve("Published.iso"), "keep")
        service.expire()
        assertFailsWith<ResponseStatusException> { service.status("admin", id) }
        assertFalse(Files.exists(root.resolve("$GAME_UPLOAD_DIRECTORY/$id")))
        assertEquals("keep", Files.readString(published))
    }

    @Test
    fun `cancellation interrupts an active transfer and removes partial files`() {
        val (id, _) = prepare()
        val reading = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val input = object : InputStream() {
            override fun read(): Int {
                reading.countDown()
                assertTrue(closed.await(5, TimeUnit.SECONDS))
                return -1
            }
            override fun close() { closed.countDown() }
        }
        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            val transfer = executor.submit { assertFails { service.transfer("admin", id, 0, input) } }
            assertTrue(reading.await(5, TimeUnit.SECONDS))
            service.cancel("admin", id)
            transfer.get(5, TimeUnit.SECONDS)
        }
        assertFalse(Files.exists(root.resolve("$GAME_UPLOAD_DIRECTORY/$id")))
    }

    @Test
    fun `scans cannot see staging and preserve registered unknown file extensions`() {
        val config = mockk<ConfigService>()
        every { config.get(ConfigProperties.Libraries.Scan.GameFileExtensions) } returns arrayOf("iso")
        every { config.get(ConfigProperties.Libraries.Scan.ScanEmptyDirectories) } returns true
        val scanner = FilesystemService(config)
        val (id, metadata) = prepare(request("Original.unknown"))
        service.transfer("admin", id, 0, "abc".byteInputStream())
        assertTrue(scanner.scanLibraryForGamefiles(library).newPaths.isEmpty())
        service.complete("admin", id, metadata, emptyMap())
        assertTrue(scanner.scanLibraryForGamefiles(library).removedGamePaths.isEmpty())
        Files.delete(root.resolve("Original.unknown"))
        assertEquals(1, scanner.scanLibraryForGamefiles(library).removedGamePaths.size)
    }

    @Test
    fun `canonical upload paths remain registered when library directory is a symlink`() {
        val alias = Files.createSymbolicLink(temp.resolve("alias"), root)
        library.directories[0].internalPath = alias.toString()
        val (id, metadata) = prepare(request(directory = alias))
        service.transfer("admin", id, 0, "abc".byteInputStream())
        service.complete("admin", id, metadata, emptyMap())
        val config = mockk<ConfigService>()
        every { config.get(ConfigProperties.Libraries.Scan.GameFileExtensions) } returns arrayOf("iso")
        every { config.get(ConfigProperties.Libraries.Scan.ScanEmptyDirectories) } returns false
        val result = FilesystemService(config).scanLibraryForGamefiles(library)
        assertTrue(result.newPaths.isEmpty())
        assertTrue(result.removedGamePaths.isEmpty())
    }

    @Test
    fun `publication waits for library lock and concurrent completions create one game`() {
        val (id, metadata) = prepare()
        service.transfer("admin", id, 0, "abc".byteInputStream())
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            val blocker = executor.submit {
                LibraryOperationLock.withLibrary(1) { locked.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)) }
            }
            assertTrue(locked.await(5, TimeUnit.SECONDS))
            val started = CountDownLatch(1)
            val first = executor.submit<GameUploadStatus> { started.countDown(); service.complete("admin", id, metadata, emptyMap()) }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            assertFalse(Files.exists(root.resolve("Original Game.iso")))
            val other = executor.submit<GameUploadStatus> { service.complete("admin", id, metadata, emptyMap()) }
            release.countDown()
            blocker.get(5, TimeUnit.SECONDS)
            assertEquals(42L, first.get(5, TimeUnit.SECONDS).gameId)
            assertEquals(42L, other.get(5, TimeUnit.SECONDS).gameId)
        }
        verify(exactly = 1) { games.createPrepared(any(), any(), any()) }
    }

    @Test
    fun `large streams use bounded reads rather than buffering whole file`() {
        val size = 128L * 1024 * 1024
        val (id, _) = prepare(request().copy(files = listOf(GameUploadFileDto("Original Game.iso", size))))
        var remaining = size
        service.transfer("admin", id, 0, object : InputStream() {
            override fun read(): Int = error("Use bounded bulk reads")
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                assertTrue(length <= 64 * 1024)
                if (remaining == 0L) return -1
                val count = minOf(length.toLong(), remaining).toInt()
                remaining -= count
                return count
            }
        })
        assertEquals(size, Files.size(root.resolve("$GAME_UPLOAD_DIRECTORY/$id/payload")))
        service.cancel("admin", id)
    }
}
