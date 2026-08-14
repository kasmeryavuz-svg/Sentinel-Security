package com.example.devicemanagement.provisioning

import com.example.devicemanagement.management.DeviceOwnerValidationResult
import org.junit.Assert.assertEquals
import org.junit.Test

class ProvisioningComplianceEvaluatorTest {
    @Test
    fun `compliance succeeds only for verified Sentinel Device Owner`() {
        val decision = ProvisioningComplianceEvaluator.evaluate(
            DeviceOwnerValidationResult.VERIFIED_DEVICE_OWNER,
        )

        assertEquals(
            ProvisioningComplianceDecision.Succeed(
                DeviceOwnerValidationResult.VERIFIED_DEVICE_OWNER,
            ),
            decision,
        )
    }

    @Test
    fun `not Device Owner fails compliance`() {
        assertEquals(
            ProvisioningComplianceDecision.FailClosed(
                DeviceOwnerValidationResult.NOT_DEVICE_OWNER,
            ),
            ProvisioningComplianceEvaluator.evaluate(
                DeviceOwnerValidationResult.NOT_DEVICE_OWNER,
            ),
        )
    }

    @Test
    fun `configuration error fails compliance`() {
        assertEquals(
            ProvisioningComplianceDecision.FailClosed(
                DeviceOwnerValidationResult.CONFIGURATION_ERROR,
            ),
            ProvisioningComplianceEvaluator.evaluate(
                DeviceOwnerValidationResult.CONFIGURATION_ERROR,
            ),
        )
    }

    @Test
    fun `unavailable validation fails compliance`() {
        assertEquals(
            ProvisioningComplianceDecision.FailClosed(
                DeviceOwnerValidationResult.UNAVAILABLE,
            ),
            ProvisioningComplianceEvaluator.evaluate(
                DeviceOwnerValidationResult.UNAVAILABLE,
            ),
        )
    }
}
