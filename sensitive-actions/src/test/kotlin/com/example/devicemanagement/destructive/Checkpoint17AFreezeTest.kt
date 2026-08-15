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
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_EXECUTOR_PRESENT)
        assertTrue(Checkpoint17BHardBlock.DESTRUCTIVE_POLICY_WRAPPER_PRESENT)
        assertTrue(Checkpoint17BHardBlock.DESTRUCTIVE_METADATA_PRESENT)
        assertTrue(Checkpoint17BHardBlock.PRODUCTION_REACHABLE_SIMULATION.not())
        assertTrue(Checkpoint17BHardBlock.TRUSTED_RUNTIME_COOLDOWN_PERSISTENCE_ADAPTER_PRESENT)
        assertTrue(Checkpoint17BHardBlock.REAL_DURABLE_DESTRUCTIVE_PRE_EXECUTION_AUDIT_PRESENT)
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_RUNTIME_COOLDOWN_ENFORCED.not())
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_DURABLE_AUDIT_ENFORCED.not())
        assertTrue(Checkpoint17BHardBlock.DESTRUCTIVE_ARTIFACT_IDENTITY_PRECONDITION_PRESENT)
        assertTrue(Checkpoint17BHardBlock.DESTRUCTIVE_HUMAN_APPROVAL_AUTHORITY_PRESENT)
        assertTrue(Checkpoint17BHardBlock.DESTRUCTIVE_WIPE_OPTION_POLICY_PRESENT)
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_ARTIFACT_IDENTITY_ENFORCED.not())
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_HUMAN_APPROVAL_ENFORCED.not())
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_WIPE_OPTION_POLICY_ENFORCED.not())
        assertTrue(Checkpoint17BHardBlock.DESTRUCTIVE_HUMAN_APPROVAL_RECORDED)
        assertTrue(Checkpoint17BHardBlock.DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED.not())
        assertTrue(Checkpoint17BHardBlock.GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED.not())
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
