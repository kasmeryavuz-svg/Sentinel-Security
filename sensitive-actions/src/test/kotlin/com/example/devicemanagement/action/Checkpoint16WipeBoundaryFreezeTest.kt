package com.example.devicemanagement.action

import com.example.devicemanagement.integration.PolicyMutationResult
import com.example.devicemanagement.integration.SensitiveActionAuthorization
import com.example.devicemanagement.integration.SensitiveActionPolicyBackend
import com.example.devicemanagement.trigger.SensitiveActionCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Checkpoint16WipeBoundaryFreezeTest {
    @Test
    fun `controlled registry remains exactly the six reversible commands`() {
        val registry = SensitiveActionRegistry.controlled(RecordingBackend())
        assertEquals(
            setOf(
                SensitiveActionCommands.DISABLE_SCREEN_CAPTURE,
                SensitiveActionCommands.ENABLE_SCREEN_CAPTURE,
                SensitiveActionCommands.DISABLE_CAMERA,
                SensitiveActionCommands.ENABLE_CAMERA,
                SensitiveActionCommands.DISABLE_STATUS_BAR,
                SensitiveActionCommands.ENABLE_STATUS_BAR,
            ),
            registry.commands(),
        )
        assertFalse(DeviceActionType.MOCK_WIPE in registry.actionTypes())
        assertEquals(6, registry.commands().size)
    }

    @Test
    fun `MOCK_WIPE remains outside controlled production composition`() {
        val factory = File(
            "src/main/kotlin/com/example/devicemanagement/action/SensitiveActionController.kt",
        ).readText()
        val productionCreate = factory
            .substringAfter("object DeviceManagementSensitiveActionControllerFactory")
            .substringBefore("internal fun createFailSafeController")

        assertTrue(productionCreate.contains("createControlledController"))
        assertFalse(productionCreate.contains("failSafe"))
        assertFalse(productionCreate.contains("SafeMockWipeAction"))
        assertFalse(productionCreate.contains("MOCK_WIPE"))
        assertFalse(productionCreate.contains("wipeData"))
        assertFalse(productionCreate.contains("wipeDevice"))
    }

    @Test
    fun `production sensitive-action sources do not call destructive DPM APIs`() {
        val sources = File("src/main/kotlin")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }

        assertFalse(sources.contains("wipeData"))
        assertFalse(sources.contains("wipeDevice"))
        assertFalse(sources.contains("DevicePolicyManager"))
        assertTrue(sources.contains("MOCK_WIPE must never be registered in controlled mode"))
        assertTrue(sources.contains("simulation_only"))
        assertTrue(sources.contains("WIPE WOULD EXECUTE"))
    }

    private class RecordingBackend : SensitiveActionPolicyBackend {
        override fun currentAuthorization() = AUTHORIZED

        override fun applyScreenCaptureDisabled(
            disabled: Boolean,
            correlationId: String,
        ) = PolicyMutationResult.Applied(disabled, disabled)

        override fun applyCameraDisabled(
            disabled: Boolean,
            correlationId: String,
        ) = PolicyMutationResult.Applied(disabled, disabled)

        override fun applyStatusBarDisabled(
            disabled: Boolean,
            correlationId: String,
        ) = PolicyMutationResult.Applied(disabled, disabled)
    }

    private companion object {
        val AUTHORIZED = SensitiveActionAuthorization(
            policyServiceAvailable = true,
            sensitiveActionsEnabled = true,
            verifiedDeviceOwner = true,
            profileOwner = false,
            expectedAdminReceiverRegistered = true,
            expectedAdminActive = true,
            managementStateConsistent = true,
        )
    }
}
