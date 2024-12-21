package de.grimsi.gameyfin.games.repositories

import de.grimsi.gameyfin.games.entities.Screenshot
import org.springframework.content.commons.store.ContentStore
import org.springframework.stereotype.Repository

@Repository
interface ScreenshotContentStore : ContentStore<Screenshot, String>