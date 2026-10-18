package org.gameyfin.app.core.tasks

import org.gameyfin.pluginapi.tasks.GameSelection
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameSelectionTest {

    @Test
    fun `parse should read comma-separated distinct IDs`() {
        assertEquals(listOf(4L, 2L), GameSelection.parse(" 4, 2,,4 ").gameIds)
    }

    @Test
    fun `parse should accept empty values`() {
        assertTrue(GameSelection.parse("").isEmpty())
        assertTrue(GameSelection.parse(" , ").isEmpty())
    }

    @Test
    fun `parse should reject invalid IDs`() {
        assertThrows<IllegalArgumentException> { GameSelection.parse("1,two") }
    }

    @Test
    fun `toString should be parseable`() {
        val selection = GameSelection(listOf(1L, 5L, 3L))
        assertEquals("1,5,3", selection.toString())
        assertEquals(selection, GameSelection.parse(selection.toString()))
    }
}
