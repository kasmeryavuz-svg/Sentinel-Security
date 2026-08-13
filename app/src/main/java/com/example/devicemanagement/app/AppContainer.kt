package com.example.devicemanagement.app

import android.content.Context
import com.example.devicemanagement.action.SensitiveActionController
import com.example.devicemanagement.logging.StructuredLogger
import com.example.devicemanagement.management.DeviceManagementDiagnostics
import com.example.devicemanagement.management.DeviceManagementLogger
import com.example.devicemanagement.management.DeviceManagementStatusProvider
import com.example.devicemanagement.management.ProvisioningReadinessProvider

class AppContainer(
    context: Context,
    sensitiveActionLogger: StructuredLogger,
    deviceManagementLogger: DeviceManagementLogger,
) {
    val sensitiveActions: SensitiveActionController =
        SensitiveActionController.createFailSafe(sensitiveActionLogger)

    val deviceManagementStatus: DeviceManagementStatusProvider =
        DeviceManagementDiagnostics.create(context, deviceManagementLogger)

    val provisioningReadiness: ProvisioningReadinessProvider =
        DeviceManagementDiagnostics.createProvisioningReadiness(
            context,
            deviceManagementLogger,
        )
}
