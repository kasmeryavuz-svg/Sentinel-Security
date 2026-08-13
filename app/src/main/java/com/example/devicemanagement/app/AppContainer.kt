package com.example.devicemanagement.app

import android.content.Context
import com.example.devicemanagement.action.SensitiveActionController
import com.example.devicemanagement.logging.StructuredLogger
import com.example.devicemanagement.management.CameraPolicyStatusProvider
import com.example.devicemanagement.management.DeviceManagementLogger
import com.example.devicemanagement.management.DeviceManagementDiagnostics
import com.example.devicemanagement.management.DeviceManagementSensitiveActions
import com.example.devicemanagement.management.DeviceManagementStatusProvider
import com.example.devicemanagement.management.DeviceOwnerValidationProvider
import com.example.devicemanagement.management.ProvisioningReadinessProvider
import com.example.devicemanagement.management.ScreenCapturePolicyStatusProvider

class AppContainer(
    context: Context,
    sensitiveActionLogger: StructuredLogger,
    deviceManagementLogger: DeviceManagementLogger,
) {
    val sensitiveActions: SensitiveActionController =
        DeviceManagementSensitiveActions.create(
            context = context,
            sensitiveActionLogger = sensitiveActionLogger,
            deviceManagementLogger = deviceManagementLogger,
        )

    val deviceManagementStatus: DeviceManagementStatusProvider =
        DeviceManagementDiagnostics.create(context, deviceManagementLogger)

    val provisioningReadiness: ProvisioningReadinessProvider =
        DeviceManagementDiagnostics.createProvisioningReadiness(
            context,
            deviceManagementLogger,
        )

    val deviceOwnerValidation: DeviceOwnerValidationProvider =
        DeviceManagementDiagnostics.createDeviceOwnerValidation(
            context,
            deviceManagementLogger,
        )

    val screenCapturePolicyStatus: ScreenCapturePolicyStatusProvider =
        DeviceManagementDiagnostics.createScreenCapturePolicyStatus(
            context,
            deviceManagementLogger,
        )

    val cameraPolicyStatus: CameraPolicyStatusProvider =
        DeviceManagementDiagnostics.createCameraPolicyStatus(
            context,
            deviceManagementLogger,
        )
}
