package com.baymax.android

import com.baymax.android.core.security.KeystoreSigner
import com.baymax.android.data.plans.ImmutablePlanRepository
import com.baymax.android.data.plans.PlanStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionPlanUnitTest {

    @Test
    fun planCreationAndImmutableHashValidation() {
        val signer = KeystoreSigner()
        val repo = ImmutablePlanRepository(signer)

        val plan = repo.createPlanFromInstruction(
            instruction = "Copy all PDF files from C:\\Users\\Public\\Documents to C:\\Users\\Public\\Documents\\Backup",
            originDeviceId = "android-node-test"
        )

        assertNotNull(plan)
        assertEquals(PlanStatus.PENDING_REVIEW, plan.status)
        assertTrue(plan.planHash.isNotBlank())
        assertFalse(plan.isExpired)
        assertEquals(1, repo.getPendingPlans().size)
    }

    @Test
    fun registrationSignatureGeneration() {
        val signer = KeystoreSigner()
        val signature = signer.signRegistration(
            invitationId = "864bcc35-11c5-404b-89e9-453a93a88db9",
            secret = "O6Y2aNoDouUVWwfp7xkLCy30",
            deviceName = "BONSAI Phone",
            publicKeyBase64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE"
        )
        assertTrue("Signature should be Base64 string", signature.isNotBlank())
    }

    @Test
    fun discardPlanChangesStatusToDiscarded() {
        val signer = KeystoreSigner()
        val repo = ImmutablePlanRepository(signer)

        val plan = repo.createPlanFromInstruction(
            instruction = "List files in directory",
            originDeviceId = "android-node-test"
        )

        val discarded = repo.discardPlan(plan.planId)
        assertTrue(discarded)
        assertEquals(0, repo.getPendingPlans().size)
        assertEquals(PlanStatus.DISCARDED, repo.getAllPlans().first().status)
    }
}
