package com.baymax.android

import com.baymax.android.data.permissions.CapabilityGrant
import com.baymax.android.data.tasks.AgentTaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Section6UnitTest {

    @Test
    fun agentTaskStatusesMatchSection9Plan() {
        val statuses = AgentTaskStatus.entries.map { it.name }
        assertTrue(statuses.contains("RECEIVED"))
        assertTrue(statuses.contains("UNDERSTANDING"))
        assertTrue(statuses.contains("PLANNING"))
        assertTrue(statuses.contains("VALIDATING"))
        assertTrue(statuses.contains("WAITING_APPROVAL"))
        assertTrue(statuses.contains("QUEUED"))
        assertTrue(statuses.contains("RUNNING"))
        assertTrue(statuses.contains("VERIFYING"))
        assertTrue(statuses.contains("COMPLETED"))
        assertTrue(statuses.contains("PARTIAL"))
        assertTrue(statuses.contains("FAILED"))
        assertTrue(statuses.contains("CANCELLED"))
        assertTrue(statuses.contains("DENIED"))
    }

    @Test
    fun capabilityGrantRevocationCheck() {
        val grant = CapabilityGrant(
            subjectDeviceId = "win-pc-1",
            capability = "FS_COPY",
            allowedRoots = "C:\\Users\\Public\\Documents"
        )
        assertFalse(grant.isRevoked)

        val revokedGrant = grant.copy(revokedAt = System.currentTimeMillis())
        assertTrue(revokedGrant.isRevoked)
    }
}
