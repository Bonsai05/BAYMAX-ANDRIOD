package com.baymax.android.data.permissions

import com.baymax.android.core.storage.BaymaxDatabase
import com.baymax.android.core.storage.StoredCapabilityGrant
import java.util.UUID

data class CapabilityGrant(
    val grantId: String = UUID.randomUUID().toString(),
    val subjectDeviceId: String,
    val capability: String,
    val allowedRoots: String = "C:\\Users\\Public\\Documents",
    val constraintsJson: String = "{\"max_files\": 500, \"max_bytes\": 1073741824}",
    val approvalMode: String = "AUTONOMOUS",
    val expiresAt: Long = System.currentTimeMillis() + 86400000L * 30, // 30 days
    val revokedAt: Long? = null
) {
    val isRevoked: Boolean
        get() = revokedAt != null || System.currentTimeMillis() > expiresAt
}

class CapabilityRepository(private val database: BaymaxDatabase) {

    fun grantCapability(
        subjectDeviceId: String,
        capability: String,
        allowedRoots: String = "C:\\Users\\Public\\Documents",
        approvalMode: String = "AUTONOMOUS"
    ): CapabilityGrant {
        val grant = CapabilityGrant(
            subjectDeviceId = subjectDeviceId,
            capability = capability,
            allowedRoots = allowedRoots,
            approvalMode = approvalMode
        )

        database.saveCapabilityGrant(
            StoredCapabilityGrant(
                grantId = grant.grantId,
                subjectDeviceId = grant.subjectDeviceId,
                capability = grant.capability,
                allowedRoots = grant.allowedRoots,
                constraintsJson = grant.constraintsJson,
                approvalMode = grant.approvalMode,
                expiresAt = grant.expiresAt,
                revokedAt = grant.revokedAt
            )
        )

        return grant
    }

    fun getGrants(): List<CapabilityGrant> {
        return database.getAllCapabilityGrants().map { stored ->
            CapabilityGrant(
                grantId = stored.grantId,
                subjectDeviceId = stored.subjectDeviceId,
                capability = stored.capability,
                allowedRoots = stored.allowedRoots,
                constraintsJson = stored.constraintsJson,
                approvalMode = stored.approvalMode,
                expiresAt = stored.expiresAt,
                revokedAt = stored.revokedAt
            )
        }
    }

    fun revokeGrant(grantId: String): Boolean {
        val grants = getGrants()
        val grant = grants.firstOrNull { it.grantId == grantId } ?: return false
        val revoked = grant.copy(revokedAt = System.currentTimeMillis())
        database.saveCapabilityGrant(
            StoredCapabilityGrant(
                grantId = revoked.grantId,
                subjectDeviceId = revoked.subjectDeviceId,
                capability = revoked.capability,
                allowedRoots = revoked.allowedRoots,
                constraintsJson = revoked.constraintsJson,
                approvalMode = revoked.approvalMode,
                expiresAt = revoked.expiresAt,
                revokedAt = revoked.revokedAt
            )
        )
        return true
    }
}
