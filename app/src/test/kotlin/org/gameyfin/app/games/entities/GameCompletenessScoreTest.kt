package org.gameyfin.app.games.entities

import io.mockk.mockk
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.app.media.Image
import org.gameyfin.pluginapi.gamemetadata.*
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.Instant
import kotlin.test.assertEquals

class GameCompletenessScoreTest {
    private fun emptyGame() = Game(
        library = mockk<Library>(relaxed = true),
        metadata = GameMetadata(path = "/test/path")
    )

    // -------------------------------------------------------------------------
    // Boundary cases
    // -------------------------------------------------------------------------

    @Test
    fun `empty game returns 0`() {
        assertEquals(0, emptyGame().calculateCompletenessScore())
    }

    @Test
    fun `fully populated game returns 100`() {
        val game = emptyGame().apply {
            title = "Full Game"
            coverImage = mockk<Image>(relaxed = true)
            headerImage = mockk<Image>(relaxed = true)
            summary = "A great game"
            release = Instant.parse("2020-01-01T00:00:00Z")
            userRating = 80
            criticRating = 90
            platforms = mutableListOf(Platform.PC_MICROSOFT_WINDOWS)
            publishers = mutableListOf(Company(name = "Pub", type = CompanyType.PUBLISHER))
            developers = mutableListOf(Company(name = "Dev", type = CompanyType.DEVELOPER))
            genres = listOf(Genre.ACTION)
            themes = listOf(Theme.FANTASY)
            keywords = listOf("fun")
            features = listOf(GameFeature.SINGLEPLAYER)
            perspectives = listOf(PlayerPerspective.FIRST_PERSON)
            images = mutableListOf(mockk<Image>(relaxed = true))
            videoUrls = listOf(URI("https://example.com/video.mp4"))
        }
        assertEquals(100, game.calculateCompletenessScore())
    }

    // -------------------------------------------------------------------------
    // Partial fill
    // -------------------------------------------------------------------------

    @Test
    fun `half of fields filled returns correct score`() {
        // Test fills 8 fields, 17 fields total
        val halfFieldPercentage: Int = (8.0 / 17.0 * 100).toInt()

        val game = emptyGame().apply {
            title = "Half"
            coverImage = mockk(relaxed = true)
            summary = "Summary"
            release = Instant.now()
            userRating = 70
            criticRating = 80
            platforms = mutableListOf(Platform.PC_MICROSOFT_WINDOWS)
            publishers = mutableListOf(Company(name = "Pub", type = CompanyType.PUBLISHER))
        }

        assertEquals(halfFieldPercentage, game.calculateCompletenessScore())
    }

    // -------------------------------------------------------------------------
    // Excluded fields must not affect the score
    // -------------------------------------------------------------------------

    @Test
    fun `comment does not affect score`() {
        val withComment = emptyGame().apply { comment = "User note" }
        val withTitle = emptyGame().apply { title = "Title" }

        assertEquals(0, withComment.calculateCompletenessScore())
        // adding title alongside comment still counts only title
        withComment.title = "Title"
        assertEquals(withTitle.calculateCompletenessScore(), withComment.calculateCompletenessScore())
    }
}

