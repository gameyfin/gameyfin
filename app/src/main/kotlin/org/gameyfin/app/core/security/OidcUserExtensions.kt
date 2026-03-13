package org.gameyfin.app.core.security

import org.springframework.security.oauth2.core.oidc.user.OidcUser

/**
 * Resolves the username from an OidcUser, using [attributeName] as the primary claim.
 *
 * Falls back through the following chain when the preferred claim is absent or blank:
 *   1. `preferred_username`
 *   2. `nickname`
 *   3. `name`
 *   4. `email`
 *   5. `sub` (always present, used as last resort)
 */
fun OidcUser.resolvedUsername(attributeName: String = "preferred_username"): String {
    // Try the configured attribute first, then fall through the standard fallback chain
    val candidates = linkedSetOf(attributeName, "preferred_username", "nickname", "name", "email")
    for (claim in candidates) {
        val value = getClaim<String>(claim)
        if (!value.isNullOrBlank()) return value
    }
    // `sub` is mandatory in OIDC and always present
    return subject
}

