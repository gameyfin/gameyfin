package org.gameyfin.app.notifications

import io.mockk.every
import io.mockk.mockk
import org.gameyfin.app.core.notifications.NotificationStore
import org.gameyfin.app.core.notifications.PluginNotificationActions
import org.gameyfin.app.core.plugins.management.GameyfinPluginManager
import org.gameyfin.pluginapi.notifications.NotificationActionContext
import org.gameyfin.pluginapi.notifications.NotificationActionHandler
import org.gameyfin.pluginapi.notifications.NotificationActionPermission
import org.junit.jupiter.api.Test
import org.pf4j.PluginState
import org.pf4j.PluginStateEvent
import org.pf4j.PluginWrapper
import org.springframework.beans.factory.ObjectProvider
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginNotificationActionsTest {
    @Test
    fun `callbacks register on start enforce permission and disappear on stop`() {
        val manager = mockk<GameyfinPluginManager>()
        val provider = mockk<ObjectProvider<GameyfinPluginManager>> { every { getObject() } returns manager }
        val store = mockk<NotificationStore>(relaxed = true)
        val wrapper = mockk<PluginWrapper> { every { pluginId } returns "test" }
        var calls = 0
        val adminAction = object : NotificationActionHandler {
            override val key = "admin"
            override fun execute(context: NotificationActionContext) {
                calls++
            }
        }
        val recipientAction = object : NotificationActionHandler {
            override val key = "recipient"
            override val permission = NotificationActionPermission.RECIPIENT
            override fun execute(context: NotificationActionContext) {
                calls++
            }
        }
        every { manager.startedPlugins } returns listOf(wrapper)
        every { manager.getExtensions(NotificationActionHandler::class.java, "test") } returns listOf(
            adminAction,
            recipientAction
        )
        val actions = PluginNotificationActions(provider, store)
        actions.initialize()
        assertFalse(actions.available("plugin:test", "admin", false))
        assertTrue(actions.available("plugin:test", "admin", true))
        assertTrue(actions.available("plugin:test", "recipient", false))
        assertFalse(actions.available("plugin:other", "recipient", true))
        val ctx = NotificationActionContext(1, "notification", "execution", emptyMap())
        assertFailsWith<IllegalStateException> { actions.execute("plugin:test", "admin", false, ctx) }
        actions.execute("plugin:test", "recipient", false, ctx)
        assertEquals(1, calls)
        val event = mockk<PluginStateEvent> {
            every { plugin } returns wrapper
            every { pluginState } returns PluginState.STOPPED
        }
        actions.pluginChanged(event)
        assertFalse(actions.available("plugin:test", "admin", true))
        assertFailsWith<IllegalStateException> { actions.execute("plugin:test", "recipient", true, ctx) }
        every { event.pluginState } returns PluginState.STARTED
        actions.pluginChanged(event)
        assertTrue(actions.available("plugin:test", "admin", true))
        every { manager.getExtensions(NotificationActionHandler::class.java, "test") } returns listOf(
            adminAction,
            adminAction
        )
        actions.pluginChanged(event) // A malformed plugin must not abort other plugins' startup.
        assertFalse(actions.available("plugin:test", "admin", true))
    }
}
