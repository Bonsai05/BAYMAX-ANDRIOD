
package com.baymax.android.core.sync

import com.baymax.android.core.storage.EventRepository

enum class OutboxProcessResult {
    COMPLETED,
    RETRY_SCHEDULED,
    MORE_PENDING,
    PERMANENT_FAILURE
}

class OutboxSyncProcessor(
    private val repository: EventRepository,
    private val transport: SyncTransport
) {

    fun process(now: Long = System.currentTimeMillis()): OutboxProcessResult {
        val pendingEvents = repository.getDuePendingEvents(now = now, limit = 50)
        if (pendingEvents.isEmpty()) {
            return OutboxProcessResult.COMPLETED
        }

        var retryScheduled = false
        var permanentFailure = false
        var processedCount = 0

        for (event in pendingEvents) {
            when (val result = transport.send(event)) {
                is SyncResult.Acknowledged -> {
                    repository.markSynced(event.id)
                    processedCount++
                }
                is SyncResult.RetryableFailure -> {
                    repository.recordRetry(
                        eventId = event.id,
                        attempts = event.attempts + 1,
                        error = result.reason,
                        now = now
                    )
                    retryScheduled = true
                }
                is SyncResult.PermanentFailure -> {
                    repository.markFailed(event.id)
                    permanentFailure = true
                }
            }
        }

        return when {
            permanentFailure -> OutboxProcessResult.PERMANENT_FAILURE
            retryScheduled -> OutboxProcessResult.RETRY_SCHEDULED
            pendingEvents.size == 50 -> OutboxProcessResult.MORE_PENDING
            else -> OutboxProcessResult.COMPLETED
        }
    }
}

