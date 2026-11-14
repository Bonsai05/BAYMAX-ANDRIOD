package com.baymax.android.core.task

import com.baymax.android.core.storage.BaymaxDatabase
import com.baymax.android.core.storage.StoredTaskCheckpoint
import java.util.UUID

enum class TaskStatus {
    RUNNING,
    HANDOFF_REQUIRED,
    RESUMED,
    COMPLETED
}

data class TaskCheckpoint(
    val taskId: String = UUID.randomUUID().toString(),
    val title: String,
    val currentStep: String,
    val tokenCount: Int,
    val tokenLimit: Int = 4096,
    val contextPackJson: String,
    val lastNodeId: String,
    val status: TaskStatus = TaskStatus.RUNNING,
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDING"
) {
    val isTokenLimitExceeded: Boolean
        get() = tokenCount >= (tokenLimit * 0.9) // 90% threshold for handoff trigger
}

class TaskContextHandoffManager(private val database: BaymaxDatabase) {

    fun createOrUpdateCheckpoint(
        taskId: String = UUID.randomUUID().toString(),
        title: String,
        currentStep: String,
        tokenCount: Int,
        tokenLimit: Int = 4096,
        conversationHistory: List<String>,
        currentNodeId: String
    ): TaskCheckpoint {
        val contextPackSnippet = buildContextPackJson(taskId, title, currentStep, conversationHistory)
        val status = if (tokenCount >= tokenLimit * 0.9) TaskStatus.HANDOFF_REQUIRED else TaskStatus.RUNNING

        val cp = TaskCheckpoint(
            taskId = taskId,
            title = title,
            currentStep = currentStep,
            tokenCount = tokenCount,
            tokenLimit = tokenLimit,
            contextPackJson = contextPackSnippet,
            lastNodeId = currentNodeId,
            status = status,
            updatedAt = System.currentTimeMillis()
        )

        database.saveTaskCheckpoint(
            StoredTaskCheckpoint(
                taskId = cp.taskId,
                title = cp.title,
                currentStep = cp.currentStep,
                tokenCount = cp.tokenCount,
                tokenLimit = cp.tokenLimit,
                contextPackJson = cp.contextPackJson,
                lastNodeId = cp.lastNodeId,
                status = cp.status.name,
                updatedAt = cp.updatedAt,
                syncStatus = cp.syncStatus
            )
        )

        return cp
    }

    fun getAllCheckpoints(): List<TaskCheckpoint> {
        return database.getAllTaskCheckpoints().map { stored ->
            TaskCheckpoint(
                taskId = stored.taskId,
                title = stored.title,
                currentStep = stored.currentStep,
                tokenCount = stored.tokenCount,
                tokenLimit = stored.tokenLimit,
                contextPackJson = stored.contextPackJson,
                lastNodeId = stored.lastNodeId,
                status = TaskStatus.entries.firstOrNull { it.name == stored.status } ?: TaskStatus.RUNNING,
                updatedAt = stored.updatedAt,
                syncStatus = stored.syncStatus
            )
        }
    }

    fun triggerHandoff(taskId: String, targetNodeId: String): Boolean {
        val cp = getAllCheckpoints().firstOrNull { it.taskId == taskId } ?: return false
        val updatedCp = cp.copy(
            lastNodeId = targetNodeId,
            status = TaskStatus.RESUMED,
            updatedAt = System.currentTimeMillis()
        )
        database.saveTaskCheckpoint(
            StoredTaskCheckpoint(
                taskId = updatedCp.taskId,
                title = updatedCp.title,
                currentStep = updatedCp.currentStep,
                tokenCount = updatedCp.tokenCount,
                tokenLimit = updatedCp.tokenLimit,
                contextPackJson = updatedCp.contextPackJson,
                lastNodeId = updatedCp.lastNodeId,
                status = updatedCp.status.name,
                updatedAt = updatedCp.updatedAt,
                syncStatus = "PENDING"
            )
        )
        return true
    }

    private fun buildContextPackJson(
        taskId: String,
        title: String,
        step: String,
        history: List<String>
    ): String {
        val historySnippet = history.takeLast(10).joinToString("\n- ")
        return """
            {
                "task_id": "$taskId",
                "title": "$title",
                "checkpoint_step": "$step",
                "history_summary": "${historySnippet.replace("\"", "\\\"")}"
            }
        """.trimIndent()
    }
}
