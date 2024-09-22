package de.grimsi.gameyfin.users.entities

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToOne
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
class PasswordResetToken(
    @Id
    val token: String,

    @OneToOne(targetEntity = User::class, fetch = FetchType.EAGER)
    val user: User,

    @CreationTimestamp
    val createdOn: Instant? = null
)