package org.gameyfin.app.collections.entities

import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.FetchType
import java.time.Instant

@Embeddable
class CollectionMetadata(
    val displayOnHomepage: Boolean = true,
    val displayOrder: Int = -1,

    @ElementCollection(fetch = FetchType.EAGER)
    @Column(nullable = false)
    val gamesAddedAt: MutableMap<Long, Instant> = mutableMapOf()
)