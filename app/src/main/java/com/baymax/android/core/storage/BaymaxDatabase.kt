package com.baymax.android.core.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

data class StoredEvent(
    val id: String,
    val payload: EncryptedPayload,
    val createdAt: Long,
    val syncStatus: String,
    val attempts: Int = 0,
    val nextRetryAt: Long = 0L,
    val lastError: String? = null
)

data class StoredMemory(
    val memoryId: String,
    val kind: String,
    val trustTier: String,
    val content: String,
    val sourceDevice: String,
    val project: String,
    val createdAt: Long,
    val syncStatus: String
)

data class StoredTaskCheckpoint(
    val taskId: String,
    val title: String,
    val currentStep: String,
    val tokenCount: Int,
    val tokenLimit: Int,
    val contextPackJson: String,
    val lastNodeId: String,
    val status: String,
    val updatedAt: Long,
    val syncStatus: String
)

data class StoredPairedDevice(
    val deviceId: String,
    val deviceName: String,
    val platform: String,
    val publicKey: String,
    val protocolVersion: String,
    val pairedAt: Long,
    val revokedAt: Long?,
    val lastSeenAt: Long,
    val endpoint: String
)

data class StoredAgentTask(
    val taskId: String,
    val originDeviceId: String,
    val targetDeviceId: String,
    val userInstruction: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val planVersion: Int,
    val planHash: String,
    val currentStep: String,
    val idempotencyKey: String
)

data class StoredCapabilityGrant(
    val grantId: String,
    val subjectDeviceId: String,
    val capability: String,
    val allowedRoots: String,
    val constraintsJson: String,
    val approvalMode: String,
    val expiresAt: Long,
    val revokedAt: Long?
)

