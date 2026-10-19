package org.gameyfin.app.core.plugins.marketplace

import com.sun.net.httpserver.HttpServer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.gameyfin.app.core.plugins.management.GameyfinPluginManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pf4j.DefaultVersionManager
import org.pf4j.VersionManager
import org.pf4j.update.FileVerifier
import org.pf4j.update.VerifyException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.*
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.io.path.outputStream
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MarketplaceFileVerifiersTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var server: HttpServer
    private lateinit var baseUrl: String

    private val httpClient = MarketplaceHttpClient(allowInsecureUrls = true, applicationVersion = "test")

    private val pluginManager = mockk<GameyfinPluginManager>()

    @BeforeEach
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}"

        every { pluginManager.versionManager } returns DefaultVersionManager()
        every { pluginManager.systemVersion } returns "2.0.0"
        every { pluginManager.isCompatible(any<String>()) } answers {
            val requires = firstArg<String?>()
            requires == null || requires == "*" || requires == ">=1.0.0"
        }
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
    }

    private fun serve(path: String, status: Int, body: String) {
        server.createContext(path) { exchange ->
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) exchange.responseBody.write(bytes)
            exchange.close()
        }
    }

    private fun sha512Hex(file: Path): String =
        MessageDigest.getInstance("SHA-512").digest(Files.readAllBytes(file)).joinToString("") { "%02x".format(it) }

    private fun createJar(
        name: String = "plugin.jar",
        attributes: Map<String, String> = mapOf(
            "Plugin-Id" to "test-plugin",
            "Plugin-Version" to "1.0.0",
            "Plugin-Requires" to ">=1.0.0"
        ),
        withManifest: Boolean = true
    ): Path {
        val file = tempDir.resolve(name)
        file.outputStream().use { out ->
            val jar = if (withManifest) {
                val manifest = Manifest().apply {
                    mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
                    attributes.forEach { (key, value) -> mainAttributes.putValue(key, value) }
                }
                JarOutputStream(out, manifest)
            } else {
                JarOutputStream(out)
            }
            jar.use {
                it.putNextEntry(JarEntry("org/example/Plugin.class"))
                it.write(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()))
                it.closeEntry()
            }
        }
        return file
    }

    private fun context(
        sha512sum: String? = null,
        id: String = "test-plugin",
        version: String = "1.0.0",
        requires: String = "*",
        url: String = "https://example.com/plugin.jar"
    ) = FileVerifier.Context(id, Date(0), version, requires, url, sha512sum)

    @Nested
    inner class MandatorySha512VerifierTest {

        private val verifier = MandatorySha512Verifier(httpClient)

        @Test
        fun `sha512 computes lower case hex digest`() {
            val file = tempDir.resolve("data.bin")
            file.writeText("hello")
            assertEquals(
                "9b71d224bd62f3785d96d46ad3ea3d73319bfbc2890caadae2dff72519673ca7" +
                        "2323c3d99ba5c11d7c7acc6e14b8c5da0c4663475c2e5c3adef46f73bcdec043",
                MandatorySha512Verifier.sha512(file)
            )
        }

        @Test
        fun `missing checksum fails`() {
            val file = createJar()
            val e = assertFailsWith<VerifyException> { verifier.verify(context(sha512sum = null), file) }
            assertTrue(e.message!!.contains("does not provide a SHA-512 checksum"))
        }

        @Test
        fun `blank checksum fails`() {
            val file = createJar()
            assertFailsWith<VerifyException> { verifier.verify(context(sha512sum = "   "), file) }
        }

        @Test
        fun `matching literal checksum succeeds`() {
            val file = createJar()
            verifier.verify(context(sha512sum = sha512Hex(file)), file)
        }

        @Test
        fun `literal checksum is compared case insensitively and trimmed`() {
            val file = createJar()
            verifier.verify(context(sha512sum = "  ${sha512Hex(file).uppercase()}  "), file)
        }

        @Test
        fun `mismatching literal checksum fails`() {
            val file = createJar()
            val e = assertFailsWith<VerifyException> { verifier.verify(context(sha512sum = "0".repeat(128)), file) }
            assertTrue(e.message!!.contains("checksum mismatch"))
        }

        @Test
        fun `checksum file in sha512sum output format succeeds`() {
            val file = createJar()
            serve("/plugin.sha512", 200, "${sha512Hex(file)}  plugin.jar\n")
            verifier.verify(context(sha512sum = "$baseUrl/plugin.sha512"), file)
        }

        @Test
        fun `checksum file containing only the checksum succeeds`() {
            val file = createJar()
            serve("/plugin.sha512", 200, "\n  ${sha512Hex(file)}\n")
            verifier.verify(context(sha512sum = "$baseUrl/plugin.sha512"), file)
        }

        @Test
        fun `checksum file with mismatching checksum fails`() {
            val file = createJar()
            serve("/plugin.sha512", 200, "${"f".repeat(128)}  plugin.jar\n")
            val e = assertFailsWith<VerifyException> { verifier.verify(context(sha512sum = "$baseUrl/plugin.sha512"), file) }
            assertTrue(e.message!!.contains("checksum mismatch"))
        }

        @Test
        fun `checksum file without valid checksum fails`() {
            val file = createJar()
            serve("/plugin.sha512", 200, "this is not a checksum")
            val e = assertFailsWith<VerifyException> { verifier.verify(context(sha512sum = "$baseUrl/plugin.sha512"), file) }
            assertTrue(e.message!!.contains("does not contain a valid SHA-512 checksum"))
        }

        @Test
        fun `empty checksum file fails`() {
            val file = createJar()
            serve("/plugin.sha512", 200, "")
            assertFailsWith<VerifyException> { verifier.verify(context(sha512sum = "$baseUrl/plugin.sha512"), file) }
        }

        @Test
        fun `checksum file over the size limit fails`() {
            val file = createJar()
            serve("/plugin.sha512", 200, sha512Hex(file) + " ".repeat(MarketplaceHttpClient.MAX_CHECKSUM_SIZE.toInt()))
            val e = assertFailsWith<VerifyException> { verifier.verify(context(sha512sum = "$baseUrl/plugin.sha512"), file) }
            assertIs<IOException>(e.cause)
        }

        @Test
        fun `unavailable checksum file fails`() {
            val file = createJar()
            serve("/plugin.sha512", 404, "")
            val e = assertFailsWith<VerifyException> { verifier.verify(context(sha512sum = "$baseUrl/plugin.sha512"), file) }
            assertIs<IOException>(e.cause)
        }

        @Test
        fun `checksum file from file URL succeeds`() {
            val file = createJar()
            val checksumFile = tempDir.resolve("plugin.sha512")
            checksumFile.writeText("${sha512Hex(file)}  plugin.jar")
            verifier.verify(context(sha512sum = checksumFile.toUri().toString()), file)
        }

        @Test
        fun `invalid checksum URI fails`() {
            val file = createJar()
            val e = assertFailsWith<VerifyException> { verifier.verify(context(sha512sum = "not a valid uri"), file) }
            assertTrue(e.message!!.contains("Invalid SHA-512 checksum or checksum URL"))
        }

        @Test
        fun `checksum URL with disallowed scheme fails`() {
            val file = createJar()
            val secureVerifier = MandatorySha512Verifier(MarketplaceHttpClient())
            val e = assertFailsWith<VerifyException> {
                secureVerifier.verify(context(sha512sum = "$baseUrl/plugin.sha512"), file)
            }
            assertIs<IllegalArgumentException>(e.cause)
        }
    }

    @Nested
    inner class PluginDescriptorVerifierTest {

        private val verifier = PluginDescriptorVerifier(pluginManager)

        @Test
        fun `valid plugin succeeds`() {
            verifier.verify(context(), createJar())
        }

        @Test
        fun `plugin without requires succeeds when compatible`() {
            val file = createJar(attributes = mapOf("Plugin-Id" to "test-plugin", "Plugin-Version" to "1.0.0"))
            verifier.verify(context(), file)
            verify { pluginManager.isCompatible(null as String?) }
        }

        @Test
        fun `file that is not a JAR fails`() {
            val file = tempDir.resolve("plugin.jar")
            file.writeText("definitely not a zip file")
            val e = assertFailsWith<VerifyException> { verifier.verify(context(), file) }
            assertTrue(e.message!!.contains("is not a valid JAR file"))
        }

        @Test
        fun `JAR without manifest fails`() {
            val file = createJar(withManifest = false)
            val e = assertFailsWith<VerifyException> { verifier.verify(context(), file) }
            assertTrue(e.message!!.contains("does not contain a manifest"))
        }

        @Test
        fun `plugin id mismatch fails`() {
            val file = createJar(
                attributes = mapOf("Plugin-Id" to "other-plugin", "Plugin-Version" to "1.0.0", "Plugin-Requires" to "*")
            )
            val e = assertFailsWith<VerifyException> { verifier.verify(context(), file) }
            assertTrue(e.message!!.contains("Plugin ID mismatch"))
        }

        @Test
        fun `missing plugin id fails`() {
            val file = createJar(attributes = mapOf("Plugin-Version" to "1.0.0"))
            val e = assertFailsWith<VerifyException> { verifier.verify(context(), file) }
            assertTrue(e.message!!.contains("Plugin ID mismatch"))
        }

        @Test
        fun `plugin version mismatch fails`() {
            val file = createJar(
                attributes = mapOf("Plugin-Id" to "test-plugin", "Plugin-Version" to "1.0.1", "Plugin-Requires" to "*")
            )
            val e = assertFailsWith<VerifyException> { verifier.verify(context(), file) }
            assertTrue(e.message!!.contains("Version mismatch"))
        }

        @Test
        fun `missing plugin version fails`() {
            val file = createJar(attributes = mapOf("Plugin-Id" to "test-plugin"))
            val e = assertFailsWith<VerifyException> { verifier.verify(context(), file) }
            assertTrue(e.message!!.contains("Version mismatch"))
        }

        @Test
        fun `falls back to string comparison if versions cannot be compared`() {
            val versionManager = mockk<VersionManager>()
            every { versionManager.compareVersions(any(), any()) } throws IllegalArgumentException("invalid")
            every { pluginManager.versionManager } returns versionManager

            verifier.verify(context(version = "1.0.0"), createJar())

            val e = assertFailsWith<VerifyException> { verifier.verify(context(version = "1.0"), createJar()) }
            assertTrue(e.message!!.contains("Version mismatch"))
        }

        @Test
        fun `incompatible plugin requires fails`() {
            val file = createJar(
                attributes = mapOf("Plugin-Id" to "test-plugin", "Plugin-Version" to "1.0.0", "Plugin-Requires" to ">=3.0.0")
            )
            val e = assertFailsWith<VerifyException> { verifier.verify(context(), file) }
            assertTrue(e.message!!.contains("requires Gameyfin version '>=3.0.0'"))
            assertTrue(e.message!!.contains("2.0.0"))
        }

        @Test
        fun `compatibility is checked against the manifest and not the index`() {
            val file = createJar(
                attributes = mapOf("Plugin-Id" to "test-plugin", "Plugin-Version" to "1.0.0", "Plugin-Requires" to ">=3.0.0")
            )
            assertFailsWith<VerifyException> { verifier.verify(context(requires = "*"), file) }
            verify { pluginManager.isCompatible(">=3.0.0") }
        }
    }

    @Nested
    inner class MarketplaceFileVerifierTest {

        private val verifier = MarketplaceFileVerifier(httpClient, pluginManager)

        @Test
        fun `valid plugin with matching checksum succeeds`() {
            val file = createJar()
            verifier.verify(context(sha512sum = sha512Hex(file)), file)
        }

        @Test
        fun `valid plugin with checksum file succeeds`() {
            val file = createJar()
            serve("/plugin.sha512", 200, "${sha512Hex(file)}  plugin.jar")
            verifier.verify(context(sha512sum = "$baseUrl/plugin.sha512"), file)
        }

        @Test
        fun `empty file fails basic verification`() {
            val file = tempDir.resolve("empty.jar")
            Files.createFile(file)
            assertFailsWith<VerifyException> { verifier.verify(context(sha512sum = "0".repeat(128)), file) }
            verify(exactly = 0) { pluginManager.isCompatible(any<String>()) }
        }

        @Test
        fun `directory fails basic verification`() {
            val directory = Files.createDirectory(tempDir.resolve("dir.jar"))
            assertFailsWith<VerifyException> { verifier.verify(context(sha512sum = "0".repeat(128)), directory) }
        }

        @Test
        fun `missing checksum fails before descriptor verification`() {
            val file = createJar()
            val e = assertFailsWith<VerifyException> { verifier.verify(context(sha512sum = null), file) }
            assertTrue(e.message!!.contains("SHA-512"))
            verify(exactly = 0) { pluginManager.isCompatible(any<String>()) }
        }

        @Test
        fun `checksum mismatch fails`() {
            val file = createJar()
            assertFailsWith<VerifyException> { verifier.verify(context(sha512sum = "0".repeat(128)), file) }
        }

        @Test
        fun `descriptor mismatch fails even with valid checksum`() {
            val file = createJar()
            val e = assertFailsWith<VerifyException> {
                verifier.verify(context(id = "other-plugin", sha512sum = sha512Hex(file)), file)
            }
            assertTrue(e.message!!.contains("Plugin ID mismatch"))
        }

        @Test
        fun `invalid JAR with valid checksum fails`() {
            val file = tempDir.resolve("plugin.jar")
            file.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
            assertFailsWith<VerifyException> { verifier.verify(context(sha512sum = sha512Hex(file)), file) }
        }
    }
}
