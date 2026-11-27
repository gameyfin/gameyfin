package org.gameyfin.app.users

import jakarta.mail.internet.AddressException
import jakarta.mail.internet.InternetAddress

internal fun parseEmailAddress(value: String?): String? {
    val email = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val valid = try {
        val address = InternetAddress(email, true)
        address.validate()
        !address.isGroup && address.address == email
    } catch (_: AddressException) {
        false
    }
    require(valid) { "Invalid email address" }
    return email
}
