package org.gameyfin.app.users.preferences

import org.springframework.data.jpa.repository.JpaRepository

interface UserPreferenceRepository : JpaRepository<UserPreference, UserPreferenceKey>