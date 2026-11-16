package org.gameyfin.plugins.download.direct

import java.io.*
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Carries producer failures across the pipe instead of turning them into successful EOF. */
internal object ArchiveStream {
    fun open(source: Path? = null, write: (OutputStream) -> Unit): InputStream {
        val input = PipedInputStream(512 * 1024)
        val output = PipedOutputStream(input)
        val failure = AtomicReference<IOException?>()
        val cancelled = AtomicBoolean()
        val producer = Thread.ofVirtual().start {
            try {
                val buffered = BufferedOutputStream(output, 512 * 1024)
                write(buffered)
                buffered.flush()
            } catch (error: Exception) {
                if (!cancelled.get()) {
                    failure.set(IOException("Archive generation failed for $source", error))
                    System.getLogger(ArchiveStream::class.java.name)
                        .log(
                            System.Logger.Level.WARNING,
                            "Archive generation failed for $source: ${error.message}",
                            error
                        )
                }
            } finally {
                output.close()
            }
        }
        return object : FilterInputStream(input) {
            private fun checked(result: Int): Int {
                if (result == -1) failure.get()?.let { throw it }
                return result
            }

            override fun read(): Int = checked(input.read())
            override fun read(b: ByteArray, off: Int, len: Int): Int = checked(input.read(b, off, len))
            override fun close() {
                cancelled.set(true)
                super.close()
                producer.interrupt()
            }
        }
    }
}
