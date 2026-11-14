package com.baymax.android.core.sync

import com.baymax.android.core.storage.EventItem
import com.baymax.android.core.storage.EventRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SyncSessionStatus(
    val isConnected: Boolean,
    val pendingCount: Int,
    val syncedCount: Int,
    val failedCount: Int,
    val lastSyncTimeMs: Long
)

class GrpcSyncClient(
    private val repository: EventRepository,
    private var transport: SyncTransport? = null
) {

    fun setTransport(transport: SyncTransport) {
        this.transport = transport
        SyncTransportRegistry.transport = transport
    }

    suspend fun syncNow(): OutboxProcessResult = withContext(Dispatchers.IO) {
        val activeTransport = transport ?: SyncTransportRegistry.transport
        if (activeTransport == null) {
            return@withContext OutboxProcessResult.RETRY_SCHEDULED
        }
        val processor = OutboxSyncProcessor(repository, activeTransport)
        return@withContext processor.process()
    }

    fun getSessionStatus(): SyncSessionStatus {
        val all = repository.getEvents()
        val pending = all.count { it.syncStatus == "PENDING" }
        val synced = all.count { it.syncStatus == "SYNCED" }
        val failed = all.count { it.syncStatus == "FAILED" }

        return SyncSessionStatus(
            isConnected = (transport != null || SyncTransportRegistry.transport != null),
            pendingCount = pending,
            syncedCount = synced,
            failedCount = failed,
            lastSyncTimeMs = System.currentTimeMillis()
        )
    }
}
