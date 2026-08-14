package com.example.devicemanagement.ui

import com.example.devicemanagement.management.CameraPolicyState
import com.example.devicemanagement.management.CameraPolicyStatus
import com.example.devicemanagement.management.DeviceOwnerValidationResult
import com.example.devicemanagement.management.ManagementMode
import com.example.devicemanagement.management.ProvisioningAvailability
import com.example.devicemanagement.management.ScreenCapturePolicyState
import com.example.devicemanagement.management.ScreenCapturePolicyStatus
import com.example.devicemanagement.management.StatusBarPolicyState
import com.example.devicemanagement.management.StatusBarPolicyStatus

/**
 * Maps read-only provider values and NON-PERSISTENT session history into
 * dashboard presentation state.
 *
 * Policy and ownership states are copied from provider APIs. This mapper does
 * not infer current policy from the last mutation result.
 */
object DashboardStateMapper {
    fun map(
        snapshot: DashboardSnapshot,
        sessionEntries: List<SessionActivityEntry>,
        pendingCapability: PolicyCapability?,
    ): DashboardViewState {
        val verification = snapshot.validation.result.toPresentation()
        val operationInProgress = pendingCapability != null
        return DashboardViewState(
            header = HeaderViewState(verification = verification),
            management = mapManagement(snapshot, verification),
            screenCapture = mapScreenCapture(
                status = snapshot.screenCapture,
                sessionEntries = sessionEntries,
                pendingCapability = pendingCapability,
                operationInProgress = operationInProgress,
            ),
            camera = mapCamera(
                status = snapshot.camera,
                sessionEntries = sessionEntries,
                pendingCapability = pendingCapability,
                operationInProgress = operationInProgress,
            ),
            statusBar = mapStatusBar(
                status = snapshot.statusBar,
                sessionEntries = sessionEntries,
                pendingCapability = pendingCapability,
                operationInProgress = operationInProgress,
            ),
            sessionActivity = sessionEntries,
            operationInProgress = operationInProgress,
        )
    }

    private fun mapManagement(
        snapshot: DashboardSnapshot,
        verification: VerificationPresentation,
    ): ManagementStatusViewState {
        val validation = snapshot.validation
        val status = snapshot.managementStatus
        val readiness = snapshot.provisioningReadiness
        return ManagementStatusViewState(
            mode = status.mode.toPresentation(),
            expectedAdminReceiver = validation.expectedAdminReceiverComponent,
            verification = verification,
            deviceOwnerProvisioning =
                readiness.deviceOwnerProvisioning.availability.toPresentation(),
            profileOwnerProvisioning =
                readiness.profileOwnerProvisioning.availability.toPresentation(),
            packageName = validation.packageName,
            registeredAdminComponents =
                validation.registeredSentinelAdminComponents.sorted(),
            isPolicyServiceAvailable = status.isPolicyServiceAvailable,
            isExpectedAdminReceiverRegistered = status.isExpectedAdminReceiverRegistered,
            isAdminActive = status.isAdminActive,
            isDeviceOwner = status.isDeviceOwner,
            isProfileOwner = status.isProfileOwner,
            diagnostics = status.diagnostics,
            validationReasons = validation.reasons,
            deviceOwnerProvisioningReasons = readiness.deviceOwnerProvisioning.reasons,
            profileOwnerProvisioningReasons = readiness.profileOwnerProvisioning.reasons,
        )
    }

    private fun mapScreenCapture(
        status: ScreenCapturePolicyStatus,
        sessionEntries: List<SessionActivityEntry>,
        pendingCapability: PolicyCapability?,
        operationInProgress: Boolean,
    ): PolicyCardViewState {
        return policyCard(
            capability = PolicyCapability.SCREEN_CAPTURE,
            state = status.state.toPresentation(),
            reasons = status.reasons,
            requiresApi34Notice = false,
            sessionEntries = sessionEntries,
            pendingCapability = pendingCapability,
            operationInProgress = operationInProgress,
        )
    }

    private fun mapCamera(
        status: CameraPolicyStatus,
        sessionEntries: List<SessionActivityEntry>,
        pendingCapability: PolicyCapability?,
        operationInProgress: Boolean,
    ): PolicyCardViewState {
        return policyCard(
            capability = PolicyCapability.CAMERA,
            state = status.state.toPresentation(),
            reasons = status.reasons,
            requiresApi34Notice = false,
            sessionEntries = sessionEntries,
            pendingCapability = pendingCapability,
            operationInProgress = operationInProgress,
        )
    }

