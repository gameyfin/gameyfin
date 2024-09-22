package de.grimsi.gameyfin.users.entities

import de.grimsi.gameyfin.core.security.EncryptionConverter
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
class PasswordResetToken(
    @Id
    @Convert(converter = EncryptionConverter::class)
    val token: String,

    @OneToOne(targetEntity = User::class, fetch = FetchType.EAGER)
    val user: User,

    @CreationTimestamp
    val createdOn: Instant? = null
)