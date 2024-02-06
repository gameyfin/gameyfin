package de.grimsi.gameyfin.users.persistence

import de.grimsi.gameyfin.users.entities.Avatar
import org.springframework.content.commons.store.ContentStore

interface AvatarContentStore : ContentStore<Avatar, String>