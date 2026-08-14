package com.example.devicemanagement.ui

import com.example.devicemanagement.management.CameraPolicyStatus
import com.example.devicemanagement.management.DeviceManagementStatus
import com.example.devicemanagement.management.DeviceOwnerValidation
import com.example.devicemanagement.management.ProvisioningReadiness
import com.example.devicemanagement.management.ScreenCapturePolicyStatus
import com.example.devicemanagement.management.StatusBarPolicyStatus

enum class PolicyCapability {
    SCREEN_CAPTURE,
    CAMERA,
    STATUS_BAR,
}

enum class VerificationPresentation {
    VERIFIED_DEVICE_OWNER,
    NOT_DEVICE_OWNER,
    CONFIGURATION_ERROR,
    UNAVAILABLE,
}

enum class PolicyPresentationState {
    DISABLED,
    ENABLED,
    UNAVAILABLE,
}

enum class OperationOutcomePresentation {
    NONE,
    PENDING,
    APPLIED,
    DENIED,
    FAILED,
}

enum class ManagementModePresentation {
    DEVICE_OWNER,
    PROFILE_OWNER,
    ORDINARY_APP,
    UNAVAILABLE,
}

enum class ProvisioningPresentation {
    ALLOWED,
    NOT_ALLOWED,
    UNAVAILABLE,
}

/**
 * Read-only snapshot assembled from existing status providers.
 *
 * The dashboard must not infer policy or ownership from this object; it only
 * carries values already returned by the public provider APIs.
 */
data class DashboardSnapshot(
    val validation: DeviceOwnerValidation,
    val managementStatus: DeviceManagementStatus,
    val provisioningReadiness: ProvisioningReadiness,
    val screenCapture: ScreenCapturePolicyStatus,
    val camera: CameraPolicyStatus,
    val statusBar: StatusBarPolicyStatus,
)

data class HeaderViewState(
    val verification: VerificationPresentation,
)

data class ManagementStatusViewState(
    val mode: ManagementModePresentation,
    val expectedAdminReceiver: String,
    val verification: VerificationPresentation,
    val deviceOwnerProvisioning: ProvisioningPresentation,
    val profileOwnerProvisioning: ProvisioningPresentation,
    val packageName: String,
    val registeredAdminComponents: List<String>,
    val isPolicyServiceAvailable: Boolean,
    val isExpectedAdminReceiverRegistered: Boolean,
    val isAdminActive: Boolean,
    val isDeviceOwner: Boolean,
    val isProfileOwner: Boolean,
    val diagnostics: List<String>,
    val validationReasons: List<String>,
    val deviceOwnerProvisioningReasons: List<String>,
    val profileOwnerProvisioningReasons: List<String>,
)

data class PolicyCardViewState(
    val capability: PolicyCapability,
    val state: PolicyPresentationState,
    val reasons: List<String>,
    val actionsEnabled: Boolean,
    val requiresApi34Notice: Boolean,
    val latestOutcome: OperationOutcomePresentation,
    val latestOutcomeDetail: String?,
    val latestCorrelationId: String?,
)

data class DashboardViewState(
    val header: HeaderViewState,
    val management: ManagementStatusViewState,
    val screenCapture: PolicyCardViewState,
    val camera: PolicyCardViewState,
    val statusBar: PolicyCardViewState,
    val sessionActivity: List<SessionActivityEntry>,
    val operationInProgress: Boolean,
)
