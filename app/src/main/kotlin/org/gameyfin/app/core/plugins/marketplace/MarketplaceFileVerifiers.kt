package org.gameyfin.app.core.plugins.marketplace

import org.gameyfin.app.core.plugins.management.GameyfinPluginManager
import org.pf4j.update.FileVerifier
import org.pf4j.update.VerifyException
import org.pf4j.update.verifier.BasicVerifier
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.jar.JarFile

/**
 * Verifies the SHA-512 checksum of a downloaded plugin. In contrast to the PF4J default verifier, a checksum is
 * mandatory. The checksum can be given as:
 * - the checksum itself (128 hex characters)
 * - a URL to a file containing the checksum (e.g. `plugin.sha512`, output of `sha512sum`)
 */
class MandatorySha512Verifier(private val httpClient: MarketplaceHttpClient) : FileVerifier {

    companion object {
        private val SHA512_REGEX = Regex("^[0-9a-fA-F]{128}$")

        fun sha512(file: Path): String {
            val digest = MessageDigest.getInstance("SHA-512")
            Files.newInputStream(file).use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    override fun verify(context: FileVerifier.Context, file: Path) {
        val declared = context.sha512sum?.trim()
        if (declared.isNullOrEmpty()) {
            throw VerifyException("Release ${context.version} of plugin '${context.id}' does not provide a SHA-512 checksum")
        }

        val expected = if (SHA512_REGEX.matches(declared)) {
            declared
        } else {
            val checksumUri = try {
                URI(declared)
            } catch (_: Exception) {
                throw VerifyException("Invalid SHA-512 checksum or checksum URL '$declared' for plugin '${context.id}'")
            }
            val content = try {
                httpClient.getString(checksumUri, MarketplaceHttpClient.MAX_CHECKSUM_SIZE)
            } catch (e: Exception) {
                throw VerifyException(e, "Could not fetch checksum file '$declared' of plugin '${context.id}'")
            }
            content.trim().split(Regex("\\s+")).firstOrNull()
                ?.takeIf { SHA512_REGEX.matches(it) }
                ?: throw VerifyException("Checksum file '$declared' of plugin '${context.id}' does not contain a valid SHA-512 checksum")
        }

        val actual = sha512(file)
        if (!actual.equals(expected, ignoreCase = true)) {
            throw VerifyException("SHA-512 checksum mismatch for plugin '${context.id}' (expected $expected, got $actual)")
        }
    }
}

/**
 * Verifies that the downloaded file is a valid plugin JAR matching the release from the marketplace index
 * (same plugin ID and version) and that it is compatible with this Gameyfin instance.
 */
class PluginDescriptorVerifier(private val pluginManager: GameyfinPluginManager) : FileVerifier {

    override fun verify(context: FileVerifier.Context, file: Path) {
        val attributes = try {
            JarFile(file.toFile()).use { it.manifest?.mainAttributes }
        } catch (e: Exception) {
            throw VerifyException(e, "Downloaded file of plugin '${context.id}' is not a valid JAR file")
        } ?: throw VerifyException("Downloaded file of plugin '${context.id}' does not contain a manifest")

        val pluginId = attributes.getValue("Plugin-Id")
        if (pluginId != context.id) {
            throw VerifyException("Plugin ID mismatch: expected '${context.id}' but downloaded file contains '$pluginId'")
        }

        val pluginVersion = attributes.getValue("Plugin-Version")
        val versionMatches = pluginVersion != null && try {
            pluginManager.versionManager.compareVersions(pluginVersion, context.version) == 0
        } catch (_: Exception) {
            pluginVersion == context.version
        }
        if (!versionMatches) {
            throw VerifyException("Version mismatch for plugin '${context.id}': expected '${context.version}' but downloaded file contains '$pluginVersion'")
        }

        val requires = attributes.getValue("Plugin-Requires")
        if (!pluginManager.isCompatible(requires)) {
            throw VerifyException("Plugin '${context.id}' requires Gameyfin version '$requires', but this is ${pluginManager.systemVersion}")
        }
    }
}

/**
 * Runs all verifiers needed for marketplace downloads.
 */
class MarketplaceFileVerifier(
    httpClient: MarketplaceHttpClient,
    pluginManager: GameyfinPluginManager
) : FileVerifier {

    private val verifiers = listOf(
        BasicVerifier(),
        MandatorySha512Verifier(httpClient),
        PluginDescriptorVerifier(pluginManager)
    )

    override fun verify(context: FileVerifier.Context, file: Path) {
        verifiers.forEach { it.verify(context, file) }
    }
}
