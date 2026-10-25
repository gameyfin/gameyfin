package org.gameyfin.app.games

import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.entities.GameMetadata
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.app.media.Image
import org.gameyfin.app.media.ImageType
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.test.assertEquals

class GameMediaOrderingTest {
    @Test
    fun `constructor builds a single gallery and preserves duplicate videos`() {
        val screenshot = Image(type = ImageType.SCREENSHOT)
        val video = URI("https://example.com/video.mp4")
        val game = Game(
            library = Library(name = "Library"), metadata = GameMetadata(path = "/game"),
            images = mutableListOf(screenshot), videoUrls = listOf(video, video),
            mediaOrder = listOf("video", "image", "video")
        )

        assertEquals(listOf(video, null, video), game.media.map { it.videoUrl })
        assertEquals(listOf(null, screenshot, null), game.media.map { it.image })
        assertEquals(listOf(screenshot), game.images)
        assertEquals(listOf(video, video), game.videoUrls)
    }

    @Test
    fun `replacing screenshots keeps video slots and removing them updates the shared order`() {
        val first = Image(type = ImageType.SCREENSHOT)
        val second = Image(type = ImageType.SCREENSHOT)
        val replacement = Image(type = ImageType.SCREENSHOT)
        val video = URI("https://example.com/video.mp4")
        val game = Game(
            library = Library(name = "Library"), metadata = GameMetadata(path = "/game"),
            images = mutableListOf(first, second), videoUrls = listOf(video),
            mediaOrder = listOf("image", "video", "image")
        )

        game.images = mutableListOf(second, replacement)
        assertEquals(listOf("image", "video", "image"), game.mediaOrder)
        assertEquals(listOf(second, replacement), game.images)
        assertEquals(listOf(video), game.videoUrls)
        game.images.removeAt(0)
        assertEquals(listOf("video", "image"), game.mediaOrder)
        game.images.add(0, first)
        assertEquals(listOf(first, replacement), game.images)
        assertEquals(listOf("video", "image", "image"), game.mediaOrder)
        game.mediaOrder = emptyList()
        assertEquals(listOf("image", "image", "video"), game.mediaOrder)
    }
}
