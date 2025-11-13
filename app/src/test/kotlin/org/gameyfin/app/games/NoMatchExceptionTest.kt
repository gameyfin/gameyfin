package org.gameyfin.app.games

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class NoMatchExceptionTest {

    @Test
    fun `should create exception with message`() {
        val message = "No match found for game"

        val exception = NoMatchException(message)

        assertEquals(message, exception.message)
    }

    @Test
    fun `should handle empty message`() {
        val exception = NoMatchException("")

        assertNotNull(exception)
        assertEquals("", exception.message)
    }

    @Test
    fun `should handle long message`() {
        val longMessage = "a".repeat(1000)

        val exception = NoMatchException(longMessage)

        assertEquals(longMessage, exception.message)
    }

    @Test
    fun `should handle special characters in message`() {
        val message = "Error: Game 'Test!@#$%' not found in DB\n\tDetails: 世界"

        val exception = NoMatchException(message)

        assertEquals(message, exception.message)
    }

    @Test
    fun `should be throwable`() {
        val exception = NoMatchException("test")

        try {
            throw exception
        } catch (e: NoMatchException) {
            assertEquals("test", e.message)
        }
    }

    @Test
    fun `should be catchable as RuntimeException`() {
        val exception = NoMatchException("test")

        try {
            throw exception
        } catch (e: RuntimeException) {
            assertEquals("test", e.message)
        }
    }
}

