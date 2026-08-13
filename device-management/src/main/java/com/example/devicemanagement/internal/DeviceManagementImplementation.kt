package com.example.devicemanagement.internal

import android.content.Context
import com.example.devicemanagement.logging.StructuredLogger
import com.example.devicemanagement.management.DeviceManagementComposition
import com.example.devicemanagement.management.DeviceManagementServices

/**
 * Linkage point used only by the facade artifact.
 *
 * This type is public because JVM linkage crosses an artifact boundary, but the
 * implementation artifact is deliberately absent from the app compile classpath.
 */
object DeviceManagementImplementation {
    @JvmStatic
    fun create(
        context: Context,
        logger: StructuredLogger,
    ): DeviceManagementServices {
        return DeviceManagementComposition.create(context.applicationContext, logger)
    }
}
