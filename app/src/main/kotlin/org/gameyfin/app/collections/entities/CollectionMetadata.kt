package org.gameyfin.app.collections.entities

import jakarta.persistence.Embeddable

@Embeddable
class CollectionMetadata(
    val displayOnHomepage: Boolean = true,
    val displayOrder: Int = -1
)