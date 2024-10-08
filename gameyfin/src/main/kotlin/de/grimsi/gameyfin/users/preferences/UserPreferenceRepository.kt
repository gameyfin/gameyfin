package de.grimsi.gameyfin.users.preferences

import org.springframework.data.jpa.repository.JpaRepository

interface UserPreferenceRepository : JpaRepository<UserPreference, UserPreferenceKey>