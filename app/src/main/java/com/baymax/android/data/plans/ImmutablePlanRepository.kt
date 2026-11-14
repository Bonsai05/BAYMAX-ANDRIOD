package com.baymax.android.data.plans

import com.baymax.android.core.security.KeystoreSigner
import java.security.MessageDigest
import java.util.UUID

enum class PlanStatus {
    PENDING_REVIEW,
    APPROVED,
    DISCARDED,
    EXPIRED,
    EXECUTED
}

data class PlanActionDetail(
    val actionId: String = UUID.randomUUID().toString(),
    val actionType: String, // FS_LIST, FS_READ, FS_COPY, FS_CREATE_DIRECTORY, FS_MOVE, FS_DELETE
    val path: String,
    val isMutation: Boolean,
    val details: String,
    val impactWarning: String? = null
)

data class ImmutablePlan(
    val planId: String = "plan-" + UUID.randomUUID().toString().take(8),
    val planHash: String,
    val userInstruction: String,
    val summary: String,
    val actions: List<PlanActionDetail>,
    val originDeviceId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 300_000L, // 5 minutes exact server expiry
    var status: PlanStatus = PlanStatus.PENDING_REVIEW
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() > expiresAt
}

class ImmutablePlanRepository(private val keystoreSigner: KeystoreSigner) {

    private val plans = mutableListOf<ImmutablePlan>()

    fun createPlanFromInstruction(
        instruction: String,
        originDeviceId: String
    ): ImmutablePlan {
        val actions = parseActionsFromInstruction(instruction)
        val planHash = computePlanHash(instruction, actions)

        val plan = ImmutablePlan(
            planHash = planHash,
            userInstruction = instruction,
            summary = "Generated execution plan with ${actions.size} action(s). Server-side immutable snapshot.",
            actions = actions,
            originDeviceId = originDeviceId
        )

        synchronized(plans) {
            plans.add(0, plan)
        }
        return plan
    }

    fun getPendingPlans(): List<ImmutablePlan> {
        synchronized(plans) {
            val now = System.currentTimeMillis()
            plans.forEach { p ->
                if (p.status == PlanStatus.PENDING_REVIEW && now > p.expiresAt) {
                    p.status = PlanStatus.EXPIRED
                }
            }
            return plans.filter { it.status == PlanStatus.PENDING_REVIEW }
        }
    }

    fun getAllPlans(): List<ImmutablePlan> {
        synchronized(plans) {
            return plans.toList()
        }
    }

    fun approvePlan(planId: String, deviceId: String): String? {
        synchronized(plans) {
            val plan = plans.firstOrNull { it.planId == planId } ?: return null
            if (plan.status == PlanStatus.PENDING_REVIEW && !plan.isExpired) {
                plan.status = PlanStatus.APPROVED
                return keystoreSigner.signApproval(
                    planId = plan.planId,
                    planHash = plan.planHash,
                    decision = "APPROVE",
                    deviceId = deviceId
                )
            }
            return null
        }
    }

    fun discardPlan(planId: String): Boolean {
        synchronized(plans) {
            val plan = plans.firstOrNull { it.planId == planId } ?: return false
            if (plan.status == PlanStatus.PENDING_REVIEW) {
                plan.status = PlanStatus.DISCARDED
                return true
            }
            return false
        }
    }

    private fun parseActionsFromInstruction(instruction: String): List<PlanActionDetail> {
        val list = mutableListOf<PlanActionDetail>()
        val lower = instruction.lowercase()

        if (lower.contains("copy")) {
            list.add(
                PlanActionDetail(
                    actionType = "FS_COPY",
                    path = "C:\\Users\\Public\\Documents\\Work",
                    isMutation = true,
                    details = "Copy matching files into destination directory",
                    impactWarning = "Destination files will be created or updated if existing."
                )
            )
        } else if (lower.contains("list") || lower.contains("show")) {
            list.add(
                PlanActionDetail(
                    actionType = "FS_LIST",
                    path = "C:\\Users\\Public\\Documents",
                    isMutation = false,
                    details = "List folder contents and metadata"
                )
            )
        } else {
            list.add(
                PlanActionDetail(
                    actionType = "FS_READ",
                    path = "C:\\Users\\Public\\Documents\\file.txt",
                    isMutation = false,
                    details = "Read metadata and inspect target file"
                )
            )
        }
        return list
    }

    private fun computePlanHash(instruction: String, actions: List<PlanActionDetail>): String {
        val raw = instruction + actions.joinToString { it.actionType + it.path }
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(raw.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }.take(16)
    }
}
