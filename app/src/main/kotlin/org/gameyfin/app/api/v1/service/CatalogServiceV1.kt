package org.gameyfin.app.api.v1.service

import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.Predicate
import org.gameyfin.app.api.security.ApiAccess
import org.gameyfin.app.api.security.ApiScope
import org.gameyfin.app.api.v1.*
import org.gameyfin.app.api.v1.dto.*
import org.gameyfin.app.collections.CollectionService
import org.gameyfin.app.collections.dto.CollectionCreateDto
import org.gameyfin.app.collections.dto.CollectionUpdateDto
import org.gameyfin.app.collections.entities.Collection
import org.gameyfin.app.collections.repositories.CollectionRepository
import org.gameyfin.app.core.plugins.management.PluginManagementEntry
import org.gameyfin.app.games.GameService
import org.gameyfin.app.games.dto.GameImageSelectionDto
import org.gameyfin.app.games.dto.GameUpdateDto
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.entities.GameFieldMetadata
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.libraries.LibraryRepository
import org.gameyfin.app.libraries.LibraryService
import org.gameyfin.app.libraries.dto.DirectoryMappingDto
import org.gameyfin.app.libraries.dto.LibraryAdminDto
import org.gameyfin.app.libraries.dto.LibraryUpdateDto
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.pluginapi.gamemetadata.*
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import tools.jackson.databind.json.JsonMapper

