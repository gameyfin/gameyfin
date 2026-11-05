package org.gameyfin.app.core.download.files

import jakarta.servlet.http.HttpServletRequest
import org.gameyfin.pluginapi.download.DownloadMetadata
import org.gameyfin.pluginapi.download.FileDownload
import org.gameyfin.pluginapi.download.RangeDownload
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.context.request.async.CallableProcessingInterceptor
import org.springframework.web.context.request.async.WebAsyncUtils
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.io.Closeable
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicBoolean

/** Shared HTTP framing for browser and API downloads. */
internal object DownloadResponses {
    private sealed interface Selection {
        data object Full : Selection
        data object Unsatisfiable : Selection
        data class Partial(val start: Long, val length: Long) : Selection
    }

    private fun select(header: String?, size: Long): Selection {
        if (header == null) return Selection.Full
        val match = Regex("bytes=(\\d*)-(\\d*)").matchEntire(header.trim()) ?: return Selection.Full
        val (first, last) = match.destructured
        if (first.isEmpty() && last.isEmpty()) return Selection.Full
        val start = first.takeIf { it.isNotEmpty() }?.toLongOrNull()
        val end = last.takeIf { it.isNotEmpty() }?.toLongOrNull()
        if (first.isNotEmpty() && start == null || last.isNotEmpty() && end == null) return Selection.Full
        if (start != null && end != null && end < start) return Selection.Full
        if (size == 0L) return Selection.Unsatisfiable
        if (start == null) {
            if (end == 0L) return Selection.Unsatisfiable
            val length = minOf(end!!, size)
            return Selection.Partial(size - length, length)
        }
        if (start >= size) return Selection.Unsatisfiable
        return Selection.Partial(start, minOf(end ?: (size - 1), size - 1) - start + 1)
    }

    fun head(metadata: DownloadMetadata?, disposition: String): ResponseEntity<StreamingResponseBody> {
        val builder = headers(ResponseEntity.ok(), disposition)
        metadata?.size?.let { builder.contentLength(it) }
        return builder.build()
    }

    fun file(
        download: FileDownload, request: HttpServletRequest, disposition: String, onStart: () -> Unit,
        transfer: (InputStream, OutputStream) -> Unit
    ): ResponseEntity<StreamingResponseBody> {
        try {
            val builder = headers(ResponseEntity.ok(), disposition)
            download.size?.let { builder.contentLength(it) }
            onStart()
            return builder.body(body(request, download.data) { output -> transfer(download.data, output) })
        } catch (error: Throwable) {
            download.data.close()
            throw error
        }
    }

    fun range(
        resource: RangeDownload, request: HttpServletRequest, disposition: String,
        onStart: () -> Unit, transfer: (InputStream, OutputStream) -> Unit
    ): ResponseEntity<StreamingResponseBody> {
        try {
            val size = requireNotNull(resource.metadata.size)
            val head = request.method == "HEAD"
            val ifRange = request.getHeader(HttpHeaders.IF_RANGE)
            val selection = if (head || ifRange != null && ifRange != resource.etag) Selection.Full
            else select(request.getHeader(HttpHeaders.RANGE), size)
            val status = when (selection) {
                Selection.Unsatisfiable -> 416
                is Selection.Partial -> 206
                Selection.Full -> 200
            }
            val builder = headers(ResponseEntity.status(status), disposition)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes").eTag(resource.etag)
            if (selection == Selection.Unsatisfiable) {
                resource.close()
                return builder.header(HttpHeaders.CONTENT_RANGE, "bytes */$size").contentLength(0).build()
            }
            val start = (selection as? Selection.Partial)?.start ?: 0
            val length = (selection as? Selection.Partial)?.length ?: size
            builder.contentLength(length)
            if (selection is Selection.Partial) {
                builder.header(HttpHeaders.CONTENT_RANGE, "bytes $start-${start + length - 1}/$size")
            }
            if (head) {
                resource.close()
                return builder.build()
            }
            val input = resource.openStream(start, length)
            onStart()
            return builder.body(body(request, { resource.use { input.close() } }) { output ->
                transfer(input, output)
            })
        } catch (error: Throwable) {
            resource.close()
            throw error
        }
    }

    private fun body(
        request: HttpServletRequest,
        resource: Closeable,
        transfer: (OutputStream) -> Unit
    ): StreamingResponseBody {
        val body = object : StreamingResponseBody, Closeable {
            private val closed = AtomicBoolean()
            override fun writeTo(outputStream: OutputStream) {
                use {
                    transfer(object : FilterOutputStream(outputStream) {
                        private var firstWrite = true

                        private fun flushFirstWrite() {
                            if (!firstWrite) return
                            firstWrite = false
                            out.flush()
                            // Spring's StreamingResponseBody stream suppresses flush(). Commit the
                            // servlet buffer once so a small ZIP header reaches the client promptly.
                            if (request.isAsyncStarted) request.asyncContext.response.flushBuffer()
                        }

                        override fun write(b: Int) {
                            out.write(b)
                            flushFirstWrite()
                        }

                        override fun write(b: ByteArray, off: Int, len: Int) {
                            out.write(b, off, len)
                            if (len > 0) flushFirstWrite()
                        }
                    })
                }
            }

            override fun close() {
                if (closed.compareAndSet(false, true)) resource.close()
            }
        }
        WebAsyncUtils.getAsyncManager(request)
            .registerCallableInterceptor(body, object : CallableProcessingInterceptor {
                override fun <T : Any> afterCompletion(request: NativeWebRequest, task: Callable<T>) = body.close()
            })
        return body
    }

    private fun headers(builder: ResponseEntity.BodyBuilder, disposition: String) = builder
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
        .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
        .header(HttpHeaders.CACHE_CONTROL, "no-store, no-transform")
}
