package de.grimsi.gameyfin.users.entities

import jakarta.annotation.Nullable
import jakarta.persistence.*
import jakarta.validation.constraints.NotNull


@Entity
@Table(name = "users")
class User(
    @field:NotNull
    var username: String,

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,

    @field:NotNull
    var password: String? = null,

    @field:Nullable
    var email: String? = null,

    var enabled: Boolean = true,

    @Embedded
    @field:Nullable
    var avatar: Avatar? = null,

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "users_roles",
        joinColumns = [JoinColumn(name = "user_id", referencedColumnName = "id")],
        inverseJoinColumns = [JoinColumn(name = "role_id", referencedColumnName = "id")]
    )
    var roles: Collection<Role> = emptyList()
)