package com.example.devicemanagement.provisioning

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProvisioningArchitectureGuardTest {
    @Test
    fun `provisioning sources cannot access mutation internals or destructive APIs`() {
        val sources = kotlinSources(
            "java/com/example/devicemanagement/provisioning",
        )

        assertFalse(sources.contains("DevicePolicyManager"))
        assertFalse(sources.contains("AndroidDevicePolicyPlatform"))
        assertFalse(sources.contains("DevicePolicyReadService"))
        assertFalse(sources.contains("DevicePolicyScreenCaptureService"))
        assertFalse(sources.contains("DevicePolicyCameraService"))
        assertFalse(sources.contains("DevicePolicyStatusBarService"))
        assertFalse(sources.contains("VerifiedPolicyMutation"))
        assertFalse(sources.contains("VerifiedPolicyMutationExecutor"))
        assertFalse(sources.contains("DefaultScreenCapturePolicy"))
        assertFalse(sources.contains("DefaultCameraPolicy"))
        assertFalse(sources.contains("DefaultStatusBarPolicy"))
        assertFalse(sources.contains("SensitiveActionPolicyBackend"))
        assertFalse(sources.contains("ApprovalAuthority"))
        assertFalse(sources.contains("ActionExecutor"))
        assertFalse(sources.contains("SensitiveActionController"))
        assertFalse(sources.contains("SensitiveActionRegistry"))
        assertFalse(sources.contains("DeviceManagementSensitiveActionControllerFactory"))
        assertFalse(sources.contains("DeviceManagementImplementation"))
        assertFalse(sources.contains("setScreenCaptureDisabled"))
        assertFalse(sources.contains("setCameraDisabled"))
        assertFalse(sources.contains("setStatusBarDisabled"))
        assertFalse(sources.contains("wipeData"))
        assertFalse(sources.contains("lockNow"))
        assertFalse(sources.contains("reboot"))
        assertFalse(sources.contains("resetPassword"))
        assertFalse(sources.contains("removeUser"))
        assertFalse(sources.contains("uninstallPackageWithActiveAdmins"))
        assertFalse(sources.contains("clearApplicationUserData"))
        assertFalse(sources.contains("setLockTaskPackages"))
        assertFalse(sources.contains("setLockTaskFeatures"))
        assertFalse(sources.contains("LockTask"))
        assertFalse(sources.contains("Class.forName"))
        assertFalse(sources.contains("System.loadLibrary"))
        assertFalse(sources.contains("Runtime.getRuntime"))
        assertFalse(sources.contains("ProcessBuilder"))
        assertFalse(sources.contains("getDeclaredMethod"))
        assertFalse(sources.contains("ACTION_PROVISION_MANAGED_DEVICE"))
        assertTrue(sources.contains("DeviceOwnerValidation"))
        assertTrue(
            sources.contains("android.app.action.GET_PROVISIONING_MODE"),
        )
        assertTrue(
            sources.contains("android.app.action.ADMIN_POLICY_COMPLIANCE"),
        )
    }

    @Test
    fun `provisioning activities stay on the public validation API`() {
        val compliance = File(
            requireNotNull(System.getProperty("appMainSourceDir")),
            "java/com/example/devicemanagement/provisioning/AdminPolicyComplianceActivity.kt",
        ).readText()

        assertTrue(compliance.contains("deviceOwnerValidation.currentValidation()"))
        assertTrue(compliance.contains("ProvisioningComplianceEvaluator.evaluate"))
        assertFalse(compliance.contains("sensitiveActions"))
        assertFalse(compliance.contains(".submit("))
    }

    private fun kotlinSources(relativeDirectory: String): String {
        return File(
            requireNotNull(System.getProperty("appMainSourceDir")),
            relativeDirectory,
        ).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
    }
}
