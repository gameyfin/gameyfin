package org.gameyfin.app.core.tasks.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.core.ConsoleAppender
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LogConfigTemplateTest {

    private fun configure(): LoggerContext {
        val folder = Files.createTempDirectory("gameyfin-logs").toString()
        val xml = javaClass.classLoader.getResourceAsStream("templates/log-config-template.xml")!!
            .bufferedReader().use { it.readText() }
            .replace("{LOG_FOLDER}", folder)
            .replace("{LOG_FILE_NAME}", "gameyfin")
            .replace("{LOG_MAX_HISTORY_DAYS}", "1")
            .replace("{LOG_LEVEL_GAMEYFIN}", "DEBUG")
            .replace("{LOG_LEVEL_ROOT}", "DEBUG")
        val context = LoggerContext()
        context.putProperty("CONSOLE_LOG_PATTERN", "%m%n")
        context.putProperty("CONSOLE_LOG_CHARSET", "UTF-8")
        JoranConfigurator().apply { this.context = context }.doConfigure(xml.byteInputStream())
        return context
    }

    @Test
    fun `websocket push logging stays at INFO even with DEBUG root`() {
        val context = configure()
        try {
            assertEquals(Level.INFO, context.getLogger("com.vaadin.hilla.push").level)
            assertEquals(Level.DEBUG, context.getLogger("com.vaadin.hilla.other").effectiveLevel)
        } finally {
            context.stop()
        }
    }

    @Test
    fun `console appender excludes task run output`() {
        val context = configure()
        try {
            val console = assertNotNull(context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).getAppender("CONSOLE"))
            assertIs<ConsoleAppender<*>>(console)
            assertTrue(console.copyOfAttachedFiltersList.any { it is NonTaskRunLogFilter })
        } finally {
            context.stop()
        }
    }
}
