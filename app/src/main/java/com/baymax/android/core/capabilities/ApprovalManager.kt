package com.baymax.android.core.capabilities

import java.util.UUID

enum class ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    EXPIRED
}

data class ApprovalRequest(
    val id: String = UUID.randomUUID().toString(),
    val capabilityId: String,
    val capabilityName: String,
    val riskLevel: RiskLevel,
    val description: String,
    val requestedBy: String = "BAYMAX Core Agent",
    val requestedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 300_000L, // 5 minute default expiry
    var status: ApprovalStatus = ApprovalStatus.PENDING
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() > expiresAt
}

class ApprovalManager {

    private val requests = mutableListOf<ApprovalRequest>()

    fun submitRequest(
        capabilityId: String,
        description: String,
        requestedBy: String = "BAYMAX Core Agent"
    ): ApprovalRequest {
        val capability = CapabilityRegistry.getCapability(capabilityId)
        val request = ApprovalRequest(
            capabilityId = capabilityId,
            capabilityName = capability?.name ?: capabilityId,
            riskLevel = capability?.riskLevel ?: RiskLevel.HIGH,
            description = description,
            requestedBy = requestedBy
        )
        synchronized(requests) {
            requests.add(0, request)
        }
        return request
    }

    fun getPendingRequests(): List<ApprovalRequest> {
        synchronized(requests) {
            val now = System.currentTimeMillis()
            requests.forEach { req ->
                if (req.status == ApprovalStatus.PENDING && now > req.expiresAt) {
                    req.status = ApprovalStatus.EXPIRED
                }
            }
            return requests.filter { it.status == ApprovalStatus.PENDING }
        }
    }

    fun getAllRequests(): List<ApprovalRequest> {
        synchronized(requests) {
            return requests.toList()
        }
    }

    fun approve(requestId: String): Boolean {
        synchronized(requests) {
            val req = requests.firstOrNull { it.id == requestId } ?: return false
            if (req.status == ApprovalStatus.PENDING && !req.isExpired) {
                req.status = ApprovalStatus.APPROVED
                return true
            }
            return false
        }
    }

    fun reject(requestId: String): Boolean {
        synchronized(requests) {
            val req = requests.firstOrNull { it.id == requestId } ?: return false
            if (req.status == ApprovalStatus.PENDING) {
                req.status = ApprovalStatus.REJECTED
                return true
            }
            return false
        }
    }
}
