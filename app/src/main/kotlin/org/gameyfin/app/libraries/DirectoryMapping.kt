package org.gameyfin.app.libraries

import jakarta.persistence.*

@Entity
class DirectoryMapping(

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,

    @Column(unique = true)
    var internalPath: String,

    var externalPath: String? = null,
)
