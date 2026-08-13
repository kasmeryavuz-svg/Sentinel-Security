package com.example.devicemanagement.app

import android.app.Application
import com.example.devicemanagement.logging.AndroidStructuredLogger

class DeviceManagementApp : Application() {
    val container: AppContainer by lazy {
        val logger = AndroidStructuredLogger()
        AppContainer(
            context = this,
            sensitiveActionLogger = logger,
            deviceManagementLogger = logger,
        )
    }
}
