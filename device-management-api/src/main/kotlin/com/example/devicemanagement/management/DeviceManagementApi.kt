package com.example.devicemanagement.management

import com.example.devicemanagement.action.SensitiveActionController
import com.example.devicemanagement.audit.AuditHistoryProvider
import com.example.devicemanagement.audit.AuditStorageStatusProvider
import com.example.devicemanagement.recovery.RecoveryInspectionProvider

/**
 * The complete JVM-visible device-management surface available to application code.
 *
 * It intentionally contains one mutation entry point, [sensitiveActions], and only
 * read-only providers besides it. Audit and recovery providers are
 * evidence/presentation only and cannot authorize, approve, replay, or mutate
 * policy.
 */
interface DeviceManagementServices {
    val sensitiveActions: SensitiveActionController
    val deviceManagementStatus: DeviceManagementStatusProvider
    val provisioningReadiness: ProvisioningReadinessProvider
    val deviceOwnerValidation: DeviceOwnerValidationProvider
    val screenCapturePolicyStatus: ScreenCapturePolicyStatusProvider
    val cameraPolicyStatus: CameraPolicyStatusProvider
    val statusBarPolicyStatus: StatusBarPolicyStatusProvider
    val auditHistory: AuditHistoryProvider
    val auditStorageStatus: AuditStorageStatusProvider
    val recoveryInspection: RecoveryInspectionProvider
}

enum class ManagementMode {
    DEVICE_OWNER,
    PROFILE_OWNER,
    ORDINARY_APP,
    UNAVAILABLE,
}

enum class ManagementCapability {
    POLICY_SERVICE_AVAILABLE,
    EXPECTED_ADMIN_RECEIVER_REGISTERED,
    ADMIN_ACTIVE,
    DEVICE_OWNER,
    PROFILE_OWNER,
}

data class DeviceManagementStatus(
    val mode: ManagementMode,
    val isPolicyServiceAvailable: Boolean,
    val isExpectedAdminReceiverRegistered: Boolean,
    val isAdminActive: Boolean,
    val isDeviceOwner: Boolean,
    val isProfileOwner: Boolean,
    val availableCapabilities: Set<ManagementCapability>,
    val diagnostics: List<String>,
)

fun interface DeviceManagementStatusProvider {
    fun currentStatus(): DeviceManagementStatus
}

enum class ProvisioningAvailability {
    ALLOWED,
    NOT_ALLOWED,
    UNAVAILABLE,
}

data class ProvisioningOption(
    val availability: ProvisioningAvailability,
    val reasons: List<String>,
) {
    val isAllowed: Boolean
        get() = availability == ProvisioningAvailability.ALLOWED
}

data class ProvisioningReadiness(
    val managementStatus: DeviceManagementStatus,
    val deviceOwnerProvisioning: ProvisioningOption,
    val profileOwnerProvisioning: ProvisioningOption,
)

fun interface ProvisioningReadinessProvider {
    fun currentReadiness(): ProvisioningReadiness
}

enum class DeviceOwnerValidationResult {
    VERIFIED_DEVICE_OWNER,
    NOT_DEVICE_OWNER,
    CONFIGURATION_ERROR,
    UNAVAILABLE,
}

data class DeviceOwnerValidation(
    val result: DeviceOwnerValidationResult,
    val packageName: String,
    val expectedAdminReceiverComponent: String,
    val registeredSentinelAdminComponents: Set<String>,
    val managementStatus: DeviceManagementStatus,
    val provisioningReadiness: ProvisioningReadiness,
    val reasons: List<String>,
)

fun interface DeviceOwnerValidationProvider {
    fun currentValidation(): DeviceOwnerValidation
}

enum class ScreenCapturePolicyState {
    DISABLED,
    ENABLED,
    UNAVAILABLE,
}

data class ScreenCapturePolicyStatus(
    val state: ScreenCapturePolicyState,
    val reasons: List<String>,
)

fun interface ScreenCapturePolicyStatusProvider {
    fun currentStatus(): ScreenCapturePolicyStatus
}

enum class CameraPolicyState {
    DISABLED,
    ENABLED,
    UNAVAILABLE,
}

data class CameraPolicyStatus(
    val state: CameraPolicyState,
    val reasons: List<String>,
)

fun interface CameraPolicyStatusProvider {
    fun currentStatus(): CameraPolicyStatus
}

enum class StatusBarPolicyState {
    DISABLED,
    ENABLED,
    UNAVAILABLE,
}

data class StatusBarPolicyStatus(
    val state: StatusBarPolicyState,
    val reasons: List<String>,
)

fun interface StatusBarPolicyStatusProvider {
    fun currentStatus(): StatusBarPolicyStatus
}
