package org.gameyfin.app.games.versions

import org.gameyfin.pluginapi.gamestore.GameStoreDlc
import org.gameyfin.pluginapi.gamestore.GameStoreProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.entities.GameMetadata
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.libraries.entities.Library
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GameDlcServiceTest {
    private val gameRepository = mockk<GameRepository>()
    private val linkRepository = mockk<GameStoreLinkRepository>()
    private val dlcRepository = mockk<GameIncludedDlcRepository>(relaxed = true)
    private val provider = mockk<GameStoreProvider>()
    private val service: GameDlcService

    init {
        service = GameDlcService(gameRepository, linkRepository, dlcRepository, registryOf(activeStore(GOG_INFO, provider)))
    }

    @Test
    fun `candidates are cached between calls`() {
        every { provider.fetchDlc("100") } returns listOf(GameStoreDlc("1", "Pack"))

        service.getCandidates("gog", "100")
        val result = service.getCandidates("gog", "https://www.gogdb.org/product/100")

        assertEquals(listOf(StoreDlcDto("1", "Pack")), result)
        verify(exactly = 1) { provider.fetchDlc("100") }
    }

    @Test
    fun `detects DLC installers in a subfolder of the game`(@TempDir tempDir: Path) {
        val gameDir = Files.createDirectories(tempDir.resolve("Cyberpunk 2077"))
        Files.createFile(gameDir.resolve("setup_cyberpunk_2077_2.0_(1).exe"))
        val dlcDir = Files.createDirectories(gameDir.resolve("DLC"))
        Files.createFile(dlcDir.resolve("setup_cyberpunk_2077_phantom_liberty_2.0_(2).exe"))
        every { gameRepository.findById(10) } returns Optional.of(
            Game(library = mockk<Library>(), title = "Cyberpunk 2077", metadata = GameMetadata(path = gameDir.toString()))
        )
        every { provider.fetchDlc("100") } returns listOf(
            GameStoreDlc("1", "Cyberpunk 2077: Phantom Liberty"),
            GameStoreDlc("2", "Cyberpunk 2077 REDmod")
        )

        val result = service.detect(10, "gog", "100")

        assertEquals(listOf(StoreDlcDto("1", "Cyberpunk 2077: Phantom Liberty")), result)
    }

    @Test
    fun `setIncluded replaces the selection and keeps the source`() {
        val link = GameStoreLink(5, 10, "gog", "100")
        every { linkRepository.findById(5) } returns Optional.of(link)
        every { linkRepository.findAllByGameId(10) } returns listOf(link)
        val old = GameIncludedDlc(1, 5, "1", "Old", LinkSource.MANUAL)
        val kept = GameIncludedDlc(2, 5, "2", "Kept", LinkSource.MANUAL)
        every { dlcRepository.findAllByLinkId(5) } returns listOf(old, kept)
        every { dlcRepository.findAllByLinkIdIn(listOf(5L)) } returns listOf(kept)
        val saved = slot<GameIncludedDlc>()
        every { dlcRepository.save(capture(saved)) } answers { firstArg() }

        service.setIncluded(5, listOf(IncludedDlcDto("2", "Kept renamed", true), IncludedDlcDto("3", "New", false)))

        verify { dlcRepository.deleteAll(match<Iterable<GameIncludedDlc>> { it.toList() == listOf(old) }) }
        assertEquals("3", saved.captured.dlcId)
        assertEquals(LinkSource.MANUAL, saved.captured.source)
        assertEquals("Kept renamed", kept.title)
        assertEquals(LinkSource.AUTO, kept.source)
    }

    @Test
    fun `setIncluded rejects invalid IDs and titles`() {
        val link = GameStoreLink(5, 10, "gog", "100")
        every { linkRepository.findById(5) } returns Optional.of(link)

        assertFailsWith<IllegalArgumentException> { service.setIncluded(5, listOf(IncludedDlcDto("abc", "Pack", false))) }
        assertFailsWith<IllegalArgumentException> { service.setIncluded(5, listOf(IncludedDlcDto("1", " ", false))) }
    }

    @Test
    fun `custom DLC that the store does not list is stored, malformed custom IDs are rejected`() {
        val link = GameStoreLink(5, 10, "gog", "100")
        every { linkRepository.findById(5) } returns Optional.of(link)
        every { linkRepository.findAllByGameId(10) } returns listOf(link)
        every { dlcRepository.findAllByLinkId(5) } returns emptyList()
        every { dlcRepository.findAllByLinkIdIn(listOf(5L)) } returns emptyList()
        val saved = slot<GameIncludedDlc>()
        every { dlcRepository.save(capture(saved)) } answers { firstArg() }

        assertFailsWith<IllegalArgumentException> {
            service.setIncluded(5, listOf(IncludedDlcDto("custom:Not Valid", "Evil", false)))
        }

        service.setIncluded(5, listOf(IncludedDlcDto("custom:songs-of-the-past", "Songs of the Past", false)))

        assertEquals("custom:songs-of-the-past", saved.captured.dlcId)
    }

    @Test
    fun `the kind of an extra is stored and updated`() {
        val link = GameStoreLink(5, 10, "gog", "100")
        every { linkRepository.findById(5) } returns Optional.of(link)
        every { linkRepository.findAllByGameId(10) } returns listOf(link)
        val existing = GameIncludedDlc(1, 5, "1", "Pack", LinkSource.MANUAL, DlcKind.DLC)
        every { dlcRepository.findAllByLinkId(5) } returns listOf(existing)
        every { dlcRepository.findAllByLinkIdIn(listOf(5L)) } returns listOf(existing)
        val saved = slot<GameIncludedDlc>()
        every { dlcRepository.save(capture(saved)) } answers { firstArg() }

        service.setIncluded(5, listOf(
            IncludedDlcDto("1", "Pack", false, DlcKind.BONUS),
            IncludedDlcDto("2", "Upgrade", false, DlcKind.EDITION_UPGRADE)
        ))

        assertEquals(DlcKind.BONUS, existing.kind)
        assertEquals(DlcKind.EDITION_UPGRADE, saved.captured.kind)
    }
}
