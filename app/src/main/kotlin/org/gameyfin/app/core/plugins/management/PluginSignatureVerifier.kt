package org.gameyfin.app.core.plugins.management

import java.nio.file.Path
import java.security.CodeSigner
import java.security.PublicKey
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.jar.JarEntry
import java.util.jar.JarFile

/**
 * Verifies JAR plugin signatures against a trusted public key.
 */
class PluginSignatureVerifier(private val publicKey: PublicKey) {

    fun verifyPluginSignature(pluginPath: Path): PluginTrustLevel {
        JarFile(pluginPath.toFile(), true).use { jarFile ->
            val entries = jarFile.entries()

            var trustLevel = PluginTrustLevel.THIRD_PARTY
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (shouldSkipEntry(entry)) continue

                if (!verifyEntryDigest(jarFile, entry)) return PluginTrustLevel.UNTRUSTED

                val codeSigners = entry.codeSigners
                if (codeSigners.isNullOrEmpty()) return PluginTrustLevel.THIRD_PARTY

                trustLevel = verifyCodeSigners(codeSigners)
                if (trustLevel != PluginTrustLevel.OFFICIAL) return trustLevel
            }

            return trustLevel
        }
    }

    private fun shouldSkipEntry(entry: JarEntry): Boolean {
        val name = entry.name.uppercase(Locale.ROOT)
        return entry.isDirectory || name == "META-INF/MANIFEST.MF" ||
            (name.startsWith("META-INF/") && '/' !in name.removePrefix("META-INF/") &&
                (name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC")))
    }

    /**
     * Reads the full entry stream to trigger JAR signature/digest verification.
     * Returns `true` if the entry is valid, `false` if signature verification failed.
     */
    fun verifyEntryDigest(jarFile: JarFile, entry: JarEntry): Boolean {
        return try {
            val buffer = ByteArray(8192)

            jarFile.getInputStream(entry).use { entryInputStream ->
                while (entryInputStream.read(buffer, 0, buffer.size) != -1) {
                    // Reading to trigger SecurityException on digest mismatch
                }
            }

            true
        } catch (_: SecurityException) {
            false
        }
    }

    /**
     * Verifies that all code signers' certificates are signed with the expected public key.
     */
    fun verifyCodeSigners(codeSigners: Array<CodeSigner>): PluginTrustLevel {
        for (codeSigner in codeSigners) {
            val certs = codeSigner.signerCertPath.certificates.toList()
            val trustLevel = verifyCertificates(certs)
            if (trustLevel != PluginTrustLevel.OFFICIAL) return trustLevel
        }
        return PluginTrustLevel.OFFICIAL
    }

    fun verifyCertificates(certs: List<Certificate>): PluginTrustLevel {
        if (certs.isEmpty()) return PluginTrustLevel.UNTRUSTED
        for (cert in certs) {
            if (cert !is X509Certificate) return PluginTrustLevel.UNTRUSTED
            try {
                cert.verify(publicKey)
            } catch (_: Exception) {
                return PluginTrustLevel.UNTRUSTED
            }
        }
        return PluginTrustLevel.OFFICIAL
    }
}
