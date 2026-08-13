package com.example.devicemanagement.app

import com.example.devicemanagement.action.SensitiveActionController
import com.example.devicemanagement.logging.StructuredLogger

class AppContainer(
    logger: StructuredLogger,
) {
    val sensitiveActions: SensitiveActionController =
        SensitiveActionController.createSimulation(logger)
}
