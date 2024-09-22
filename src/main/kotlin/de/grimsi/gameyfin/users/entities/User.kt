package de.grimsi.gameyfin.users.entities

import de.grimsi.gameyfin.core.security.EncryptionConverter
import jakarta.annotation.Nullable
import jakarta.persistence.*
import jakarta.validation.constraints.NotNull
import org.springframework.security.oauth2.core.oidc.user.OidcUser


@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,

    @NotNull
    @Column(unique = true)
    var username: String,

    var password: String? = null,

    var oidcProviderId: String? = null,

    @Nullable
    @Column(unique = true)
    @Convert(converter = EncryptionConverter::class)
    var email: String,

    // TODO: Add email confirmation
    var emailConfirmed: Boolean = true,

    var enabled: Boolean = true,

    @Embedded
    @Nullable
    var avatar: Avatar? = null,

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "users_roles",
        joinColumns = [JoinColumn(name = "user_id", referencedColumnName = "id")],
        inverseJoinColumns = [JoinColumn(name = "role_id", referencedColumnName = "id")]
    )
    var roles: Set<Role> = emptySet()
) {

    constructor(oidcUser: OidcUser) : this(
        username = oidcUser.preferredUsername,
        email = oidcUser.email,
        emailConfirmed = true,
        enabled = true,
        oidcProviderId = oidcUser.subject
    )
}