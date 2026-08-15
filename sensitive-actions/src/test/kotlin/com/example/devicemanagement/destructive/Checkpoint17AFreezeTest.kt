package com.example.devicemanagement.destructive

import com.example.devicemanagement.action.DeviceActionType
import com.example.devicemanagement.action.SensitiveActionRegistry
import com.example.devicemanagement.integration.PolicyMutationResult
import com.example.devicemanagement.integration.SensitiveActionAuthorization
import com.example.devicemanagement.integration.SensitiveActionPolicyBackend
import com.example.devicemanagement.trigger.SensitiveActionCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Checkpoint17AFreezeTest {
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
    fun `17A simulation is not production reachable and does not mention destructive Android APIs`() {
        val sources = File("src/main/kotlin")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        assertFalse(sources.contains("wipeData"))
        assertFalse(sources.contains("wipeDevice"))
        assertFalse(sources.contains("import android.app.admin.DevicePolicyManager"))
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_EXECUTOR_PRESENT.not())
        assertTrue(Checkpoint17BHardBlock.DESTRUCTIVE_POLICY_WRAPPER_PRESENT.not())
        assertTrue(Checkpoint17BHardBlock.DESTRUCTIVE_METADATA_PRESENT.not())
        assertTrue(Checkpoint17BHardBlock.PRODUCTION_REACHABLE_SIMULATION.not())
    }

    @Test
    fun `17B gates are listed for explicit review`() {
        assertTrue(
            Checkpoint17BHardBlock.gatesRequiringExplicitModification.any {
                it.contains("checkpoint17BForbiddenDpmMethodNames")
            },
        )
        assertTrue(
            Checkpoint17BHardBlock.gatesRequiringExplicitModification.any {
                it.contains("device_admin_receiver.xml")
            },
        )
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
