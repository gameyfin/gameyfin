package org.gameyfin.app.api.v1

import io.mockk.*
import org.gameyfin.app.api.security.ApiScope
import org.gameyfin.app.api.v1.controller.CollectionControllerV1
import org.gameyfin.app.api.v1.dto.CollectionV1
import org.gameyfin.app.api.v1.dto.CreateCollectionV1
import org.gameyfin.app.api.v1.dto.GameV1
import org.gameyfin.app.api.v1.dto.PageV1
import org.gameyfin.app.api.v1.service.CatalogServiceV1
import org.gameyfin.app.core.tasks.DeletionTaskService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CollectionControllerV1Test {
    private val access = allowingAccess()
    private val catalog = mockk<CatalogServiceV1>()
    private val deletions = mockk<DeletionTaskService>()
    private val controller = CollectionControllerV1(access, catalog, deletions)

    private val collection = CollectionV1("2", "Collection", null, null, null)
    private val emptyGames = PageV1<GameV1>(emptyList(), 0, 50, 0)

    @Test
    fun `collections can be listed created patched and deleted`() {
        val page = PageV1(listOf(collection), 0, 50, 1)
        every { catalog.collections(any()) } returns page
        every { catalog.collection("2") } returns collection
        every { catalog.createCollection(any()) } returns collection
        every { catalog.patchCollection("2", any()) } returns collection
        every { deletions.deleteCollection(2) } returns taskRun("run-col")

        assertEquals(page, controller.collections(0, 50, "id"))
        assertEquals(collection, controller.collection("2"))
        val created = controller.createCollection(CreateCollectionV1("Collection"))
        assertEquals(201, created.statusCode.value())
        assertEquals("/v1/collections/2", created.headers.location.toString())
        assertEquals(collection, controller.patchCollection("2", mapOf("name" to "Renamed")))
        val deleted = controller.deleteCollection("2")
        assertEquals(202, deleted.statusCode.value())
        assertEquals("/v1/task-runs/run-col", deleted.headers.location.toString())
    }

    @Test
    fun `collection games and membership delegate to the catalog`() {
        every { catalog.collection("2") } returns collection
        every { catalog.games(any(), null, null, "2", null, null, null) } returns emptyGames
        every { catalog.membership(any(), any(), any()) } just runs

        assertEquals(emptyGames, controller.collectionGames("2", 0, 50))
        assertEquals(204, controller.addGame("2", "10").statusCode.value())
        assertEquals(204, controller.removeGame("2", "10").statusCode.value())

        verify { access.require(ApiScope.COLLECTIONS_READ) }
        verify { catalog.membership("2", "10", true) }
        verify { catalog.membership("2", "10", false) }
    }
}
