package org.gameyfin.app.libraries.extensions

import io.mockk.*
import org.gameyfin.app.libraries.dto.DirectoryMappingDto
import org.gameyfin.app.libraries.dto.LibraryField
import org.gameyfin.app.libraries.dto.LibraryMetadataDto
import org.gameyfin.app.libraries.entities.DirectoryMapping
import org.gameyfin.app.libraries.entities.IgnoredPath
import org.gameyfin.app.libraries.entities.IgnoredPathPluginSource
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.app.libraries.entities.LibraryMetadata
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryExtensionsTest {

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `toSummaryDto should include admin-only fields for admins`() {
        val createdAt = Instant.parse("2024-01-01T00:00:00Z")
        val updatedAt = Instant.parse("2024-02-01T00:00:00Z")
        val library = createTestLibrary(
            id = 1L,
            name = "Library",
            platforms = mutableListOf(Platform.PC_MICROSOFT_WINDOWS),
            createdAt = createdAt,
            updatedAt = updatedAt
        )

        val result = library.toSummaryDto(admin = true)

        assertEquals(1L, result.id)
        assertEquals("Library", result.name)
        assertEquals(createdAt, result.createdAt)
        assertEquals(updatedAt, result.updatedAt)
        assertEquals(listOf(Platform.PC_MICROSOFT_WINDOWS), result.platforms)
        assertEquals(LibraryMetadataDto(true, 3), result.metadata)
    }

    @Test
    fun `toSummaryDto should omit admin-only fields for users`() {
        val library = createTestLibrary(id = 1L, platforms = mutableListOf(Platform.PC_MICROSOFT_WINDOWS))

        val result = library.toSummaryDto(admin = false)

        assertNull(result.platforms)
    }

    @Test
    fun `toSummaryDto should not access games or ignored paths`() {
        val library = createTestLibrary(id = 1L)

        library.toSummaryDto(admin = true)

        verify(exactly = 0) { library.games }
        verify(exactly = 0) { library.ignoredPaths }
    }

    @Test
    fun `toPatchDto should only contain requested fields`() {
        val library = createTestLibrary(id = 1L, name = "Renamed", platforms = mutableListOf(Platform.PLAYSTATION_5))

        val result = library.toPatchDto(setOf(LibraryField.NAME), admin = true)

        assertNotNull(result)
        assertEquals(listOf(LibraryField.NAME), result.fields)
        assertEquals("Renamed", result.values.name)
        assertNull(result.values.platforms)
        assertNull(result.values.directories)
    }

    @Test
    fun `toPatchDto should drop admin-only fields for users`() {
        val library = createTestLibrary(
            id = 1L,
            name = "Renamed",
            platforms = mutableListOf(Platform.PLAYSTATION_5)
        )

        val result = library.toPatchDto(setOf(LibraryField.NAME, LibraryField.PLATFORMS), admin = false)

        assertNotNull(result)
        assertEquals(listOf(LibraryField.NAME), result.fields)
        assertNull(result.values.platforms)
    }

    @Test
    fun `toPatchDto should return null if no field is visible`() {
        val library = createTestLibrary(id = 1L)

        val result = library.toPatchDto(setOf(LibraryField.DIRECTORIES, LibraryField.IGNORED_PATHS), admin = false)

        assertNull(result)
    }

    @Test
    fun `toPatchDto should map admin-only collections for admins`() {
        val library = createTestLibrary(
            id = 1L,
            directories = mutableListOf(createDirectoryMapping("/path", "/ext")),
            ignoredPaths = mutableListOf(createTestIgnoredPath("/ignored"))
        )

        val result = library.toPatchDto(setOf(LibraryField.IGNORED_PATHS, LibraryField.DIRECTORIES), admin = true)

        assertNotNull(result)
        assertEquals(listOf(LibraryField.DIRECTORIES, LibraryField.IGNORED_PATHS), result.fields)
        assertEquals(listOf(DirectoryMappingDto("/path", "/ext")), result.values.directories)
        assertEquals(listOf("/ignored"), result.values.ignoredPaths?.map { it.path })
    }

    @Test
    fun `toAdminDto should create AdminDto with correct properties`() {
        val dir1 = createDirectoryMapping("/path1", "/ext1")
        val dir2 = createDirectoryMapping("/path2", "/ext2")

        val ignoredPath1 = IgnoredPath(id = 0L, path = "/unmatched1", source = IgnoredPathPluginSource(mutableListOf()))
        val ignoredPath2 = IgnoredPath(id = 1L, path = "/unmatched2", source = IgnoredPathPluginSource(mutableListOf()))

        val library = createTestLibrary(
            id = 1L,
            name = "Admin Library",
            directories = mutableListOf(dir1, dir2),
            platforms = mutableListOf(Platform.PC_MICROSOFT_WINDOWS, Platform.PLAYSTATION_5),
            ignoredPaths = mutableListOf(ignoredPath1, ignoredPath2)
        )

        val result = library.toAdminDto()

        assertEquals(1L, result.id)
        assertEquals("Admin Library", result.name)
        assertEquals(2, result.directories.size)
        assertEquals("/path1", result.directories[0].internalPath)
        assertEquals("/ext1", result.directories[0].externalPath)
        assertEquals(2, result.platforms.size)
        assertTrue(result.platforms.contains(Platform.PC_MICROSOFT_WINDOWS))
        assertEquals(2, result.ignoredPaths?.size ?: 0)
        verify(exactly = 0) { library.games }
    }

    @Test
    fun `toAdminDto should handle library with no directories`() {
        val library = createTestLibrary(id = 1L, directories = mutableListOf())

        val result = library.toAdminDto()

        assertTrue(result.directories.isEmpty())
    }

    @Test
    fun `toAdminDto should handle library with no platforms`() {
        val library = createTestLibrary(id = 1L, platforms = mutableListOf())

        val result = library.toAdminDto()

        assertTrue(result.platforms.isEmpty())
    }

    @Test
    fun `toAdminDto should handle empty unmatchedPaths`() {
        val library = createTestLibrary(
            id = 1L,
            ignoredPaths = mutableListOf()
        )

        val result = library.toAdminDto()

        assertTrue(result.ignoredPaths?.isEmpty() ?: false)
    }

    @Test
    fun `toAdminDto should include all unmatchedPaths`() {
        val library = createTestLibrary(
            id = 1L,
            ignoredPaths = mutableListOf(
                createTestIgnoredPath("/path1"),
                createTestIgnoredPath("/path2"),
                createTestIgnoredPath("/path3")
            )
        )

        val result = library.toAdminDto()

        assertEquals(3, result.ignoredPaths?.size ?: 0)
        assertTrue(result.ignoredPaths?.map { it.path }?.contains("/path1") ?: false)
        assertTrue(result.ignoredPaths.map { it.path }.contains("/path2"))
        assertTrue(result.ignoredPaths.map { it.path }.contains("/path3"))
    }

    @Test
    fun `toAdminDto should map directory with null externalPath`() {
        val dir = createDirectoryMapping("/internal", null)
        val library = createTestLibrary(
            id = 1L,
            directories = mutableListOf(dir)
        )

        val result = library.toAdminDto()

        assertEquals(1, result.directories.size)
        assertEquals("/internal", result.directories[0].internalPath)
        assertEquals(null, result.directories[0].externalPath)
    }

    private fun createTestLibrary(
        id: Long,
        name: String = "Test Library",
        directories: MutableList<DirectoryMapping> = mutableListOf(),
        platforms: MutableList<Platform> = mutableListOf(),
        ignoredPaths: MutableList<IgnoredPath> = mutableListOf(),
        createdAt: Instant = Instant.now(),
        updatedAt: Instant = Instant.now()
    ): Library {
        return mockk<Library>(relaxed = true) {
            every { this@mockk.id } returns id
            every { this@mockk.name } returns name
            every { this@mockk.createdAt } returns createdAt
            every { this@mockk.updatedAt } returns updatedAt
            every { this@mockk.directories } returns directories
            every { this@mockk.platforms } returns platforms
            every { this@mockk.games } returns mutableListOf()
            every { this@mockk.ignoredPaths } returns ignoredPaths
            every { this@mockk.metadata } returns LibraryMetadata(displayOnHomepage = true, displayOrder = 3)
        }
    }

    private fun createDirectoryMapping(
        internalPath: String,
        externalPath: String?
    ): DirectoryMapping {
        return mockk<DirectoryMapping>(relaxed = true) {
            every { this@mockk.internalPath } returns internalPath
            every { this@mockk.externalPath } returns externalPath
        }
    }

    private fun createTestIgnoredPath(path: String): IgnoredPath {
        val pluginSource = IgnoredPathPluginSource(mutableListOf())
        return IgnoredPath(id = 0L, path = path, source = pluginSource)
    }
}
