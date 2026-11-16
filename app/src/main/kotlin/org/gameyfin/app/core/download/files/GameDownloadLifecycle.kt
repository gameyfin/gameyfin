package org.gameyfin.app.core.download.files

import jakarta.servlet.http.HttpServletRequest
import org.gameyfin.app.games.entities.Game
import org.springframework.http.ResponseEntity
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.context.request.async.CallableProcessingInterceptor
import org.springframework.web.context.request.async.WebAsyncUtils
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.io.Closeable
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.Callable

/** Coordinates download preparation and streaming with explicit game removal. */
internal object GameDownloadLifecycle {
    private val monitor = Any()
    private val active = mutableMapOf<Long, MutableSet<Transfer>>()
    private val removing = mutableSetOf<Long>()
    private val current = ThreadLocal<Transfer>()
    private const val CANCELLED = "Download cancelled because the game is being removed."

    fun checkCancelled() = current.get()?.checkCancelled() ?: Unit

    fun prepare(
        gameId: Long,
        request: HttpServletRequest,
        loadGame: () -> Game,
        prepareResponse: (Game, Transfer) -> ResponseEntity<StreamingResponseBody>
    ): ResponseEntity<StreamingResponseBody> {
        // Loading the game and registering must be atomic with removal. After removal,
        // the database lookup rejects requests rather than reopening deleted files.
        val (game, transfer) = synchronized(monitor) {
            if (gameId in removing) throw IOException(CANCELLED)
            val game = loadGame()
            val transfer = Transfer(gameId)
            active.getOrPut(gameId) { mutableSetOf() }.add(transfer)
            game to transfer
        }
        try {
            return transfer.run {
                val response = prepareResponse(game, transfer)
                val body = response.body
                if (body == null) {
                    // HEAD and unsatisfiable range responses have already closed their source.
                    transfer.releaseResource()
                    transfer.close()
                    response
                } else {
                    if (body is Closeable) transfer.own(body)
                    val trackedBody = object : StreamingResponseBody, Closeable {
                        override fun writeTo(outputStream: OutputStream) {
                            use {
                                transfer.run {
                                    body.writeTo(object : FilterOutputStream(outputStream) {
                                        override fun write(b: Int) {
                                            transfer.checkCancelled()
                                            out.write(b)
                                        }

                                        override fun write(b: ByteArray, off: Int, len: Int) {
                                            transfer.checkCancelled()
                                            out.write(b, off, len)
                                        }
                                    })
                                }
                            }
                        }

                        override fun close() = transfer.close()
                    }
                    WebAsyncUtils.getAsyncManager(request).registerCallableInterceptor(
                        trackedBody, object : CallableProcessingInterceptor {
                            override fun <T : Any> afterCompletion(request: NativeWebRequest, task: Callable<T>) =
                                trackedBody.close()
                        }
                    )
                    ResponseEntity.status(response.statusCode).headers(response.headers).body(trackedBody)
                }
            }
        } catch (error: Throwable) {
            transfer.close()
            throw error
        }
    }

    fun <T> remove(gameId: Long, action: () -> T): T {
        val transfers = synchronized(monitor) {
            check(removing.add(gameId)) { "Game is already being removed" }
            active[gameId]?.toList().orEmpty()
        }
        try {
            // Cancel now, including prepared responses that have not started streaming.
            // Never wait for transfers to finish naturally or join their worker threads.
            val failures = transfers.mapNotNull { transfer ->
                runCatching { transfer.cancel() }.exceptionOrNull()
            }
            if (failures.isNotEmpty()) {
                throw IOException("Could not close all downloads. The game was not removed.").also { error ->
                    failures.forEach(error::addSuppressed)
                }
            }
            return action()
        } finally {
            synchronized(monitor) { removing.remove(gameId) }
        }
    }

    class Transfer internal constructor(private val gameId: Long) : Closeable {
        @Volatile
        private var cancelled = false
        private var resource: Closeable? = null
        private var worker: Thread? = null
        private var closed = false

        @Synchronized
        fun own(resource: Closeable) {
            if (cancelled || closed) {
                resource.close()
                throw IOException(CANCELLED)
            }
            this.resource = resource
        }

        @Synchronized
        fun releaseResource() {
            resource = null
        }

        fun <T> whileActive(action: () -> T): T = synchronized(monitor) {
            checkCancelled()
            if (gameId in removing) throw IOException(CANCELLED)
            action()
        }

        fun checkCancelled() {
            if (cancelled) throw IOException(CANCELLED)
        }

        fun <T> run(action: () -> T): T {
            synchronized(this) {
                checkCancelled()
                worker = Thread.currentThread()
            }
            current.set(this)
            try {
                return action().also { checkCancelled() }
            } finally {
                current.remove()
                synchronized(this) {
                    worker = null
                    if (cancelled) Thread.interrupted()
                }
            }
        }

        fun cancel() {
            synchronized(this) {
                cancelled = true
                worker?.interrupt()
            }
            close()
        }

        override fun close() {
            val toClose = synchronized(this) {
                if (closed) return
                closed = true
                resource.also { resource = null }
            }
            try {
                toClose?.close()
            } finally {
                synchronized(monitor) {
                    active[gameId]?.let { transfers ->
                        transfers.remove(this)
                        if (transfers.isEmpty()) active.remove(gameId)
                    }
                }
            }
        }
    }
}
