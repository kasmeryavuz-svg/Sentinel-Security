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
        assertFalse(uiSources.contains("DevicePolicyScreenCaptureService"))
        assertFalse(uiSources.contains("DevicePolicyCameraService"))
        assertFalse(uiSources.contains("SensitiveActionPolicyBackend"))
        assertFalse(uiSources.contains("SensitiveActionRegistry"))
        assertFalse(uiSources.contains("VerifiedPolicyMutation"))
        assertFalse(uiSources.contains("DeviceOwnerMutationGuard"))
        assertFalse(uiSources.contains("DeviceManagementSensitiveActionBackend"))
        assertFalse(uiSources.contains("DefaultScreenCapturePolicy"))
        assertFalse(uiSources.contains("DefaultCameraPolicy"))
        assertFalse(
            uiSources.contains(
                "import com.example.devicemanagement.management.ScreenCapturePolicy\n",
            ),
        )
        assertFalse(
            uiSources.contains(
                "import com.example.devicemanagement.management.CameraPolicy\n",
            ),
        )
        assertFalse(uiSources.contains("setScreenCaptureDisabled"))
        assertFalse(uiSources.contains("setCameraDisabled"))
        assertFalse(uiSources.contains("ActionExecutor"))
        assertFalse(uiSources.contains("ApprovalAuthority"))
        assertFalse(uiSources.contains("DeviceAction"))
    }
}