@Service
@Transactional(readOnly = true)
class CatalogServiceV1(
    private val games: GameRepository,
    private val libraries: LibraryRepository,
    private val collections: CollectionRepository,
    private val gameService: GameService,
    private val libraryService: LibraryService,
    private val collectionService: CollectionService,
    private val mapper: JsonMapper,
    private val access: ApiAccess
) {
    fun games(
        page: Pageable,
        q: String?,
        libraryId: String?,
        collectionId: String?,
        platform: String?,
        externalProvider: String?,
        externalId: String?
    ): PageV1<GameV1> {
        require((externalProvider == null) == (externalId == null)) { "externalProvider and externalId must be supplied together" }
        require(q == null || q.length <= 255) { "Search term is too long" }
        val library = libraryId?.let(::apiId)
        val collection = collectionId?.let(::apiId)
        val platformValue = platform?.let { apiEnum<Platform>(it) }
        val spec = Specification<Game> { root, query, cb ->
            query.distinct(true)
            val predicates = mutableListOf<Predicate>()
            q?.let {
                val escaped = it.lowercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
                predicates += cb.like(cb.lower(root.get("title")), "%$escaped%", '\\')
            }
            library?.let { predicates += cb.equal(root.get<Any>("library").get<Long>("id"), it) }
            collection?.let { predicates += cb.equal(root.join<Any, Any>("collections").get<Long>("id"), it) }
            platformValue?.let { predicates += cb.equal(root.join<Game, Platform>("platforms"), it) }
            externalProvider?.let {
                val ids = root.join<Game, Any>("metadata")
                    .joinMap<Any, PluginManagementEntry, String>(
                        "originalIds",
                        JoinType.INNER
                    )
                predicates += cb.equal(ids.key().get<String>("pluginId"), it)
                predicates += cb.equal(ids.value(), externalId)
            }
            cb.and(*predicates.toTypedArray())
        }
        return games.findAll(spec, page).publicPage { it.publicGame() }
    }

    fun game(id: String) = gameEntity(id).publicGame()
    fun gameEntity(id: String): Game =
        games.findById(apiId(id)).orElseThrow { NoSuchElementException("Game not found") }

    fun libraryEntity(id: String): Library =
        libraries.findById(apiId(id)).orElseThrow { NoSuchElementException("Library not found") }

    fun collectionEntity(id: String): Collection =
        collections.findById(apiId(id)).orElseThrow { NoSuchElementException("Collection not found") }

    fun libraries(page: Pageable) = libraries.findAll(page).publicPage { it.publicLibrary() }
    fun library(id: String) = libraryEntity(id).publicLibrary()
    fun configuration(id: String) = libraryEntity(id).let {
        LibraryConfigurationV1(it.directories.map { d ->
            DirectoryV1(
                d.internalPath,
                d.externalPath
            )
        }, it.platforms.map { p -> p.name })
    }

    fun collections(page: Pageable) = collections.findAll(page).publicPage { it.publicCollection() }
    fun collection(id: String) = collectionEntity(id).publicCollection()

    @Transactional
    fun patchGame(id: String, values: Map<String, Any?>): GameV1 {
        val fields = setOf(
            "title",
            "platforms",
            "release",
            "summary",
            "comment",
            "publishers",
            "developers",
            "genres",
            "themes",
            "features",
            "perspectives",
            "keywords"
        )
        validatePatch(values, fields, setOf("release", "summary", "comment"))
        val patch = convert(values, GamePatchV1::class.java)
        patch.title?.let(::validateName)
        val game = gameEntity(id)
        gameService.edit(update(game.id!!, patch))
        if ("release" in values && patch.release == null) game.release = null
        if ("summary" in values && patch.summary == null) game.summary = null
        if ("comment" in values && patch.comment == null) game.comment = null
        val source = gameService.editingSourceFactory()
        values.keys.forEach { field ->
            game.metadata.fields[field]?.let { it.source = source() }
                ?: run { game.metadata.fields[field] = GameFieldMetadata(source = source()) }
        }
        games.flush()
        return game.publicGame()
    }

    @Transactional
    fun replaceMedia(id: String, media: GameMediaV1, files: Map<String, MultipartFile>): GameV1 {
        val game = gameEntity(id)
        fun ImageSelectionV1.internal() = GameImageSelectionDto(this.id?.let(::apiId), url, uploadKey)
        gameService.edit(
            update(game.id!!, GamePatchV1()).copy(
                cover = media.cover?.internal(), header = media.header?.internal(),
                images = media.images.map { it.internal() }, videoUrls = media.videoUrls, mediaOrder = media.mediaOrder
            ), files
        )
        gameService.clearArtwork(game.id!!, media.cover == null, media.header == null)
        games.flush()
        return game.publicGame()
    }

    private fun update(id: Long, patch: GamePatchV1) = GameUpdateDto(
        id, patch.title, patch.platforms?.map { apiEnum<Platform>(it) }?.toSet(), patch.release,
        null, null, patch.comment, patch.summary, patch.developers, patch.publishers,
        patch.genres?.map { apiEnum<Genre>(it) }, patch.themes?.map { apiEnum<Theme>(it) },
        patch.features?.map { apiEnum<GameFeature>(it) }, patch.perspectives?.map { apiEnum<PlayerPerspective>(it) },
        patch.keywords, null
    )

    @Transactional
    fun createLibrary(input: CreateLibraryV1): LibraryV1 {
        validateName(input.name)
        validateDirectories(input.directories)
        val id = libraryService.createReturningId(
            LibraryAdminDto(
                0,
                input.name,
                null,
                directories = input.directories.map { DirectoryMappingDto(it.internalPath, it.externalPath) },
                platforms = input.platforms.map { apiEnum<Platform>(it) },
                ignoredPaths = null,
                metadata = null
            ), false
        )
        libraries.flush()
        return library(id.toString())
    }

    @Transactional
    fun patchLibrary(id: String, values: Map<String, Any?>): LibraryV1 {
        validatePatch(values, setOf("name", "directories", "platforms"))
        val patch = convert(values, LibraryPatchV1::class.java)
        val library = libraryEntity(id)
        patch.name?.let(::validateName)
        patch.directories?.let { directories ->
            validateDirectories(directories)
            if (library.directories.any { existing -> directories.none { it.internalPath == existing.internalPath } }) {
                access.require(ApiScope.GAMES_DELETE)
            }
        }
        libraryService.update(
            LibraryUpdateDto(
                library.id!!, patch.name,
                patch.directories?.map { DirectoryMappingDto(it.internalPath, it.externalPath) },
                patch.platforms?.map { apiEnum<Platform>(it) })
        )
        libraries.flush()
        return library.publicLibrary()
    }

    @Transactional
    fun createCollection(input: CreateCollectionV1): CollectionV1 {
        validateName(input.name)
        val id = collectionService.createReturningId(CollectionCreateDto(input.name, input.description))
        collections.flush()
        return collection(id.toString())
    }

    @Transactional
    fun patchCollection(id: String, values: Map<String, Any?>): CollectionV1 {
        validatePatch(values, setOf("name", "description"), setOf("description"))
        val patch = convert(values, CollectionPatchV1::class.java)
        val collection = collectionEntity(id)
        patch.name?.let(::validateName)
        collectionService.update(
            CollectionUpdateDto(
                collection.id!!,
                patch.name?.takeUnless { it == collection.name },
                patch.description
            )
        )
        if ("description" in values && patch.description == null) collection.description = null
        collections.flush()
        return collection.publicCollection()
    }

    @Transactional
    fun membership(collectionId: String, gameId: String, present: Boolean) {
        val collection = collectionEntity(collectionId)
        val game = gameEntity(gameId)
        val exists = collection.games.any { it.id == game.id }
        if (present && !exists) collectionService.addGame(collection.id!!, game.id!!)
        if (!present && exists) collectionService.removeGame(collection.id!!, game.id!!)
    }

    private fun validateDirectories(directories: List<DirectoryV1>) {
        require(directories.size in 1..100 && directories.all { it.internalPath.isNotBlank() }) { "Provide 1 to 100 nonempty directory paths" }
        require(directories.map { it.internalPath }.distinct().size == directories.size) { "Duplicate directories" }
    }

    private fun validateName(value: String) {
        require(value.isNotBlank() && value.length <= 255) { "Name must contain 1 to 255 characters" }
    }

    private fun validatePatch(values: Map<String, Any?>, allowed: Set<String>, nullable: Set<String> = emptySet()) {
        require(allowed.containsAll(values.keys)) { "Unknown or read-only field" }
        require(values.none { it.value == null && it.key !in nullable }) { "Null is not allowed for this field" }
    }

    private fun <T> convert(values: Map<String, Any?>, type: Class<T>): T = try {
        mapper.convertValue(values, type)
    } catch (_: Exception) {
        throw IllegalArgumentException("Invalid field value")
    }
}
