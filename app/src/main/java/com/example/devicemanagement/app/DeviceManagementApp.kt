package com.example.devicemanagement.app

import android.app.Application
import com.example.devicemanagement.logging.AndroidStructuredLogger

/**
 * Application entry point. Startup only reconstructs services from current
 * device state. It does not submit triggers, issue approvals, or apply
 * policy mutations.
 */
class DeviceManagementApp : Application() {
    val container: AppContainer by lazy {
        val logger = AndroidStructuredLogger()
        AppContainer(
            context = this,
            logger = logger,
        )
    }
}
