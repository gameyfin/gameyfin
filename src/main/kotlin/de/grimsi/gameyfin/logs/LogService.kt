package de.grimsi.gameyfin.logs

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import de.grimsi.gameyfin.config.ConfigProperties
import de.grimsi.gameyfin.config.ConfigService
import de.grimsi.gameyfin.logs.util.AsyncFileTailer
import io.github.oshai.kotlinlogging.KotlinLogging
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.boot.logging.LogLevel
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.time.Duration.Companion.seconds

@Service
class LogService(
    private val config: ConfigService
) {

    companion object {
        private const val LOG_CONFIG_TEMPLATE = "templates/log-config-template.xml"
        private const val LOG_FILE_NAME = "gameyfin"
        private val LOG_REFRESH_INTERVAL = 5.seconds
        private const val LOG_STREAM_RETENTION = 1000
    }

    private val log = KotlinLogging.logger {}

    private var logFilePath: Path? = null
    private val sink: Sinks.Many<String> = Sinks.many().replay().limit(LOG_STREAM_RETENTION)
    private var tailer: AsyncFileTailer? = null

    @EventListener(ApplicationStartedEvent::class)
    fun configureFileLogging() {
        return configureFileLogging(
            config.get(ConfigProperties.Logs.Folder)!!,
            config.get(ConfigProperties.Logs.MaxHistoryDays)!!,
            config.get(ConfigProperties.Logs.Level)!!
        )
    }

    fun configureFileLogging(folder: String, maxHistoryDays: Int, level: LogLevel) {
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        val configurator = JoranConfigurator()
        configurator.context = context
        context.reset()

        generateLogConfigXml(folder.removeSuffix("/"), maxHistoryDays, level).use {
            log.info { "Setting log level to $level" }
            log.info { "Setting log retention to $maxHistoryDays days" }
            configurator.doConfigure(it)

            val newLogFilePath = Paths.get(folder, "$LOG_FILE_NAME.log")
            if (newLogFilePath != logFilePath) {
                logFilePath = newLogFilePath

                tailer?.stopTailing()
                tailer = AsyncFileTailer(newLogFilePath.toFile(), LOG_REFRESH_INTERVAL, sink)
                tailer?.startTailing()
            }
        }
    }

    fun streamLogs(): Flux<String> {
        return sink.asFlux()
    }

    private fun generateLogConfigXml(
        folder: String,
        maxHistoryDays: Int,
        level: LogLevel
    ): InputStream {
        val template = javaClass.classLoader.getResourceAsStream(LOG_CONFIG_TEMPLATE)
            ?: throw IllegalStateException("Log config template not found")

        val templateString = template.bufferedReader().use { it.readText() }
        return templateString
            .replace("{LOG_FOLDER}", folder)
            .replace("{LOG_FILE_NAME}", LOG_FILE_NAME)
            .replace("{LOG_MAX_HISTORY_DAYS}", maxHistoryDays.toString())
            .replace("{LOG_LEVEL}", level.toString())
            .byteInputStream()
    }
}