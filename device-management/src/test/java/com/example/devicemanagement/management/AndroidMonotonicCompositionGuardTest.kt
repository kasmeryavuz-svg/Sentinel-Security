package com.example.devicemanagement.management

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AndroidMonotonicCompositionGuardTest {
    @Test
    fun `production composition uses Android elapsed realtime monotonic clock`() {
        val sourceRoot = File(
            requireNotNull(System.getProperty("deviceManagementSourceDir")),
        )
        val compositionSource = File(
            sourceRoot,
            "java/com/example/devicemanagement/management/DeviceManagementSensitiveActions.kt",
        ).readText()

        assertTrue(compositionSource.contains("AndroidElapsedRealtimeMonotonicTimeSource"))
        assertTrue(compositionSource.contains("SystemClock.elapsedRealtime()"))
        assertTrue(
            compositionSource.contains(
                "monotonicTimeSource = AndroidElapsedRealtimeMonotonicTimeSource",
            ),
        )
        assertFalse(compositionSource.contains("System.nanoTime"))
        assertFalse(compositionSource.contains("currentTimeMillis"))
    }
}

class StatusBarSdkGateGuardTest {
    @Test
    fun `status bar platform service is gated to API 34 and uses verified read back`() {
        val sourceRoot = File(
            requireNotNull(System.getProperty("deviceManagementSourceDir")),
        )
        val infrastructure = File(
            sourceRoot,
            "java/com/example/devicemanagement/management/AndroidDeviceManagementInfrastructure.kt",
        ).readText()
        val statusProvider = File(
            sourceRoot,
            "java/com/example/devicemanagement/management/StatusBarPolicyStatus.kt",
        ).readText()
        val executor = File(
            sourceRoot,
            "java/com/example/devicemanagement/management/VerifiedPolicyMutation.kt",
        ).readText()

        assertTrue(infrastructure.contains("UPSIDE_DOWN_CAKE"))
        assertTrue(infrastructure.contains("manager.isStatusBarDisabled"))
        assertTrue(infrastructure.contains("manager.setStatusBarDisabled(adminComponent, disabled)"))
        assertTrue(statusProvider.contains("STATUS_BAR_POLICY_MIN_SDK"))
        assertTrue(statusProvider.contains("UPSIDE_DOWN_CAKE"))
        assertTrue(executor.contains("setter_rejected"))
        assertTrue(executor.contains("service.isStatusBarDisabled()"))
        assertFalse(infrastructure.contains("dumpsys"))
        assertFalse(infrastructure.contains("Settings.Global"))
        assertFalse(infrastructure.contains("LockTask"))
    }
}
