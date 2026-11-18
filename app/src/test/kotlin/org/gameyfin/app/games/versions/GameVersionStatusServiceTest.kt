package org.gameyfin.app.games.versions

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.entities.GameMetadata
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.libraries.entities.Library
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tools.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameVersionStatusServiceTest {
    private val gameRepository = mockk<GameRepository>()
    private val linkRepository = mockk<GameStoreLinkRepository>()
    private val versionRepository = mockk<StoreVersionRepository>()
    private val stateRepository = mockk<GameVersionStateRepository>()
    private val service = GameVersionStatusService(
        gameRepository,
        linkRepository,
        versionRepository,
        stateRepository,
        ObjectMapper(),
        registryOf(activeStore(STEAM_INFO), activeStore(GOG_INFO), activeStore(EPIC_INFO))
    )

    @Test
    fun `status compares latest version only within installed branch`() {
        val link = GameStoreLink(1, 10, "steam", "123", tracked = true)
        val installed = StoreVersion(11, 1, "100", "", "Public build 100", Instant.parse("2024-01-01T00:00:00Z"))
        val publicLatest = StoreVersion(12, 1, "200", "", "Public build 200", Instant.parse("2024-02-01T00:00:00Z"))
        val otherBranch = StoreVersion(13, 1, "300", "beta", "Beta build 300", Instant.parse("2024-03-01T00:00:00Z"))
        every { linkRepository.findAllByGameId(10) } returns listOf(link)
        every { versionRepository.findAllByLinkId(1) } returns listOf(installed, publicLatest, otherBranch)
        every { stateRepository.findById(1) } returns Optional.of(GameVersionState(1, 11, LinkSource.AUTO))

        val status = service.getStatus(10).single()

        assertEquals(12, status.latestVersionId)
        assertTrue(status.updateAvailable == true)
    }

    @Test
    fun `an old build on a side branch reports the newer main branch release`() {
        val link = GameStoreLink(1, 10, "gog", "123", tracked = true)
        val classic = StoreVersion(11, 1, "1", "Classic", "1.32", Instant.parse("2018-11-06T00:00:00Z"))
        val main = StoreVersion(12, 1, "2", "", "5.01", Instant.parse("2026-10-07T00:00:00Z"))
        every { linkRepository.findAllByGameId(10) } returns listOf(link)
        every { versionRepository.findAllByLinkId(1) } returns listOf(classic, main)
        every { stateRepository.findById(1) } returns Optional.of(GameVersionState(1, 11, LinkSource.MANUAL))

        val status = service.getStatus(10).single()

        assertEquals(12, status.latestVersionId)
        assertTrue(status.updateAvailable == true)
    }

    @Test
    fun `a side branch that is newer than the main branch stays up to date`() {
        val link = GameStoreLink(1, 10, "steam", "123", tracked = true)
        val beta = StoreVersion(11, 1, "300", "beta", "Beta build 300", Instant.parse("2024-03-01T00:00:00Z"))
        val main = StoreVersion(12, 1, "200", "", "Public build 200", Instant.parse("2024-02-01T00:00:00Z"))
        every { linkRepository.findAllByGameId(10) } returns listOf(link)
        every { versionRepository.findAllByLinkId(1) } returns listOf(beta, main)
        every { stateRepository.findById(1) } returns Optional.of(GameVersionState(1, 11, LinkSource.MANUAL))

        val status = service.getStatus(10).single()

        assertEquals(11, status.latestVersionId)
        assertTrue(status.updateAvailable == false)
    }

    @Test
    fun `status leaves update unknown when installed build is not cached`() {
        val link = GameStoreLink(1, 10, "gog", "123", tracked = true)
        every { linkRepository.findAllByGameId(10) } returns listOf(link)
        every { versionRepository.findAllByLinkId(1) } returns listOf(
            StoreVersion(12, 1, "new", "", "2.0", Instant.parse("2024-02-01T00:00:00Z"))
        )
        every { stateRepository.findById(1) } returns Optional.of(GameVersionState(1, 99, LinkSource.AUTO))

        val status = service.getStatus(10).single()

        assertNull(status.updateAvailable)
    }

    @Test
    fun `detects Steam build from matching app manifest`(@TempDir tempDir: Path) {
        val installDir = Files.createDirectories(tempDir.resolve("steamapps/common/Test Game"))
        Files.writeString(
            tempDir.resolve("steamapps/appmanifest_123.acf"),
            """
            "AppState"
            {
                "appid" "123"
                "buildid" "456"
            }
            """.trimIndent()
        )
        val link = GameStoreLink(1, 10, "steam", "123")
        val version = StoreVersion(11, 1, "456", "", "Public build 456")
        every { linkRepository.findById(1) } returns Optional.of(link)
        every { stateRepository.findById(1) } returns Optional.empty()
        every { gameRepository.findById(10) } returns Optional.of(
            Game(library = mockk<Library>(), metadata = GameMetadata(path = installDir.toString()))
        )
        every { versionRepository.findAllByLinkId(1) } returns listOf(version)
        every { stateRepository.save(any<GameVersionState>()) } answers { firstArg() }

        val result = service.detectInstalledVersion(1)

        assertTrue(result.detected)
        assertEquals(11, result.installedVersionId)
        verify { stateRepository.save(match { it.source == LinkSource.AUTO && it.installedVersionId == 11L }) }
    }

    @Test
    fun `detects GOG version from product info build ID`(@TempDir tempDir: Path) {
        val gameDir = Files.createDirectories(tempDir.resolve("GOG Games/Test"))
        Files.writeString(gameDir.resolve("goggame-987.info"), """{"buildId":"gog-build-5"}""")
        val link = GameStoreLink(1, 10, "gog", "987")
        val version = StoreVersion(11, 1, "gog-build-5", "", "1.5")
        every { linkRepository.findById(1) } returns Optional.of(link)
        every { stateRepository.findById(1) } returns Optional.empty()
        every { gameRepository.findById(10) } returns Optional.of(
            Game(library = mockk<Library>(), metadata = GameMetadata(path = gameDir.toString()))
        )
        every { versionRepository.findAllByLinkId(1) } returns listOf(version)
        every { stateRepository.save(any<GameVersionState>()) } answers { firstArg() }

        val result = service.detectInstalledVersion(1)

        assertTrue(result.detected)
        assertEquals("1.5", result.installedVersionLabel)
    }

    @Test
    fun `detects GOG version from offline installer file name`(@TempDir tempDir: Path) {
        val gameDir = Files.createDirectories(tempDir.resolve("GOG Games/Drive Rally"))
        Files.createFile(gameDir.resolve("setup_drive_rally_1.5.1_(64bit)_(93251).exe"))
        val link = GameStoreLink(1, 10, "gog", "987")
        val versions = listOf(StoreVersion(11, 1, "59781019998704278", "", "1.4.8.0"), StoreVersion(12, 1, "59972322423858091", "", "1.5.1"))
        every { linkRepository.findById(1) } returns Optional.of(link)
        every { stateRepository.findById(1) } returns Optional.empty()
        every { gameRepository.findById(10) } returns Optional.of(
            Game(library = mockk<Library>(), metadata = GameMetadata(path = gameDir.toString()))
        )
        every { versionRepository.findAllByLinkId(1) } returns versions
        every { stateRepository.save(any<GameVersionState>()) } answers { firstArg() }

        val result = service.detectInstalledVersion(1)

        assertTrue(result.detected)
        assertEquals("1.5.1", result.installedVersionLabel)
    }

    @Test
    fun `preview detects the installed version without saving it`(@TempDir tempDir: Path) {
        val gameDir = Files.createDirectories(tempDir.resolve("GOG Games/Test"))
        Files.writeString(gameDir.resolve("goggame-987.info"), """{"buildId":"gog-build-5"}""")
        val link = GameStoreLink(1, 10, "gog", "987")
        every { linkRepository.findById(1) } returns Optional.of(link)
        every { gameRepository.findById(10) } returns Optional.of(
            Game(library = mockk<Library>(), metadata = GameMetadata(path = gameDir.toString()))
        )
        every { versionRepository.findAllByLinkId(1) } returns listOf(StoreVersion(11, 1, "gog-build-5", "", "1.5"))

        val result = service.previewDetection(1)

        assertTrue(result.detected)
        assertEquals(11, result.installedVersionId)
        verify(exactly = 0) { stateRepository.save(any<GameVersionState>()) }
    }

    @Test
    fun `installed version can be stored as detected instead of manual`() {
        val link = GameStoreLink(1, 10, "gog", "987")
        every { linkRepository.findById(1) } returns Optional.of(link)
        every { linkRepository.findAllByGameId(10) } returns listOf(link)
        every { versionRepository.findByIdAndLinkId(11, 1) } returns StoreVersion(11, 1, "b", "", "1.5")
        every { versionRepository.findAllByLinkId(1) } returns emptyList()
        every { stateRepository.findById(1) } returns Optional.empty()
        every { stateRepository.save(any<GameVersionState>()) } answers { firstArg() }

        service.setInstalledVersion(1, 11, LinkSource.AUTO)

        verify { stateRepository.save(match { it.source == LinkSource.AUTO && it.installedVersionId == 11L }) }
    }

    @Test
    fun `manual installed version is preserved during detection`() {
        val link = GameStoreLink(1, 10, "steam", "123")
        val manualState = GameVersionState(1, 11, LinkSource.MANUAL)
        every { linkRepository.findById(1) } returns Optional.of(link)
        every { stateRepository.findById(1) } returns Optional.of(manualState)
        every { versionRepository.findByIdAndLinkId(11, 1) } returns StoreVersion(11, 1, "456", "", "Manual")

        val result = service.detectInstalledVersion(1)

        assertFalse(result.detected)
        assertEquals(11, result.installedVersionId)
        verify(exactly = 0) { gameRepository.findById(any()) }
        verify(exactly = 0) { stateRepository.save(any<GameVersionState>()) }
    }

    @Test
    fun `detects Epic version from the launcher manifest`(@TempDir tempDir: Path) {
        val gameDir = Files.createDirectories(tempDir.resolve("Epic Games/Test"))
        val egstore = Files.createDirectories(gameDir.resolve(".egstore"))
        Files.write(egstore.resolve("ABCDEF.manifest"), epicManifest("38294-x64"))
        val link = GameStoreLink(1, 10, "epic", "2ae6edb2223c4f8c97e9839b5b6497bb")
        val version = StoreVersion(11, 1, "38294-x64", "", "38294-x64 (2025-07-23)")
        every { linkRepository.findById(1) } returns Optional.of(link)
        every { stateRepository.findById(1) } returns Optional.empty()
        every { gameRepository.findById(10) } returns Optional.of(
            Game(library = mockk<Library>(), metadata = GameMetadata(path = gameDir.toString()))
        )
        every { versionRepository.findAllByLinkId(1) } returns listOf(version)
        every { stateRepository.save(any<GameVersionState>()) } answers { firstArg() }

        val result = service.detectInstalledVersion(1)

        assertTrue(result.detected)
        assertEquals("38294-x64 (2025-07-23)", result.installedVersionLabel)
    }

    private fun epicManifest(buildVersion: String): ByteArray {
        fun fstring(value: String): ByteArray {
            val raw = (value + "\u0000").toByteArray()
            return java.nio.ByteBuffer.allocate(4 + raw.size).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putInt(raw.size).put(raw).array()
        }

        val meta = java.nio.ByteBuffer.allocate(14).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .putInt(0).put(21).putInt(18).put(0).putInt(0).array() +
                fstring("AppName") + fstring(buildVersion)
        val header = java.nio.ByteBuffer.allocate(41).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .putInt(0x44BEC00C).putInt(41).putInt(meta.size).putInt(meta.size)
            .put(ByteArray(20)).put(0).putInt(18).array()
        return header + meta
    }
}
