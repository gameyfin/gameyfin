package org.gameyfin.app.libraries

import io.mockk.*
import org.gameyfin.app.core.security.isCurrentUserAdmin
import org.gameyfin.app.core.tasks.DeletionTaskService
import org.gameyfin.app.core.sync.EntityEventType
import org.gameyfin.app.libraries.dto.*
import org.gameyfin.app.libraries.enums.ScanType
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Instant
import kotlin.test.assertEquals

class LibraryEndpointTest {

    private lateinit var libraryService: LibraryService
    private lateinit var libraryScanService: LibraryScanService
    private lateinit var libraryEndpoint: LibraryEndpoint
    private lateinit var deletionTaskService: DeletionTaskService

    @BeforeEach
    fun setup() {
        libraryService = mockk()
        libraryScanService = mockk()
        deletionTaskService = mockk(relaxed = true)
        libraryEndpoint = LibraryEndpoint(libraryService, libraryScanService, deletionTaskService)

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
        val adminEvent = LibraryEvent(EntityEventType.CREATED, 1L, summary = createSummaryDto(1L, admin = true))
        val adminFlux: Flux<List<LibraryEvent>> = Flux.just(listOf(adminEvent))
        every { LibraryService.subscribeAdmin() } returns adminFlux

        val result = libraryEndpoint.subscribeToLibraryEvents()

        assertEquals(adminFlux, result)
        unmockkObject(LibraryService.Companion)
    }

    @Test
    fun `subscribeToLibraryEvents should return user flux when user is not admin`() {
        mockkObject(LibraryService.Companion)
        every { isCurrentUserAdmin() } returns false
        val userEvent = LibraryEvent(EntityEventType.CREATED, 1L, summary = createSummaryDto(1L, admin = false))
        val userFlux: Flux<List<LibraryEvent>> = Flux.just(listOf(userEvent))
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
        val event = LibraryEvent(
            EntityEventType.UPDATED,
            1L,
            patch = LibraryPatchDto(
                updatedAt = Instant.now(),
                fields = listOf(LibraryField.NAME),
                values = LibraryPatchValuesDto(name = "Renamed")
            )
        )
        val adminFlux: Flux<List<LibraryEvent>> = Flux.just(listOf(event))
        every { LibraryService.subscribeAdmin() } returns adminFlux

        val result = libraryEndpoint.subscribeToLibraryEvents()

        StepVerifier.create(result)
            .expectNext(listOf(event))
            .verifyComplete()
        unmockkObject(LibraryService.Companion)
    }

    @Test
    fun `sync should delegate to service with admin flag`() {
        val since = Instant.parse("2024-01-01T00:00:00Z")
        val syncDto = LibrarySyncDto(Instant.now(), false, listOf(createSummaryDto(1L, true)), listOf(1L))
        every { isCurrentUserAdmin() } returns true
        every { libraryService.sync(since, true) } returns syncDto

        val result = libraryEndpoint.sync(since)

        assertEquals(syncDto, result)
        verify(exactly = 1) { libraryService.sync(since, true) }
    }

    @Test
    fun `sync should delegate to service for non-admin users`() {
        val syncDto = LibrarySyncDto(Instant.now(), true, emptyList(), emptyList())
        every { isCurrentUserAdmin() } returns false
        every { libraryService.sync(null, false) } returns syncDto

        val result = libraryEndpoint.sync(null)

        assertEquals(syncDto, result)
        verify(exactly = 1) { libraryService.sync(null, false) }
    }

    @Test
    fun `getById should return full library from service`() {
        val dto = createAdminDto(1L)
        every { libraryService.getAdminDtoById(1L) } returns dto

        assertEquals(dto, libraryEndpoint.getById(1L))
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
            ignoredPaths = listOf(IgnoredPathDto(0L, "/unmatched", IgnoredPathSourceTypeDto.PLUGIN, "SomePlugin"))
        )
        every { libraryService.update(updateDto) } just Runs

        libraryEndpoint.updateLibrary(updateDto)

        verify(exactly = 1) { libraryService.update(updateDto) }
    }

    @Test
    fun `updateLibraries should call service to update libraries`() {
        val updateDto1 = LibraryUpdateDto(id = 1L, name = "Updated Name 1")
        val updateDto2 = LibraryUpdateDto(id = 2L, name = "Updated Name 2")
        val updateDtos = listOf(updateDto1, updateDto2)

        every { libraryService.update(updateDtos) } just Runs

        libraryEndpoint.updateLibraries(updateDtos)

        verify(exactly = 1) { libraryService.update(updateDtos) }
    }

    @Test
    fun `deleteLibrary should call service to delete library`() {
        every { deletionTaskService.deleteLibrary(1L) } returns mockk()

        libraryEndpoint.deleteLibrary(1L)

        verify(exactly = 1) { deletionTaskService.deleteLibrary(1L) }
    }

    @Test
    fun `deleteLibrary should handle multiple library deletions`() {
        every { deletionTaskService.deleteLibrary(any()) } returns mockk()

        libraryEndpoint.deleteLibrary(1L)
        libraryEndpoint.deleteLibrary(2L)
        libraryEndpoint.deleteLibrary(3L)

        verify(exactly = 1) { deletionTaskService.deleteLibrary(1L) }
        verify(exactly = 1) { deletionTaskService.deleteLibrary(2L) }
        verify(exactly = 1) { deletionTaskService.deleteLibrary(3L) }
    }

    private fun createSummaryDto(id: Long, admin: Boolean) = LibrarySummaryDto(
        id = id,
        name = "Test Library",
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        metadata = LibraryMetadataDto(true, 1),
        platforms = if (admin) listOf(Platform.PC_MICROSOFT_WINDOWS) else null
    )

    private fun createAdminDto(
        id: Long = 1L,
        name: String = "Test Library"
    ): LibraryAdminDto {
        return LibraryAdminDto(
            id = id,
            createdAt = Instant.now(),
            name = name,
            directories = emptyList(),
            platforms = emptyList(),
            ignoredPaths = emptyList(),
            metadata = LibraryMetadataDto(true, 1)
        )
    }
}
