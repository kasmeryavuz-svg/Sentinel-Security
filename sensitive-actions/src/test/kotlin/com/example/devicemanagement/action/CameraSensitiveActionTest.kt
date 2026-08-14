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

        assertTrue(result is ActionResult.Applied)
        result as ActionResult.Applied
        assertEquals(SensitiveActionOperation.DISABLE_CAMERA, result.operation)
        assertEquals(true, result.requestedDisabled)
        assertEquals(true, result.observedDisabled)
        assertAuthoritativeCorrelation(result, backend)
        assertEquals(listOf(true), backend.cameraWrites)
    }

    @Test
    fun `verified Device Owner can enable camera`() {
        val backend = RecordingBackend()

        val result = controller(backend).submit(trigger(
            SensitiveActionCommands.ENABLE_CAMERA,
        ))

        assertTrue(result is ActionResult.Applied)
        result as ActionResult.Applied
        assertEquals(SensitiveActionOperation.ENABLE_CAMERA, result.operation)
        assertEquals(false, result.requestedDisabled)
        assertEquals(false, result.observedDisabled)
        assertAuthoritativeCorrelation(result, backend)
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

        assertRejected(ordinaryResult, "decision_denied:DEVICE_OWNER_NOT_VERIFIED")
        assertRejected(profileResult, "decision_denied:PROFILE_OWNER_NOT_ALLOWED")
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

        assertRejected(result, "decision_denied:SERVICE_UNAVAILABLE")
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

        assertTrue(denied is ActionResult.Rejected)
        assertEquals("validation_changed", (denied as ActionResult.Rejected).reason)
        assertTrue(failed is ActionResult.Failed)
        assertEquals("security_exception", (failed as ActionResult.Failed).reason)
        assertTrue(denied.correlationId != "correlation")
        assertTrue(failed.correlationId != "correlation")
    }

    private fun controller(backend: SensitiveActionPolicyBackend) =
        createControlledController(
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

    private fun assertAuthoritativeCorrelation(
        result: ActionResult.Applied,
        backend: RecordingBackend,
    ) {
        assertTrue(result.correlationId.isNotBlank())
        assertTrue(result.correlationId != "correlation")
        assertEquals(listOf(result.correlationId), backend.correlations)
    }

    private fun assertRejected(result: ActionResult, reason: String) {
        assertTrue(result is ActionResult.Rejected)
        result as ActionResult.Rejected
        assertEquals(reason, result.reason)
        assertTrue(result.correlationId?.isNotBlank() == true)
        assertTrue(result.correlationId != "correlation")
    }

    private class RecordingBackend(
        private val authorization: SensitiveActionAuthorization = authorized,
        private val mutationResult: PolicyMutationResult? = null,
    ) : SensitiveActionPolicyBackend {
        val cameraWrites = mutableListOf<Boolean>()
        val correlations = mutableListOf<String>()

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
            correlations += correlationId
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
