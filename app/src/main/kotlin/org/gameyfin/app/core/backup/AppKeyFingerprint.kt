package org.gameyfin.app.core.backup

import org.gameyfin.app.core.security.EncryptionUtils
import java.security.MessageDigest
import java.util.*

/**
 * Encrypted columns are exported as-is (still encrypted), so a backup can only be restored by an instance using the
 * same APP_KEY. To detect a mismatch without storing the key itself, a fingerprint (salted SHA-256 hash) of the key
 * is stored in the backup manifest.
 */
object AppKeyFingerprint {
    private const val DOMAIN = "gameyfin-backup-app-key:"

    fun of(base64Key: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(DOMAIN.toByteArray())
        digest.update(Base64.getDecoder().decode(base64Key.trim()))
        return "sha256:" + HexFormat.of().formatHex(digest.digest())
    }

    fun current(): String = of(EncryptionUtils.getAppKey())
}
