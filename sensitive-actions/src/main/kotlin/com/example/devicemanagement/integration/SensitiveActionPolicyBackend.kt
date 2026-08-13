package com.example.devicemanagement.integration

/**
 * Composition contract for the device-management module.
 *
 * Application and UI code must receive only the configured
 * SensitiveActionController, never the real backend implementation.
 */
interface SensitiveActionPolicyBackend {
    fun currentAuthorization(): SensitiveActionAuthorization

    fun applyScreenCaptureDisabled(
        disabled: Boolean,
        correlationId: String,
    ): PolicyMutationResult

    fun applyCameraDisabled(
        disabled: Boolean,
        correlationId: String,
    ): PolicyMutationResult
}

data class SensitiveActionAuthorization(
    val policyServiceAvailable: Boolean,
    val sensitiveActionsEnabled: Boolean,
    val verifiedDeviceOwner: Boolean,
    val profileOwner: Boolean,
    val expectedAdminReceiverRegistered: Boolean,
    val expectedAdminActive: Boolean,
    val managementStateConsistent: Boolean,
)

sealed interface PolicyMutationResult {
    data class Applied(
        val requestedDisabled: Boolean,
        val observedDisabled: Boolean,
    ) : PolicyMutationResult

    data class Denied(val reason: String) : PolicyMutationResult

    data class Failed(val reason: String) : PolicyMutationResult
}

internal fun interface MonotonicTimeSource {
    fun nowMillis(): Long
}
