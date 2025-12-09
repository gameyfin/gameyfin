package org.gameyfin.app.libraries

import com.vaadin.hilla.exception.EndpointException
import io.mockk.*
import org.gameyfin.app.core.security.getCurrentAuth
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.entities.GameMetadata
import org.gameyfin.app.libraries.dto.*
import org.gameyfin.app.libraries.entities.DirectoryMapping
import org.gameyfin.app.libraries.entities.IgnoredPath
import org.gameyfin.app.libraries.entities.IgnoredPathUserSource
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.app.libraries.enums.ScanType
import org.gameyfin.app.libraries.extensions.toDto
import org.gameyfin.app.libraries.extensions.toDtos
import org.gameyfin.app.users.UserService
import org.gameyfin.app.users.entities.User
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.core.Authentication
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LibraryServiceTest {

    private lateinit var libraryRepository: LibraryRepository
    private lateinit var libraryScanService: LibraryScanService
    private lateinit var userService: UserService
    private lateinit var libraryService: LibraryService
    private lateinit var ignoredPathRepository: IgnoredPathRepository

    @BeforeEach
    fun setup() {
        libraryRepository = mockk()
        libraryScanService = mockk()
        userService = mockk()
        ignoredPathRepository = mockk()
        libraryService = LibraryService(libraryRepository, libraryScanService, userService, ignoredPathRepository)

        mockkStatic("org.gameyfin.app.libraries.extensions.LibraryExtensionsKt")
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `getAll should return all libraries as DTOs`() {
        val library1 = createTestLibrary(1L, "Library 1")
        val library2 = createTestLibrary(2L, "Library 2")
        val libraries = listOf(library1, library2)
        val libraryDtos = listOf(
            createAdminDto(1L, "Library 1"),
            createAdminDto(2L, "Library 2")
        )

        every { libraryRepository.findAll() } returns libraries
        every { libraries.toDtos() } returns libraryDtos

        val result = libraryService.getAll()

        assertEquals(2, result.size)
        assertEquals(libraryDtos, result)
        verify(exactly = 1) { libraryRepository.findAll() }
    }

    @Test
    fun `getAll should return empty list when no libraries exist`() {
        val emptyList = emptyList<Library>()

        every { libraryRepository.findAll() } returns emptyList
        every { emptyList.toDtos() } returns emptyList()

        val result = libraryService.getAll()

        assertTrue(result.isEmpty())
        verify(exactly = 1) { libraryRepository.findAll() }
    }

    @Test
    fun `getById should return library when it exists`() {
        val library = createTestLibrary(1L, "Test Library")

        every { libraryRepository.findByIdOrNull(1L) } returns library

        val result = libraryService.getById(1L)

        assertEquals(library, result)
        verify(exactly = 1) { libraryRepository.findByIdOrNull(1L) }
    }

    @Test
    fun `getById should throw exception when library not found`() {
        every { libraryRepository.findByIdOrNull(999L) } returns null

        assertThrows(IllegalArgumentException::class.java) {
            libraryService.getById(999L)
        }
    }

    @Test
    fun `create should create library with unique directories`() {
        val dto = createAdminDto(
            name = "New Library",
            directories = listOf(
                DirectoryMappingDto("/path1", "/external1"),
                DirectoryMappingDto("/path1", "/external1"),
                DirectoryMappingDto("/path2", "/external2")
            )
        )

        every { libraryRepository.findByPaths(any()) } returns emptyList()
        every { libraryRepository.save(any()) } answers { firstArg() }
        every { libraryScanService.triggerScan(any(), any()) } just Runs

        libraryService.create(dto, scanAfterCreation = false)

        verify {
            libraryRepository.save(match { library ->
                library.directories.size == 2 &&
                        library.directories.any { it.internalPath == "/path1" } &&
                        library.directories.any { it.internalPath == "/path2" }
            })
        }
    }

    @Test
    fun `create should trigger scan when scanAfterCreation is true`() {
        val dto = createAdminDto(name = "New Library")
        val savedLibrary = createTestLibrary(1L, "New Library")

        every { libraryRepository.findByPaths(any()) } returns emptyList()
        every { libraryRepository.save(any()) } returns savedLibrary
        every { libraryScanService.triggerScan(ScanType.QUICK, listOf(1L)) } just Runs

        libraryService.create(dto, scanAfterCreation = true)

        verify(exactly = 1) { libraryScanService.triggerScan(ScanType.QUICK, listOf(1L)) }
    }

    @Test
    fun `create should not trigger scan when scanAfterCreation is false`() {
        val dto = createAdminDto(name = "New Library")

        every { libraryRepository.findByPaths(any()) } returns emptyList()
        every { libraryRepository.save(any()) } answers { firstArg() }

        libraryService.create(dto, scanAfterCreation = false)

        verify(exactly = 0) { libraryScanService.triggerScan(any(), any()) }
    }

    @Test
    fun `create should throw exception when directory already exists in another library`() {
        val dto = createAdminDto(
            name = "New Library",
            directories = listOf(DirectoryMappingDto("/existing/path", null))
        )
        val existingLibrary = createTestLibrary(2L, "Existing Library")

        every { libraryRepository.findByPaths(listOf("/existing/path")) } returns
                listOf(Pair("/existing/path", existingLibrary))

        assertThrows(EndpointException::class.java) {
            libraryService.create(dto, scanAfterCreation = false)
        }
    }

    @Test
    fun `update should update library name when provided`() {
        val library = createTestLibrary(1L, "Old Name")
        val updateDto = LibraryUpdateDto(id = 1L, name = "New Name")

        every { libraryRepository.findByIdOrNull(1L) } returns library
        every { libraryRepository.save(library) } returnsArgument 0

        libraryService.update(updateDto)

        assertEquals("New Name", library.name)
        verify(exactly = 1) { libraryRepository.save(library) }
    }

    @Test
    fun `update should throw exception when library not found`() {
        val updateDto = LibraryUpdateDto(id = 999L, name = "New Name")

        every { libraryRepository.findByIdOrNull(999L) } returns null

        assertThrows(IllegalArgumentException::class.java) {
            libraryService.update(updateDto)
        }
    }

    @Test
    fun `update should update directories when provided`() {
        val library = createTestLibrary(
            id = 1L,
            directories = mutableListOf(
                createDirectoryMapping("/old/path1"),
                createDirectoryMapping("/old/path2")
            )
        )
        val updateDto = LibraryUpdateDto(
            id = 1L,
            directories = listOf(
                DirectoryMappingDto("/new/path1", "/ext1"),
                DirectoryMappingDto("/new/path2", "/ext2")
            )
        )

        every { libraryRepository.findByIdOrNull(1L) } returns library
        every { libraryRepository.findByPaths(any()) } returns emptyList()
        every { libraryRepository.save(library) } returns library

        libraryService.update(updateDto)

        assertEquals(2, library.directories.size)
        assertTrue(library.directories.any { it.internalPath == "/new/path1" })
        assertTrue(library.directories.any { it.internalPath == "/new/path2" })
    }

    @Test
    fun `update should remove games when directory is removed`() {
        val game1 = createTestGame(1L, "/old/path1/game1")
        val game2 = createTestGame(2L, "/old/path2/game2")
        val game3 = createTestGame(3L, "/keep/path/game3")

        val library = createTestLibrary(
            id = 1L,
            directories = mutableListOf(
                createDirectoryMapping("/old/path1"),
                createDirectoryMapping("/old/path2"),
                createDirectoryMapping("/keep/path")
            ),
            games = mutableListOf(game1, game2, game3)
        )

        val updateDto = LibraryUpdateDto(
            id = 1L,
            directories = listOf(DirectoryMappingDto("/keep/path", null))
        )

        every { libraryRepository.findByIdOrNull(1L) } returns library
        every { libraryRepository.findByPaths(any()) } returns emptyList()
        every { libraryRepository.save(library) } returns library

        libraryService.update(updateDto)

        assertEquals(1, library.games.size)
        assertEquals(game3, library.games[0])
    }

    @Test
    fun `update should update platforms when provided`() {
        val library = createTestLibrary(1L, platforms = mutableListOf(Platform.PLAYSTATION_5))
        val updateDto = LibraryUpdateDto(
            id = 1L,
            platforms = listOf(Platform.XBOX_SERIES_X_S, Platform.PC_MICROSOFT_WINDOWS)
        )

        every { libraryRepository.findByIdOrNull(1L) } returns library
        every { libraryRepository.save(library) } returns library

        libraryService.update(updateDto)

        assertEquals(2, library.platforms.size)
        assertTrue(library.platforms.contains(Platform.XBOX_SERIES_X_S))
        assertTrue(library.platforms.contains(Platform.PC_MICROSOFT_WINDOWS))
    }

    @Test
    fun `update should update ignoredPaths when provided`() {
        val library = createTestLibrary(
            1L,
            ignoredPaths = mutableListOf(createTestIgnoredPath("/old/unmatched"))
        )

        val updateDto = LibraryUpdateDto(
            id = 1L,
            ignoredPaths = listOf(
                createTestIgnoredPath("/new/unmatched1").toDto(),
                createTestIgnoredPath("/new/unmatched2").toDto()
            )
        )

        val user = mockk<User>() {
            every { id } returns 42L
            every { username } returns "testuser"
        }

        val auth = mockk<Authentication> {
            every { isAuthenticated } returns true
            every { principal } returns "testuser"
            every { name } returns "testuser"
        }

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns auth
        every { userService.getByUsername("testuser") } returns user
        every { libraryRepository.findByIdOrNull(1L) } returns library
        every { libraryRepository.save(library) } returns library
        every { ignoredPathRepository.findByPath(any<String>()) } returns null

        libraryService.update(updateDto)

        assertEquals(2, library.ignoredPaths.size)
        assertTrue(library.ignoredPaths.map { it.path }.contains("/new/unmatched1"))
        assertTrue(library.ignoredPaths.map { it.path }.contains("/new/unmatched2"))
    }

    @Test
    fun `update should throw exception when duplicate directory in another library`() {
        val library = createTestLibrary(1L)
        val otherLibrary = createTestLibrary(2L, "Other Library")
        val updateDto = LibraryUpdateDto(
            id = 1L,
            directories = listOf(DirectoryMappingDto("/duplicate/path", null))
        )

        every { libraryRepository.findByIdOrNull(1L) } returns library
        every { libraryRepository.findByPaths(listOf("/duplicate/path")) } returns
                listOf(Pair("/duplicate/path", otherLibrary))

        assertThrows(EndpointException::class.java) {
            libraryService.update(updateDto)
        }
    }

    @Test
    fun `update should allow duplicate directory in same library`() {
        val library = createTestLibrary(
            id = 1L,
            directories = mutableListOf(createDirectoryMapping("/existing/path"))
        )
        val updateDto = LibraryUpdateDto(
            id = 1L,
            directories = listOf(DirectoryMappingDto("/existing/path", "/new/external"))
        )

        every { libraryRepository.findByIdOrNull(1L) } returns library
        every { libraryRepository.findByPaths(listOf("/existing/path")) } returns
                listOf(Pair("/existing/path", library))
        every { libraryRepository.save(library) } returns library

        libraryService.update(updateDto)

        verify(exactly = 1) { libraryRepository.save(library) }
    }

    @Test
    fun `update should update existing directory mapping`() {
        val existingMapping = createDirectoryMapping("/path1", "/old/external")
        val library = createTestLibrary(
            id = 1L,
            directories = mutableListOf(existingMapping)
        )
        val updateDto = LibraryUpdateDto(
            id = 1L,
            directories = listOf(DirectoryMappingDto("/path1", "/new/external"))
        )

        every { libraryRepository.findByIdOrNull(1L) } returns library
        every { libraryRepository.findByPaths(listOf("/path1")) } returns
                listOf(Pair("/path1", library))
        every { libraryRepository.save(any<Library>()) } returnsArgument 0


        libraryService.update(updateDto)

        assertEquals(1, library.directories.size)
        assertEquals("/new/external", existingMapping.externalPath)
    }

    @Test
    fun `update should update timestamp`() {
        val library = createTestLibrary(1L)
        val updateDto = LibraryUpdateDto(id = 1L, name = "Updated")
        val beforeUpdate = Instant.now()

        every { libraryRepository.findByIdOrNull(1L) } returns library
        every { libraryRepository.save(library) } answers {
            library.updatedAt = Instant.now()
            library
        }

        libraryService.update(updateDto)

        assertNotNull(library.updatedAt)
        assertTrue(library.updatedAt!! > beforeUpdate)
    }

    @Test
    fun `update with list should call update for every element`() {
        val library1 = createTestLibrary(1L)
        val library2 = createTestLibrary(2L)
        val updateDto1 = LibraryUpdateDto(id = 1L, name = "Updated 1")
        val updateDto2 = LibraryUpdateDto(id = 2L, name = "Updated 2")
        val beforeUpdate = Instant.now()

        every { libraryRepository.findByIdOrNull(1L) } returns library1
        every { libraryRepository.findByIdOrNull(2L) } returns library2
        every { libraryRepository.save(library1) } answers {
            library1.updatedAt = Instant.now()
            library1
        }
        every { libraryRepository.save(library2) } answers {
            library2.updatedAt = Instant.now()
            library2
        }

        libraryService.update(listOf(updateDto1, updateDto2))

        assertNotNull(library1.updatedAt)
        assertTrue(library1.updatedAt!! > beforeUpdate)
        assertNotNull(library2.updatedAt)
        assertTrue(library2.updatedAt!! > beforeUpdate)
    }

    @Test
    fun `delete should delete library by id`() {
        every { libraryRepository.deleteById(1L) } just Runs

        libraryService.delete(1L)

        verify(exactly = 1) { libraryRepository.deleteById(1L) }
    }

    private fun createTestLibrary(
        id: Long = 1L,
        name: String = "Test Library",
        directories: MutableList<DirectoryMapping> = mutableListOf(),
        platforms: MutableList<Platform> = mutableListOf(),
        games: MutableList<Game> = mutableListOf(),
        ignoredPaths: MutableList<IgnoredPath> = mutableListOf()
    ): Library {
        var libraryName = name
        var updatedAtTime = Instant.now()
        return mockk<Library>(relaxed = true) {
            every { this@mockk.id } returns id
            every { this@mockk.name } answers { libraryName }
            every { this@mockk.name = any() } propertyType String::class answers {
                libraryName = value
            }
            every { this@mockk.directories } returns directories
            every { this@mockk.platforms } returns platforms
            every { this@mockk.games } returns games
            every { this@mockk.ignoredPaths } returns ignoredPaths
            every { this@mockk.updatedAt } answers { updatedAtTime }
            every { this@mockk.updatedAt = any() } propertyType Instant::class answers {
                updatedAtTime = value
            }
        }
    }

    private fun createDirectoryMapping(
        internalPath: String,
        externalPath: String? = null
    ): DirectoryMapping {
        var extPath = externalPath
        return mockk<DirectoryMapping>(relaxed = true) {
            every { this@mockk.internalPath } returns internalPath
            every { this@mockk.externalPath } answers { extPath }
            every { this@mockk.externalPath = any() } propertyType String::class answers {
                extPath = value
            }
        }
    }

    private fun createTestIgnoredPath(path: String): IgnoredPath {
        val user = mockk<User> {
            every { id } returns 42L
        }
        val pluginSource = IgnoredPathUserSource(user)
        return IgnoredPath(id = 0L, path = path, source = pluginSource)
    }

    private fun createTestGame(id: Long, path: String): Game {
        val metadata = GameMetadata(path = path)
        return mockk<Game>(relaxed = true) {
            every { this@mockk.id } returns id
            every { this@mockk.metadata } returns metadata
        }
    }

    private fun createAdminDto(
        id: Long = 1L,
        name: String = "Test Library",
        directories: List<DirectoryMappingDto> = emptyList(),
        platforms: List<Platform> = emptyList()
    ): LibraryAdminDto {
        return LibraryAdminDto(
            id = id,
            createdAt = Instant.now(),
            name = name,
            directories = directories,
            platforms = platforms,
            gameIds = emptyList(),
            stats = LibraryStatsDto(0, 0),
            ignoredPaths = emptyList(),
            metadata = LibraryMetadataDto(true, 1)
        )
    }
}


