package org.gameyfin.app.libraries.entities

import jakarta.persistence.Embeddable

@Embeddable
class LibraryMetadata(
    val displayOnHomepage: Boolean = true,
    val displayOrder: Int = -1
)