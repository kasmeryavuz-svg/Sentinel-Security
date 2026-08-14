package com.example.devicemanagement.ui

import com.example.devicemanagement.management.CameraPolicyState
import com.example.devicemanagement.management.CameraPolicyStatus
import com.example.devicemanagement.management.DeviceManagementStatus
import com.example.devicemanagement.management.DeviceOwnerValidation
import com.example.devicemanagement.management.DeviceOwnerValidationResult
import com.example.devicemanagement.management.ManagementCapability
import com.example.devicemanagement.management.ManagementMode
import com.example.devicemanagement.management.ProvisioningAvailability
import com.example.devicemanagement.management.ProvisioningOption
import com.example.devicemanagement.management.ProvisioningReadiness
import com.example.devicemanagement.management.ScreenCapturePolicyState
import com.example.devicemanagement.management.ScreenCapturePolicyStatus
import com.example.devicemanagement.management.StatusBarPolicyState
import com.example.devicemanagement.management.StatusBarPolicyStatus

internal object DashboardTestFixtures {
    const val EXPECTED_ADMIN =
        "com.example.devicemanagement/com.example.devicemanagement.management.SentinelDeviceAdminReceiver"
    const val PACKAGE_NAME = "com.example.devicemanagement"

    fun snapshot(
        validationResult: DeviceOwnerValidationResult =
            DeviceOwnerValidationResult.VERIFIED_DEVICE_OWNER,
        mode: ManagementMode = ManagementMode.DEVICE_OWNER,
        screenCapture: ScreenCapturePolicyState = ScreenCapturePolicyState.ENABLED,
        camera: CameraPolicyState = CameraPolicyState.ENABLED,
        statusBar: StatusBarPolicyState = StatusBarPolicyState.ENABLED,
        statusBarReasons: List<String> = emptyList(),
        screenCaptureReasons: List<String> = emptyList(),
        cameraReasons: List<String> = emptyList(),
        isDeviceOwner: Boolean = mode == ManagementMode.DEVICE_OWNER,
        isProfileOwner: Boolean = mode == ManagementMode.PROFILE_OWNER,
        isPolicyServiceAvailable: Boolean = true,
        deviceOwnerProvisioning: ProvisioningAvailability = ProvisioningAvailability.NOT_ALLOWED,
        profileOwnerProvisioning: ProvisioningAvailability = ProvisioningAvailability.NOT_ALLOWED,
    ): DashboardSnapshot {
        val status = DeviceManagementStatus(
            mode = mode,
            isPolicyServiceAvailable = isPolicyServiceAvailable,
            isExpectedAdminReceiverRegistered = true,
            isAdminActive = isDeviceOwner || isProfileOwner,
            isDeviceOwner = isDeviceOwner,
            isProfileOwner = isProfileOwner,
            availableCapabilities = buildSet {
                if (isPolicyServiceAvailable) {
                    add(ManagementCapability.POLICY_SERVICE_AVAILABLE)
                }
                add(ManagementCapability.EXPECTED_ADMIN_RECEIVER_REGISTERED)
                if (isDeviceOwner) add(ManagementCapability.DEVICE_OWNER)
                if (isProfileOwner) add(ManagementCapability.PROFILE_OWNER)
            },
            diagnostics = listOf("diagnostic"),
        )
        val readiness = ProvisioningReadiness(
            managementStatus = status,
            deviceOwnerProvisioning = ProvisioningOption(
                availability = deviceOwnerProvisioning,
                reasons = listOf("device-owner-provisioning"),
            ),
            profileOwnerProvisioning = ProvisioningOption(
                availability = profileOwnerProvisioning,
                reasons = listOf("profile-owner-provisioning"),
            ),
        )
        return DashboardSnapshot(
            validation = DeviceOwnerValidation(
                result = validationResult,
                packageName = PACKAGE_NAME,
                expectedAdminReceiverComponent = EXPECTED_ADMIN,
                registeredSentinelAdminComponents = setOf(EXPECTED_ADMIN),
                managementStatus = status,
                provisioningReadiness = readiness,
                reasons = listOf("validation-reason"),
            ),
            managementStatus = status,
            provisioningReadiness = readiness,
            screenCapture = ScreenCapturePolicyStatus(screenCapture, screenCaptureReasons),
            camera = CameraPolicyStatus(camera, cameraReasons),
            statusBar = StatusBarPolicyStatus(statusBar, statusBarReasons),
        )
    }

    fun sessionEntry(
        capability: PolicyCapability,
        requestedDisabled: Boolean = true,
        outcome: OperationOutcomePresentation = OperationOutcomePresentation.APPLIED,
        correlationId: String = "corr-1",
        sessionTimestampMillis: Long = 1_000L,
        reason: String? = null,
    ): SessionActivityEntry {
        return SessionActivityEntry(
            capability = capability,
            requestedDisabled = requestedDisabled,
            outcome = outcome,
            correlationId = correlationId,
            sessionTimestampMillis = sessionTimestampMillis,
            reason = reason,
        )
    }
}
