package de.grimsi.gameyfin.users.entities

import jakarta.persistence.*
import jakarta.validation.constraints.NotNull


@Entity
class Role(
    @NotNull
    @Column(unique = true)
    var rolename: String,

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,

    @ManyToMany(mappedBy = "roles", fetch = FetchType.EAGER)
    var users: Collection<User> = emptyList()
)