package org.gameyfin.app.users.entities

import org.gameyfin.app.games.entities.Image
import jakarta.persistence.*
import org.gameyfin.app.core.Role
import org.gameyfin.app.core.security.EncryptionConverter
import org.springframework.security.oauth2.core.oidc.user.OidcUser


@Entity
@Table(name = "users")
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
    var email: String,

    var emailConfirmed: Boolean = false,

    var enabled: Boolean = false,

    @OneToOne(cascade = [CascadeType.ALL])
    var avatar: Image? = null,

    @ElementCollection(targetClass = Role::class, fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    var roles: List<Role> = emptyList()
) {

    constructor(oidcUser: OidcUser) : this(
        username = oidcUser.preferredUsername,
        email = oidcUser.email,
        emailConfirmed = true,
        enabled = true,
        oidcProviderId = oidcUser.subject
    )
}