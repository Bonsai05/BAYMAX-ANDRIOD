package com.baymax.android

import com.baymax.android.core.capabilities.CapabilityRegistry
import com.baymax.android.core.capabilities.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityRegistryUnitTest {

    @Test
    fun registeredCapabilitiesMatchStage6Requirements() {
        val caps = CapabilityRegistry.REGISTERED_CAPABILITIES
        assertTrue("Registered capabilities should not be empty", caps.isNotEmpty())

        val deviceInfo = CapabilityRegistry.getCapability("cap.device_info")
        assertNotNull(deviceInfo)
        assertEquals(RiskLevel.LOW, deviceInfo?.riskLevel)

        val taskerExec = CapabilityRegistry.getCapability("cap.tasker_exec")
        assertNotNull(taskerExec)
        assertEquals(RiskLevel.CRITICAL, taskerExec?.riskLevel)
        assertTrue(taskerExec!!.requiresUserApproval)
    }
}
