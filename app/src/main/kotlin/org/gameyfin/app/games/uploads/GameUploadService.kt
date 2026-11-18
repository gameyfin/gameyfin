package org.gameyfin.app.games.uploads

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.app.core.filesystem.GAME_UPLOAD_DIRECTORY
import org.gameyfin.app.core.plugins.dto.ExternalProviderIdDto
import org.gameyfin.app.games.GameService
import org.gameyfin.app.games.dto.GameUpdateDto
import org.gameyfin.app.libraries.LibraryCoreService
import org.gameyfin.app.libraries.LibraryOperationLock
import org.gameyfin.app.libraries.LibraryRepository
import org.gameyfin.app.libraries.entities.Library
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.io.InputStream
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Upload state is deliberately transient: interrupted uploads restart rather than resume. */
@Service
class GameUploadService(
    private val libraries: LibraryCoreService,
    private val libraryRepository: LibraryRepository,
    private val games: GameService,
    transactionManager: PlatformTransactionManager
) {
    private class Session(
        val id: UUID,
        val owner: String,
        val request: GameUploadRequest,
        val target: Path,
        val staging: Path
    ) {
        val lock = ReentrantLock()
        val cancelled = AtomicBoolean()
        val completed = mutableSetOf<Int>()
        var prepared: GameService.PreparedMetadata? = null

        @Volatile
        var touched: Instant = Instant.now()

        @Volatile
        var gameId: Long? = null

        @Volatile
        var input: InputStream? = null
        val payload: Path get() = staging.resolve("payload")
    }

    private val sessions = ConcurrentHashMap<UUID, Session>()
    private val transaction = TransactionTemplate(transactionManager)
    private val log = KotlinLogging.logger {}
    private val marker = "gameyfin-upload-staging-v1"

    companion object {
        const val OWNER_DIR = ".owner"
    }

    /** Preflight checks are read-only; creation and publication recheck before changing files. */
    fun validateDestination(request: GameUploadDestinationDto) {
        LibraryOperationLock.withLibrary(request.libraryId) {
            val root = destination(request.directory, library(request.libraryId))
            availableDestination(root, request.name)
        }
    }

    fun create(owner: String, request: GameUploadRequest): GameUploadStatus =
        LibraryOperationLock.withLibrary(request.libraryId) {
            validateManifest(request)
            val root = destination(request.directory, library(request.libraryId))
            val target = availableDestination(root, request.name)
            val stagingRoot = stagingRoot(root)
            val id = UUID.randomUUID()
            val staging = Files.createDirectory(stagingRoot.resolve(id.toString()))
            val session = Session(id, owner, request, target, staging)
            sessions[id] = session
            status(session)
        }

    fun prepare(owner: String, id: UUID, originalIds: Map<String, ExternalProviderIdDto>): GameUpdateDto =
        withSession(owner, id) { session ->
            require(session.gameId == null && session.completed.isEmpty()) { "Upload has already started" }
            val library = library(session.request.libraryId)
            checkDestination(session, library)
            val prepared = games.prepareMetadata(originalIds, session.target, library)
            session.prepared = prepared
            prepared.draft.uploadDraft()
        }

    /** Each submission starts over, including all previously transferred files. */
    fun restart(owner: String, id: UUID) = withSession(owner, id) { session ->
        require(session.gameId == null) { "Game has already been published" }
        checkStaging(session)
        deleteTree(session.payload)
        session.completed.clear()
    }

    fun transfer(owner: String, id: UUID, index: Int, input: InputStream) = withSession(owner, id) { session ->
        require(session.gameId == null && session.prepared != null) { "Prepare metadata before uploading" }
        require(index in session.request.files.indices && index !in session.completed) { "Invalid file index" }
        checkDestination(session, library(session.request.libraryId))
        checkStaging(session)
        val file = session.request.files[index]
        val target = if (session.request.folder) {
            resolveWithin(session.payload, file.path).also { createPayloadDirectories(session.staging, it.parent) }
        } else session.payload
        session.input = input
        try {
            Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
                val buffer = ByteArray(64 * 1024)
                var received = 0L
                while (true) {
                    checkActive(session)
                    val count = input.read(buffer)
                    if (count == -1) break
                    require(count.toLong() <= file.size - received) { "File exceeds declared size" }
                    output.write(buffer, 0, count)
                    received += count
                    session.touched = Instant.now()
                }
                require(received == file.size) { "Incomplete file upload" }
            }
            session.completed.add(index)
        } catch (e: Exception) {
            runCatching {
                checkStaging(session)
                deleteTree(session.payload)
            }.onFailure { e.addSuppressed(it) }
            session.completed.clear()
            throw e
        } finally {
            session.input = null
        }
    }

    fun complete(
        owner: String,
        id: UUID,
        metadata: GameUpdateDto,
        media: Map<String, MultipartFile>
    ): GameUploadStatus =
        withSession(owner, id) { session ->
            if (session.gameId == null) {
                require(session.completed.size == session.request.files.size) { "Upload every file before publishing" }
                val prepared = requireNotNull(session.prepared) { "Metadata is required" }
                require(!(metadata.title ?: prepared.draft.title).isNullOrBlank()) { "Title is required" }
                checkStaging(session)
                LibraryOperationLock.withLibrary(session.request.libraryId) {
                    val library = library(session.request.libraryId)
                    checkDestination(session, library)
                    val target = session.target
                    conflictIfExists(target)
                    val payloadKey = Files.readAttributes(
                        session.payload,
                        BasicFileAttributes::class.java,
                        LinkOption.NOFOLLOW_LINKS
                    ).fileKey()
                    var published = false
                    try {
                        val gameId = transaction.execute {
                            checkActive(session)
                            // No replacement option: a concurrent destination must never be overwritten.
                            Files.move(session.payload, target)
                            published = true
                            val game = games.createPrepared(prepared, target, library)
                            games.edit(metadata.copy(id = game.id!!, title = metadata.title?.trim()), media)
                            libraries.addGamesToLibrary(listOf(game), library, true)
                            checkActive(session)
                            game.id
                        }!!
                        session.gameId = gameId
                        runCatching { deleteTree(session.staging) }
                            .onFailure { log.warn(it) { "Could not remove completed upload staging" } }
                    } catch (e: Exception) {
                        if (published) {
                            // Keep completed files available for a fresh submission if persistence fails.
                            runCatching {
                                val currentKey = Files.readAttributes(
                                    target,
                                    BasicFileAttributes::class.java,
                                    LinkOption.NOFOLLOW_LINKS
                                ).fileKey()
                                check(payloadKey != null && payloadKey == currentKey) { "Published files changed; preserving them for recovery" }
                                Files.move(target, session.payload)
                            }.onFailure { e.addSuppressed(it) }
                        }
                        throw e
                    }
                }
            }
            status(session)
        }

    fun status(owner: String, id: UUID): GameUploadStatus = withSession(owner, id) { status(it) }

    fun cancel(owner: String, id: UUID) {
        val session = owned(owner, id)
        if (session.gameId != null) return
        session.cancelled.set(true)
        runCatching { session.input?.close() }
        session.lock.withLock {
            if (session.gameId == null) {
                checkStaging(session)
                deleteTree(session.staging)
            }
            sessions.remove(id, session)
        }
    }

    @Scheduled(fixedDelay = 60 * 60 * 1000)
    fun expire() {
        val cutoff = Instant.now().minus(Duration.ofHours(24))
        sessions.values.forEach { session ->
            if (session.touched.isBefore(cutoff) && session.lock.tryLock()) {
                try {
                    if (session.touched.isBefore(cutoff)) {
                        session.cancelled.set(true)
                        if (Files.exists(session.staging, LinkOption.NOFOLLOW_LINKS)) {
                            checkStaging(session)
                            deleteTree(session.staging)
                        }
                        sessions.remove(session.id, session)
                    }
                } finally {
                    session.lock.unlock()
                }
            }
        }
    }

    @EventListener(ApplicationReadyEvent::class)
    fun cleanInterruptedUploads() {
        libraryRepository.findAll().flatMap { it.directories }.map { it.internalPath }.distinct().forEach { directory ->
            runCatching {
                val root = Path.of(directory).toRealPath().resolve(GAME_UPLOAD_DIRECTORY)
                if (ownedStagingRoot(root)) {
                    Files.newDirectoryStream(root).use { children ->
                        children.filter { child ->
                            runCatching { UUID.fromString(child.fileName.toString()) }.getOrNull()
                                ?.let { !sessions.containsKey(it) } == true
                        }
                            .forEach(::deleteTree)
                    }
                }
            }.onFailure { log.warn(it) { "Could not clean interrupted game uploads in $directory" } }
        }
    }

    private fun library(id: Long): Library = libraries.getWithGamesAndIgnoredPaths(id)
        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Library not found")

    private fun availableDestination(root: Path, name: String): Path {
        component(name)
        require(name != GAME_UPLOAD_DIRECTORY) { "This folder name is reserved for uploads" }
        val target = resolveWithin(root, name)
        conflictIfExists(target)
        return target
    }

    private fun destination(directory: String, library: Library): Path {
        val configured = requireNotNull(library.directories.firstOrNull { it.internalPath == directory }) {
            "Select a configured library directory"
        }
        val root = Path.of(configured.internalPath).toRealPath()
        require(Files.isDirectory(root) && Files.isWritable(root)) { "Library directory must be writable" }
        return root
    }

    private fun checkDestination(session: Session, library: Library) {
        require(destination(session.request.directory, library) == session.target.parent) { "Library directory has changed" }
    }

    private fun checkStaging(session: Session) {
        require(ownedStagingRoot(session.staging.parent) && session.staging.toRealPath() == session.staging) {
            "Upload staging directory has changed"
        }
    }

    private fun stagingRoot(root: Path): Path {
        val staging = root.resolve(GAME_UPLOAD_DIRECTORY)
        if (!Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectory(staging)
            Files.writeString(staging.resolve(OWNER_DIR), marker, StandardOpenOption.CREATE_NEW)
        }
        require(ownedStagingRoot(staging)) { "Reserved upload directory already exists or is unsafe" }
        return staging
    }

    private fun ownedStagingRoot(path: Path): Boolean =
        Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path) &&
                Files.isRegularFile(path.resolve(OWNER_DIR), LinkOption.NOFOLLOW_LINKS) &&
                Files.readString(path.resolve(OWNER_DIR)) == marker

    private fun resolveWithin(root: Path, name: String): Path {
        val target = root.resolve(name).normalize()
        if (!target.startsWith(root) || target == root) throw IllegalArgumentException("Invalid upload path")
        return target
    }

    private fun createPayloadDirectories(staging: Path, parent: Path) {
        var directory = staging
        for (component in staging.relativize(parent)) {
            directory = directory.resolve(component)
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(directory)
            require(Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) { "Invalid upload directory" }
        }
    }

    private fun validateManifest(request: GameUploadRequest) {
        require(request.files.isNotEmpty() && request.files.size <= 100_000) { "Select between 1 and 100000 files" }
        if (!request.folder) require(request.files.size == 1 && request.files.single().path == request.name) {
            "Select one file per game"
        }
        val paths = HashSet<String>()
        var total = 0L
        request.files.forEach { file ->
            require(file.size >= 0) { "Invalid file size" }
            total = Math.addExact(total, file.size)
            file.path.split('/').forEach(::component)
            require(paths.add(file.path)) { "Duplicate file path" }
        }
        paths.forEach { path ->
            var parent = path.substringBeforeLast('/', "")
            while (parent.isNotEmpty()) {
                require(parent !in paths) { "Conflicting file and directory paths" }
                parent = parent.substringBeforeLast('/', "")
            }
        }
    }

    private fun component(name: String) {
        require(
            name.isNotEmpty() && name != "." && name != ".." &&
                    name.none { it == '/' || it == '\\' || it == ':' || it.code < 32 || it.code == 127 }) { "Invalid file or folder name" }
        // Reject rather than silently normalize names on Windows.
        if (System.getProperty("os.name").lowercase().contains("win")) {
            require(name.none { it in "<>\"|?*" } && !name.endsWith('.') && !name.endsWith(' ') &&
                    !name.substringBefore('.').uppercase().matches(Regex("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]"))) {
                "Unsupported file or folder name"
            }
        }
    }

    private fun conflictIfExists(path: Path) {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "A file or folder with this name already exists")
        }
    }

    private fun owned(owner: String, id: UUID): Session = sessions[id]?.takeIf { it.owner == owner }
        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Upload session not found or expired")

    private fun checkActive(session: Session) {
        if (session.cancelled.get()) throw ResponseStatusException(HttpStatus.CONFLICT, "Upload cancelled")
    }

    private fun <T> withSession(owner: String, id: UUID, action: (Session) -> T): T {
        val session = owned(owner, id)
        return session.lock.withLock {
            checkActive(session)
            session.touched = Instant.now()
            action(session)
        }
    }

    private fun status(session: Session) = GameUploadStatus(session.id, session.gameId, session.completed.size)

    private fun deleteTree(path: Path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: java.io.IOException?): FileVisitResult {
                if (exc != null) throw exc
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }
}
