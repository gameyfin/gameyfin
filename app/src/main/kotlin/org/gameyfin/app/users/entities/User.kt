package org.gameyfin.app.users.entities

import jakarta.persistence.*
import org.gameyfin.app.core.Role
import org.gameyfin.app.core.security.EncryptionConverter
import org.gameyfin.app.media.Image
import org.gameyfin.app.users.parseEmailAddress
import org.springframework.security.oauth2.core.oidc.user.OidcUser


@Entity
@Table(name = "users")
@EntityListeners(UserEntityListener::class)
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,

    @Column(unique = true)
    @Convert(converter = EncryptionConverter::class)
    var username: String,

    var password: String? = null,

    var oidcProviderId: String? = null,

    @Column(unique = true)
    @Convert(converter = EncryptionConverter::class)
    var email: String?,

    var emailConfirmed: Boolean = false,

    var enabled: Boolean = false,

    @OneToOne(cascade = [CascadeType.ALL])
    var avatar: Image? = null,

    @ElementCollection(targetClass = Role::class, fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    var roles: List<Role> = emptyList()
) {

    constructor(
        oidcUser: OidcUser,
        resolvedUsername: String,
        email: String? = parseEmailAddress(oidcUser.email)
    ) : this(
        username = resolvedUsername,
        email = email,
        emailConfirmed = email != null,
        enabled = true,
        oidcProviderId = oidcUser.subject
    )
}