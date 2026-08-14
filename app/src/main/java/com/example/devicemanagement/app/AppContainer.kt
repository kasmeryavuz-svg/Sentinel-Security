package com.example.devicemanagement.app

import android.content.Context
import com.example.devicemanagement.action.SensitiveActionController
import com.example.devicemanagement.logging.StructuredLogger
import com.example.devicemanagement.management.CameraPolicyStatusProvider
import com.example.devicemanagement.management.DeviceManagement
import com.example.devicemanagement.management.DeviceManagementStatusProvider
import com.example.devicemanagement.management.DeviceOwnerValidationProvider
import com.example.devicemanagement.management.ProvisioningReadinessProvider
import com.example.devicemanagement.management.ScreenCapturePolicyStatusProvider
import com.example.devicemanagement.management.StatusBarPolicyStatusProvider

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
}
