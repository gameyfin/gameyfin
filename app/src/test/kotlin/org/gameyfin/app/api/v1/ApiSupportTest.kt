package org.gameyfin.app.api.v1

import org.gameyfin.app.collections.entities.Collection
import org.gameyfin.app.core.plugins.management.PluginManagementEntry
import org.gameyfin.app.games.entities.Company
import org.gameyfin.app.games.entities.CompanyType
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.entities.GameMetadata
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.app.media.Image
import org.gameyfin.app.media.ImageType
import org.gameyfin.pluginapi.gamemetadata.*
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import kotlin.test.*
import org.gameyfin.app.api.v1.dto.*

class ApiSupportTest {
    @Test
    fun `api ids must be positive numbers`() {
        assertEquals(42, apiId("42"))
        listOf("0", "-1", "abc", "").forEach { assertFailsWith<IllegalArgumentException> { apiId(it) } }
    }

    @Test
    fun `pages validate bounds and add a stable id order`() {
        assertEquals(PageRequest.of(1, 20, Sort.by(Sort.Direction.ASC, "id")), apiPage(1, 20, "id"))
        assertEquals(
            PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"))),
            apiPage(0, 5, "createdAt,desc")
        )
        assertEquals(Sort.by(Sort.Direction.ASC, "updatedAt").and(Sort.by("id")), apiPage(0, 5, "updatedAt,asc").sort)
        listOf(
            { apiPage(-1, 10, "id") }, { apiPage(0, 0, "id") }, { apiPage(0, 201, "id") },
            { apiPage(Int.MAX_VALUE, 200, "id") }, { apiPage(0, 10, "title") }, { apiPage(0, 10, "id,up") },
            { apiPage(0, 10, "id,asc,desc") }
        ).forEach { assertFailsWith<IllegalArgumentException> { it() } }
    }

    @Test
    fun `enum codes are matched exactly`() {
        assertEquals(Genre.entries.first(), apiEnum<Genre>(Genre.entries.first().name))
        val error = assertFailsWith<IllegalArgumentException> { apiEnum<Genre>("not-a-genre") }
        assertEquals("Unknown Genre code: not-a-genre", error.message)
    }

    @Test
    fun `pages map their content`() {
        val page = PageImpl(listOf(1, 2), PageRequest.of(2, 2), 10).publicPage { "#$it" }
        assertEquals(PageV1(listOf("#1", "#2"), 2, 2, 10), page)
    }

