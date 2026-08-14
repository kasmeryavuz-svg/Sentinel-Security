package com.example.devicemanagement.management

import android.content.Context
import com.example.devicemanagement.internal.DeviceManagementImplementation
import com.example.devicemanagement.logging.StructuredLogger

/**
 * The sole public composition facade for production device-management services.
 */
object DeviceManagement {
    @JvmStatic
    fun create(
        context: Context,
        logger: StructuredLogger,
    ): DeviceManagementServices {
        return DeviceManagementImplementation.create(
            context = context.applicationContext,
            logger = logger,
        )
    }
}
