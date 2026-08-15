package com.example.devicemanagement.management

import com.example.devicemanagement.destructive.DestructiveLiveFacts
import com.example.devicemanagement.destructive.DestructiveLiveFactsSource
import com.example.devicemanagement.destructive.DestructiveManagementValidation

/**
 * Production live-facts adapter for the future real destructive chain.
 * Mapping live Device Owner state does not authorize or execute a wipe.
 */
internal class AndroidDestructiveLiveFactsSource(
    private val validationProvider: DeviceOwnerValidationProvider,
    private val platform: DevicePolicyPlatform,
) : DestructiveLiveFactsSource {
    override fun currentFacts(): DestructiveLiveFacts {
        val validation = validationProvider.currentValidation()
        val status = validation.managementStatus
        return DestructiveLiveFacts(
            runningPackage = validation.packageName,
            expectedAdminComponent = validation.expectedAdminReceiverComponent,
            registeredSentinelAdminSet = validation.registeredSentinelAdminComponents,
            isDeviceOwner = status.isDeviceOwner,
            isProfileOwner = status.isProfileOwner,
            isExpectedAdminActive = status.isAdminActive,
            activeAdminComponentSet = platform.policyService()
                ?.activeAdminComponentNames()
                .orEmpty(),
            managementValidationState = when (validation.result) {
                DeviceOwnerValidationResult.VERIFIED_DEVICE_OWNER ->
                    DestructiveManagementValidation.VERIFIED_DEVICE_OWNER
                DeviceOwnerValidationResult.NOT_DEVICE_OWNER ->
                    DestructiveManagementValidation.NOT_DEVICE_OWNER
                DeviceOwnerValidationResult.CONFIGURATION_ERROR ->
                    DestructiveManagementValidation.CONFIGURATION_ERROR
                DeviceOwnerValidationResult.UNAVAILABLE ->
                    DestructiveManagementValidation.UNAVAILABLE
            },
            policyServiceAvailable = status.isPolicyServiceAvailable,
        )
    }
}
