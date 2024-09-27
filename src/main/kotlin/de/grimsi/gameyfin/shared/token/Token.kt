package de.grimsi.gameyfin.shared.token

import de.grimsi.gameyfin.core.security.EncryptionConverter
import de.grimsi.gameyfin.users.entities.User
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.Type
import java.time.Instant
import java.util.*
import kotlin.time.toJavaDuration

@Entity
class Token<T : TokenType>(
    @Id
    @Convert(converter = EncryptionConverter::class)
    val secret: String = UUID.randomUUID().toString(),

    @Type(TokenTypeUserType::class)
    val type: T,

    @OneToOne(targetEntity = User::class, fetch = FetchType.EAGER)
    val user: User,

    @CreationTimestamp
    val createdOn: Instant? = null
) {
    val expired: Boolean
        get() = type.expiration.isFinite() &&
                createdOn?.plus(type.expiration.toJavaDuration())!!.isBefore(Instant.now())
}