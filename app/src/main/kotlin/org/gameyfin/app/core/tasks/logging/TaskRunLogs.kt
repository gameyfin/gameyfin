package org.gameyfin.app.core.tasks.logging

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.springframework.stereotype.Service
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.*

/**
 * Access to the log files of task runs, which are written by the sifting appender configured in
 * `log-config-template.xml` into `<log folder>/tasks/<run ID>.log`.
 */
@Service
class TaskRunLogs(private val config: ConfigService) {

    companion object {
        private val log = KotlinLogging.logger {}

        const val TASK_LOG_DIRECTORY = "tasks"
        private const val LOG_FILE_EXTENSION = ".log"
        private const val MAX_LOG_BYTES = 2L * 1024 * 1024
    }

    fun directory(): Path = Path.of(config.get(ConfigProperties.Logs.Folder)!!, TASK_LOG_DIRECTORY)

    /** The run ID is parsed as UUID, so it can never be used to access other files */
    fun file(runId: String): Path = directory().resolve("${UUID.fromString(runId)}$LOG_FILE_EXTENSION")

    fun exists(runId: String): Boolean = file(runId).isRegularFile()

    /**
     * Reads the log of a run. Only the last [MAX_LOG_BYTES] are returned for very large logs.
     */
    fun read(runId: String): String? {
        val file = file(runId)
        if (!file.isRegularFile()) return null
        RandomAccessFile(file.toFile(), "r").use { raf ->
            val length = raf.length()
            val start = maxOf(0L, length - MAX_LOG_BYTES)
            raf.seek(start)
            val bytes = ByteArray((length - start).toInt())
            raf.readFully(bytes)
            val content = String(bytes, Charsets.UTF_8)
            return if (start > 0) "[... ${start / 1024} KB omitted ...]\n" + content.substringAfter('\n') else content
        }
    }

    fun delete(runId: String) {
        try {
            file(runId).deleteIfExists()
        } catch (e: Exception) {
            log.warn { "Could not delete log of task run $runId: ${e.message}" }
        }
    }

    /** IDs of all runs for which a log file exists */
    fun listRunIds(): List<String> {
        val directory = directory()
        if (!directory.isDirectory()) return emptyList()
        return Files.list(directory).use { files ->
            files.toList()
                .map { it.name }
                .filter { it.endsWith(LOG_FILE_EXTENSION) }
                .map { it.removeSuffix(LOG_FILE_EXTENSION) }
                .filter { runCatching { UUID.fromString(it) }.isSuccess }
        }
    }
}