    @Test
    fun `games expose public fields and image urls`() {
        val created = Instant.parse("2024-01-01T00:00:00Z")
        val library = Library(id = 3, name = "Library")
        val game = Game(
            id = 9, createdAt = created, updatedAt = created, library = library,
            platforms = mutableListOf(Platform.entries.first()), title = "Title",
            coverImage = Image(id = 1, type = ImageType.COVER, mimeType = "image/png", contentLength = 10),
            headerImage = Image(id = 2, type = ImageType.HEADER),
            comment = "Comment", summary = "Summary", release = Instant.parse("2020-05-06T23:00:00Z"),
            userRating = 80, criticRating = 70,
            publishers = mutableListOf(Company(name = "Publisher", type = CompanyType.PUBLISHER)),
            developers = mutableListOf(Company(name = "Developer", type = CompanyType.DEVELOPER)),
            genres = listOf(Genre.entries.first()), themes = listOf(Theme.entries.first()),
            keywords = listOf("keyword"), features = listOf(GameFeature.entries.first()),
            perspectives = listOf(PlayerPerspective.entries.first()),
            images = mutableListOf(Image(id = 3, type = ImageType.SCREENSHOT)),
            videoUrls = listOf(URI("https://video.test/1")),
            metadata = GameMetadata(
                "/private/path", fileSize = 1234,
                originalIds = mapOf(PluginManagementEntry("igdb") to "42")
            )
        )

        val public = game.publicGame()

        assertEquals("9", public.id)
        assertEquals("3", public.libraryId)
        assertEquals(LocalDate.of(2020, 5, 6), public.release)
        assertEquals(listOf(Platform.entries.first().name), public.platforms)
        assertEquals(listOf("Publisher"), public.publishers)
        assertEquals(listOf("Developer"), public.developers)
        assertEquals(listOf(Genre.entries.first().name), public.genres)
        assertEquals(listOf(Theme.entries.first().name), public.themes)
        assertEquals(listOf(GameFeature.entries.first().name), public.features)
        assertEquals(listOf(PlayerPerspective.entries.first().name), public.perspectives)
        assertEquals(ImageV1("1", "/v1/games/9/images/1", "image/png", 10), public.cover)
        assertEquals("/v1/games/9/images/2", public.header!!.url)
        assertEquals(listOf("3"), public.images.map { it.id })
        assertEquals(listOf("https://video.test/1"), public.videoUrls)
        assertEquals(listOf("image", "video"), public.mediaOrder)
        assertEquals(listOf(ExternalIdV1("igdb", "42")), public.externalIds)
        assertEquals(1234, public.fileSize)
        assertEquals(80, public.userRating)
        assertEquals(70, public.criticRating)
        assertEquals("Summary", public.summary)
        assertEquals("Comment", public.comment)
        assertEquals(listOf("keyword"), public.keywords)
        assertEquals(created, public.createdAt)
    }

    @Test
    fun `games without optional data map to nulls`() {
        val public = testGame().publicGame()
        assertNull(public.cover)
        assertNull(public.header)
        assertNull(public.release)
        assertTrue(public.images.isEmpty())
    }

    @Test
    fun `libraries and collections expose public fields`() {
        val time = Instant.parse("2024-01-01T00:00:00Z")
        val library = Library(
            id = 4, name = "Library", platforms = mutableListOf(Platform.entries.first()),
            createdAt = time, updatedAt = time
        )
        assertEquals(
            LibraryV1("4", "Library", listOf(Platform.entries.first().name), time, time),
            library.publicLibrary()
        )
        val collection =
            Collection(id = 5, name = "Favorites", description = "Best", createdAt = time, updatedAt = time)
        assertEquals(CollectionV1("5", "Favorites", "Best", time, time), collection.publicCollection())
    }

    @Test
    fun `strict inputs reject unknown fields and provide defaults`() {
        val error = assertFailsWith<IllegalArgumentException> { ScanV1().unknown("extra", true) }
        assertEquals("Unknown input field: extra", error.message)
        assertEquals("QUICK", ScanV1().type)
        assertEquals("QUEUED", AcceptedScanV1("1", "FULL").status)
        assertEquals(GameMediaV1(null, null, emptyList(), emptyList(), emptyList()), GameMediaV1())
        assertEquals(ImageSelectionV1(null, null, null), ImageSelectionV1())
        assertNull(DirectoryV1("/games").externalPath)
        assertFalse(CreateLibraryV1("Library", listOf(DirectoryV1("/games"))).scanAfterCreation)
        assertTrue(CreateLibraryV1("Library", listOf(DirectoryV1("/games"))).platforms.isEmpty())
        assertNull(CreateCollectionV1("Collection").description)
        assertNull(CreateRequestV1("Game", "PC").release)
        assertEquals("PENDING", RequestStatusV1("PENDING").status)
        assertEquals(GamePatchV1(), GamePatchV1(title = null))
        assertEquals(LibraryPatchV1(), LibraryPatchV1(name = null))
        assertEquals(CollectionPatchV1(), CollectionPatchV1(name = null))
        assertEquals(
            UserV1("1", "user", false, null),
            UserV1("1", "user", false, null, null, null, null, null, null)
        )
        assertEquals(setOf("games:read"), MeV1("1", "user", setOf("games:read")).permissions)
    }
}
