package com.example.devicemanagement.app

import android.content.Context
import com.example.devicemanagement.action.SensitiveActionController
import com.example.devicemanagement.audit.AuditHistoryProvider
import com.example.devicemanagement.audit.AuditStorageStatusProvider
import com.example.devicemanagement.logging.StructuredLogger
import com.example.devicemanagement.management.CameraPolicyStatusProvider
import com.example.devicemanagement.management.DeviceManagement
import com.example.devicemanagement.management.DeviceManagementStatusProvider
import com.example.devicemanagement.management.DeviceOwnerValidationProvider
import com.example.devicemanagement.management.ProvisioningReadinessProvider
import com.example.devicemanagement.management.ScreenCapturePolicyStatusProvider
import com.example.devicemanagement.management.StatusBarPolicyStatusProvider
import com.example.devicemanagement.recovery.RecoveryInspectionProvider

/**
 * Reconstructs the public device-management surface from current device state.
 * The only mutation handle is [sensitiveActions]. Recovery inspection is
 * read-only evidence and is never an authorization or replay path.
 */
class AppContainer(
    context: Context,
    logger: StructuredLogger,
) {
    private val services =
        DeviceManagement.create(
            context = context,
            logger = logger,
        )

    val sensitiveActions: SensitiveActionController = services.sensitiveActions

    val deviceManagementStatus: DeviceManagementStatusProvider =
        services.deviceManagementStatus

    val provisioningReadiness: ProvisioningReadinessProvider =
        services.provisioningReadiness

    val deviceOwnerValidation: DeviceOwnerValidationProvider =
        services.deviceOwnerValidation

    val screenCapturePolicyStatus: ScreenCapturePolicyStatusProvider =
        services.screenCapturePolicyStatus

    val cameraPolicyStatus: CameraPolicyStatusProvider = services.cameraPolicyStatus

    val statusBarPolicyStatus: StatusBarPolicyStatusProvider =
        services.statusBarPolicyStatus

    val auditHistory: AuditHistoryProvider = services.auditHistory

    val auditStorageStatus: AuditStorageStatusProvider = services.auditStorageStatus

    val recoveryInspection: RecoveryInspectionProvider = services.recoveryInspection
}
