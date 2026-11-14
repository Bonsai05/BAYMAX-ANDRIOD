package com.baymax.android.data.tasks

import android.util.Log
import com.baymax.android.core.network.MobileApiClient
import com.baymax.android.core.storage.BaymaxDatabase
import com.baymax.android.core.storage.StoredAgentTask
import java.security.MessageDigest
import java.util.UUID

enum class AgentTaskStatus {
    RECEIVED,
    UNDERSTANDING,
    PLANNING,
    VALIDATING,
    WAITING_APPROVAL,
    QUEUED,
    RUNNING,
    VERIFYING,
    COMPLETED,
    PARTIAL,
    FAILED,
    CANCELLED,
    DENIED
}

data class AgentTask(
    val taskId: String = UUID.randomUUID().toString(),
    val originDeviceId: String,
    val targetDeviceId: String,
    val userInstruction: String,
    val status: AgentTaskStatus = AgentTaskStatus.RECEIVED,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val planVersion: Int = 1,
    val planHash: String = "",
    val currentStep: String = "Queued for submission",
    val idempotencyKey: String = UUID.randomUUID().toString()
)

class TaskRepository(
    private val database: BaymaxDatabase,
    private val apiClient: MobileApiClient? = null
) {

    var onTaskUpdated: (() -> Unit)? = null

    companion object {
        private const val TAG = "BAYMAX_TASK"
        private const val WORKSPACE_PATH = "C:\\Users\\suraj\\Downloads\\WORKENIVORNMENT"
    }

    fun submitTask(
        instruction: String,
        originDeviceId: String,
        targetDeviceId: String,
        endpoint: String? = null
    ): AgentTask {
        val planHash = computeHash(instruction)

        // Automatically format path to Windows workspace if unspecified
        val formattedInstruction = if (instruction.contains("file", ignoreCase = true) && !instruction.contains("C:\\", ignoreCase = true)) {
            "$instruction in $WORKSPACE_PATH"
        } else {
            instruction
        }

        val task = AgentTask(
            originDeviceId = originDeviceId,
            targetDeviceId = targetDeviceId,
            userInstruction = formattedInstruction,
            planHash = planHash
        )

        database.saveAgentTask(
            StoredAgentTask(
                taskId = task.taskId,
                originDeviceId = task.originDeviceId,
                targetDeviceId = task.targetDeviceId,
                userInstruction = task.userInstruction,
                status = task.status.name,
                createdAt = task.createdAt,
                updatedAt = task.updatedAt,
                planVersion = task.planVersion,
                planHash = task.planHash,
                currentStep = task.currentStep,
                idempotencyKey = task.idempotencyKey
            )
        )
        onTaskUpdated?.invoke()

        if (apiClient != null && !endpoint.isNullOrBlank()) {
            Log.d(TAG, "Dispatching live signed POST /mobile/tasks to $endpoint...")
            val result = apiClient.submitSignedTask(
                endpoint = endpoint,
                deviceId = originDeviceId,
                userInstruction = formattedInstruction,
                idempotencyKey = task.idempotencyKey
            )
            result.onSuccess { taskResponse ->
                Log.d(TAG, "Task submitted successfully to Windows AgentHost! Server TaskID: ${taskResponse.taskId}, Status: ${taskResponse.status}")

                rebindTaskId(task.taskId, taskResponse.taskId, AgentTaskStatus.QUEUED, "Queued on Windows host (${taskResponse.status})")

                startPollingTaskStatus(endpoint, originDeviceId, taskResponse.taskId)
            }.onFailure { err ->
                Log.e(TAG, "Live network submission failed: ${err.message}. Task queued locally in Outbox.")
            }
        }

        return task
    }

    fun startPollingTaskStatus(endpoint: String, deviceId: String, taskId: String) {
        val client = apiClient ?: return
        Thread {
            var isTerminal = false
            var attempts = 0
            val maxAttempts = 40 // Poll for 80 seconds (40 * 2s)

            while (!isTerminal && attempts < maxAttempts) {
                try {
                    Thread.sleep(2000)
                } catch (_: InterruptedException) {
                    break
                }
                attempts++

                val result = client.pollTaskStatus(endpoint, deviceId, taskId)
                result.onSuccess { res ->
                    Log.d(TAG, "Poll #$attempts GET /mobile/tasks/$taskId status: ${res.status}")
                    val mappedStatus = when (res.status.lowercase()) {
                        "running" -> AgentTaskStatus.RUNNING
                        "completed" -> AgentTaskStatus.COMPLETED
                        "failed" -> AgentTaskStatus.FAILED
                        else -> AgentTaskStatus.QUEUED
                    }

                    if (mappedStatus == AgentTaskStatus.COMPLETED || mappedStatus == AgentTaskStatus.FAILED) {
                        isTerminal = true
                    }

                    updateTaskStatus(taskId, mappedStatus, "Windows Host status: ${res.status}")
                }.onFailure { err ->
                    Log.w(TAG, "Poll attempt $attempts error: ${err.message}")
                }
            }
        }.start()
    }

    private fun rebindTaskId(oldTaskId: String, newTaskId: String, status: AgentTaskStatus, step: String) {
        val tasks = getAllTasks()
        val task = tasks.firstOrNull { it.taskId == oldTaskId } ?: return
        val updated = task.copy(
            taskId = newTaskId,
            status = status,
            currentStep = step,
            updatedAt = System.currentTimeMillis()
        )
        database.saveAgentTask(
            StoredAgentTask(
                taskId = updated.taskId,
                originDeviceId = updated.originDeviceId,
                targetDeviceId = updated.targetDeviceId,
                userInstruction = updated.userInstruction,
                status = updated.status.name,
                createdAt = updated.createdAt,
                updatedAt = updated.updatedAt,
                planVersion = updated.planVersion,
                planHash = updated.planHash,
                currentStep = updated.currentStep,
                idempotencyKey = updated.idempotencyKey
            )
        )
        onTaskUpdated?.invoke()
    }

    fun getAllTasks(): List<AgentTask> {
        return database.getAllAgentTasks().map { stored ->
            AgentTask(
                taskId = stored.taskId,
                originDeviceId = stored.originDeviceId,
                targetDeviceId = stored.targetDeviceId,
                userInstruction = stored.userInstruction,
                status = AgentTaskStatus.entries.firstOrNull { it.name == stored.status } ?: AgentTaskStatus.RECEIVED,
                createdAt = stored.createdAt,
                updatedAt = stored.updatedAt,
                planVersion = stored.planVersion,
                planHash = stored.planHash,
                currentStep = stored.currentStep,
                idempotencyKey = stored.idempotencyKey
            )
        }
    }

    fun updateTaskStatus(taskId: String, status: AgentTaskStatus, step: String? = null): Boolean {
        val tasks = getAllTasks()
        val task = tasks.firstOrNull { it.taskId == taskId } ?: return false
        val updated = task.copy(
            status = status,
            currentStep = step ?: task.currentStep,
            updatedAt = System.currentTimeMillis()
        )
        database.saveAgentTask(
            StoredAgentTask(
                taskId = updated.taskId,
                originDeviceId = updated.originDeviceId,
                targetDeviceId = updated.targetDeviceId,
                userInstruction = updated.userInstruction,
                status = updated.status.name,
                createdAt = updated.createdAt,
                updatedAt = updated.updatedAt,
                planVersion = updated.planVersion,
                planHash = updated.planHash,
                currentStep = updated.currentStep,
                idempotencyKey = updated.idempotencyKey
            )
        )
        onTaskUpdated?.invoke()
        return true
    }

    private fun computeHash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }.take(16)
    }
}
