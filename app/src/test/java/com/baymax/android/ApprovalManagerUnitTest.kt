package com.baymax.android

import com.baymax.android.core.capabilities.ApprovalManager
import com.baymax.android.core.capabilities.ApprovalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApprovalManagerUnitTest {

    private lateinit var manager: ApprovalManager

    @Before
    fun setUp() {
        manager = ApprovalManager()
    }

    @Test
    fun submitAndApproveRequest() {
        val req = manager.submitRequest(
            capabilityId = "cap.storage_export",
            description = "Test export request"
        )

        assertEquals(ApprovalStatus.PENDING, req.status)
        assertEquals(1, manager.getPendingRequests().size)

        val approved = manager.approve(req.id)
        assertTrue(approved)
        assertEquals(0, manager.getPendingRequests().size)
        assertEquals(ApprovalStatus.APPROVED, manager.getAllRequests().first().status)
    }

    @Test
    fun rejectRequest() {
        val req = manager.submitRequest(
            capabilityId = "cap.tasker_exec",
            description = "Test automation request"
        )

        val rejected = manager.reject(req.id)
        assertTrue(rejected)
        assertEquals(0, manager.getPendingRequests().size)
        assertEquals(ApprovalStatus.REJECTED, manager.getAllRequests().first().status)
    }
}
