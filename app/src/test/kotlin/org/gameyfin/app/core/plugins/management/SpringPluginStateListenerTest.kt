package org.gameyfin.app.core.plugins.management

import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.pf4j.*
import org.springframework.context.ApplicationEventPublisher
import kotlin.test.assertEquals

class SpringPluginStateListenerTest {

    private lateinit var eventPublisher: ApplicationEventPublisher
    private lateinit var listener: SpringPluginStateListener

    @BeforeEach
    fun setup() {
        eventPublisher = mockk(relaxed = true)
        listener = SpringPluginStateListener(eventPublisher)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `pluginStateChanged should publish event when event is not null`() {
        val pluginManager = mockk<PluginManager>()
        val pluginWrapper = mockk<PluginWrapper>()
        val plugin = mockk<Plugin>()

        every { pluginWrapper.pluginId } returns "test-plugin"
        every { pluginWrapper.plugin } returns plugin

        val event = PluginStateEvent(pluginManager, pluginWrapper, PluginState.STARTED)

        listener.pluginStateChanged(event)

        verify(exactly = 1) { eventPublisher.publishEvent(event) }
    }

    @Test
    fun `pluginStateChanged should publish event when plugin state is STOPPED`() {
        val pluginManager = mockk<PluginManager>()
        val pluginWrapper = mockk<PluginWrapper>()
        val plugin = mockk<Plugin>()

        every { pluginWrapper.pluginId } returns "test-plugin"
        every { pluginWrapper.plugin } returns plugin

        val event = PluginStateEvent(pluginManager, pluginWrapper, PluginState.STOPPED)

        listener.pluginStateChanged(event)

        verify(exactly = 1) { eventPublisher.publishEvent(event) }
    }

    @Test
    fun `pluginStateChanged should publish event when plugin state is DISABLED`() {
        val pluginManager = mockk<PluginManager>()
        val pluginWrapper = mockk<PluginWrapper>()
        val plugin = mockk<Plugin>()

        every { pluginWrapper.pluginId } returns "test-plugin"
        every { pluginWrapper.plugin } returns plugin

        val event = PluginStateEvent(pluginManager, pluginWrapper, PluginState.DISABLED)

        listener.pluginStateChanged(event)

        verify(exactly = 1) { eventPublisher.publishEvent(event) }
    }

    @Test
    fun `pluginStateChanged should publish event when plugin state is FAILED`() {
        val pluginManager = mockk<PluginManager>()
        val pluginWrapper = mockk<PluginWrapper>()
        val plugin = mockk<Plugin>()

        every { pluginWrapper.pluginId } returns "test-plugin"
        every { pluginWrapper.plugin } returns plugin

        val event = PluginStateEvent(pluginManager, pluginWrapper, PluginState.FAILED)

        listener.pluginStateChanged(event)

        verify(exactly = 1) { eventPublisher.publishEvent(event) }
    }

    @Test
    fun `pluginStateChanged should not publish event when event is null`() {
        listener.pluginStateChanged(null)

        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }

    @Test
    fun `pluginStateChanged should publish multiple events in sequence`() {
        val pluginManager = mockk<PluginManager>()
        val pluginWrapper = mockk<PluginWrapper>()
        val plugin = mockk<Plugin>()

        every { pluginWrapper.pluginId } returns "test-plugin"
        every { pluginWrapper.plugin } returns plugin

        val event1 = PluginStateEvent(pluginManager, pluginWrapper, PluginState.STARTED)
        val event2 = PluginStateEvent(pluginManager, pluginWrapper, PluginState.STOPPED)
        val event3 = PluginStateEvent(pluginManager, pluginWrapper, PluginState.DISABLED)

        listener.pluginStateChanged(event1)
        listener.pluginStateChanged(event2)
        listener.pluginStateChanged(event3)

        verify(exactly = 3) { eventPublisher.publishEvent(any(PluginStateEvent::class)) }
        verify(exactly = 1) { eventPublisher.publishEvent(event1) }
        verify(exactly = 1) { eventPublisher.publishEvent(event3) }
        verify(exactly = 1) { eventPublisher.publishEvent(event3) }
    }

    @Test
    fun `pluginStateChanged should handle events from different plugins`() {
        val pluginManager = mockk<PluginManager>()
        val pluginWrapper1 = mockk<PluginWrapper>()
        val pluginWrapper2 = mockk<PluginWrapper>()
        val plugin1 = mockk<Plugin>()
        val plugin2 = mockk<Plugin>()

        every { pluginWrapper1.pluginId } returns "plugin1"
        every { pluginWrapper1.plugin } returns plugin1
        every { pluginWrapper2.pluginId } returns "plugin2"
        every { pluginWrapper2.plugin } returns plugin2

        val event1 = PluginStateEvent(pluginManager, pluginWrapper1, PluginState.STARTED)
        val event2 = PluginStateEvent(pluginManager, pluginWrapper2, PluginState.STARTED)

        listener.pluginStateChanged(event1)
        listener.pluginStateChanged(event2)

        verify(exactly = 2) { eventPublisher.publishEvent(any(PluginStateEvent::class)) }
        verify(exactly = 1) { eventPublisher.publishEvent(event1) }
        verify(exactly = 1) { eventPublisher.publishEvent(event2) }
    }

    @Test
    fun `pluginStateChanged should publish event with old state information`() {
        val pluginManager = mockk<PluginManager>()
        val pluginWrapper = mockk<PluginWrapper>()
        val plugin = mockk<Plugin>()

        every { pluginWrapper.pluginId } returns "test-plugin"
        every { pluginWrapper.plugin } returns plugin
        every { pluginWrapper.pluginState } returns PluginState.STARTED

        // Create event with old state (constructor takes source, plugin, oldState)
        val event = PluginStateEvent(pluginManager, pluginWrapper, PluginState.STOPPED)

        listener.pluginStateChanged(event)

        verify(exactly = 1) { eventPublisher.publishEvent(event) }

        // Verify the event has the expected states
        assertEquals(PluginState.STARTED, event.pluginState)
        assertEquals(PluginState.STOPPED, event.oldState)
    }
}

