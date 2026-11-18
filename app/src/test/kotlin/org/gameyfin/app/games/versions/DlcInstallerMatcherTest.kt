package org.gameyfin.app.games.versions

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DlcInstallerMatcherTest {
    private val phantom = StoreDlcInfo("1", "Cyberpunk 2077: Phantom Liberty")
    private val map = StoreDlcInfo("2", "Cyberpunk 2077 - Piggyback Interactive Map")
    private val vigilante = StoreDlcInfo("3", "Cyberpunk 2077 - Quadra Sport R-7 “Vigilante”")
    private val seasonPass = StoreDlcInfo("4", "Cyberpunk 2077 Season Pass")
    private val seasonPassBundle = StoreDlcInfo("5", "Cyberpunk 2077 Season Pass Bundle")
    private val all = listOf(phantom, map, vigilante, seasonPass, seasonPassBundle)

    private fun match(vararg names: String) = DlcInstallerMatcher.match("Cyberpunk 2077", names.toList(), all)

    @Test
    fun `detects a DLC from a GOG installer name`() {
        assertEquals(listOf(phantom), match("setup_cyberpunk_2077_phantom_liberty_2.0_(64bit)_(12345).exe"))
    }

    @Test
    fun `base game installers do not match any DLC`() {
        assertTrue(match("setup_cyberpunk_2077_2.0_(64bit)_(12345).exe", "setup_cyberpunk_2077_2.0_(64bit)_(12345)-1.bin").isEmpty())
    }

    @Test
    fun `detects DLC placed in a subfolder`() {
        assertEquals(listOf(map), match("DLC", "Piggyback Interactive Map", "readme.txt"))
    }

    @Test
    fun `punctuation and quotes in titles do not prevent a match`() {
        assertEquals(listOf(vigilante), match("setup_cyberpunk_2077_quadra_sport_r-7_vigilante_(1).exe"))
    }

    @Test
    fun `the most specific DLC wins when titles overlap`() {
        assertEquals(listOf(seasonPassBundle), match("setup_cyberpunk_2077_season_pass_bundle_1.0.exe"))
        assertEquals(listOf(seasonPass), match("setup_cyberpunk_2077_season_pass_1.0.exe"))
    }

    @Test
    fun `version numbers do not create false matches`() {
        val part = StoreDlcInfo("9", "Some Game Part 2")
        assertTrue(DlcInstallerMatcher.match("Some Game", listOf("setup_some_game_1.2_(1).exe"), listOf(part)).isEmpty())
    }

    @Test
    fun `accents are ignored`() {
        val dlc = StoreDlcInfo("7", "Pokémon Extra Pack")
        assertEquals(listOf(dlc), DlcInstallerMatcher.match("Game", listOf("setup_game_pokemon_extra_pack.exe"), listOf(dlc)))
    }

    @Test
    fun `no candidates or names give no result`() {
        assertTrue(DlcInstallerMatcher.match("Game", emptyList(), all).isEmpty())
        assertTrue(DlcInstallerMatcher.match("Game", listOf("setup_game.exe"), emptyList()).isEmpty())
    }
}
