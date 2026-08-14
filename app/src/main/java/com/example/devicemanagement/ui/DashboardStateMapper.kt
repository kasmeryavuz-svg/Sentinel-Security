package com.example.devicemanagement.ui

import com.example.devicemanagement.audit.AuditActionNames
import com.example.devicemanagement.audit.AuditEvent
import com.example.devicemanagement.audit.AuditEventPhase
import com.example.devicemanagement.audit.AuditHistory
import com.example.devicemanagement.audit.AuditSchema
import com.example.devicemanagement.audit.AuditStorageHealth
import com.example.devicemanagement.audit.AuditStorageStatus
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
 * Maps read-only provider values and durable audit history into dashboard
 * presentation state.
 *
 * Policy and ownership states are copied from provider APIs. This mapper does
 * not infer current policy from audit events, and never treats an unmatched
 * REQUESTED event as Applied.
 */
object DashboardStateMapper {
    fun map(
        snapshot: DashboardSnapshot,
        auditHistory: AuditHistory,
        auditStatus: AuditStorageStatus,
        pendingCapability: PolicyCapability?,
    ): DashboardViewState {
        val verification = snapshot.validation.result.toPresentation()
        val operationInProgress = pendingCapability != null
        val auditLog = AuditLogMapper.rows(auditHistory.events)
        return DashboardViewState(
            header = HeaderViewState(verification = verification),
            management = mapManagement(snapshot, verification),
            screenCapture = mapScreenCapture(
                status = snapshot.screenCapture,
                auditLog = auditLog,
                pendingCapability = pendingCapability,
                operationInProgress = operationInProgress,
            ),
            camera = mapCamera(
                status = snapshot.camera,
                auditLog = auditLog,
                pendingCapability = pendingCapability,
                operationInProgress = operationInProgress,
            ),
            statusBar = mapStatusBar(
                status = snapshot.statusBar,
                auditLog = auditLog,
                pendingCapability = pendingCapability,
                operationInProgress = operationInProgress,
            ),
            auditLog = auditLog,
            auditStorageHealth = auditStatus.health,
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
        auditLog: List<AuditLogRow>,
        pendingCapability: PolicyCapability?,
        operationInProgress: Boolean,
    ): PolicyCardViewState {
        return policyCard(
            capability = PolicyCapability.SCREEN_CAPTURE,
            state = status.state.toPresentation(),
            reasons = status.reasons,
            requiresApi34Notice = false,
            auditLog = auditLog,
            pendingCapability = pendingCapability,
            operationInProgress = operationInProgress,
        )
    }

    private fun mapCamera(
        status: CameraPolicyStatus,
        auditLog: List<AuditLogRow>,
        pendingCapability: PolicyCapability?,
        operationInProgress: Boolean,
    ): PolicyCardViewState {
        return policyCard(
            capability = PolicyCapability.CAMERA,
            state = status.state.toPresentation(),
            reasons = status.reasons,
            requiresApi34Notice = false,
            auditLog = auditLog,
            pendingCapability = pendingCapability,
            operationInProgress = operationInProgress,
        )
    }

    private fun mapStatusBar(
        status: StatusBarPolicyStatus,
        auditLog: List<AuditLogRow>,
        pendingCapability: PolicyCapability?,
        operationInProgress: Boolean,
    ): PolicyCardViewState {
        val requiresApi34Notice = status.reportsApi34Requirement()
        return policyCard(
            capability = PolicyCapability.STATUS_BAR,
            state = status.state.toPresentation(),
            reasons = status.reasons,
            requiresApi34Notice = requiresApi34Notice,
            auditLog = auditLog,
            pendingCapability = pendingCapability,
            operationInProgress = operationInProgress,
        )
    }

    private fun policyCard(
        capability: PolicyCapability,
        state: PolicyPresentationState,
        reasons: List<String>,
        requiresApi34Notice: Boolean,
        auditLog: List<AuditLogRow>,
        pendingCapability: PolicyCapability?,
        operationInProgress: Boolean,
    ): PolicyCardViewState {
        val latest = auditLog.firstOrNull { row ->
            capabilityForAction(row.actionName) == capability
        }
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
                latest?.status.toOperationOutcome()
            },
            latestOutcomeDetail = if (pending) null else latest?.reasonCode,
            latestCorrelationId = if (pending) null else latest?.correlationId,
        )
    }
}

internal object AuditLogMapper {
    fun rows(
        events: List<AuditEvent>,
        limit: Int = AuditSchema.DASHBOARD_LIMIT,
    ): List<AuditLogRow> {
        val newestFirst = events.sortedByDescending { it.sequence }
        val rows = LinkedHashMap<String, AuditLogRow>()
        newestFirst.forEach { event ->
            if (event.correlationId in rows) {
                return@forEach
            }
            rows[event.correlationId] = rowForSequence(event, newestFirst)
        }
        return rows.values.take(limit)
    }

    private fun rowForSequence(
        newest: AuditEvent,
        newestFirst: List<AuditEvent>,
    ): AuditLogRow {
        val sequence = newestFirst.filter { it.correlationId == newest.correlationId }
        val terminal = sequence.firstOrNull { it.phase != AuditEventPhase.REQUESTED }
        val displayed = terminal ?: newest
        val status = when (displayed.phase) {
            AuditEventPhase.APPLIED -> AuditLogStatus.APPLIED
            AuditEventPhase.REJECTED -> AuditLogStatus.REJECTED
            AuditEventPhase.FAILED -> AuditLogStatus.FAILED
            AuditEventPhase.SIMULATED -> AuditLogStatus.SIMULATED
            AuditEventPhase.REQUESTED -> AuditLogStatus.INTERRUPTED
        }
        return AuditLogRow(
            timestampMillis = displayed.presentationWallClockMillis,
            actionName = displayed.actionName,
            status = status,
            correlationId = displayed.correlationId,
            reasonCode = displayed.reasonCode?.name,
        )
    }
}

internal fun capabilityForAction(actionName: String): PolicyCapability? {
    return when (actionName) {
        AuditActionNames.DISABLE_SCREEN_CAPTURE,
        AuditActionNames.ENABLE_SCREEN_CAPTURE,
        -> PolicyCapability.SCREEN_CAPTURE
        AuditActionNames.DISABLE_CAMERA,
        AuditActionNames.ENABLE_CAMERA,
        -> PolicyCapability.CAMERA
        AuditActionNames.DISABLE_STATUS_BAR,
        AuditActionNames.ENABLE_STATUS_BAR,
        -> PolicyCapability.STATUS_BAR
        else -> null
    }
}

internal fun AuditLogStatus?.toOperationOutcome(): OperationOutcomePresentation {
    return when (this) {
        AuditLogStatus.APPLIED -> OperationOutcomePresentation.APPLIED
        AuditLogStatus.REJECTED -> OperationOutcomePresentation.DENIED
        AuditLogStatus.FAILED -> OperationOutcomePresentation.FAILED
        AuditLogStatus.SIMULATED -> OperationOutcomePresentation.SIMULATED
        AuditLogStatus.INTERRUPTED -> OperationOutcomePresentation.INTERRUPTED
        null -> OperationOutcomePresentation.NONE
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
