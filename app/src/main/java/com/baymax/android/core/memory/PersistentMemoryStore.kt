package com.baymax.android.core.memory

import com.baymax.android.core.storage.BaymaxDatabase
import com.baymax.android.core.storage.StoredMemory
import java.util.UUID

enum class MemoryKind(val label: String) {
    WORKING("Working Memory"),
    EPISODIC("Episodic Memory"),
    SEMANTIC("Semantic Memory"),
    PROFILE("User Profile"),
    DEVICE("Device Sensor State")
}

enum class TrustTier(val label: String) {
    T0("T0 - User Confirmed"),
    T1("T1 - BAYMAX System Derived"),
    T2("T2 - Untrusted Third-Party")
}

data class MemoryItem(
    val memoryId: String = UUID.randomUUID().toString(),
    val kind: MemoryKind,
    val trustTier: TrustTier,
    val content: String,
    val sourceDevice: String,
    val project: String = "BAYMAX",
    val createdAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDING"
)

class PersistentMemoryStore(private val database: BaymaxDatabase) {

    fun addMemory(
        content: String,
        kind: MemoryKind = MemoryKind.EPISODIC,
        trustTier: TrustTier = TrustTier.T1,
        sourceDevice: String = "android-node",
        project: String = "BAYMAX"
    ): MemoryItem {
        val item = MemoryItem(
            kind = kind,
            trustTier = trustTier,
            content = content,
            sourceDevice = sourceDevice,
            project = project
        )

        database.insertMemory(
            StoredMemory(
                memoryId = item.memoryId,
                kind = item.kind.name,
                trustTier = item.trustTier.name,
                content = item.content,
                sourceDevice = item.sourceDevice,
                project = item.project,
                createdAt = item.createdAt,
                syncStatus = item.syncStatus
            )
        )

        return item
    }

    fun getAllMemories(): List<MemoryItem> {
        return database.getAllMemories().map { stored ->
            MemoryItem(
                memoryId = stored.memoryId,
                kind = MemoryKind.entries.firstOrNull { it.name == stored.kind } ?: MemoryKind.EPISODIC,
                trustTier = TrustTier.entries.firstOrNull { it.name == stored.trustTier } ?: TrustTier.T1,
                content = stored.content,
                sourceDevice = stored.sourceDevice,
                project = stored.project,
                createdAt = stored.createdAt,
                syncStatus = stored.syncStatus
            )
        }
    }
}
