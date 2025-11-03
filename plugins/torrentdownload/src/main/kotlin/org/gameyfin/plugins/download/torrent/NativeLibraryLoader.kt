package org.gameyfin.plugins.download.torrent

import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files

object NativeLibraryLoader {
    private val logger = LoggerFactory.getLogger(NativeLibraryLoader::class.java)

    fun loadLibtorrent4j() {
        try {
            val osName = System.getProperty("os.name").lowercase()
            val osArch = System.getProperty("os.arch").lowercase()

            val (libFolder, libName) = when {
                osName.contains("mac") || osName.contains("darwin") -> {
                    // macOS ARM64 (M1/M2/M3)
                    "arm64" to "libtorrent4j.dylib"
                }

                osName.contains("windows") -> {
                    // Windows x86_64
                    "x86_64" to "libtorrent4j.dll"
                }

                osName.contains("linux") -> {
                    val arch = when {
                        osArch.contains("aarch64") || osArch.contains("arm64") -> "arm64"
                        osArch.contains("arm") && !osArch.contains("64") -> "arm"
                        else -> "x86_64"
                    }
                    arch to "libtorrent4j.so"
                }

                else -> {
                    throw UnsatisfiedLinkError("Unsupported operating system: $osName")
                }
            }

            val resourcePath = "/lib/$libFolder/$libName"
            logger.debug("Attempting to load native library from: $resourcePath")

            val inputStream = NativeLibraryLoader::class.java.getResourceAsStream(resourcePath)
                ?: throw UnsatisfiedLinkError("Native library not found in JAR: $resourcePath")

            // Create a temporary file to extract the native library
            val tempDir = Files.createTempDirectory("libtorrent4j-native").toFile()
            tempDir.deleteOnExit()

            val tempLibFile = File(tempDir, libName)
            tempLibFile.deleteOnExit()

            // Extract the native library to the temporary file
            inputStream.use { input ->
                FileOutputStream(tempLibFile).use { output ->
                    input.copyTo(output, bufferSize = 8192 * 8)
                }
            }

            // Set the system property for libtorrent4j to use
            System.setProperty("libtorrent4j.jni.path", tempLibFile.absolutePath)

            logger.debug("Successfully extracted native library to: ${tempLibFile.absolutePath}")

        } catch (e: Exception) {
            logger.error("Failed to load native library", e)
            throw UnsatisfiedLinkError("Failed to load libtorrent4j native library: ${e.message}")
        }
    }
}

