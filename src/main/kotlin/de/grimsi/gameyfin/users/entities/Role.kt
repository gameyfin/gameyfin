package de.grimsi.gameyfin.users.entities

import jakarta.persistence.*
import jakarta.validation.constraints.NotNull


@Entity
class Role(
    @NotNull
    var rolename: String,

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,

    @ManyToMany(mappedBy = "roles")
    var users: Collection<User> = emptyList()
)