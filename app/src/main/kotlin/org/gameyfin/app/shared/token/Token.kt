package org.gameyfin.app.shared.token

import org.gameyfin.app.core.security.EncryptionMapConverter
import jakarta.persistence.*
import org.gameyfin.app.core.security.EncryptionConverter
import org.gameyfin.app.users.entities.User
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
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

    @ManyToOne(targetEntity = User::class, fetch = FetchType.EAGER)
    @OnDelete(action = OnDeleteAction.CASCADE)
    val creator: User,

    @Convert(converter = EncryptionMapConverter::class)
    val payload: Map<String, String> = emptyMap(),

    @CreationTimestamp
    val createdOn: Instant? = null
) {
    val expired: Boolean
        get() = type.expiration.isFinite() &&
                createdOn?.plus(type.expiration.toJavaDuration())!!.isBefore(Instant.now())
}