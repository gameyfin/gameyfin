package org.gameyfin.app.core.plugins.management

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.security.CodeSigner
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.cert.CertPath
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PluginSignatureVerifierTest {

    private lateinit var publicKey: PublicKey
    private lateinit var verifier: PluginSignatureVerifier

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        publicKey = keyPairGenerator.generateKeyPair().public
        verifier = PluginSignatureVerifier(publicKey)
    }

    // ========================================================================================
    // verifyPluginSignature tests
    // ========================================================================================

    @Test
    fun `verifyPluginSignature should return THIRD_PARTY for unsigned JAR`() {
        val jarPath = createUnsignedJar("unsigned-plugin.jar", mapOf("com/example/MyClass.class" to "dummy content"))

        val result = verifier.verifyPluginSignature(jarPath)

        assertEquals(PluginTrustLevel.THIRD_PARTY, result)
    }

    @Test
    fun `verifyPluginSignature should return OFFICIAL for JAR with only META-INF entries`() {
        val jarPath = createUnsignedJar("meta-only.jar", mapOf("META-INF/services/some.service" to "org.example.Impl"))

        val result = verifier.verifyPluginSignature(jarPath)

        assertEquals(PluginTrustLevel.OFFICIAL, result)
    }

    @Test
    fun `verifyPluginSignature should return OFFICIAL for empty JAR`() {
        val jarPath = createUnsignedJar("empty.jar", emptyMap())

        val result = verifier.verifyPluginSignature(jarPath)

        assertEquals(PluginTrustLevel.OFFICIAL, result)
    }

    // ========================================================================================
    // verifyEntryDigest tests
    // ========================================================================================

    @Test
    fun `verifyEntryDigest should return true for valid JAR entry`() {
        val jarPath = createUnsignedJar("valid-entry.jar", mapOf("test.txt" to "hello world"))
        val jarFile = JarFile(jarPath.toFile())
        val entry = jarFile.getJarEntry("test.txt")

        val result = verifier.verifyEntryDigest(jarFile, entry)

        assertTrue(result)
    }

    // ========================================================================================
    // verifyCertificates tests
    // ========================================================================================

    @Test
    fun `verifyCertificates should return OFFICIAL for empty certificate list`() {
        val result = verifier.verifyCertificates(emptyList())

        assertEquals(PluginTrustLevel.OFFICIAL, result)
    }

    @Test
    fun `verifyCertificates should return OFFICIAL for non-X509 certificates`() {
        val nonX509Cert = mockk<Certificate>()

        val result = verifier.verifyCertificates(listOf(nonX509Cert))

        assertEquals(PluginTrustLevel.OFFICIAL, result)
    }

    @Test
    fun `verifyCertificates should return UNTRUSTED when certificate verification fails`() {
        val badCert = mockk<X509Certificate>()
        every { badCert.verify(any()) } throws java.security.SignatureException("bad signature")

        val result = verifier.verifyCertificates(listOf(badCert))

        assertEquals(PluginTrustLevel.UNTRUSTED, result)
    }

    @Test
    fun `verifyCertificates should return OFFICIAL when certificate verification succeeds`() {
        val goodCert = mockk<X509Certificate>()
        every { goodCert.verify(any()) } just Runs

        val result = verifier.verifyCertificates(listOf(goodCert))

        assertEquals(PluginTrustLevel.OFFICIAL, result)
    }

    @Test
    fun `verifyCertificates should return UNTRUSTED if any certificate in list fails`() {
        val goodCert = mockk<X509Certificate>()
        every { goodCert.verify(any()) } just Runs

        val badCert = mockk<X509Certificate>()
        every { badCert.verify(any()) } throws java.security.SignatureException("bad signature")

        val result = verifier.verifyCertificates(listOf(goodCert, badCert))

        assertEquals(PluginTrustLevel.UNTRUSTED, result)
    }

    // ========================================================================================
    // verifyCodeSigners tests
    // ========================================================================================

    @Test
    fun `verifyCodeSigners should return OFFICIAL when all signers have valid certificates`() {
        val goodCert = mockk<X509Certificate>()
        every { goodCert.verify(any()) } just Runs

        val certPath = mockk<CertPath>()
        every { certPath.certificates } returns listOf(goodCert)

        val codeSigner = mockk<CodeSigner>()
        every { codeSigner.signerCertPath } returns certPath

        val result = verifier.verifyCodeSigners(arrayOf(codeSigner))

        assertEquals(PluginTrustLevel.OFFICIAL, result)
    }

    @Test
    fun `verifyCodeSigners should return UNTRUSTED when a signer has invalid certificate`() {
        val badCert = mockk<X509Certificate>()
        every { badCert.verify(any()) } throws java.security.SignatureException("bad signature")

        val certPath = mockk<CertPath>()
        every { certPath.certificates } returns listOf(badCert)

        val codeSigner = mockk<CodeSigner>()
        every { codeSigner.signerCertPath } returns certPath

        val result = verifier.verifyCodeSigners(arrayOf(codeSigner))

        assertEquals(PluginTrustLevel.UNTRUSTED, result)
    }

    @Test
    fun `verifyCodeSigners should return UNTRUSTED on first invalid signer and skip remaining`() {
        val badCert = mockk<X509Certificate>()
        every { badCert.verify(any()) } throws java.security.SignatureException("bad signature")

        val badCertPath = mockk<CertPath>()
        every { badCertPath.certificates } returns listOf(badCert)

        val badSigner = mockk<CodeSigner>()
        every { badSigner.signerCertPath } returns badCertPath

        val goodCert = mockk<X509Certificate>()
        every { goodCert.verify(any()) } just Runs

        val goodCertPath = mockk<CertPath>()
        every { goodCertPath.certificates } returns listOf(goodCert)

        val goodSigner = mockk<CodeSigner>()
        every { goodSigner.signerCertPath } returns goodCertPath

        val result = verifier.verifyCodeSigners(arrayOf(badSigner, goodSigner))

        assertEquals(PluginTrustLevel.UNTRUSTED, result)
    }

    // ========================================================================================
    // Helper methods
    // ========================================================================================

    private fun createUnsignedJar(fileName: String, entries: Map<String, String>): Path {
        val jarPath = tempDir.resolve(fileName)
        val manifest = Manifest()
        manifest.mainAttributes.putValue("Manifest-Version", "1.0")

        JarOutputStream(jarPath.toFile().outputStream(), manifest).use { jos ->
            for ((name, content) in entries) {
                jos.putNextEntry(JarEntry(name))
                jos.write(content.toByteArray())
                jos.closeEntry()
            }
        }

        return jarPath
    }
}

