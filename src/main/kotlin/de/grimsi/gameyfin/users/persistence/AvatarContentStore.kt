package de.grimsi.gameyfin.users.persistence

import de.grimsi.gameyfin.users.entities.Avatar
import org.springframework.content.commons.store.ContentStore
import org.springframework.stereotype.Repository

@Repository
interface AvatarContentStore : ContentStore<Avatar, String>