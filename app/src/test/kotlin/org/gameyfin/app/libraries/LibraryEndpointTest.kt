package org.gameyfin.app.libraries

import io.mockk.*
import org.gameyfin.app.core.security.isCurrentUserAdmin
import org.gameyfin.app.libraries.dto.*
import org.gameyfin.app.libraries.enums.ScanType
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import kotlin.test.assertEquals

class LibraryEndpointTest {

    private lateinit var libraryService: LibraryService
    private lateinit var libraryScanService: LibraryScanService
    private lateinit var libraryEndpoint: LibraryEndpoint

    @BeforeEach
    fun setup() {
        libraryService = mockk()
        libraryScanService = mockk()
        libraryEndpoint = LibraryEndpoint(libraryService, libraryScanService)

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `subscribeToLibraryEvents should return admin flux when user is admin`() {
        mockkObject(LibraryService.Companion)
        every { isCurrentUserAdmin() } returns true
        val adminEvent = LibraryAdminEvent.Created(createAdminDto(1L))
        val adminFlux: Flux<List<LibraryAdminEvent>> = Flux.just(listOf(adminEvent))
        every { LibraryService.subscribeAdmin() } returns adminFlux

        val result = libraryEndpoint.subscribeToLibraryEvents()

        assertEquals(adminFlux, result)
        unmockkObject(LibraryService.Companion)
    }

    @Test
    fun `subscribeToLibraryEvents should return user flux when user is not admin`() {
        mockkObject(LibraryService.Companion)
        every { isCurrentUserAdmin() } returns false
        val userDto = LibraryUserDto(id = 1L, name = "Test Library", games = emptyList())
        val userEvent = LibraryUserEvent.Created(userDto)
        val userFlux: Flux<List<LibraryUserEvent>> = Flux.just(listOf(userEvent))
        every { LibraryService.subscribeUser() } returns userFlux

        val result = libraryEndpoint.subscribeToLibraryEvents()

        assertEquals(userFlux, result)
        unmockkObject(LibraryService.Companion)
    }

    @Suppress("ReactiveStreamsUnusedPublisher")
    @Test
    fun `subscribeToLibraryEvents should emit admin events to admin subscribers`() {
        mockkObject(LibraryService.Companion)
        every { isCurrentUserAdmin() } returns true
        val event = LibraryAdminEvent.Updated(createAdminDto(1L))
        val adminFlux: Flux<List<LibraryAdminEvent>> = Flux.just(listOf(event))
        every { LibraryService.subscribeAdmin() } returns adminFlux

        val result = libraryEndpoint.subscribeToLibraryEvents()

        StepVerifier.create(result)
            .expectNext(listOf(event))
            .verifyComplete()
        unmockkObject(LibraryService.Companion)
    }

    @Test
    fun `getAll should return all libraries from service`() {
        val libraries = listOf(createAdminDto(1L), createAdminDto(2L))
        every { libraryService.getAll() } returns libraries

        val result = libraryEndpoint.getAll()

        assertEquals(libraries, result)
        verify(exactly = 1) { libraryService.getAll() }
    }

    @Test
    fun `getAll should return empty list when no libraries exist`() {
        every { libraryService.getAll() } returns emptyList()

        val result = libraryEndpoint.getAll()

        assertEquals(emptyList(), result)
        verify(exactly = 1) { libraryService.getAll() }
    }

    @Test
    fun `subscribeToScanProgressEvents should return flux when user is admin`() {
        mockkObject(LibraryScanService.Companion)
        every { isCurrentUserAdmin() } returns true
        val scanProgress = mockk<LibraryScanProgress>(relaxed = true)
        val flux: Flux<List<LibraryScanProgress>> = Flux.just(listOf(scanProgress))
        every { LibraryScanService.subscribeToScanProgressEvents() } returns flux

        val result = libraryEndpoint.subscribeToScanProgressEvents()

        assertEquals(flux, result)
        unmockkObject(LibraryScanService.Companion)
    }

    @Test
    fun `subscribeToScanProgressEvents should return empty flux when user is not admin`() {
        every { isCurrentUserAdmin() } returns false

        val result = libraryEndpoint.subscribeToScanProgressEvents()

        StepVerifier.create(result)
            .verifyComplete()
    }

    @Test
    fun `triggerScan should call scan service with default parameters`() {
        every { libraryScanService.triggerScan(ScanType.QUICK, null) } just Runs

        libraryEndpoint.triggerScan(ScanType.QUICK, null)

        verify(exactly = 1) { libraryScanService.triggerScan(ScanType.QUICK, null) }
    }

    @Test
    fun `triggerScan should call scan service with provided scan type`() {
        every { libraryScanService.triggerScan(ScanType.FULL, null) } just Runs

        libraryEndpoint.triggerScan(ScanType.FULL, null)

        verify(exactly = 1) { libraryScanService.triggerScan(ScanType.FULL, null) }
    }

    @Test
    fun `triggerScan should call scan service with specific library ids`() {
        val libraryIds = listOf(1L, 2L, 3L)
        every { libraryScanService.triggerScan(ScanType.QUICK, libraryIds) } just Runs

        libraryEndpoint.triggerScan(ScanType.QUICK, libraryIds)

        verify(exactly = 1) { libraryScanService.triggerScan(ScanType.QUICK, libraryIds) }
    }

    @Test
    fun `triggerScan should handle scheduled scan type`() {
        every { libraryScanService.triggerScan(ScanType.SCHEDULED, null) } just Runs

        libraryEndpoint.triggerScan(ScanType.SCHEDULED, null)

        verify(exactly = 1) { libraryScanService.triggerScan(ScanType.SCHEDULED, null) }
    }

    @Test
    fun `createLibrary should call service to create library with scan`() {
        val dto = createAdminDto(name = "New Library")
        every { libraryService.create(dto, true) } just Runs

        libraryEndpoint.createLibrary(dto, scanAfterCreation = true)

        verify(exactly = 1) { libraryService.create(dto, true) }
    }

    @Test
    fun `createLibrary should call service to create library without scan`() {
        val dto = createAdminDto(name = "New Library")
        every { libraryService.create(dto, false) } just Runs

        libraryEndpoint.createLibrary(dto, scanAfterCreation = false)

        verify(exactly = 1) { libraryService.create(dto, false) }
    }

    @Test
    fun `createLibrary should use default scan parameter`() {
        val dto = createAdminDto(name = "New Library")
        every { libraryService.create(dto, true) } just Runs

        libraryEndpoint.createLibrary(dto)

        verify(exactly = 1) { libraryService.create(dto, true) }
    }

    @Test
    fun `updateLibrary should call service to update library`() {
        val updateDto = LibraryUpdateDto(id = 1L, name = "Updated Name")
        every { libraryService.update(updateDto) } just Runs

        libraryEndpoint.updateLibrary(updateDto)

        verify(exactly = 1) { libraryService.update(updateDto) }
    }

    @Test
    fun `updateLibrary should handle complex update dto`() {
        val updateDto = LibraryUpdateDto(
            id = 1L,
            name = "Updated",
            directories = listOf(DirectoryMappingDto("/path", "/ext")),
            platforms = listOf(Platform.PC_MICROSOFT_WINDOWS),
            unmatchedPaths = listOf("/unmatched")
        )
        every { libraryService.update(updateDto) } just Runs

        libraryEndpoint.updateLibrary(updateDto)

        verify(exactly = 1) { libraryService.update(updateDto) }
    }

    @Test
    fun `deleteLibrary should call service to delete library`() {
        every { libraryService.delete(1L) } just Runs

        libraryEndpoint.deleteLibrary(1L)

        verify(exactly = 1) { libraryService.delete(1L) }
    }

    @Test
    fun `deleteLibrary should handle multiple library deletions`() {
        every { libraryService.delete(any()) } just Runs

        libraryEndpoint.deleteLibrary(1L)
        libraryEndpoint.deleteLibrary(2L)
        libraryEndpoint.deleteLibrary(3L)

        verify(exactly = 1) { libraryService.delete(1L) }
        verify(exactly = 1) { libraryService.delete(2L) }
        verify(exactly = 1) { libraryService.delete(3L) }
    }

    private fun createAdminDto(
        id: Long = 1L,
        name: String = "Test Library"
    ): LibraryAdminDto {
        return LibraryAdminDto(
            id = id,
            name = name,
            directories = emptyList(),
            platforms = emptyList(),
            games = emptyList(),
            stats = LibraryStatsDto(0, 0),
            unmatchedPaths = emptyList()
        )
    }
}

