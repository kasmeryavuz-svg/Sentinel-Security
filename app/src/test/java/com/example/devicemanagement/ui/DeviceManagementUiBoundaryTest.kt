package com.example.devicemanagement.ui

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class DeviceManagementUiBoundaryTest {
    @Test
    fun `UI cannot reference policy infrastructure or sensitive action internals`() {
        val uiSources = File(
            requireNotNull(System.getProperty("appMainSourceDir")),
            "java/com/example/devicemanagement/ui",
        ).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }

        assertFalse(uiSources.contains("import android.app.admin.DevicePolicyManager"))
        assertFalse(uiSources.contains("AndroidDevicePolicyPlatform"))
        assertFalse(uiSources.contains("DevicePolicyReadService"))
        assertFalse(uiSources.contains("ActionExecutor"))
        assertFalse(uiSources.contains("DeviceAction"))
    }
}
