package com.example.devicemanagement.management

/**
 * Evaluates a freshly obtained validation result for immediate policy mutation.
 *
 * This deliberately accepts a validation value rather than caching one. The caller
 * must invoke the provider after obtaining infrastructure and immediately before
 * entering the capability-specific setter/read-back branch.
 */
internal object DeviceOwnerMutationGuard {
    fun denialReason(validation: DeviceOwnerValidation): String? {
        val status = validation.managementStatus
        return when {
            validation.result != DeviceOwnerValidationResult.VERIFIED_DEVICE_OWNER ->
                "device_owner_not_verified:${validation.result.name}"
            !status.isPolicyServiceAvailable -> "policy_service_unavailable"
            status.mode != ManagementMode.DEVICE_OWNER ||
                !status.isDeviceOwner ||
                status.isProfileOwner -> "management_state_inconsistent"
            !status.isExpectedAdminReceiverRegistered -> "admin_receiver_not_registered"
            !status.isAdminActive -> "expected_admin_not_active"
            validation.expectedAdminReceiverComponent !in
                validation.registeredSentinelAdminComponents ->
                "expected_admin_component_mismatch"
            else -> null
        }
    }
}