class BaymaxDatabase(
    context: Context,
    databaseName: String = DEFAULT_DATABASE_NAME
) : SQLiteOpenHelper(
    context.applicationContext,
    databaseName,
    null,
    4
) {

    companion object {
        private const val TABLE_EVENTS = "local_events"
        private const val ID = "event_id"
        private const val PAYLOAD = "payload"
        private const val IV = "iv"
        private const val CREATED = "created_at"
        private const val STATUS = "sync_status"
        private const val ATTEMPTS = "attempt_count"
        private const val NEXT_RETRY = "next_retry_at"
        private const val LAST_ERROR = "last_error"

        private const val TABLE_MEMORIES = "persistent_memories"
        private const val TABLE_CHECKPOINTS = "task_checkpoints"

        private const val TABLE_PAIRED_DEVICES = "paired_devices"
        private const val TABLE_AGENT_TASKS = "agent_tasks"
        private const val TABLE_CAPABILITY_GRANTS = "capability_grants"

        const val DEFAULT_DATABASE_NAME = "baymax_local.db"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_EVENTS (
                $ID TEXT PRIMARY KEY,
                $PAYLOAD BLOB NOT NULL,
                $IV BLOB NOT NULL,
                $CREATED INTEGER NOT NULL,
                $STATUS TEXT NOT NULL
                    CHECK ($STATUS IN ('PENDING', 'SYNCED', 'FAILED')),
                $ATTEMPTS INTEGER NOT NULL DEFAULT 0,
                $NEXT_RETRY INTEGER NOT NULL DEFAULT 0,
                $LAST_ERROR TEXT
            )
            """.trimIndent()
        )

        createMemoryTables(db)
        createSection6Tables(db)
        createIndexes(db)
    }

    private fun createMemoryTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_MEMORIES (
                memory_id TEXT PRIMARY KEY,
                kind TEXT NOT NULL,
                trust_tier TEXT NOT NULL,
                content TEXT NOT NULL,
                source_device TEXT NOT NULL,
                project TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                sync_status TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_CHECKPOINTS (
                task_id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                current_step TEXT NOT NULL,
                token_count INTEGER NOT NULL,
                token_limit INTEGER NOT NULL,
                context_pack_json TEXT NOT NULL,
                last_node_id TEXT NOT NULL,
                status TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                sync_status TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun createSection6Tables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_PAIRED_DEVICES (
                device_id TEXT PRIMARY KEY,
                device_name TEXT NOT NULL,
                platform TEXT NOT NULL,
                public_key TEXT NOT NULL,
                protocol_version TEXT NOT NULL,
                paired_at INTEGER NOT NULL,
                revoked_at INTEGER,
                last_seen_at INTEGER NOT NULL,
                endpoint TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_AGENT_TASKS (
                task_id TEXT PRIMARY KEY,
                origin_device_id TEXT NOT NULL,
                target_device_id TEXT NOT NULL,
                user_instruction TEXT NOT NULL,
                status TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                plan_version INTEGER NOT NULL,
                plan_hash TEXT NOT NULL,
                current_step TEXT NOT NULL,
                idempotency_key TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_CAPABILITY_GRANTS (
                grant_id TEXT PRIMARY KEY,
                subject_device_id TEXT NOT NULL,
                capability TEXT NOT NULL,
                allowed_roots TEXT NOT NULL,
                constraints_json TEXT NOT NULL,
                approval_mode TEXT NOT NULL,
                expires_at INTEGER NOT NULL,
                revoked_at INTEGER
            )
            """.trimIndent()
        )
    }

    private fun createIndexes(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS idx_events_status_retry
            ON $TABLE_EVENTS ($STATUS, $NEXT_RETRY, $CREATED)
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS idx_memories_kind_tier
            ON $TABLE_MEMORIES (kind, trust_tier, created_at)
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS idx_agent_tasks_status
            ON $TABLE_AGENT_TASKS (status, created_at)
            """.trimIndent()
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        if (oldVersion < 2) {
            db.execSQL(
                "ALTER TABLE $TABLE_EVENTS ADD COLUMN $ATTEMPTS INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "ALTER TABLE $TABLE_EVENTS ADD COLUMN $NEXT_RETRY INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "ALTER TABLE $TABLE_EVENTS ADD COLUMN $LAST_ERROR TEXT"
            )
        }
        if (oldVersion < 3) {
            createMemoryTables(db)
        }
        if (oldVersion < 4) {
            createSection6Tables(db)
        }
        createIndexes(db)
    }

    fun insertEvent(event: StoredEvent) {
        val values = ContentValues().apply {
            put(ID, event.id)
            put(PAYLOAD, event.payload.ciphertext)
            put(IV, event.payload.iv)
            put(CREATED, event.createdAt)
            put(STATUS, event.syncStatus)
            put(ATTEMPTS, event.attempts)
            put(NEXT_RETRY, event.nextRetryAt)
            put(LAST_ERROR, event.lastError)
        }

        writableDatabase.insertOrThrow(TABLE_EVENTS, null, values)
    }

    fun getAllEvents(): List<StoredEvent> {
        val events = mutableListOf<StoredEvent>()

        readableDatabase.query(
            TABLE_EVENTS,
            arrayOf(
                ID, PAYLOAD, IV, CREATED, STATUS,
                ATTEMPTS, NEXT_RETRY, LAST_ERROR
            ),
            null, null, null, null,
            "$CREATED DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                events += cursor.toStoredEvent()
            }
        }

        return events
    }

    fun getDuePendingEvents(
        now: Long,
        limit: Int = 50
    ): List<StoredEvent> {
        require(limit > 0)

        val events = mutableListOf<StoredEvent>()

        readableDatabase.query(
            TABLE_EVENTS,
            arrayOf(
                ID, PAYLOAD, IV, CREATED, STATUS,
                ATTEMPTS, NEXT_RETRY, LAST_ERROR
            ),
            "$STATUS = ? AND $NEXT_RETRY <= ?",
            arrayOf("PENDING", now.toString()),
            null,
            null,
            "$CREATED ASC",
            limit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                events += cursor.toStoredEvent()
            }
        }

        return events
    }

    private fun android.database.Cursor.toStoredEvent() =
        StoredEvent(
            id = getString(0),
            payload = EncryptedPayload(
                ciphertext = getBlob(1),
                iv = getBlob(2)
            ),
            createdAt = getLong(3),
            syncStatus = getString(4),
            attempts = getInt(5),
            nextRetryAt = getLong(6),
            lastError = if (isNull(7)) null else getString(7)
        )

    fun updateSyncStatus(
        eventId: String,
        status: String
    ): Boolean {
        require(status in setOf("PENDING", "SYNCED", "FAILED"))

        val values = ContentValues().apply {
            put(STATUS, status)
            if (status == "SYNCED") {
                put(LAST_ERROR, null as String?)
            }
        }

        return writableDatabase.update(
            TABLE_EVENTS, values, "$ID = ?", arrayOf(eventId)
        ) == 1
    }

    fun recordRetry(
        eventId: String,
        attempts: Int,
        nextRetryAt: Long,
        error: String
    ): Boolean {
        val values = ContentValues().apply {
            put(ATTEMPTS, attempts)
            put(NEXT_RETRY, nextRetryAt)
            put(LAST_ERROR, error.take(500))
        }

        return writableDatabase.update(
            TABLE_EVENTS, values, "$ID = ?", arrayOf(eventId)
        ) == 1
    }

    // --- Persistent Memory Storage ---
    fun insertMemory(mem: StoredMemory) {
        val values = ContentValues().apply {
            put("memory_id", mem.memoryId)
            put("kind", mem.kind)
            put("trust_tier", mem.trustTier)
            put("content", mem.content)
            put("source_device", mem.sourceDevice)
            put("project", mem.project)
            put("created_at", mem.createdAt)
            put("sync_status", mem.syncStatus)
        }
        writableDatabase.insertWithOnConflict(
            TABLE_MEMORIES,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getAllMemories(): List<StoredMemory> {
        val list = mutableListOf<StoredMemory>()
        readableDatabase.query(
            TABLE_MEMORIES,
            arrayOf("memory_id", "kind", "trust_tier", "content", "source_device", "project", "created_at", "sync_status"),
            null, null, null, null, "created_at DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list += StoredMemory(
                    memoryId = cursor.getString(0),
                    kind = cursor.getString(1),
                    trustTier = cursor.getString(2),
                    content = cursor.getString(3),
                    sourceDevice = cursor.getString(4),
                    project = cursor.getString(5),
                    createdAt = cursor.getLong(6),
                    syncStatus = cursor.getString(7)
                )
            }
        }
        return list
    }

    // --- Task Checkpoints & Handoff ---
    fun saveTaskCheckpoint(cp: StoredTaskCheckpoint) {
        val values = ContentValues().apply {
            put("task_id", cp.taskId)
            put("title", cp.title)
            put("current_step", cp.currentStep)
            put("token_count", cp.tokenCount)
            put("token_limit", cp.tokenLimit)
            put("context_pack_json", cp.contextPackJson)
            put("last_node_id", cp.lastNodeId)
            put("status", cp.status)
            put("updated_at", cp.updatedAt)
            put("sync_status", cp.syncStatus)
        }
        writableDatabase.insertWithOnConflict(
            TABLE_CHECKPOINTS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getAllTaskCheckpoints(): List<StoredTaskCheckpoint> {
        val list = mutableListOf<StoredTaskCheckpoint>()
        readableDatabase.query(
            TABLE_CHECKPOINTS,
            arrayOf("task_id", "title", "current_step", "token_count", "token_limit", "context_pack_json", "last_node_id", "status", "updated_at", "sync_status"),
            null, null, null, null, "updated_at DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list += StoredTaskCheckpoint(
                    taskId = cursor.getString(0),
                    title = cursor.getString(1),
                    currentStep = cursor.getString(2),
                    tokenCount = cursor.getInt(3),
                    tokenLimit = cursor.getInt(4),
                    contextPackJson = cursor.getString(5),
                    lastNodeId = cursor.getString(6),
                    status = cursor.getString(7),
                    updatedAt = cursor.getLong(8),
                    syncStatus = cursor.getString(9)
                )
            }
        }
        return list
    }

    // --- Section 6 Paired Devices ---
    fun savePairedDevice(dev: StoredPairedDevice) {
        val values = ContentValues().apply {
            put("device_id", dev.deviceId)
            put("device_name", dev.deviceName)
            put("platform", dev.platform)
            put("public_key", dev.publicKey)
            put("protocol_version", dev.protocolVersion)
            put("paired_at", dev.pairedAt)
            put("revoked_at", dev.revokedAt)
            put("last_seen_at", dev.lastSeenAt)
            put("endpoint", dev.endpoint)
        }
        writableDatabase.insertWithOnConflict(
            TABLE_PAIRED_DEVICES,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getAllPairedDevices(): List<StoredPairedDevice> {
        val list = mutableListOf<StoredPairedDevice>()
        readableDatabase.query(
            TABLE_PAIRED_DEVICES,
            arrayOf("device_id", "device_name", "platform", "public_key", "protocol_version", "paired_at", "revoked_at", "last_seen_at", "endpoint"),
            null, null, null, null, "last_seen_at DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list += StoredPairedDevice(
                    deviceId = cursor.getString(0),
                    deviceName = cursor.getString(1),
                    platform = cursor.getString(2),
                    publicKey = cursor.getString(3),
                    protocolVersion = cursor.getString(4),
                    pairedAt = cursor.getLong(5),
                    revokedAt = if (cursor.isNull(6)) null else cursor.getLong(6),
                    lastSeenAt = cursor.getLong(7),
                    endpoint = cursor.getString(8)
                )
            }
        }
        return list
    }

    // --- Section 6 Agent Tasks ---
    fun saveAgentTask(task: StoredAgentTask) {
        val values = ContentValues().apply {
            put("task_id", task.taskId)
            put("origin_device_id", task.originDeviceId)
            put("target_device_id", task.targetDeviceId)
            put("user_instruction", task.userInstruction)
            put("status", task.status)
            put("created_at", task.createdAt)
            put("updated_at", task.updatedAt)
            put("plan_version", task.planVersion)
            put("plan_hash", task.planHash)
            put("current_step", task.currentStep)
            put("idempotency_key", task.idempotencyKey)
        }
        writableDatabase.insertWithOnConflict(
            TABLE_AGENT_TASKS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getAllAgentTasks(): List<StoredAgentTask> {
        val list = mutableListOf<StoredAgentTask>()
        readableDatabase.query(
            TABLE_AGENT_TASKS,
            arrayOf("task_id", "origin_device_id", "target_device_id", "user_instruction", "status", "created_at", "updated_at", "plan_version", "plan_hash", "current_step", "idempotency_key"),
            null, null, null, null, "created_at DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list += StoredAgentTask(
                    taskId = cursor.getString(0),
                    originDeviceId = cursor.getString(1),
                    targetDeviceId = cursor.getString(2),
                    userInstruction = cursor.getString(3),
                    status = cursor.getString(4),
                    createdAt = cursor.getLong(5),
                    updatedAt = cursor.getLong(6),
                    planVersion = cursor.getInt(7),
                    planHash = cursor.getString(8),
                    currentStep = cursor.getString(9),
                    idempotencyKey = cursor.getString(10)
                )
            }
        }
        return list
    }

    // --- Section 6 Capability Grants ---
    fun saveCapabilityGrant(grant: StoredCapabilityGrant) {
        val values = ContentValues().apply {
            put("grant_id", grant.grantId)
            put("subject_device_id", grant.subjectDeviceId)
            put("capability", grant.capability)
            put("allowed_roots", grant.allowedRoots)
            put("constraints_json", grant.constraintsJson)
            put("approval_mode", grant.approvalMode)
            put("expires_at", grant.expiresAt)
            put("revoked_at", grant.revokedAt)
        }
        writableDatabase.insertWithOnConflict(
            TABLE_CAPABILITY_GRANTS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getAllCapabilityGrants(): List<StoredCapabilityGrant> {
        val list = mutableListOf<StoredCapabilityGrant>()
        readableDatabase.query(
            TABLE_CAPABILITY_GRANTS,
            arrayOf("grant_id", "subject_device_id", "capability", "allowed_roots", "constraints_json", "approval_mode", "expires_at", "revoked_at"),
            null, null, null, null, "expires_at DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list += StoredCapabilityGrant(
                    grantId = cursor.getString(0),
                    subjectDeviceId = cursor.getString(1),
                    capability = cursor.getString(2),
                    allowedRoots = cursor.getString(3),
                    constraintsJson = cursor.getString(4),
                    approvalMode = cursor.getString(5),
                    expiresAt = cursor.getLong(6),
                    revokedAt = if (cursor.isNull(7)) null else cursor.getLong(7)
                )
            }
        }
        return list
    }

    fun newEventId(): String = UUID.randomUUID().toString()
}
