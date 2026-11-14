package com.baymax.android

import com.baymax.android.core.memory.MemoryKind
import com.baymax.android.core.memory.TrustTier
import com.baymax.android.core.task.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryAndHandoffUnitTest {

    @Test
    fun memoryKindAndTrustTierValidation() {
        assertEquals("Working Memory", MemoryKind.WORKING.label)
        assertEquals("T0 - User Confirmed", TrustTier.T0.label)
        assertEquals("T2 - Untrusted Third-Party", TrustTier.T2.label)
    }

    @Test
    fun taskStatusEnumValuesMatchStage12() {
        val statuses = TaskStatus.entries.map { it.name }
        assertTrue(statuses.contains("RUNNING"))
        assertTrue(statuses.contains("HANDOFF_REQUIRED"))
        assertTrue(statuses.contains("RESUMED"))
        assertTrue(statuses.contains("COMPLETED"))
    }
}
