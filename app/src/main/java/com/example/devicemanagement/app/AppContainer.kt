package com.example.devicemanagement.app

import android.content.Context
import com.example.devicemanagement.action.SensitiveActionController
import com.example.devicemanagement.logging.StructuredLogger
import com.example.devicemanagement.management.DeviceManagementDiagnostics
import com.example.devicemanagement.management.DeviceManagementLogger
import com.example.devicemanagement.management.DeviceManagementStatusProvider

class AppContainer(
    context: Context,
    sensitiveActionLogger: StructuredLogger,
    deviceManagementLogger: DeviceManagementLogger,
) {
    val sensitiveActions: SensitiveActionController =
        SensitiveActionController.createFailSafe(sensitiveActionLogger)

    val deviceManagementStatus: DeviceManagementStatusProvider =
        DeviceManagementDiagnostics.create(context, deviceManagementLogger)
}
