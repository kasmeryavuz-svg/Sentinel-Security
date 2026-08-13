package com.example.devicemanagement.app

import android.app.Application
import com.example.devicemanagement.logging.AndroidStructuredLogger

class DeviceManagementApp : Application() {
    val container: AppContainer by lazy {
        AppContainer(AndroidStructuredLogger())
    }
}
