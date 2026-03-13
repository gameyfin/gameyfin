package org.gameyfin.app.games

import io.mockk.*
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.core.filesystem.FilesystemService
import org.gameyfin.app.core.plugins.PluginService
import org.gameyfin.app.core.plugins.dto.ExternalProviderIdDto
import org.gameyfin.app.core.plugins.management.GameyfinPluginManager
import org.gameyfin.app.core.plugins.management.PluginManagementEntry
import org.gameyfin.app.games.dto.GameDto
import org.gameyfin.app.games.dto.GameUpdateDto
import org.gameyfin.app.games.dto.GameUpdateMetadataDto
import org.gameyfin.app.games.entities.*
import org.gameyfin.app.games.extensions.toDtos
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.app.media.Image
import org.gameyfin.app.media.ImageService
import org.gameyfin.app.media.ImageType
import org.gameyfin.app.users.UserService
import org.gameyfin.app.users.entities.User
import org.gameyfin.pluginapi.gamemetadata.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.pf4j.PluginWrapper
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GameServiceTest {

    private lateinit var gameRepository: GameRepository
    private lateinit var pluginManager: GameyfinPluginManager
    private lateinit var pluginService: PluginService
    private lateinit var config: ConfigService
    private lateinit var companyService: CompanyService
    private lateinit var userService: UserService
    private lateinit var imageService: ImageService
    private lateinit var filesystemService: FilesystemService
    private lateinit var gameService: GameService

    private lateinit var library: Library
    private lateinit var mockUser: User
    private lateinit var securityContext: SecurityContext
    private lateinit var authentication: Authentication

    @BeforeEach
    fun setup() {
        gameRepository = mockk()
        pluginManager = mockk()
        pluginService = mockk()
        config = mockk()
        companyService = mockk()
        userService = mockk()
        imageService = mockk()
        filesystemService = mockk()

        gameService = GameService(
            gameRepository,
            pluginManager,
            pluginService,
            config,
            companyService,
            userService,
            imageService,
            filesystemService
        )

        library = mockk(relaxed = true) {
            every { id } returns 1L
            every { platforms } returns mutableListOf()
        }

        mockUser = mockk {
            every { username } returns "testuser"
        }

        securityContext = mockk()
        authentication = mockk()
        mockkStatic(SecurityContextHolder::class)
        every { SecurityContextHolder.getContext() } returns securityContext
        every { securityContext.authentication } returns authentication

        mockkStatic("org.gameyfin.app.games.extensions.GameExtensionsKt")
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `getAll should return all games as DTOs`() {
        val game1 = createTestGame(1L)
        val game2 = createTestGame(2L)
        val games = listOf(game1, game2)
        val gameDtos = mockk<List<GameDto>>()

        every { gameRepository.findAll() } returns games
        every { games.toDtos() } returns gameDtos

        val result = gameService.getAll()

        assertEquals(gameDtos, result)
        verify(exactly = 1) { gameRepository.findAll() }
    }

    @Test
    fun `getAll should return empty list when no games exist`() {
        val emptyList = emptyList<Game>()
        val emptyDtoList = emptyList<GameDto>()

        every { gameRepository.findAll() } returns emptyList
        every { emptyList.toDtos() } returns emptyDtoList

        val result = gameService.getAll()

        assertEquals(emptyList(), result)
        verify(exactly = 1) { gameRepository.findAll() }
    }

    @Test
    fun `create with list should process and save games`() {
        val game1 = createTestGame(id = null)
        val game2 = createTestGame(id = null)
        val games = listOf(game1, game2)
        val publisher = Company(id = 1L, name = "Publisher", type = CompanyType.PUBLISHER)
        val developer = Company(id = 2L, name = "Developer", type = CompanyType.DEVELOPER)
        val image = mockk<Image>(relaxed = true)

        every { companyService.createOrGet(any()) } returnsMany listOf(publisher, developer, publisher, developer)
        every { imageService.createOrGet(any()) } returns image
        every { gameRepository.saveAll(games) } returns games

        val result = gameService.create(games)

        assertEquals(games, result)
        verify(exactly = 1) { gameRepository.saveAll(games) }
    }

    @Test
    fun `create with list should skip games with existing IDs`() {
        val game1 = createTestGame(id = 1L)
        val game2 = createTestGame(id = null)
        val games = listOf(game1, game2)

        every { companyService.createOrGet(any()) } returns mockk(relaxed = true)
        every { imageService.createOrGet(any()) } returns mockk(relaxed = true)
        every { gameRepository.saveAll(listOf(game2)) } returns listOf(game2)

        val result = gameService.create(games)

        assertEquals(listOf(game2), result)
        verify(exactly = 1) { gameRepository.saveAll(listOf(game2)) }
    }

    @Test
    fun `edit should update game title when provided`() {
        val gameId = 1L
        val existingGame = createTestGame(gameId)
        val userDetails = mockk<UserDetails> {
            every { username } returns "testuser"
        }

        every { authentication.principal } returns userDetails
        every { gameRepository.findByIdOrNull(gameId) } returns existingGame
        every { userService.getByUsernameNonNull("testuser") } returns mockUser
        every { gameRepository.save(existingGame) } returns existingGame

        val updateDto = GameUpdateDto(
            id = gameId,
            title = "Updated Title",
            platforms = null,
            release = null,
            coverUrl = null,
            headerUrl = null,
            comment = null,
            summary = null,
            developers = null,
            publishers = null,
            genres = null,
            themes = null,
            keywords = null,
            features = null,
            perspectives = null,
            metadata = null
        )

        gameService.edit(updateDto)

        assertEquals("Updated Title", existingGame.title)
        verify(exactly = 1) { gameRepository.save(existingGame) }
    }

    @Test
    fun `edit should throw exception when game not found`() {
        val gameId = 999L
        every { gameRepository.findByIdOrNull(gameId) } returns null

        val updateDto = GameUpdateDto(
            id = gameId,
            title = "Updated Title",
            platforms = null,
            release = null,
            coverUrl = null,
            headerUrl = null,
            comment = null,
            summary = null,
            developers = null,
            publishers = null,
            genres = null,
            themes = null,
            keywords = null,
            features = null,
            perspectives = null,
            metadata = null
        )

        assertThrows(IllegalArgumentException::class.java) {
            gameService.edit(updateDto)
        }
    }

    @Test
    fun `edit should update platforms when provided`() {
        val gameId = 1L
        val existingGame = createTestGame(gameId)
        val userDetails = mockk<UserDetails> {
            every { username } returns "testuser"
        }
        val newPlatforms = setOf(Platform.PLAYSTATION_5, Platform.XBOX_SERIES_X_S)

        every { authentication.principal } returns userDetails
        every { gameRepository.findByIdOrNull(gameId) } returns existingGame
        every { userService.getByUsernameNonNull("testuser") } returns mockUser
        every { gameRepository.save(existingGame) } returns existingGame

        val updateDto = GameUpdateDto(
            id = gameId,
            title = null,
            platforms = newPlatforms,
            release = null,
            coverUrl = null,
            headerUrl = null,
            comment = null,
            summary = null,
            developers = null,
            publishers = null,
            genres = null,
            themes = null,
            keywords = null,
            features = null,
            perspectives = null,
            metadata = null
        )

        gameService.edit(updateDto)

        assertEquals(newPlatforms.toList(), existingGame.platforms)
        verify(exactly = 1) { gameRepository.save(existingGame) }
    }

    @Test
    fun `edit should update release date when provided`() {
        val gameId = 1L
        val existingGame = createTestGame(gameId)
        val userDetails = mockk<UserDetails> {
            every { username } returns "testuser"
        }
        val newReleaseDate = LocalDate.of(2024, 6, 15)

        every { authentication.principal } returns userDetails
        every { gameRepository.findByIdOrNull(gameId) } returns existingGame
        every { userService.getByUsernameNonNull("testuser") } returns mockUser
        every { gameRepository.save(existingGame) } returns existingGame

        val updateDto = GameUpdateDto(
            id = gameId,
            title = null,
            platforms = null,
            release = newReleaseDate,
            coverUrl = null,
            headerUrl = null,
            comment = null,
            summary = null,
            developers = null,
            publishers = null,
            genres = null,
            themes = null,
            keywords = null,
            features = null,
            perspectives = null,
            metadata = null
        )

        gameService.edit(updateDto)

        val expectedInstant = newReleaseDate.atStartOfDay(ZoneOffset.UTC).toInstant()
        assertEquals(expectedInstant, existingGame.release)
        verify(exactly = 1) { gameRepository.save(existingGame) }
    }

    @Test
    fun `edit should create and download new cover image when coverUrl provided`() {
        val gameId = 1L
        val existingGame = createTestGame(gameId)
        val userDetails = mockk<UserDetails> {
            every { username } returns "testuser"
        }
        val newCoverUrl = "https://example.com/new-cover.jpg"
        val newCoverImage = mockk<Image>(relaxed = true)

        every { authentication.principal } returns userDetails
        every { gameRepository.findByIdOrNull(gameId) } returns existingGame
        every { userService.getByUsernameNonNull("testuser") } returns mockUser
        every { imageService.createOrGet(any()) } returns newCoverImage
        every { imageService.downloadIfNew(newCoverImage) } just Runs
        every { gameRepository.save(existingGame) } returns existingGame

        val updateDto = GameUpdateDto(
            id = gameId,
            title = null,
            platforms = null,
            release = null,
            coverUrl = newCoverUrl,
            headerUrl = null,
            comment = null,
            summary = null,
            developers = null,
            publishers = null,
            genres = null,
            themes = null,
            keywords = null,
            features = null,
            perspectives = null,
            metadata = null
        )

        gameService.edit(updateDto)

        assertEquals(newCoverImage, existingGame.coverImage)
        verify(exactly = 1) { imageService.createOrGet(match { it.originalUrl == newCoverUrl && it.type == ImageType.COVER }) }
        verify(exactly = 1) { imageService.downloadIfNew(newCoverImage) }
        verify(exactly = 1) { gameRepository.save(existingGame) }
    }

    @Test
    fun `edit should update developers when provided`() {
        val gameId = 1L
        val existingGame = createTestGame(gameId)
        val userDetails = mockk<UserDetails> {
            every { username } returns "testuser"
        }
        val newDevelopers = listOf("Dev1", "Dev2")
        val company1 = Company(id = 1L, name = "Dev1", type = CompanyType.DEVELOPER)
        val company2 = Company(id = 2L, name = "Dev2", type = CompanyType.DEVELOPER)

        every { authentication.principal } returns userDetails
        every { gameRepository.findByIdOrNull(gameId) } returns existingGame
        every { userService.getByUsernameNonNull("testuser") } returns mockUser
        every { companyService.createOrGet(match { it.name == "Dev1" }) } returns company1
        every { companyService.createOrGet(match { it.name == "Dev2" }) } returns company2
        every { gameRepository.save(existingGame) } returns existingGame

        val updateDto = GameUpdateDto(
            id = gameId,
            title = null,
            platforms = null,
            release = null,
            coverUrl = null,
            headerUrl = null,
            comment = null,
            summary = null,
            developers = newDevelopers,
            publishers = null,
            genres = null,
            themes = null,
            keywords = null,
            features = null,
            perspectives = null,
            metadata = null
        )

        gameService.edit(updateDto)

        assertEquals(listOf(company1, company2), existingGame.developers)
        verify(exactly = 1) { gameRepository.save(existingGame) }
    }

    @Test
    fun `edit should update publishers when provided`() {
        val gameId = 1L
        val existingGame = createTestGame(gameId)
        val userDetails = mockk<UserDetails> {
            every { username } returns "testuser"
        }
        val newPublishers = listOf("Pub1", "Pub2")
        val company1 = Company(id = 1L, name = "Pub1", type = CompanyType.PUBLISHER)
        val company2 = Company(id = 2L, name = "Pub2", type = CompanyType.PUBLISHER)

        every { authentication.principal } returns userDetails
        every { gameRepository.findByIdOrNull(gameId) } returns existingGame
        every { userService.getByUsernameNonNull("testuser") } returns mockUser
        every { companyService.createOrGet(match { it.name == "Pub1" }) } returns company1
        every { companyService.createOrGet(match { it.name == "Pub2" }) } returns company2
        every { gameRepository.save(existingGame) } returns existingGame

        val updateDto = GameUpdateDto(
            id = gameId,
            title = null,
            platforms = null,
            release = null,
            coverUrl = null,
            headerUrl = null,
            comment = null,
            summary = null,
            developers = null,
            publishers = newPublishers,
            genres = null,
            themes = null,
            keywords = null,
            features = null,
            perspectives = null,
            metadata = null
        )

        gameService.edit(updateDto)

        assertEquals(listOf(company1, company2), existingGame.publishers)
        verify(exactly = 1) { gameRepository.save(existingGame) }
    }

    @Test
    fun `edit should update matchConfirmed metadata field when provided`() {
        val gameId = 1L
        val existingGame = createTestGame(gameId)
        val userDetails = mockk<UserDetails> {
            every { username } returns "testuser"
        }

        every { authentication.principal } returns userDetails
        every { gameRepository.findByIdOrNull(gameId) } returns existingGame
        every { userService.getByUsernameNonNull("testuser") } returns mockUser
        every { gameRepository.save(existingGame) } returns existingGame

        val updateDto = GameUpdateDto(
            id = gameId,
            title = null,
            platforms = null,
            release = null,
            coverUrl = null,
            headerUrl = null,
            comment = null,
            summary = null,
            developers = null,
            publishers = null,
            genres = null,
            themes = null,
            keywords = null,
            features = null,
            perspectives = null,
            metadata = GameUpdateMetadataDto(matchConfirmed = true)
        )

        gameService.edit(updateDto)

        assertEquals(true, existingGame.metadata.matchConfirmed)
        verify(exactly = 1) { gameRepository.save(existingGame) }
    }

    @Test
    fun `delete should remove game from repository`() {
        val gameId = 1L
        every { gameRepository.deleteById(gameId) } just Runs

        gameService.delete(gameId)

        verify(exactly = 1) { gameRepository.deleteById(gameId) }
    }

    @Test
    fun `getById should return game when found`() {
        val gameId = 1L
        val game = createTestGame(gameId)
        every { gameRepository.findByIdOrNull(gameId) } returns game

        val result = gameService.getById(gameId)

        assertEquals(game, result)
        verify(exactly = 1) { gameRepository.findByIdOrNull(gameId) }
    }

    @Test
    fun `getById should throw exception when game not found`() {
        val gameId = 999L
        every { gameRepository.findByIdOrNull(gameId) } returns null

        assertThrows(IllegalArgumentException::class.java) {
            gameService.getById(gameId)
        }
    }

    @Test
    fun `incrementDownloadCount should increment and save`() {
        val game = createTestGame(1L)
        val initialCount = game.metadata.downloadCount

        every { gameRepository.save(game) } returns game

        gameService.incrementDownloadCount(game)

        assertEquals(initialCount + 1, game.metadata.downloadCount)
        verify(exactly = 1) { gameRepository.save(game) }
    }

    @Test
    fun `getPotentialMatches should return empty list when no plugins available`() {
        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns emptyList()

        val result = gameService.getPotentialMatches("Test Game", emptySet())

        assertEquals(emptyList(), result)
    }

    @Test
    fun `matchManually should return null when no valid results found`() {
        val originalIds = mapOf<String, ExternalProviderIdDto>()
        val path = Path.of("/test/game.exe")

        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns emptyList()

        val result = gameService.matchManually(originalIds, path, library, null, persist = false)

        assertNull(result)
    }

    @Test
    fun `matchManually should set replaceGameId when provided`() {
        val originalIds = mapOf(
            "org.gameyfin.app.games.TestProvider" to ExternalProviderIdDto("test-plugin", "123")
        )
        val path = Path.of("/test/game.exe")
        val replaceGameId = 5L
        val existingGame = createTestGame(replaceGameId)
        val metadata = org.gameyfin.pluginapi.gamemetadata.GameMetadata(
            originalId = "123",
            title = "Test Game",
            platforms = setOf(Platform.PC_MICROSOFT_WINDOWS)
        )

        val provider = spyk(TestProvider(metadata))

        val pluginEntry = mockk<PluginManagementEntry>(relaxed = true) {
            every { pluginId } returns "test-plugin"
            every { priority } returns 1
        }

        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(provider)
        every { pluginService.getPluginManagementEntry(provider.javaClass) } returns pluginEntry
        every { gameRepository.findByIdOrNull(replaceGameId) } returns existingGame
        every { imageService.createOrGet(any()) } returns mockk(relaxed = true)
        every { filesystemService.calculateFileSize(any()) } returns 1000L

        val result = gameService.matchManually(originalIds, path, library, replaceGameId, persist = false)

        assertNotNull(result)
        assertEquals(replaceGameId, result.id)
        assertEquals(existingGame.createdAt, result.createdAt)
    }

    @Test
    fun `update should return null when game not found`() {
        val game = createTestGame(999L)
        every { gameRepository.findByIdOrNull(999L) } returns null

        assertThrows(IllegalArgumentException::class.java) {
            gameService.updateMetadata(game)
        }
    }

    @Test
    fun `update should return null when plugin manager cannot find provider`() {
        val pluginEntry = mockk<PluginManagementEntry> {
            every { pluginId } returns "test-plugin"
        }
        val game = createTestGame(1L)
        game.metadata.originalIds = mapOf(pluginEntry to "123")

        every { gameRepository.findByIdOrNull(1L) } returns game
        every { pluginManager.getExtensions("test-plugin") } returns emptyList<Any>()

        assertThrows(NoSuchElementException::class.java) {
            gameService.updateMetadata(game)
        }
    }

    @Test
    fun `update should return null when matchManually fails`() {
        val pluginEntry = mockk<PluginManagementEntry>(relaxed = true) {
            every { pluginId } returns "test-plugin"
        }
        val game = createTestGame(1L)
        game.metadata.originalIds = mapOf(pluginEntry to "123")

        val provider = mockk<GameMetadataProvider>(relaxed = true) {
            every { fetchById("123") } throws RuntimeException("Failed to fetch")
        }

        every { gameRepository.findByIdOrNull(1L) } returns game
        every { pluginManager.getExtensions("test-plugin") } returns listOf(provider)
        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(provider)
        every { pluginService.getPluginManagementEntry(provider.javaClass) } returns pluginEntry
        every { pluginManager.getPluginForExtension(provider::class) } returns null

        val result = gameService.updateMetadata(game)

        assertNull(result)
    }

    @Test
    fun `update should return null when no fields were updated`() {
        val pluginEntry = mockk<PluginManagementEntry>(relaxed = true) {
            every { pluginId } returns "test-plugin"
            every { priority } returns 1
        }
        val game = createTestGame(1L)
        game.metadata.originalIds = mapOf(pluginEntry to "123")
        game.title = "Test Game"
        game.platforms = mutableListOf(Platform.PC_MICROSOFT_WINDOWS)
        // Add existing field metadata to simulate a previously matched game
        game.metadata.fields["title"] = GameFieldMetadata(source = GameFieldPluginSource(plugin = pluginEntry))
        game.metadata.fields["platforms"] = GameFieldMetadata(source = GameFieldPluginSource(plugin = pluginEntry))

        val metadata = org.gameyfin.pluginapi.gamemetadata.GameMetadata(
            originalId = "123",
            title = "Test Game",
            release = game.release,
            platforms = setOf(Platform.PC_MICROSOFT_WINDOWS)
        )
        val provider = spyk(TestProvider(metadata))

        every { gameRepository.findByIdOrNull(1L) } returns game
        every { pluginManager.getExtensions("test-plugin") } returns listOf(provider)
        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(provider)
        every { pluginService.getPluginManagementEntry(provider.javaClass) } returns pluginEntry
        every { imageService.createOrGet(any()) } returns mockk(relaxed = true)
        every { filesystemService.calculateFileSize(any()) } returns 1000L
        every { library.platforms } returns mutableListOf(Platform.PC_MICROSOFT_WINDOWS)

        val result = gameService.updateMetadata(game)

        assertNull(result)
    }

    @Test
    fun `update should update title when changed and not user-modified`() {
        val pluginEntry = mockk<PluginManagementEntry>(relaxed = true) {
            every { pluginId } returns "test-plugin"
            every { priority } returns 1
        }
        val game = createTestGame(1L)
        game.metadata.originalIds = mapOf(pluginEntry to "123")
        game.title = "Old Title"
        game.metadata.fields["title"] = GameFieldMetadata(source = GameFieldPluginSource(plugin = pluginEntry))

        val metadata = org.gameyfin.pluginapi.gamemetadata.GameMetadata(
            originalId = "123",
            title = "New Title",
            platforms = setOf(Platform.PC_MICROSOFT_WINDOWS)
        )
        val provider = spyk(TestProvider(metadata))

        every { gameRepository.findByIdOrNull(1L) } returns game
        every { pluginManager.getExtensions("test-plugin") } returns listOf(provider)
        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(provider)
        every { pluginService.getPluginManagementEntry(provider.javaClass) } returns pluginEntry
        every { imageService.createOrGet(any()) } returns mockk(relaxed = true)
        every { filesystemService.calculateFileSize(any()) } returns 1000L

        val result = gameService.updateMetadata(game)

        assertNotNull(result)
        assertEquals("New Title", result.title)
    }

    @Test
    fun `update should not update title when user-modified`() {
        val pluginEntry = mockk<PluginManagementEntry>(relaxed = true) {
            every { pluginId } returns "test-plugin"
            every { priority } returns 1
        }
        val game = createTestGame(1L)
        game.metadata.originalIds = mapOf(pluginEntry to "123")
        game.title = "User Modified Title"
        game.platforms = mutableListOf(Platform.PC_MICROSOFT_WINDOWS)
        game.metadata.fields["title"] = GameFieldMetadata(source = GameFieldUserSource(user = mockUser))
        game.metadata.fields["platforms"] = GameFieldMetadata(source = GameFieldPluginSource(plugin = pluginEntry))

        val metadata = org.gameyfin.pluginapi.gamemetadata.GameMetadata(
            originalId = "123",
            title = "New Title",
            release = game.release,
            platforms = setOf(Platform.PC_MICROSOFT_WINDOWS)
        )
        val provider = spyk(TestProvider(metadata))

        every { gameRepository.findByIdOrNull(1L) } returns game
        every { pluginManager.getExtensions("test-plugin") } returns listOf(provider)
        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(provider)
        every { pluginService.getPluginManagementEntry(provider.javaClass) } returns pluginEntry
        every { imageService.createOrGet(any()) } returns mockk(relaxed = true)
        every { filesystemService.calculateFileSize(any()) } returns 1000L
        every { library.platforms } returns mutableListOf(Platform.PC_MICROSOFT_WINDOWS)

        val result = gameService.updateMetadata(game)

        // Should return null because no fields were actually updated
        assertNull(result)
        assertEquals("User Modified Title", game.title)
    }

    @Test
    fun `update should update multiple fields when changed`() {
        val pluginEntry = mockk<PluginManagementEntry>(relaxed = true) {
            every { pluginId } returns "test-plugin"
            every { priority } returns 1
        }
        val game = createTestGame(1L)
        game.metadata.originalIds = mapOf(pluginEntry to "123")
        game.title = "Old Title"
        game.summary = "Old Summary"
        game.genres = listOf(Genre.ACTION)
        game.metadata.fields["title"] = GameFieldMetadata(source = GameFieldPluginSource(plugin = pluginEntry))
        game.metadata.fields["summary"] = GameFieldMetadata(source = GameFieldPluginSource(plugin = pluginEntry))
        game.metadata.fields["genres"] = GameFieldMetadata(source = GameFieldPluginSource(plugin = pluginEntry))

        val metadata = org.gameyfin.pluginapi.gamemetadata.GameMetadata(
            originalId = "123",
            title = "New Title",
            description = "New Summary",
            genres = setOf(Genre.ACTION, Genre.ADVENTURE),
            platforms = setOf(Platform.PC_MICROSOFT_WINDOWS)
        )
        val provider = spyk(TestProvider(metadata))

        every { gameRepository.findByIdOrNull(1L) } returns game
        every { pluginManager.getExtensions("test-plugin") } returns listOf(provider)
        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(provider)
        every { pluginService.getPluginManagementEntry(provider.javaClass) } returns pluginEntry
        every { imageService.createOrGet(any()) } returns mockk(relaxed = true)
        every { filesystemService.calculateFileSize(any()) } returns 1000L

        val result = gameService.updateMetadata(game)

        assertNotNull(result)
        assertEquals("New Title", result.title)
        assertEquals("New Summary", result.summary)
        assertEquals(listOf(Genre.ACTION, Genre.ADVENTURE), result.genres)
    }

    @Test
    fun `getPotentialMatches should query all plugins and merge results`() {
        val metadata1 = org.gameyfin.pluginapi.gamemetadata.GameMetadata(
            originalId = "123",
            title = "Test Game",
            platforms = setOf(Platform.PC_MICROSOFT_WINDOWS),
            release = null,
            coverUrls = setOf(java.net.URI("https://example.com/cover1.jpg")),
            headerUrls = null,
            publishedBy = setOf("Publisher1"),
            developedBy = setOf("Developer1")
        )

        val metadata2 = org.gameyfin.pluginapi.gamemetadata.GameMetadata(
            originalId = "456",
            title = "Test Game",
            platforms = setOf(Platform.PC_MICROSOFT_WINDOWS),
            release = null,
            coverUrls = setOf(java.net.URI("https://example.com/cover2.jpg")),
            headerUrls = setOf(java.net.URI("https://example.com/header2.jpg")),
            publishedBy = null,
            developedBy = null
        )

        val provider1 = spyk(TestProvider(metadata1))
        val provider2 = spyk(TestProvider(metadata2))

        val pluginEntry1 = mockk<PluginManagementEntry>(relaxed = true) {
            every { pluginId } returns "test-plugin-1"
            every { priority } returns 10
        }

        val pluginEntry2 = mockk<PluginManagementEntry>(relaxed = true) {
            every { pluginId } returns "test-plugin-2"
            every { priority } returns 5
        }

        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(provider1, provider2)
        every { pluginService.getPluginManagementEntry(provider1.javaClass) } returns pluginEntry1
        every { pluginService.getPluginManagementEntry(provider2.javaClass) } returns pluginEntry2

        val results = gameService.getPotentialMatches("Test Game", emptySet())

        assertEquals(1, results.size)
        assertEquals("Test Game", results[0].title)
        assertEquals(setOf(Platform.PC_MICROSOFT_WINDOWS), results[0].platforms)
        assertEquals(2, results[0].coverUrls?.size)
        assertEquals(1, results[0].headerUrls?.size)
        assertEquals(listOf("Publisher1"), results[0].publishers)
    }

    @Test
    fun `getPotentialMatches should filter by platform`() {
        val metadata1 = org.gameyfin.pluginapi.gamemetadata.GameMetadata(
            originalId = "123",
            title = "PC Game",
            platforms = setOf(Platform.PC_MICROSOFT_WINDOWS),
            release = null
        )

        val metadata2 = org.gameyfin.pluginapi.gamemetadata.GameMetadata(
            originalId = "456",
            title = "Console Game",
            platforms = setOf(Platform.PLAYSTATION_5),
            release = null
        )

        val provider = mockk<GameMetadataProvider>(relaxed = true) {
            every { fetchByTitle("Test", setOf(Platform.PC_MICROSOFT_WINDOWS), 10) } returns listOf(
                metadata1,
                metadata2
            )
        }

        val pluginEntry = mockk<PluginManagementEntry>(relaxed = true) {
            every { pluginId } returns "test-plugin"
            every { priority } returns 1
        }

        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(provider)
        every { pluginService.getPluginManagementEntry(provider.javaClass) } returns pluginEntry

        val results = gameService.getPotentialMatches("Test", setOf(Platform.PC_MICROSOFT_WINDOWS))

        assertEquals(1, results.size)
        assertEquals("PC Game", results[0].title)
    }

    @Test
    fun `getPotentialMatches should handle plugin exceptions gracefully`() {
        val metadata = org.gameyfin.pluginapi.gamemetadata.GameMetadata(
            originalId = "123",
            title = "Test Game",
            platforms = setOf(Platform.PC_MICROSOFT_WINDOWS),
            release = null
        )

        val workingProvider = spyk(TestProvider(metadata))
        val failingProvider = mockk<GameMetadataProvider>(relaxed = true) {
            every { fetchByTitle(any(), any(), any()) } throws RuntimeException("Plugin error")
        }

        val pluginEntry1 = mockk<PluginManagementEntry>(relaxed = true) {
            every { pluginId } returns "working-plugin"
            every { priority } returns 1
        }

        val pluginEntry2 = mockk<PluginManagementEntry>(relaxed = true) {
            every { pluginId } returns "failing-plugin"
            every { priority } returns 1
        }

        val pluginWrapper = mockk<PluginWrapper>(relaxed = true) {
            every { pluginId } returns "failing-plugin"
        }

        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(
            workingProvider,
            failingProvider
        )
        every { pluginService.getPluginManagementEntry(workingProvider.javaClass) } returns pluginEntry1
        every { pluginService.getPluginManagementEntry(failingProvider.javaClass) } returns pluginEntry2
        every { pluginManager.getPluginForExtension(failingProvider::class) } returns pluginWrapper

        val results = gameService.getPotentialMatches("Test Game", emptySet())

        assertEquals(1, results.size)
        assertEquals("Test Game", results[0].title)
    }

    @Test
    fun `getPotentialMatches should sort by fuzzy match and release date`() {
        val oldRelease = Instant.parse("2010-01-01T00:00:00Z")
        val newRelease = Instant.parse("2023-01-01T00:00:00Z")

        val metadata1 = org.gameyfin.pluginapi.gamemetadata.GameMetadata(
            originalId = "1",
            title = "Test Game",
            platforms = setOf(Platform.PC_MICROSOFT_WINDOWS),
            release = oldRelease
        )

        val metadata2 = org.gameyfin.pluginapi.gamemetadata.GameMetadata(
            originalId = "2",
            title = "Test Game",
            platforms = setOf(Platform.PC_MICROSOFT_WINDOWS),
            release = newRelease
        )

        val metadata3 = org.gameyfin.pluginapi.gamemetadata.GameMetadata(
            originalId = "3",
            title = "Completely Different Title",
            platforms = setOf(Platform.PC_MICROSOFT_WINDOWS),
            release = newRelease
        )

        val provider = mockk<GameMetadataProvider>(relaxed = true) {
            every { fetchByTitle("Test Game", emptySet(), 10) } returns listOf(metadata1, metadata2, metadata3)
        }

        val pluginEntry = mockk<PluginManagementEntry>(relaxed = true) {
            every { pluginId } returns "test-plugin"
            every { priority } returns 1
        }

        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(provider)
        every { pluginService.getPluginManagementEntry(provider.javaClass) } returns pluginEntry

        val results = gameService.getPotentialMatches("Test Game", emptySet())

        // First result should be newer "Test Game" (better match + newer)
        assertEquals("Test Game", results[0].title)
        assertEquals(newRelease, results[0].release)

        // Second result should be older "Test Game" (same match but older)
        assertEquals("Test Game", results[1].title)
        assertEquals(oldRelease, results[1].release)

        // Third result should be "Completely Different Title" (worse match)
        assertEquals("Completely Different Title", results[2].title)
    }

    @Test
    fun `matchFromFile should extract title using regex when configured`() {
        val metadata = org.gameyfin.pluginapi.gamemetadata.GameMetadata(
            originalId = "123",
            title = "Test Game",
            platforms = setOf(Platform.PC_MICROSOFT_WINDOWS)
        )

        val provider = spyk(TestProvider(metadata))
        val pluginEntry = mockk<PluginManagementEntry>(relaxed = true) {
            every { pluginId } returns "test-plugin"
            every { priority } returns 1
        }

        val pluginWrapper = mockk<PluginWrapper>(relaxed = true) {
            every { pluginId } returns "test-plugin"
        }

        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(provider)
        every { pluginService.getPluginManagementEntry(provider.javaClass) } returns pluginEntry
        every { pluginManager.whichPlugin(ofType<Class<out GameMetadataProvider>>()) } returns pluginWrapper
        every { config.get(ConfigProperties.Libraries.Scan.ExtractTitleUsingRegex) } returns true
        every { config.get(ConfigProperties.Libraries.Scan.TitleExtractionRegex) } returns "^[^\\(\\[]*"
        every { config.get(ConfigProperties.Libraries.Scan.TitleMatchMinRatio) } returns 85
        every { imageService.createOrGet(any()) } returns mockk(relaxed = true)
        every { filesystemService.calculateFileSize(any()) } returns 1000L

        val path = Path.of("/test/Test Game (2023).exe")
        val result = gameService.matchFromFile(path, library)

        assertNotNull(result)
        assertEquals("Test Game", result.title)
    }

    @Test
    fun `matchFromFile should return null when no plugins return results`() {
        val provider = mockk<GameMetadataProvider>(relaxed = true) {
            every { supportedPlatforms } returns setOf(Platform.PC_MICROSOFT_WINDOWS)
            every { fetchByTitle(any(), any()) } returns emptyList()
        }

        val pluginEntry = mockk<PluginManagementEntry>(relaxed = true) {
            every { pluginId } returns "test-plugin"
            every { priority } returns 1
        }

        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(provider)
        every { pluginService.getPluginManagementEntry(provider.javaClass) } returns pluginEntry
        every { config.get(ConfigProperties.Libraries.Scan.ExtractTitleUsingRegex) } returns false

        val path = Path.of("/test/Unknown Game.exe")
        val result = gameService.matchFromFile(path, library)

        assertNull(result)
    }

    @Test
    fun `matchFromFile should filter by library platforms`() {
        val pcMetadata = org.gameyfin.pluginapi.gamemetadata.GameMetadata(
            originalId = "123",
            title = "Test Game PC",
            platforms = setOf(Platform.PC_MICROSOFT_WINDOWS)
        )

        val consoleMetadata = org.gameyfin.pluginapi.gamemetadata.GameMetadata(
            originalId = "456",
            title = "Test Game Console",
            platforms = setOf(Platform.PLAYSTATION_5)
        )

        val provider = mockk<GameMetadataProvider>(relaxed = true) {
            every { supportedPlatforms } returns setOf(Platform.PC_MICROSOFT_WINDOWS, Platform.PLAYSTATION_5)
            every { fetchByTitle("Test Game", setOf(Platform.PC_MICROSOFT_WINDOWS)) } returns listOf(
                pcMetadata,
                consoleMetadata
            )
        }

        val pluginEntry = mockk<PluginManagementEntry>(relaxed = true) {
            every { pluginId } returns "test-plugin"
            every { priority } returns 1
        }

        val pluginWrapper = mockk<PluginWrapper>(relaxed = true) {
            every { pluginId } returns "test-plugin"
        }

        val pcLibrary = mockk<Library>(relaxed = true) {
            every { id } returns 1L
            every { platforms } returns mutableListOf(Platform.PC_MICROSOFT_WINDOWS)
        }

        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(provider)
        every { pluginService.getPluginManagementEntry(provider.javaClass) } returns pluginEntry
        every { pluginManager.whichPlugin(ofType<Class<out GameMetadataProvider>>()) } returns pluginWrapper
        every { config.get(ConfigProperties.Libraries.Scan.ExtractTitleUsingRegex) } returns false
        every { config.get(ConfigProperties.Libraries.Scan.TitleMatchMinRatio) } returns 85
        every { imageService.createOrGet(any()) } returns mockk(relaxed = true)
        every { filesystemService.calculateFileSize(any()) } returns 1000L

        val path = Path.of("/test/Test Game.exe")
        val result = gameService.matchFromFile(path, pcLibrary)

        assertNotNull(result)
        assertEquals("Test Game PC", result.title)
    }

    @Test
    fun `matchFromFile should handle invalid regex gracefully`() {
        val metadata = org.gameyfin.pluginapi.gamemetadata.GameMetadata(
            originalId = "123",
            title = "Test Game",
            platforms = setOf(Platform.PC_MICROSOFT_WINDOWS)
        )

        val provider = spyk(TestProvider(metadata))
        val pluginEntry = mockk<PluginManagementEntry>(relaxed = true) {
            every { pluginId } returns "test-plugin"
            every { priority } returns 1
        }

        val pluginWrapper = mockk<PluginWrapper>(relaxed = true) {
            every { pluginId } returns "test-plugin"
        }

        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(provider)
        every { pluginService.getPluginManagementEntry(provider.javaClass) } returns pluginEntry
        every { pluginManager.whichPlugin(ofType<Class<out GameMetadataProvider>>()) } returns pluginWrapper
        every { config.get(ConfigProperties.Libraries.Scan.ExtractTitleUsingRegex) } returns true
        every { config.get(ConfigProperties.Libraries.Scan.TitleExtractionRegex) } returns "["  // Invalid regex
        every { config.get(ConfigProperties.Libraries.Scan.TitleMatchMinRatio) } returns 85
        every { imageService.createOrGet(any()) } returns mockk(relaxed = true)
        every { filesystemService.calculateFileSize(any()) } returns 1000L

        val path = Path.of("/test/Test Game.exe")
        val result = gameService.matchFromFile(path, library)

        // Should still work with the full filename
        assertNotNull(result)
    }

    @Test
    fun `matchManually should return null when no plugins return valid results`() {
        val originalIds = mapOf<String, ExternalProviderIdDto>()
        val path = Path.of("/test/game.exe")

        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns emptyList()

        val result = gameService.matchManually(originalIds, path, library, null, persist = false)

        assertNull(result)
    }

    @Test
    fun `matchManually should persist game when persist is true`() {
        val originalIds = mapOf(
            "org.gameyfin.app.games.TestProvider" to ExternalProviderIdDto("test-plugin", "123")
        )
        val path = Path.of("/test/game.exe")
        val metadata = org.gameyfin.pluginapi.gamemetadata.GameMetadata(
            originalId = "123",
            title = "Test Game",
            platforms = setOf(Platform.PC_MICROSOFT_WINDOWS)
        )

        val provider = spyk(TestProvider(metadata))
        val pluginEntry = mockk<PluginManagementEntry>(relaxed = true) {
            every { pluginId } returns "test-plugin"
            every { priority } returns 1
        }

        val savedGame = createTestGame(10L, "Test Game")

        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(provider)
        every { pluginService.getPluginManagementEntry(provider.javaClass) } returns pluginEntry
        every { companyService.createOrGet(any()) } returns mockk(relaxed = true)
        every { imageService.createOrGet(any()) } returns mockk(relaxed = true)
        every { imageService.downloadIfNew(any()) } just Runs
        every { filesystemService.calculateFileSize(any()) } returns 1000L
        every { gameRepository.save(any()) } returns savedGame

        val result = gameService.matchManually(originalIds, path, library, null, persist = true)

        assertNotNull(result)
        verify(exactly = 1) { gameRepository.save(any()) }
    }

    @Test
    fun `matchManually should merge results from multiple plugins with priority`() {
        val highPriorityMetadata = org.gameyfin.pluginapi.gamemetadata.GameMetadata(
            originalId = "123",
            title = "High Priority Title",
            description = "High Priority Description",
            platforms = setOf(Platform.PC_MICROSOFT_WINDOWS),
            coverUrls = setOf(java.net.URI("https://high-priority.com/cover.jpg")),
            genres = setOf(Genre.ACTION)
        )

        val lowPriorityMetadata = org.gameyfin.pluginapi.gamemetadata.GameMetadata(
            originalId = "456",
            title = "Low Priority Title",
            description = "Low Priority Description",
            platforms = setOf(Platform.PC_MICROSOFT_WINDOWS, Platform.PLAYSTATION_5),
            coverUrls = setOf(java.net.URI("https://low-priority.com/cover.jpg")),
            genres = setOf(Genre.ADVENTURE)
        )

        val highPriorityProvider = spyk(TestProvider(highPriorityMetadata))
        val lowPriorityProvider = spyk(TestProvider(lowPriorityMetadata))

        val highPriorityEntry = mockk<PluginManagementEntry>(relaxed = true) {
            every { pluginId } returns "high-priority-plugin"
            every { priority } returns 100
        }

        val lowPriorityEntry = mockk<PluginManagementEntry>(relaxed = true) {
            every { pluginId } returns "low-priority-plugin"
            every { priority } returns 10
        }

        val originalIds = mapOf(
            highPriorityProvider.javaClass.name to ExternalProviderIdDto("high-priority-plugin", "123"),
            lowPriorityProvider.javaClass.name to ExternalProviderIdDto("low-priority-plugin", "456")
        )
        val path = Path.of("/test/game.exe")

        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(
            highPriorityProvider,
            lowPriorityProvider
        )
        every { pluginService.getPluginManagementEntry(highPriorityProvider.javaClass) } returns highPriorityEntry
        every { pluginService.getPluginManagementEntry(lowPriorityProvider.javaClass) } returns lowPriorityEntry
        every { imageService.createOrGet(any()) } returns mockk(relaxed = true)
        every { filesystemService.calculateFileSize(any()) } returns 1000L

        val result = gameService.matchManually(originalIds, path, library, null, persist = false)

        assertNotNull(result)
        // High priority values should be used
        assertEquals("High Priority Title", result.title)
        assertEquals("High Priority Description", result.summary)
        assertEquals(listOf(Genre.ACTION), result.genres)
    }

    @Test
    fun `matchManually should filter platforms to only those supported by the library`() {
        val metadata = org.gameyfin.pluginapi.gamemetadata.GameMetadata(
            originalId = "123",
            title = "Multi-Platform Game",
            platforms = setOf(
                Platform.PC_MICROSOFT_WINDOWS,
                Platform.PLAYSTATION_5,
                Platform.XBOX_SERIES_X_S,
                Platform.NINTENDO_SWITCH
            )
        )

        val provider = spyk(TestProvider(metadata))
        val pluginEntry = mockk<PluginManagementEntry>(relaxed = true) {
            every { pluginId } returns "test-plugin"
            every { priority } returns 1
        }

        val originalIds = mapOf(
            provider.javaClass.name to ExternalProviderIdDto("test-plugin", "123")
        )
        val path = Path.of("/test/game.exe")

        // Library only supports PC and PlayStation 5
        val restrictedLibrary = mockk<Library>(relaxed = true) {
            every { id } returns 1L
            every { platforms } returns mutableListOf(Platform.PC_MICROSOFT_WINDOWS, Platform.PLAYSTATION_5)
        }

        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(provider)
        every { pluginService.getPluginManagementEntry(provider.javaClass) } returns pluginEntry
        every { imageService.createOrGet(any()) } returns mockk(relaxed = true)
        every { filesystemService.calculateFileSize(any()) } returns 1000L

        val result = gameService.matchManually(originalIds, path, restrictedLibrary, null, persist = false)

        assertNotNull(result)
        assertEquals("Multi-Platform Game", result.title)
        // Platforms should be filtered to only PC and PlayStation 5
        assertEquals(2, result.platforms.size)
        assertEquals(
            setOf(Platform.PC_MICROSOFT_WINDOWS, Platform.PLAYSTATION_5),
            result.platforms.toSet()
        )
    }

    @Test
    fun `matchManually should return empty platforms when library platforms don't match metadata platforms`() {
        val metadata = org.gameyfin.pluginapi.gamemetadata.GameMetadata(
            originalId = "123",
            title = "Console Exclusive Game",
            platforms = setOf(Platform.PLAYSTATION_5, Platform.XBOX_SERIES_X_S)
        )

        val provider = spyk(TestProvider(metadata))
        val pluginEntry = mockk<PluginManagementEntry>(relaxed = true) {
            every { pluginId } returns "test-plugin"
            every { priority } returns 1
        }

        val originalIds = mapOf(
            provider.javaClass.name to ExternalProviderIdDto("test-plugin", "123")
        )
        val path = Path.of("/test/game.exe")

        // Library only supports PC
        val pcOnlyLibrary = mockk<Library>(relaxed = true) {
            every { id } returns 1L
            every { platforms } returns mutableListOf(Platform.PC_MICROSOFT_WINDOWS)
        }

        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(provider)
        every { pluginService.getPluginManagementEntry(provider.javaClass) } returns pluginEntry
        every { imageService.createOrGet(any()) } returns mockk(relaxed = true)
        every { filesystemService.calculateFileSize(any()) } returns 1000L

        val result = gameService.matchManually(originalIds, path, pcOnlyLibrary, null, persist = false)

        assertNotNull(result)
        assertEquals("Console Exclusive Game", result.title)
        // Platforms should be empty since there's no intersection
        assertEquals(0, result.platforms.size)
    }

    @Test
    fun `matchManually should preserve all platforms when library platforms list is empty`() {
        val metadata = org.gameyfin.pluginapi.gamemetadata.GameMetadata(
            originalId = "123",
            title = "Multi-Platform Game",
            platforms = setOf(Platform.PC_MICROSOFT_WINDOWS, Platform.PLAYSTATION_5)
        )

        val provider = spyk(TestProvider(metadata))
        val pluginEntry = mockk<PluginManagementEntry>(relaxed = true) {
            every { pluginId } returns "test-plugin"
            every { priority } returns 1
        }

        val originalIds = mapOf(
            provider.javaClass.name to ExternalProviderIdDto("test-plugin", "123")
        )
        val path = Path.of("/test/game.exe")

        // Library with no platform restrictions
        val unrestrictedLibrary = mockk<Library>(relaxed = true) {
            every { id } returns 1L
            every { platforms } returns mutableListOf()
        }

        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(provider)
        every { pluginService.getPluginManagementEntry(provider.javaClass) } returns pluginEntry
        every { imageService.createOrGet(any()) } returns mockk(relaxed = true)
        every { filesystemService.calculateFileSize(any()) } returns 1000L

        val result = gameService.matchManually(originalIds, path, unrestrictedLibrary, null, persist = false)

        assertNotNull(result)
        assertEquals("Multi-Platform Game", result.title)
        // When library has no platform restrictions, result should have no platforms (empty intersect empty)
        assertEquals(0, result.platforms.size)
    }

    @Test
    fun `matchManually should filter platforms when replacing an existing game`() {
        val metadata = org.gameyfin.pluginapi.gamemetadata.GameMetadata(
            originalId = "123",
            title = "Updated Game",
            platforms = setOf(
                Platform.PC_MICROSOFT_WINDOWS,
                Platform.PLAYSTATION_5,
                Platform.XBOX_SERIES_X_S
            )
        )

        val provider = spyk(TestProvider(metadata))
        val pluginEntry = mockk<PluginManagementEntry>(relaxed = true) {
            every { pluginId } returns "test-plugin"
            every { priority } returns 1
        }

        val originalIds = mapOf(
            provider.javaClass.name to ExternalProviderIdDto("test-plugin", "123")
        )
        val path = Path.of("/test/game.exe")
        val replaceGameId = 5L
        val existingGame = createTestGame(replaceGameId)

        // Library only supports PC and PlayStation 5
        val restrictedLibrary = mockk<Library>(relaxed = true) {
            every { id } returns 1L
            every { platforms } returns mutableListOf(Platform.PC_MICROSOFT_WINDOWS, Platform.PLAYSTATION_5)
        }

        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(provider)
        every { pluginService.getPluginManagementEntry(provider.javaClass) } returns pluginEntry
        every { gameRepository.findByIdOrNull(replaceGameId) } returns existingGame
        every { imageService.createOrGet(any()) } returns mockk(relaxed = true)
        every { filesystemService.calculateFileSize(any()) } returns 1000L

        val result = gameService.matchManually(originalIds, path, restrictedLibrary, replaceGameId, persist = false)

        assertNotNull(result)
        assertEquals(replaceGameId, result.id)
        assertEquals("Updated Game", result.title)
        // Platforms should be filtered even when replacing
        assertEquals(2, result.platforms.size)
        assertEquals(
            setOf(Platform.PC_MICROSOFT_WINDOWS, Platform.PLAYSTATION_5),
            result.platforms.toSet()
        )
    }

    // ========================
    // create(List<Game>) batch — cover/header image branches
    // ========================

    @Test
    fun `create with list should handle games with null coverImage and headerImage`() {
        val game = createTestGame(id = null)
        game.coverImage = null
        game.headerImage = null
        val games = listOf(game)

        every { companyService.createOrGet(any()) } returns mockk(relaxed = true)
        every { imageService.createOrGet(any()) } returns mockk(relaxed = true)
        every { gameRepository.saveAll(games) } returns games

        val result = gameService.create(games)

        assertEquals(games, result)
        verify(exactly = 0) { imageService.createOrGet(match { it.type == ImageType.COVER }) }
        verify(exactly = 0) { imageService.createOrGet(match { it.type == ImageType.HEADER }) }
    }

    @Test
    fun `create with list should process coverImage and headerImage when present`() {
        val coverImage = Image(originalUrl = "https://example.com/cover.jpg", type = ImageType.COVER)
        val headerImage = Image(originalUrl = "https://example.com/header.jpg", type = ImageType.HEADER)
        val processedCover = mockk<Image>(relaxed = true)
        val processedHeader = mockk<Image>(relaxed = true)

        val game = createTestGame(id = null)
        game.coverImage = coverImage
        game.headerImage = headerImage
        val games = listOf(game)

        every { companyService.createOrGet(any()) } returns mockk(relaxed = true)
        every { imageService.createOrGet(coverImage) } returns processedCover
        every { imageService.createOrGet(headerImage) } returns processedHeader
        every { imageService.createOrGet(match { it != coverImage && it != headerImage }) } returns mockk(relaxed = true)
        every { gameRepository.saveAll(games) } returns games

        val result = gameService.create(games)

        assertEquals(games, result)
        assertEquals(processedCover, game.coverImage)
        assertEquals(processedHeader, game.headerImage)
    }

    @Test
    fun `create with list should skip all games when all have existing IDs`() {
        val game1 = createTestGame(id = 1L)
        val game2 = createTestGame(id = 2L)
        val games = listOf(game1, game2)

        every { gameRepository.saveAll(emptyList()) } returns emptyList()

        val result = gameService.create(games)

        assertEquals(emptyList(), result)
    }

    // ========================
    // edit — OidcUser branch
    // ========================

    @Test
    fun `edit should resolve user from OidcUser principal`() {
        val gameId = 1L
        val existingGame = createTestGame(gameId)
        val oidcUser = mockk<OidcUser> {
            every { preferredUsername } returns "oidcuser"
        }

        every { authentication.principal } returns oidcUser
        every { gameRepository.findByIdOrNull(gameId) } returns existingGame
        every { userService.getByUsernameNonNull("oidcuser") } returns mockUser
        every { gameRepository.save(existingGame) } returns existingGame

        val updateDto = GameUpdateDto(
            id = gameId,
            title = "OIDC Updated Title",
            platforms = null,
            release = null,
            coverUrl = null,
            headerUrl = null,
            comment = null,
            summary = null,
            developers = null,
            publishers = null,
            genres = null,
            themes = null,
            keywords = null,
            features = null,
            perspectives = null,
            metadata = null
        )

        gameService.edit(updateDto)

        assertEquals("OIDC Updated Title", existingGame.title)
        verify(exactly = 1) { userService.getByUsernameNonNull("oidcuser") }
    }

    @Test
    fun `edit should throw error for unknown principal type`() {
        val gameId = 1L
        val existingGame = createTestGame(gameId)

        every { authentication.principal } returns "unknownPrincipal"
        every { gameRepository.findByIdOrNull(gameId) } returns existingGame

        val updateDto = GameUpdateDto(
            id = gameId,
            title = "Title",
            platforms = null,
            release = null,
            coverUrl = null,
            headerUrl = null,
            comment = null,
            summary = null,
            developers = null,
            publishers = null,
            genres = null,
            themes = null,
            keywords = null,
            features = null,
            perspectives = null,
            metadata = null
        )

        assertThrows(IllegalStateException::class.java) {
            gameService.edit(updateDto)
        }
    }

    // ========================
    // edit — headerUrl branch
    // ========================

    @Test
    fun `edit should create and download new header image when headerUrl provided`() {
        val gameId = 1L
        val existingGame = createTestGame(gameId)
        val userDetails = mockk<UserDetails> {
            every { username } returns "testuser"
        }
        val newHeaderUrl = "https://example.com/new-header.jpg"
        val newHeaderImage = mockk<Image>(relaxed = true)

        every { authentication.principal } returns userDetails
        every { gameRepository.findByIdOrNull(gameId) } returns existingGame
        every { userService.getByUsernameNonNull("testuser") } returns mockUser
        every { imageService.createOrGet(any()) } returns newHeaderImage
        every { imageService.downloadIfNew(newHeaderImage) } just Runs
        every { gameRepository.save(existingGame) } returns existingGame

        val updateDto = GameUpdateDto(
            id = gameId,
            title = null,
            platforms = null,
            release = null,
            coverUrl = null,
            headerUrl = newHeaderUrl,
            comment = null,
            summary = null,
            developers = null,
            publishers = null,
            genres = null,
            themes = null,
            keywords = null,
            features = null,
            perspectives = null,
            metadata = null
        )

        gameService.edit(updateDto)

        assertEquals(newHeaderImage, existingGame.headerImage)
        verify(exactly = 1) {
            imageService.createOrGet(match { it.originalUrl == newHeaderUrl && it.type == ImageType.HEADER })
        }
        verify(exactly = 1) { imageService.downloadIfNew(newHeaderImage) }
    }

    // ========================
    // edit — comment, summary, genres, themes, keywords, features, perspectives branches
    // ========================

    @Test
    fun `edit should update comment when provided`() {
        val gameId = 1L
        val existingGame = createTestGame(gameId)
        val userDetails = mockk<UserDetails> {
            every { username } returns "testuser"
        }

        every { authentication.principal } returns userDetails
        every { gameRepository.findByIdOrNull(gameId) } returns existingGame
        every { userService.getByUsernameNonNull("testuser") } returns mockUser
        every { gameRepository.save(existingGame) } returns existingGame

        val updateDto = GameUpdateDto(
            id = gameId,
            title = null,
            platforms = null,
            release = null,
            coverUrl = null,
            headerUrl = null,
            comment = "Updated Comment",
            summary = null,
            developers = null,
            publishers = null,
            genres = null,
            themes = null,
            keywords = null,
            features = null,
            perspectives = null,
            metadata = null
        )

        gameService.edit(updateDto)

        assertEquals("Updated Comment", existingGame.comment)
    }

    @Test
    fun `edit should update summary when provided`() {
        val gameId = 1L
        val existingGame = createTestGame(gameId)
        val userDetails = mockk<UserDetails> {
            every { username } returns "testuser"
        }

        every { authentication.principal } returns userDetails
        every { gameRepository.findByIdOrNull(gameId) } returns existingGame
        every { userService.getByUsernameNonNull("testuser") } returns mockUser
        every { gameRepository.save(existingGame) } returns existingGame

        val updateDto = GameUpdateDto(
            id = gameId,
            title = null,
            platforms = null,
            release = null,
            coverUrl = null,
            headerUrl = null,
            comment = null,
            summary = "Updated Summary",
            developers = null,
            publishers = null,
            genres = null,
            themes = null,
            keywords = null,
            features = null,
            perspectives = null,
            metadata = null
        )

        gameService.edit(updateDto)

        assertEquals("Updated Summary", existingGame.summary)
    }

    @Test
    fun `edit should update genres when provided`() {
        val gameId = 1L
        val existingGame = createTestGame(gameId)
        val userDetails = mockk<UserDetails> {
            every { username } returns "testuser"
        }
        val newGenres = listOf(Genre.ACTION, Genre.ADVENTURE)

        every { authentication.principal } returns userDetails
        every { gameRepository.findByIdOrNull(gameId) } returns existingGame
        every { userService.getByUsernameNonNull("testuser") } returns mockUser
        every { gameRepository.save(existingGame) } returns existingGame

        val updateDto = GameUpdateDto(
            id = gameId,
            title = null,
            platforms = null,
            release = null,
            coverUrl = null,
            headerUrl = null,
            comment = null,
            summary = null,
            developers = null,
            publishers = null,
            genres = newGenres,
            themes = null,
            keywords = null,
            features = null,
            perspectives = null,
            metadata = null
        )

        gameService.edit(updateDto)

        assertEquals(newGenres.toList(), existingGame.genres)
    }

    @Test
    fun `edit should update themes when provided`() {
        val gameId = 1L
        val existingGame = createTestGame(gameId)
        val userDetails = mockk<UserDetails> {
            every { username } returns "testuser"
        }
        val newThemes = listOf(Theme.FANTASY, Theme.SCIENCE_FICTION)

        every { authentication.principal } returns userDetails
        every { gameRepository.findByIdOrNull(gameId) } returns existingGame
        every { userService.getByUsernameNonNull("testuser") } returns mockUser
        every { gameRepository.save(existingGame) } returns existingGame

        val updateDto = GameUpdateDto(
            id = gameId,
            title = null,
            platforms = null,
            release = null,
            coverUrl = null,
            headerUrl = null,
            comment = null,
            summary = null,
            developers = null,
            publishers = null,
            genres = null,
            themes = newThemes,
            keywords = null,
            features = null,
            perspectives = null,
            metadata = null
        )

        gameService.edit(updateDto)

        assertEquals(newThemes.toList(), existingGame.themes)
    }

    @Test
    fun `edit should update keywords when provided`() {
        val gameId = 1L
        val existingGame = createTestGame(gameId)
        val userDetails = mockk<UserDetails> {
            every { username } returns "testuser"
        }
        val newKeywords = listOf("keyword1", "keyword2")

        every { authentication.principal } returns userDetails
        every { gameRepository.findByIdOrNull(gameId) } returns existingGame
        every { userService.getByUsernameNonNull("testuser") } returns mockUser
        every { gameRepository.save(existingGame) } returns existingGame

        val updateDto = GameUpdateDto(
            id = gameId,
            title = null,
            platforms = null,
            release = null,
            coverUrl = null,
            headerUrl = null,
            comment = null,
            summary = null,
            developers = null,
            publishers = null,
            genres = null,
            themes = null,
            keywords = newKeywords,
            features = null,
            perspectives = null,
            metadata = null
        )

        gameService.edit(updateDto)

        assertEquals(newKeywords, existingGame.keywords)
    }

    @Test
    fun `edit should update features when provided`() {
        val gameId = 1L
        val existingGame = createTestGame(gameId)
        val userDetails = mockk<UserDetails> {
            every { username } returns "testuser"
        }
        val newFeatures = listOf(GameFeature.SINGLEPLAYER, GameFeature.MULTIPLAYER)

        every { authentication.principal } returns userDetails
        every { gameRepository.findByIdOrNull(gameId) } returns existingGame
        every { userService.getByUsernameNonNull("testuser") } returns mockUser
        every { gameRepository.save(existingGame) } returns existingGame

        val updateDto = GameUpdateDto(
            id = gameId,
            title = null,
            platforms = null,
            release = null,
            coverUrl = null,
            headerUrl = null,
            comment = null,
            summary = null,
            developers = null,
            publishers = null,
            genres = null,
            themes = null,
            keywords = null,
            features = newFeatures,
            perspectives = null,
            metadata = null
        )

        gameService.edit(updateDto)

        assertEquals(newFeatures.toList(), existingGame.features)
    }

    @Test
    fun `edit should update perspectives when provided`() {
        val gameId = 1L
        val existingGame = createTestGame(gameId)
        val userDetails = mockk<UserDetails> {
            every { username } returns "testuser"
        }
        val newPerspectives = listOf(PlayerPerspective.FIRST_PERSON, PlayerPerspective.THIRD_PERSON)

        every { authentication.principal } returns userDetails
        every { gameRepository.findByIdOrNull(gameId) } returns existingGame
        every { userService.getByUsernameNonNull("testuser") } returns mockUser
        every { gameRepository.save(existingGame) } returns existingGame

        val updateDto = GameUpdateDto(
            id = gameId,
            title = null,
            platforms = null,
            release = null,
            coverUrl = null,
            headerUrl = null,
            comment = null,
            summary = null,
            developers = null,
            publishers = null,
            genres = null,
            themes = null,
            keywords = null,
            features = null,
            perspectives = newPerspectives,
            metadata = null
        )

        gameService.edit(updateDto)

        assertEquals(newPerspectives.toList(), existingGame.perspectives)
    }

    // ========================
    // edit — all fields at once + metadata.fields source update branch
    // ========================

    @Test
    fun `edit should update all fields and set metadata field sources when fields exist`() {
        val gameId = 1L
        val pluginEntry = mockk<PluginManagementEntry>(relaxed = true) {
            every { pluginId } returns "test-plugin"
        }

        val existingGame = createTestGame(gameId)
        // Pre-populate metadata fields so that the ?.source = ... branch is exercised
        existingGame.metadata.fields = mutableMapOf(
            "title" to GameFieldMetadata(source = GameFieldPluginSource(plugin = pluginEntry)),
            "platforms" to GameFieldMetadata(source = GameFieldPluginSource(plugin = pluginEntry)),
            "release" to GameFieldMetadata(source = GameFieldPluginSource(plugin = pluginEntry)),
            "coverImage" to GameFieldMetadata(source = GameFieldPluginSource(plugin = pluginEntry)),
            "headerImage" to GameFieldMetadata(source = GameFieldPluginSource(plugin = pluginEntry)),
            "comment" to GameFieldMetadata(source = GameFieldPluginSource(plugin = pluginEntry)),
            "summary" to GameFieldMetadata(source = GameFieldPluginSource(plugin = pluginEntry)),
            "developers" to GameFieldMetadata(source = GameFieldPluginSource(plugin = pluginEntry)),
            "publishers" to GameFieldMetadata(source = GameFieldPluginSource(plugin = pluginEntry)),
            "genres" to GameFieldMetadata(source = GameFieldPluginSource(plugin = pluginEntry)),
            "themes" to GameFieldMetadata(source = GameFieldPluginSource(plugin = pluginEntry)),
            "keywords" to GameFieldMetadata(source = GameFieldPluginSource(plugin = pluginEntry)),
            "features" to GameFieldMetadata(source = GameFieldPluginSource(plugin = pluginEntry)),
            "perspectives" to GameFieldMetadata(source = GameFieldPluginSource(plugin = pluginEntry))
        )

        val userDetails = mockk<UserDetails> {
            every { username } returns "testuser"
        }
        val newCoverImage = mockk<Image>(relaxed = true)
        val newHeaderImage = mockk<Image>(relaxed = true)

        every { authentication.principal } returns userDetails
        every { gameRepository.findByIdOrNull(gameId) } returns existingGame
        every { userService.getByUsernameNonNull("testuser") } returns mockUser
        every { imageService.createOrGet(match { it.type == ImageType.COVER }) } returns newCoverImage
        every { imageService.createOrGet(match { it.type == ImageType.HEADER }) } returns newHeaderImage
        every { imageService.downloadIfNew(any()) } just Runs
        every { companyService.createOrGet(any()) } returns mockk(relaxed = true)
        every { gameRepository.save(existingGame) } returns existingGame

        val updateDto = GameUpdateDto(
            id = gameId,
            title = "All Fields Title",
            platforms = setOf(Platform.PLAYSTATION_5),
            release = LocalDate.of(2025, 1, 1),
            coverUrl = "https://example.com/cover.jpg",
            headerUrl = "https://example.com/header.jpg",
            comment = "All Fields Comment",
            summary = "All Fields Summary",
            developers = listOf("DevA"),
            publishers = listOf("PubA"),
            genres = listOf(Genre.ROLE_PLAYING),
            themes = listOf(Theme.FANTASY),
            keywords = listOf("kw1"),
            features = listOf(GameFeature.SINGLEPLAYER),
            perspectives = listOf(PlayerPerspective.FIRST_PERSON),
            metadata = null
        )

        gameService.edit(updateDto)

        assertEquals("All Fields Title", existingGame.title)
        assertEquals(listOf(Platform.PLAYSTATION_5), existingGame.platforms)
        assertEquals(
            LocalDate.of(2025, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant(),
            existingGame.release
        )
        assertEquals(newCoverImage, existingGame.coverImage)
        assertEquals(newHeaderImage, existingGame.headerImage)
        assertEquals("All Fields Comment", existingGame.comment)
        assertEquals("All Fields Summary", existingGame.summary)
        assertEquals(listOf(Genre.ROLE_PLAYING), existingGame.genres)
        assertEquals(listOf(Theme.FANTASY), existingGame.themes)
        assertEquals(listOf("kw1"), existingGame.keywords)
        assertEquals(listOf(GameFeature.SINGLEPLAYER), existingGame.features)
        assertEquals(listOf(PlayerPerspective.FIRST_PERSON), existingGame.perspectives)

        // Verify all field sources were updated to user source
        existingGame.metadata.fields.forEach { (_, meta) ->
            assert(meta.source is GameFieldUserSource) {
                "Expected GameFieldUserSource but got ${meta.source}"
            }
        }
    }

    @Test
    fun `edit should handle metadata with null matchConfirmed`() {
        val gameId = 1L
        val existingGame = createTestGame(gameId)
        existingGame.metadata.matchConfirmed = false

        val userDetails = mockk<UserDetails> {
            every { username } returns "testuser"
        }

        every { authentication.principal } returns userDetails
        every { gameRepository.findByIdOrNull(gameId) } returns existingGame
        every { userService.getByUsernameNonNull("testuser") } returns mockUser
        every { gameRepository.save(existingGame) } returns existingGame

        val updateDto = GameUpdateDto(
            id = gameId,
            title = null,
            platforms = null,
            release = null,
            coverUrl = null,
            headerUrl = null,
            comment = null,
            summary = null,
            developers = null,
            publishers = null,
            genres = null,
            themes = null,
            keywords = null,
            features = null,
            perspectives = null,
            metadata = GameUpdateMetadataDto(matchConfirmed = null)
        )

        gameService.edit(updateDto)

        // matchConfirmed should remain unchanged when null is provided
        assertEquals(false, existingGame.metadata.matchConfirmed)
    }

    @Test
    fun `edit should not update fields when all are null`() {
        val gameId = 1L
        val existingGame = createTestGame(gameId)
        val originalTitle = existingGame.title
        val originalPlatforms = existingGame.platforms.toList()

        val userDetails = mockk<UserDetails> {
            every { username } returns "testuser"
        }

        every { authentication.principal } returns userDetails
        every { gameRepository.findByIdOrNull(gameId) } returns existingGame
        every { userService.getByUsernameNonNull("testuser") } returns mockUser
        every { gameRepository.save(existingGame) } returns existingGame

        val updateDto = GameUpdateDto(
            id = gameId,
            title = null,
            platforms = null,
            release = null,
            coverUrl = null,
            headerUrl = null,
            comment = null,
            summary = null,
            developers = null,
            publishers = null,
            genres = null,
            themes = null,
            keywords = null,
            features = null,
            perspectives = null,
            metadata = null
        )

        gameService.edit(updateDto)

        assertEquals(originalTitle, existingGame.title)
        assertEquals(originalPlatforms, existingGame.platforms)
        verify(exactly = 1) { gameRepository.save(existingGame) }
    }

    // ========================
    // create(single) — called via matchManually(persist=true) with images
    // ========================

    @Test
    fun `matchManually with persist should download coverImage and headerImage`() {
        val originalIds = mapOf(
            "org.gameyfin.app.games.TestProvider" to ExternalProviderIdDto("test-plugin", "123")
        )
        val path = Path.of("/test/game.exe")
        val coverUri = java.net.URI("https://example.com/cover.jpg")
        val headerUri = java.net.URI("https://example.com/header.jpg")
        val screenshotUri = java.net.URI("https://example.com/screenshot.jpg")

        val metadata = org.gameyfin.pluginapi.gamemetadata.GameMetadata(
            originalId = "123",
            title = "Image Test Game",
            platforms = setOf(Platform.PC_MICROSOFT_WINDOWS),
            coverUrls = setOf(coverUri),
            headerUrls = setOf(headerUri),
            screenshotUrls = setOf(screenshotUri)
        )

        val provider = spyk(TestProvider(metadata))
        val pluginEntry = mockk<PluginManagementEntry>(relaxed = true) {
            every { pluginId } returns "test-plugin"
            every { priority } returns 1
        }

        val coverImage = Image(originalUrl = coverUri.toString(), type = ImageType.COVER)
        val headerImage = Image(originalUrl = headerUri.toString(), type = ImageType.HEADER)
        val screenshotImage = Image(originalUrl = screenshotUri.toString(), type = ImageType.SCREENSHOT)

        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(provider)
        every { pluginService.getPluginManagementEntry(provider.javaClass) } returns pluginEntry
        every { companyService.createOrGet(any()) } returns mockk(relaxed = true)
        every { imageService.createOrGet(match { it.type == ImageType.COVER }) } returns coverImage
        every { imageService.createOrGet(match { it.type == ImageType.HEADER }) } returns headerImage
        every { imageService.createOrGet(match { it.type == ImageType.SCREENSHOT }) } returns screenshotImage
        every { imageService.downloadIfNew(any()) } just Runs
        every { filesystemService.calculateFileSize(any()) } returns 2000L
        every { gameRepository.save(any()) } answers { firstArg() }

        val result = gameService.matchManually(originalIds, path, library, null, persist = true)

        assertNotNull(result)
        verify(atLeast = 1) { imageService.downloadIfNew(any()) }
        verify(exactly = 1) { gameRepository.save(any()) }
    }

    @Test
    fun `matchManually with persist should handle image download exception gracefully`() {
        val originalIds = mapOf(
            "org.gameyfin.app.games.TestProvider" to ExternalProviderIdDto("test-plugin", "123")
        )
        val path = Path.of("/test/game.exe")
        val coverUri = java.net.URI("https://example.com/cover.jpg")

        val metadata = org.gameyfin.pluginapi.gamemetadata.GameMetadata(
            originalId = "123",
            title = "Error Image Game",
            platforms = setOf(Platform.PC_MICROSOFT_WINDOWS),
            coverUrls = setOf(coverUri)
        )

        val provider = spyk(TestProvider(metadata))
        val pluginEntry = mockk<PluginManagementEntry>(relaxed = true) {
            every { pluginId } returns "test-plugin"
            every { priority } returns 1
        }

        val coverImage = Image(originalUrl = coverUri.toString(), type = ImageType.COVER)

        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(provider)
        every { pluginService.getPluginManagementEntry(provider.javaClass) } returns pluginEntry
        every { companyService.createOrGet(any()) } returns mockk(relaxed = true)
        every { imageService.createOrGet(any()) } returns coverImage
        every { imageService.downloadIfNew(any()) } throws RuntimeException("Download failed")
        every { filesystemService.calculateFileSize(any()) } returns 2000L
        every { gameRepository.save(any()) } answers { firstArg() }

        val result = gameService.matchManually(originalIds, path, library, null, persist = true)

        // Should still succeed despite image download error
        assertNotNull(result)
        verify(exactly = 1) { gameRepository.save(any()) }
    }

    @Test
    fun `getEnumPropertyValues should return all enum entries`() {
        val result = gameService.getEnumPropertyValues()

        assertEquals(Genre.entries, result.genres)
        assertEquals(Theme.entries, result.themes)
        assertEquals(GameFeature.entries, result.features)
        assertEquals(PlayerPerspective.entries, result.perspectives)
    }

    // ========================
    // applyMetadataFields — all fields populated
    // ========================

    @Test
    fun `matchManually should populate all metadata fields when fully-populated metadata is provided`() {
        val coverUri = java.net.URI("https://example.com/cover.jpg")
        val headerUri = java.net.URI("https://example.com/header.jpg")
        val screenshotUri = java.net.URI("https://example.com/screenshot.jpg")
        val videoUri = java.net.URI("https://example.com/video.mp4")
        val releaseInstant = Instant.parse("2024-06-15T00:00:00Z")

        val metadata = org.gameyfin.pluginapi.gamemetadata.GameMetadata(
            originalId = "full-123",
            title = "Fully Populated Game",
            description = "A full description",
            platforms = setOf(Platform.PC_MICROSOFT_WINDOWS),
            coverUrls = setOf(coverUri),
            headerUrls = setOf(headerUri),
            screenshotUrls = setOf(screenshotUri),
            release = releaseInstant,
            userRating = 85,
            criticRating = 90,
            publishedBy = setOf("TestPublisher"),
            developedBy = setOf("TestDeveloper"),
            genres = setOf(Genre.ACTION),
            themes = setOf(Theme.FANTASY),
            keywords = setOf("keyword1", "keyword2"),
            features = setOf(GameFeature.SINGLEPLAYER),
            perspectives = setOf(PlayerPerspective.FIRST_PERSON),
            videoUrls = setOf(videoUri)
        )

        val provider = spyk(TestProvider(metadata))
        val pluginEntry = mockk<PluginManagementEntry>(relaxed = true) {
            every { pluginId } returns "test-plugin"
            every { priority } returns 1
        }

        val originalIds = mapOf(
            provider.javaClass.name to ExternalProviderIdDto("test-plugin", "full-123")
        )
        val path = Path.of("/test/full-game.exe")

        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(provider)
        every { pluginService.getPluginManagementEntry(provider.javaClass) } returns pluginEntry
        every { imageService.createOrGet(any()) } returns mockk(relaxed = true)
        every { filesystemService.calculateFileSize(any()) } returns 5000L

        val result = gameService.matchManually(originalIds, path, library, null, persist = false)

        assertNotNull(result)
        assertEquals("Fully Populated Game", result.title)
        assertEquals("A full description", result.summary)
        assertEquals(releaseInstant, result.release)
        assertEquals(85, result.userRating)
        assertEquals(90, result.criticRating)
        assertEquals(1, result.publishers.size)
        assertEquals("TestPublisher", result.publishers[0].name)
        assertEquals(CompanyType.PUBLISHER, result.publishers[0].type)
        assertEquals(1, result.developers.size)
        assertEquals("TestDeveloper", result.developers[0].name)
        assertEquals(CompanyType.DEVELOPER, result.developers[0].type)
        assertEquals(listOf(Genre.ACTION), result.genres)
        assertEquals(listOf(Theme.FANTASY), result.themes)
        assertEquals(listOf("keyword1", "keyword2"), result.keywords)
        assertEquals(listOf(GameFeature.SINGLEPLAYER), result.features)
        assertEquals(listOf(PlayerPerspective.FIRST_PERSON), result.perspectives)
        assertEquals(listOf(videoUri), result.videoUrls)
        assertEquals(1, result.images.size)
    }

    @Test
    fun `matchManually should handle metadata with blank title and null-or-empty optional fields`() {
        // Metadata with empty/null optional fields to exercise the takeIf guards
        val metadata = org.gameyfin.pluginapi.gamemetadata.GameMetadata(
            originalId = "sparse-456",
            title = "Sparse Game",
            description = "",           // blank description
            platforms = emptySet(),      // empty platforms
            coverUrls = null,
            headerUrls = null,
            screenshotUrls = null,
            release = null,
            userRating = null,
            criticRating = null,
            publishedBy = emptySet(),    // empty publishers
            developedBy = emptySet(),    // empty developers
            genres = emptySet(),         // empty genres
            themes = emptySet(),         // empty themes
            keywords = emptySet(),       // empty keywords
            features = emptySet(),       // empty features
            perspectives = emptySet(),   // empty perspectives
            videoUrls = emptySet()      // empty videoUrls
        )

        val provider = spyk(TestProvider(metadata))
        val pluginEntry = mockk<PluginManagementEntry>(relaxed = true) {
            every { pluginId } returns "test-plugin"
            every { priority } returns 1
        }

        val originalIds = mapOf(
            provider.javaClass.name to ExternalProviderIdDto("test-plugin", "sparse-456")
        )
        val path = Path.of("/test/sparse-game.exe")

        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(provider)
        every { pluginService.getPluginManagementEntry(provider.javaClass) } returns pluginEntry
        every { imageService.createOrGet(any()) } returns mockk(relaxed = true)
        every { filesystemService.calculateFileSize(any()) } returns 1000L

        val result = gameService.matchManually(originalIds, path, library, null, persist = false)

        assertNotNull(result)
        // Title should still be set (it's non-blank)
        assertEquals("Sparse Game", result.title)
        // All optional fields should remain at defaults since metadata was empty/null
        assertNull(result.summary)
        assertNull(result.release)
        assertNull(result.coverImage)
        assertNull(result.headerImage)
        assertNull(result.userRating)
        assertNull(result.criticRating)
        assertEquals(emptyList<Company>(), result.publishers)
        assertEquals(emptyList<Company>(), result.developers)
        assertEquals(emptyList<Genre>(), result.genres)
        assertEquals(emptyList<Theme>(), result.themes)
        assertEquals(emptyList<String>(), result.keywords)
        assertEquals(emptyList<GameFeature>(), result.features)
        assertEquals(emptyList<PlayerPerspective>(), result.perspectives)
        assertEquals(emptyList<java.net.URI>(), result.videoUrls)
        assertEquals(emptyList<Image>(), result.images)
    }

    @Test
    fun `matchManually should skip fields from lower priority plugin when higher priority already set them`() {
        val highPriorityMetadata = org.gameyfin.pluginapi.gamemetadata.GameMetadata(
            originalId = "hp-1",
            title = "HP Title",
            description = "HP Summary",
            platforms = setOf(Platform.PC_MICROSOFT_WINDOWS),
            release = Instant.parse("2024-01-01T00:00:00Z"),
            userRating = 95,
            criticRating = 88,
            publishedBy = setOf("HP Publisher"),
            developedBy = setOf("HP Developer"),
            genres = setOf(Genre.ROLE_PLAYING),
            themes = setOf(Theme.SCIENCE_FICTION),
            keywords = setOf("hp-kw"),
            features = setOf(GameFeature.MULTIPLAYER),
            perspectives = setOf(PlayerPerspective.THIRD_PERSON),
            videoUrls = setOf(java.net.URI("https://hp.com/video.mp4"))
        )

        val lowPriorityMetadata = org.gameyfin.pluginapi.gamemetadata.GameMetadata(
            originalId = "lp-2",
            title = "LP Title",
            description = "LP Summary",
            platforms = setOf(Platform.PLAYSTATION_5),
            release = Instant.parse("2023-01-01T00:00:00Z"),
            userRating = 50,
            criticRating = 40,
            publishedBy = setOf("LP Publisher"),
            developedBy = setOf("LP Developer"),
            genres = setOf(Genre.ADVENTURE),
            themes = setOf(Theme.FANTASY),
            keywords = setOf("lp-kw"),
            features = setOf(GameFeature.SINGLEPLAYER),
            perspectives = setOf(PlayerPerspective.FIRST_PERSON),
            videoUrls = setOf(java.net.URI("https://lp.com/video.mp4"))
        )

        val highProvider = spyk(TestProvider(highPriorityMetadata))
        val lowProvider = spyk(TestProvider(lowPriorityMetadata))

        val highEntry = mockk<PluginManagementEntry>(relaxed = true) {
            every { pluginId } returns "hp-plugin"
            every { priority } returns 100
        }
        val lowEntry = mockk<PluginManagementEntry>(relaxed = true) {
            every { pluginId } returns "lp-plugin"
            every { priority } returns 10
        }

        val originalIds = mapOf(
            highProvider.javaClass.name to ExternalProviderIdDto("hp-plugin", "hp-1"),
            lowProvider.javaClass.name to ExternalProviderIdDto("lp-plugin", "lp-2")
        )
        val path = Path.of("/test/priority-game.exe")

        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(
            highProvider,
            lowProvider
        )
        every { pluginService.getPluginManagementEntry(highProvider.javaClass) } returns highEntry
        every { pluginService.getPluginManagementEntry(lowProvider.javaClass) } returns lowEntry
        every { imageService.createOrGet(any()) } returns mockk(relaxed = true)
        every { filesystemService.calculateFileSize(any()) } returns 3000L

        val result = gameService.matchManually(originalIds, path, library, null, persist = false)

        assertNotNull(result)
        // All fields should come from the high-priority plugin
        assertEquals("HP Title", result.title)
        assertEquals("HP Summary", result.summary)
        assertEquals(Instant.parse("2024-01-01T00:00:00Z"), result.release)
        assertEquals(95, result.userRating)
        assertEquals(88, result.criticRating)
        assertEquals("HP Publisher", result.publishers[0].name)
        assertEquals("HP Developer", result.developers[0].name)
        assertEquals(listOf(Genre.ROLE_PLAYING), result.genres)
        assertEquals(listOf(Theme.SCIENCE_FICTION), result.themes)
        assertEquals(listOf("hp-kw"), result.keywords)
        assertEquals(listOf(GameFeature.MULTIPLAYER), result.features)
        assertEquals(listOf(PlayerPerspective.THIRD_PERSON), result.perspectives)
        assertEquals(listOf(java.net.URI("https://hp.com/video.mp4")), result.videoUrls)
    }

    private fun createTestGame(id: Long?, title: String = "Test Game"): Game {
        return Game(
            id = id,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            library = library,
            title = title,
            platforms = mutableListOf(Platform.PC_MICROSOFT_WINDOWS),
            coverImage = mockk<Image>(),
            headerImage = mockk<Image>(),
            comment = "Comment",
            summary = "Summary",
            release = Instant.parse("2000-01-01T00:00:00Z"),
            userRating = 0,
            criticRating = 0,
            publishers = mutableListOf(),
            developers = mutableListOf(),
            genres = emptyList(),
            themes = emptyList(),
            keywords = emptyList(),
            features = emptyList(),
            perspectives = emptyList(),
            images = mutableListOf(),
            videoUrls = emptyList(),
            metadata = org.gameyfin.app.games.entities.GameMetadata(
                path = "/test/path",
                fileSize = 1000L
            )
        )
    }
}

// Test helper class for GameMetadataProvider
class TestProvider(private val metadataToReturn: org.gameyfin.pluginapi.gamemetadata.GameMetadata) :
    GameMetadataProvider {
    override val supportedPlatforms: Set<Platform> = setOf(Platform.PC_MICROSOFT_WINDOWS)

    override fun fetchById(id: String): org.gameyfin.pluginapi.gamemetadata.GameMetadata {
        return metadataToReturn
    }

    override fun fetchByTitle(
        gameTitle: String,
        platformFilter: Set<Platform>,
        maxResults: Int
    ): List<org.gameyfin.pluginapi.gamemetadata.GameMetadata> {
        return listOf(metadataToReturn)
    }
}
