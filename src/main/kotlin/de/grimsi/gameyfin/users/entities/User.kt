package de.grimsi.gameyfin.users.entities

import de.grimsi.gameyfin.meta.Roles
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
    var email: String,

    var email_confirmed: Boolean = false,

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
    var roles: Collection<Role> = emptyList()
) {

    constructor(oidcUser: OidcUser) : this(
        username = oidcUser.preferredUsername,
        email = oidcUser.email,
        oidcProviderId = oidcUser.subject
    ) {
        // FIXME: Implement role mapping from OIDC provider
        this.roles = listOf(Role(Roles.ADMIN.roleName))
    }
}