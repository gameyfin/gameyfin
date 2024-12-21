package de.grimsi.gameyfin.games.repositories

import de.grimsi.gameyfin.games.entities.Image
import org.springframework.content.commons.store.ContentStore
import org.springframework.stereotype.Repository

@Repository
interface ImageContentStore : ContentStore<Image, String>