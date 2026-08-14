package com.example.devicemanagement.provisioning

import com.example.devicemanagement.management.DeviceOwnerValidationResult

sealed class ProvisioningComplianceDecision {
    data class Succeed(val result: DeviceOwnerValidationResult) : ProvisioningComplianceDecision()
    data class FailClosed(val result: DeviceOwnerValidationResult) : ProvisioningComplianceDecision()
}

/**
 * Compliance succeeds only for a verified Sentinel Device Owner.
 *
 * This evaluator does not mutate policy. Not Device Owner, configuration
 * error, and unavailable validation all fail closed.
 */
object ProvisioningComplianceEvaluator {
    fun evaluate(result: DeviceOwnerValidationResult): ProvisioningComplianceDecision {
        return if (result == DeviceOwnerValidationResult.VERIFIED_DEVICE_OWNER) {
            ProvisioningComplianceDecision.Succeed(result)
        } else {
            ProvisioningComplianceDecision.FailClosed(result)
        }
    }
}