    private fun mapStatusBar(
        status: StatusBarPolicyStatus,
        sessionEntries: List<SessionActivityEntry>,
        pendingCapability: PolicyCapability?,
        operationInProgress: Boolean,
    ): PolicyCardViewState {
        val requiresApi34Notice = status.reportsApi34Requirement()
        return policyCard(
            capability = PolicyCapability.STATUS_BAR,
            state = status.state.toPresentation(),
            reasons = status.reasons,
            requiresApi34Notice = requiresApi34Notice,
            sessionEntries = sessionEntries,
            pendingCapability = pendingCapability,
            operationInProgress = operationInProgress,
        )
    }

    private fun policyCard(
        capability: PolicyCapability,
        state: PolicyPresentationState,
        reasons: List<String>,
        requiresApi34Notice: Boolean,
        sessionEntries: List<SessionActivityEntry>,
        pendingCapability: PolicyCapability?,
        operationInProgress: Boolean,
    ): PolicyCardViewState {
        val latest = sessionEntries.firstOrNull { it.capability == capability }
        val pending = pendingCapability == capability
        return PolicyCardViewState(
            capability = capability,
            state = state,
            reasons = reasons,
            actionsEnabled = !operationInProgress && !requiresApi34Notice,
            requiresApi34Notice = requiresApi34Notice,
            latestOutcome = if (pending) {
                OperationOutcomePresentation.PENDING
            } else {
                latest?.outcome ?: OperationOutcomePresentation.NONE
            },
            latestOutcomeDetail = if (pending) null else latest?.reason,
            latestCorrelationId = if (pending) null else latest?.correlationId,
        )
    }
}

internal fun DeviceOwnerValidationResult.toPresentation(): VerificationPresentation {
    return when (this) {
        DeviceOwnerValidationResult.VERIFIED_DEVICE_OWNER ->
            VerificationPresentation.VERIFIED_DEVICE_OWNER
        DeviceOwnerValidationResult.NOT_DEVICE_OWNER ->
            VerificationPresentation.NOT_DEVICE_OWNER
        DeviceOwnerValidationResult.CONFIGURATION_ERROR ->
            VerificationPresentation.CONFIGURATION_ERROR
        DeviceOwnerValidationResult.UNAVAILABLE ->
            VerificationPresentation.UNAVAILABLE
    }
}

internal fun ManagementMode.toPresentation(): ManagementModePresentation {
    return when (this) {
        ManagementMode.DEVICE_OWNER -> ManagementModePresentation.DEVICE_OWNER
        ManagementMode.PROFILE_OWNER -> ManagementModePresentation.PROFILE_OWNER
        ManagementMode.ORDINARY_APP -> ManagementModePresentation.ORDINARY_APP
        ManagementMode.UNAVAILABLE -> ManagementModePresentation.UNAVAILABLE
    }
}

internal fun ProvisioningAvailability.toPresentation(): ProvisioningPresentation {
    return when (this) {
        ProvisioningAvailability.ALLOWED -> ProvisioningPresentation.ALLOWED
        ProvisioningAvailability.NOT_ALLOWED -> ProvisioningPresentation.NOT_ALLOWED
        ProvisioningAvailability.UNAVAILABLE -> ProvisioningPresentation.UNAVAILABLE
    }
}

internal fun ScreenCapturePolicyState.toPresentation(): PolicyPresentationState {
    return when (this) {
        ScreenCapturePolicyState.DISABLED -> PolicyPresentationState.DISABLED
        ScreenCapturePolicyState.ENABLED -> PolicyPresentationState.ENABLED
        ScreenCapturePolicyState.UNAVAILABLE -> PolicyPresentationState.UNAVAILABLE
    }
}

internal fun CameraPolicyState.toPresentation(): PolicyPresentationState {
    return when (this) {
        CameraPolicyState.DISABLED -> PolicyPresentationState.DISABLED
        CameraPolicyState.ENABLED -> PolicyPresentationState.ENABLED
        CameraPolicyState.UNAVAILABLE -> PolicyPresentationState.UNAVAILABLE
    }
}

internal fun StatusBarPolicyState.toPresentation(): PolicyPresentationState {
    return when (this) {
        StatusBarPolicyState.DISABLED -> PolicyPresentationState.DISABLED
        StatusBarPolicyState.ENABLED -> PolicyPresentationState.ENABLED
        StatusBarPolicyState.UNAVAILABLE -> PolicyPresentationState.UNAVAILABLE
    }
}

internal fun StatusBarPolicyStatus.reportsApi34Requirement(): Boolean {
    if (state != StatusBarPolicyState.UNAVAILABLE) {
        return false
    }
    return reasons.any { reason ->
        reason.contains("API 34") || reason.contains("Android 14")
    }
}
