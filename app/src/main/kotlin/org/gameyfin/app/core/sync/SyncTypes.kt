package org.gameyfin.app.core.sync

import java.time.Duration

enum class EntityEventType {
    CREATED,
    UPDATED,
    DELETED
}

/**
 * Entities are timestamped at flush time but only become visible on commit. Delta syncs therefore re-deliver
 * everything updated shortly before the requested timestamp, so slow transactions cannot slip through the gap.
 * Clients upsert idempotently, so overlapping deliveries are harmless.
 */
val SYNC_SAFETY_WINDOW: Duration = Duration.ofSeconds(30)
