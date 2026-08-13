package com.example.devicemanagement.action

import com.example.devicemanagement.integration.MonotonicTimeSource
import com.example.devicemanagement.integration.PolicyMutationResult
import com.example.devicemanagement.integration.SensitiveActionAuthorization
import com.example.devicemanagement.integration.SensitiveActionPolicyBackend
import com.example.devicemanagement.logging.StructuredLogger
import com.example.devicemanagement.trigger.SensitiveActionCommands
import com.example.devicemanagement.trigger.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraSensitiveActionTest {
    private val logger = NoOpLogger()

    @Test
    fun `verified Device Owner can disable camera`() {
        val backend = RecordingBackend()

        val result = controller(backend).submit(trigger(
            SensitiveActionCommands.DISABLE_CAMERA,
        ))

        assertEquals(
            ActionResult.Applied(
                operation = SensitiveActionOperation.DISABLE_CAMERA,
                requestedDisabled = true,
                observedDisabled = true,
                correlationId = "correlation",
            ),
            result,
        )
        assertEquals(listOf(true), backend.cameraWrites)
    }

    @Test
    fun `verified Device Owner can enable camera`() {
        val backend = RecordingBackend()

        val result = controller(backend).submit(trigger(
            SensitiveActionCommands.ENABLE_CAMERA,
        ))

        assertEquals(
            ActionResult.Applied(
                operation = SensitiveActionOperation.ENABLE_CAMERA,
                requestedDisabled = false,
                observedDisabled = false,
                correlationId = "correlation",
            ),
            result,
        )
        assertEquals(listOf(false), backend.cameraWrites)
    }

    @Test
    fun `ordinary app and Profile Owner are denied before camera writer`() {
        val ordinaryBackend = RecordingBackend(
            authorization = authorized.copy(verifiedDeviceOwner = false),
        )
        val profileBackend = RecordingBackend(
            authorization = authorized.copy(
                verifiedDeviceOwner = false,
                profileOwner = true,
            ),
        )

        val ordinaryResult = controller(ordinaryBackend).submit(trigger(
            SensitiveActionCommands.DISABLE_CAMERA,
        ))
        val profileResult = controller(profileBackend).submit(trigger(
            SensitiveActionCommands.DISABLE_CAMERA,
        ))

        assertEquals(
            ActionResult.Rejected("decision_denied:DEVICE_OWNER_NOT_VERIFIED"),
            ordinaryResult,
        )
        assertEquals(
            ActionResult.Rejected("decision_denied:PROFILE_OWNER_NOT_ALLOWED"),
            profileResult,
        )
        assertTrue(ordinaryBackend.cameraWrites.isEmpty())
        assertTrue(profileBackend.cameraWrites.isEmpty())
    }

    @Test
    fun `unavailable policy service is denied before camera writer`() {
        val backend = RecordingBackend(
            authorization = authorized.copy(policyServiceAvailable = false),
        )

        val result = controller(backend).submit(trigger(
            SensitiveActionCommands.DISABLE_CAMERA,
        ))

        assertEquals(
            ActionResult.Rejected("decision_denied:SERVICE_UNAVAILABLE"),
            result,
        )
        assertTrue(backend.cameraWrites.isEmpty())
    }

    @Test
    fun `malformed and excessive lifetime requests cannot reach camera writer`() {
        val backend = RecordingBackend()
        val controller = controller(backend)

        val malformed = controller.submit(Trigger("unknown", "correlation", 2_000L))
        val excessiveLifetime = controller.submit(
            Trigger(
                SensitiveActionCommands.DISABLE_CAMERA,
                "correlation",
                61_001L,
            ),
        )

        assertTrue(malformed is ActionResult.Rejected)
        assertTrue(excessiveLifetime is ActionResult.Rejected)
        assertTrue(backend.cameraWrites.isEmpty())
    }

    @Test
    fun `camera backend denial and failure preserve correlation ID`() {
        val denied = controller(
            RecordingBackend(
                mutationResult = PolicyMutationResult.Denied("validation_changed"),
            ),
        ).submit(trigger(SensitiveActionCommands.DISABLE_CAMERA))
        val failed = controller(
            RecordingBackend(
                mutationResult = PolicyMutationResult.Failed("security_exception"),
            ),
        ).submit(trigger(SensitiveActionCommands.ENABLE_CAMERA))

        assertEquals(
            ActionResult.Rejected("validation_changed", "correlation"),
            denied,
        )
        assertEquals(
            ActionResult.Failed("security_exception", "correlation"),
            failed,
        )
    }

    private fun controller(backend: SensitiveActionPolicyBackend) =
        SensitiveActionController.createControlled(
            backend = backend,
            logger = logger,
            nowEpochMillis = { 1_000L },
            monotonicTimeSource = MonotonicTimeSource { 100L },
        )

    private fun trigger(command: String) = Trigger(
        command = command,
        requestId = "correlation",
        expiresAtEpochMillis = 2_000L,
    )

    private class RecordingBackend(
        private val authorization: SensitiveActionAuthorization = authorized,
        private val mutationResult: PolicyMutationResult? = null,
    ) : SensitiveActionPolicyBackend {
        val cameraWrites = mutableListOf<Boolean>()

        override fun currentAuthorization(): SensitiveActionAuthorization = authorization

        override fun applyScreenCaptureDisabled(
            disabled: Boolean,
            correlationId: String,
        ): PolicyMutationResult {
            error("camera action must not invoke screen-capture policy")
        }

        override fun applyCameraDisabled(
            disabled: Boolean,
            correlationId: String,
        ): PolicyMutationResult {
            cameraWrites += disabled
            return mutationResult ?: PolicyMutationResult.Applied(disabled, disabled)
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
        val authorized = SensitiveActionAuthorization(
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
