package org.gameyfin.app.games.repositories

import org.gameyfin.app.media.Image
import org.springframework.content.commons.store.ContentStore
import org.springframework.stereotype.Repository

@Repository
interface ImageContentStore : ContentStore<Image, String>