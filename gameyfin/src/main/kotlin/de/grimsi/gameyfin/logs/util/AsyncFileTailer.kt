package de.grimsi.gameyfin.logs.util

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.apache.commons.io.input.Tailer
import org.apache.commons.io.input.TailerListenerAdapter
import reactor.core.publisher.Sinks
import java.io.File
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * Wraps Tailer from Apache Commons IO to tail a file asynchronously using Kotlin Coroutines.
 * Results are emitted to a sink
 *
 * @param file The file to tail
 * @param interval The interval to check for new lines
 * @param sink The sink to emit new lines to
 * @see Tailer
 */
class AsyncFileTailer(
    private val file: File,
    interval: Duration,
    private val sink: Sinks.Many<String>
) {
    private val log = KotlinLogging.logger {}

    private var tailerJob: Job? = null

    private val tailer = Tailer.builder()
        .setFile(file)
        .setTailerListener(object : TailerListenerAdapter() {
            override fun handle(line: String?) {
                line?.let { sink.tryEmitNext(it) }
            }
        })
        // Who tf thought it was a good idea to start a thread by default?
        .setStartThread(false)
        .setDelayDuration(interval.toJavaDuration())
        .get()

    fun startTailing() {
        if (tailerJob == null || tailerJob?.isCancelled == true) {
            tailerJob = CoroutineScope(Dispatchers.IO).launch {
                tailer.run()
            }

            log.debug { "Started tailing the file: ${file.name}" }
        } else {
            log.error { "File tailing for file ${file.name} is already running!" }
        }
    }

    fun stopTailing() {
        tailerJob?.let {
            it.cancel()
            tailerJob = null
            log.debug { "Stopped tailing the file: ${file.name}" }
        }
    }
}