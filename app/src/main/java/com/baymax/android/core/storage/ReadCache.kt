package com.baymax.android.core.storage

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory read cache to ensure instant UI rendering on Android nodes
 * even during process restarts or background sync operations.
 */
class ReadCache {

    private val cachedEvents = ConcurrentHashMap<String, EventItem>()
    @Volatile
    private var lastCacheUpdate: Long = 0L

    fun updateEvents(events: List<EventItem>) {
        cachedEvents.clear()
        events.forEach { event ->
            cachedEvents[event.id] = event
        }
        lastCacheUpdate = System.currentTimeMillis()
    }

    fun getCachedEvents(): List<EventItem> {
        return cachedEvents.values.sortedByDescending { it.createdAt }
    }

    fun getEvent(id: String): EventItem? {
        return cachedEvents[id]
    }

    fun putEvent(event: EventItem) {
        cachedEvents[event.id] = event
        lastCacheUpdate = System.currentTimeMillis()
    }

    fun getLastUpdatedTimestamp(): Long = lastCacheUpdate

    fun clear() {
        cachedEvents.clear()
        lastCacheUpdate = 0L
    }
}
