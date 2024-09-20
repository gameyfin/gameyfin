package de.grimsi.gameyfin.logs

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import de.grimsi.gameyfin.config.ConfigProperties
import de.grimsi.gameyfin.config.ConfigService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.io.input.Tailer
import org.apache.commons.io.input.TailerListenerAdapter
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.boot.logging.LogLevel
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration

@Service
class LogService(
    private val config: ConfigService
) {

    companion object {
        private const val LOG_CONFIG_TEMPLATE = "log-config-template.xml"
        private const val LOG_FILE_NAME = "gameyfin"
        private val LOG_REFRESH_INTERVAL = Duration.ofSeconds(5)
    }

    private val log = KotlinLogging.logger {}

    private var logFilePath: Path = Paths.get(config.get(ConfigProperties.LogsFolder)!!, "$LOG_FILE_NAME.log")

    private val sink: Sinks.Many<String> = Sinks.many().multicast().onBackpressureBuffer()

    @EventListener(ApplicationStartedEvent::class)
    fun configureFileLogging() {
        return configureFileLogging(
            config.get(ConfigProperties.LogsFolder)!!,
            config.get(ConfigProperties.LogsMaxHistoryDays)!!,
            config.get(ConfigProperties.LogsLevel)!!
        )
    }

    init {
        val tailer = Tailer.builder()
            .setFile(logFilePath.toFile())
            .setTailerListener(object : TailerListenerAdapter() {
                override fun handle(line: String) {
                    sink.tryEmitNext(line)
                }
            })
            .setDelayDuration(LOG_REFRESH_INTERVAL)
            .setTailFromEnd(true)
            .get()

        Thread(tailer).start()
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
            logFilePath = Paths.get(config.get(ConfigProperties.LogsFolder)!!, "$LOG_FILE_NAME.log")
        }
    }

    fun streamLogs(): Flux<String> {
        return sink.asFlux()
    }

    fun getInitialLogs(): Flux<String> {
        return Flux.fromStream(Files.lines(logFilePath))
    }

    private fun generateLogConfigXml(
        folder: String,
        maxHistoryDays: Int,
        level: LogLevel
    ): InputStream {
        val template = javaClass.classLoader.getResourceAsStream(LOG_CONFIG_TEMPLATE)

        if (template == null) {
            throw IllegalStateException("Log config template not found")
        }

        val templateString = template.bufferedReader().use { it.readText() }
        return templateString
            .replace("{LOG_FOLDER}", folder)
            .replace("{LOG_FILE_NAME}", LOG_FILE_NAME)
            .replace("{LOG_MAX_HISTORY_DAYS}", maxHistoryDays.toString())
            .replace("{LOG_LEVEL}", level.toString())
            .byteInputStream()
    }
}