package com.example.devicemanagement.action

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MockWipeIsolationTest {
    @Test
    fun `mock wipe remains simulation only and never calls DPM`() {
        val action = File("src/main/kotlin/com/example/devicemanagement/action/SafeMockWipeAction.kt")
            .readText()
        val registry = File(
            "src/main/kotlin/com/example/devicemanagement/action/SensitiveActionRegistry.kt",
        ).readText()
        val controller = File(
            "src/main/kotlin/com/example/devicemanagement/action/SensitiveActionController.kt",
        ).readText()

        assertTrue(action.contains("simulation_only"))
        assertTrue(action.contains("WIPE WOULD EXECUTE"))
        assertTrue(action.contains("ActionResult.Simulated"))
        assertFalse(action.contains("DevicePolicyManager"))
        assertFalse(action.contains("wipeData"))
        assertFalse(action.contains("wipeDevice"))
        assertFalse(action.contains("lockNow"))
        assertFalse(action.contains("resetPassword"))
        assertFalse(action.contains("setCameraDisabled"))
        assertTrue(registry.contains("MOCK_WIPE must never be registered in controlled mode"))
        assertTrue(registry.contains("fun failSafe"))
        assertTrue(controller.contains("createFailSafeController"))
        assertTrue(controller.contains("SensitiveActionRegistry.controlled"))
        assertFalse(controller.contains("wipeData"))
    }

    @Test
    fun `controlled factory never wires the fail-safe registry`() {
        val factory = File(
            "src/main/kotlin/com/example/devicemanagement/action/SensitiveActionController.kt",
        ).readText()
        val create = factory.substringAfter("object DeviceManagementSensitiveActionControllerFactory")
            .substringBefore("internal fun createFailSafeController")

        assertTrue(create.contains("createControlledController"))
        assertFalse(create.contains("failSafe"))
        assertFalse(create.contains("SafeMockWipeAction"))
        assertFalse(create.contains("MOCK_WIPE"))
    }
}
