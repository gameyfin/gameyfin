package de.grimsi.gameyfin.users.entities

import jakarta.annotation.Nullable
import jakarta.persistence.*
import jakarta.validation.constraints.NotNull


@Entity
@Table(name = "users")
class User(
    @NotNull
    @Column(unique = true)
    var username: String,

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,

    @NotNull
    var password: String,

    @Nullable
    @Column(unique = true)
    var email: String? = null,

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
)