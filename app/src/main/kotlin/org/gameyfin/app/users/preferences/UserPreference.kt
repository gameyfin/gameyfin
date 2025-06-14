package org.gameyfin.app.users.preferences

import jakarta.persistence.*
import org.gameyfin.app.core.security.EncryptionConverter
import java.io.Serializable

@Entity
class UserPreference(
    @EmbeddedId
    val id: UserPreferenceKey,

    @Column(name = "`value`")
    @Convert(converter = EncryptionConverter::class)
    var value: String
)

@Embeddable
data class UserPreferenceKey(
    @Column(name = "`key`")
    val key: String,

    @Column(name = "user_id")
    val userId: Long
) : Serializable