package org.gameyfin.app.api.v1

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.gameyfin.app.api.security.ApiScope
import org.gameyfin.app.api.v1.controller.LibraryControllerV1
import org.gameyfin.app.api.v1.dto.*
import org.gameyfin.app.api.v1.service.CatalogServiceV1
import org.gameyfin.app.core.tasks.DeletionTaskService
import org.gameyfin.app.libraries.LibraryScanService
import org.gameyfin.app.libraries.enums.ScanType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LibraryControllerV1Test {
    private val access = allowingAccess()
    private val catalog = mockk<CatalogServiceV1>()
    private val scans = mockk<LibraryScanService>(relaxed = true)
    private val deletions = mockk<DeletionTaskService>()
    private val controller = LibraryControllerV1(access, catalog, scans, deletions)

    private val library = LibraryV1("1", "Library", emptyList(), null, null)

    @Test
    fun `libraries can be listed fetched configured and patched`() {
        val page = PageV1(listOf(library), 0, 50, 1)
        val configuration = LibraryConfigurationV1(listOf(DirectoryV1("/games")), emptyList())
        every { catalog.libraries(any()) } returns page
        every { catalog.library("1") } returns library
        every { catalog.configuration("1") } returns configuration
        every { catalog.patchLibrary("1", any()) } returns library

        assertEquals(page, controller.libraries(0, 50, "name"))
        assertEquals(library, controller.library("1"))
        assertEquals(configuration, controller.configuration("1"))
        assertEquals(library, controller.patchLibrary("1", mapOf("name" to "Renamed")))
        verify { access.require(ApiScope.LIBRARIES_WRITE) }
        assertFailsWith<IllegalArgumentException> { controller.libraries(0, 50, "title") }
    }

    @Test
    fun `library creation optionally queues a quick scan`() {
        every { catalog.createLibrary(any()) } returns library
        val input = CreateLibraryV1("Library", listOf(DirectoryV1("/games")))

        val created = controller.createLibrary(input)
        assertEquals(201, created.statusCode.value())
        assertEquals("/v1/libraries/1", created.headers.location.toString())
        verify(exactly = 0) { scans.triggerScan(any(), any(), any()) }
        verify(exactly = 0) { access.require(ApiScope.LIBRARIES_SCAN) }

        controller.createLibrary(input.copy(scanAfterCreation = true))
        verify { access.require(ApiScope.LIBRARIES_SCAN) }
        verify { scans.triggerScan(ScanType.QUICK, listOf(1L), any()) }
    }

    @Test
    fun `library deletion and scans are accepted`() {
        every { catalog.library("1") } returns library
        every { deletions.deleteLibrary(1) } returns taskRun("run-lib")

        val deleted = controller.deleteLibrary("1")
        assertEquals(202, deleted.statusCode.value())
        assertEquals("/v1/task-runs/run-lib", deleted.headers.location.toString())
        assertEquals("run-lib", deleted.body!!.id)

        val scan = controller.scan("1", ScanV1("FULL"))
        assertEquals(202, scan.statusCode.value())
        assertEquals(AcceptedScanV1("1", "FULL"), scan.body)
        verify { scans.triggerScan(ScanType.FULL, listOf(1L), any()) }

        assertFailsWith<IllegalArgumentException> { controller.scan("1", ScanV1("PARTIAL")) }
    }
}
