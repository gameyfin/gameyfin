package org.gameyfin.app.collections.dto

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class CollectionEventsTest {
    @Test
    fun userEventsCarryType() {
        val dto = CollectionUserDto(1L, Instant.now(), Instant.now(), "N", null, emptyList(), null)
        assertEquals("created", CollectionUserEvent.Created(dto).type)
        assertEquals("updated", CollectionUserEvent.Updated(dto).type)
        assertEquals("deleted", CollectionUserEvent.Deleted(1L).type)
    }

    @Test
    fun adminEventsCarryType() {
        val dto = CollectionAdminDto(1L, Instant.now(), Instant.now(), "N", null, emptyList(), null, null)
        assertEquals("created", CollectionAdminEvent.Created(dto).type)
        assertEquals("updated", CollectionAdminEvent.Updated(dto).type)
        assertEquals("deleted", CollectionAdminEvent.Deleted(1L).type)
    }
}

