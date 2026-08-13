@file:OptIn(com.example.devicemanagement.integration.SensitiveActionCompositionApi::class)

package com.example.devicemanagement.action

import com.example.devicemanagement.integration.PolicyMutationResult
import com.example.devicemanagement.integration.SensitiveActionAuthorization
import com.example.devicemanagement.integration.SensitiveActionPolicyBackend
import com.example.devicemanagement.logging.StructuredLogger
import com.example.devicemanagement.trigger.SensitiveActionCommands
import com.example.devicemanagement.trigger.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveActionRegistryTest {
    private val backend = RecordingBackend()
    private val logger = NoOpLogger()

    @Test
    fun `controlled registry is fixed complete and contains no mock wipe`() {
        val registry = SensitiveActionRegistry.controlled(backend)
        val expectedCommands = setOf(
            SensitiveActionCommands.DISABLE_SCREEN_CAPTURE,
            SensitiveActionCommands.ENABLE_SCREEN_CAPTURE,
            SensitiveActionCommands.DISABLE_CAMERA,
            SensitiveActionCommands.ENABLE_CAMERA,
        )
        val expectedTypes = setOf(
            DeviceActionType.DISABLE_SCREEN_CAPTURE,
            DeviceActionType.ENABLE_SCREEN_CAPTURE,
            DeviceActionType.DISABLE_CAMERA,
            DeviceActionType.ENABLE_CAMERA,
        )

        assertEquals(expectedCommands, registry.commands())
        assertEquals(expectedTypes, registry.actionTypes())
        assertFalse(DeviceActionType.MOCK_WIPE in registry.actionTypes())
        expectedCommands.forEach { command ->
            val type = registry.actionTypeForCommand(command)
            assertNotNull(type)
            assertNotNull(registry.actionForType(requireNotNull(type)))
        }
    }

    @Test
    fun `fail-safe registry contains only mock wipe simulation`() {
        val registry = SensitiveActionRegistry.failSafe(logger)

        assertEquals(
            setOf(SensitiveActionCommands.MOCK_WIPE_SIMULATION),
            registry.commands(),
        )
        assertEquals(setOf(DeviceActionType.MOCK_WIPE), registry.actionTypes())
    }

    @Test
    fun `all action types have exactly one explicit registry implementation`() {
        val controlled = SensitiveActionRegistry.controlled(backend).actionTypes()
        val failSafe = SensitiveActionRegistry.failSafe(logger).actionTypes()

        assertTrue(controlled.intersect(failSafe).isEmpty())
        assertEquals(DeviceActionType.entries.toSet(), controlled + failSafe)
    }

    @Test
    fun `duplicate command registration fails construction`() {
        val error = runCatching {
            SensitiveActionRegistry(
                listOf(
                    registration("duplicate", DeviceActionType.DISABLE_CAMERA),
                    registration("duplicate", DeviceActionType.ENABLE_CAMERA),
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("duplicate sensitive action commands"))
    }

    @Test
    fun `duplicate action type registration fails construction`() {
        val error = runCatching {
            SensitiveActionRegistry(
                listOf(
                    registration("one", DeviceActionType.DISABLE_CAMERA),
                    registration("two", DeviceActionType.DISABLE_CAMERA),
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("duplicate sensitive action types"))
    }

    @Test
    fun `unknown command has no action and fails closed`() {
        val registry = SensitiveActionRegistry.controlled(backend)

        assertNull(registry.actionTypeForCommand("unknown"))
        val result = SensitiveActionController.createControlledInternal(
            backend = backend,
            logger = logger,
            nowEpochMillis = { 1_000L },
        ).submit(Trigger("unknown", "caller-id", 2_000L))

        assertTrue(result is ActionResult.Rejected)
        assertTrue(backend.writes.isEmpty())
    }

    @Test
    fun `controlled mode cannot accept mock wipe`() {
        val result = SensitiveActionController.createControlledInternal(
            backend = backend,
            logger = logger,
            nowEpochMillis = { 1_000L },
        ).submit(Trigger("mock_wipe", "caller-id", 2_000L))

        assertTrue(result is ActionResult.Rejected)
        assertTrue(backend.writes.isEmpty())
    }

    @Test
    fun `registry collections cannot be mutated after construction`() {
        val registry = SensitiveActionRegistry.controlled(backend)

        val commandMutation = runCatching {
            @Suppress("UNCHECKED_CAST")
            (registry.commands() as MutableSet<String>).add("injected")
        }.exceptionOrNull()
        val typeMutation = runCatching {
            @Suppress("UNCHECKED_CAST")
            (registry.actionTypes() as MutableSet<DeviceActionType>)
                .add(DeviceActionType.MOCK_WIPE)
        }.exceptionOrNull()

        assertTrue(commandMutation is UnsupportedOperationException)
        assertTrue(typeMutation is UnsupportedOperationException)
        assertNull(registry.actionTypeForCommand("injected"))
    }

    @Test
    fun `registry exposes no post-construction registration operation`() {
        val operationNames = SensitiveActionRegistry::class.java.declaredMethods
            .map { it.name.lowercase() }

        assertFalse(operationNames.any {
            it.startsWith("register") || it.startsWith("add") || it.startsWith("put")
        })
    }

    private fun registration(
        command: String,
        type: DeviceActionType,
    ): SensitiveActionRegistration {
        return SensitiveActionRegistration(
            command = command,
            action = object : DeviceAction {
                override val type = type
                override fun execute(request: ActionRequest): ActionResult {
                    return ActionResult.Simulated("test", request.correlationId)
                }
            },
        )
    }

    private class RecordingBackend : SensitiveActionPolicyBackend {
        val writes = mutableListOf<String>()

        override fun currentAuthorization() = AUTHORIZED

        override fun applyScreenCaptureDisabled(
            disabled: Boolean,
            correlationId: String,
        ): PolicyMutationResult {
            writes += "screen:$disabled"
            return PolicyMutationResult.Applied(disabled, disabled)
        }

        override fun applyCameraDisabled(
            disabled: Boolean,
            correlationId: String,
        ): PolicyMutationResult {
            writes += "camera:$disabled"
            return PolicyMutationResult.Applied(disabled, disabled)
        }
    }

    private class NoOpLogger : StructuredLogger {
        override fun info(event: String, fields: Map<String, Any?>) = Unit
        override fun warn(event: String, fields: Map<String, Any?>) = Unit
        override fun error(
            event: String,
            fields: Map<String, Any?>,
            throwable: Throwable?,
        ) = Unit
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
