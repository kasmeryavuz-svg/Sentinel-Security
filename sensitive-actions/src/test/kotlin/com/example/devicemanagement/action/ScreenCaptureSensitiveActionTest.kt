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

class ScreenCaptureSensitiveActionTest {
    private val logger = NoOpLogger()

    @Test
    fun `verified Device Owner can disable screen capture`() {
        val backend = RecordingBackend()

        val result = controller(backend).submit(trigger(
            SensitiveActionCommands.DISABLE_SCREEN_CAPTURE,
        ))

        assertTrue(result is ActionResult.Applied)
        assertEquals(listOf(true), backend.writes)
    }

    @Test
    fun `verified Device Owner can enable screen capture`() {
        val backend = RecordingBackend()

        val result = controller(backend).submit(trigger(
            SensitiveActionCommands.ENABLE_SCREEN_CAPTURE,
        ))

        assertTrue(result is ActionResult.Applied)
        assertEquals(listOf(false), backend.writes)
    }

    @Test
    fun `ordinary app state is denied before policy writer`() {
        val backend = RecordingBackend(
            authorization = authorized.copy(verifiedDeviceOwner = false),
        )

        val result = controller(backend).submit(trigger(
            SensitiveActionCommands.DISABLE_SCREEN_CAPTURE,
        ))

        assertEquals(
            ActionResult.Rejected("decision_denied:DEVICE_OWNER_NOT_VERIFIED"),
            result,
        )
        assertTrue(backend.writes.isEmpty())
    }

    @Test
    fun `Profile Owner state is denied before policy writer`() {
        val backend = RecordingBackend(
            authorization = authorized.copy(
                verifiedDeviceOwner = false,
                profileOwner = true,
            ),
        )

        val result = controller(backend).submit(trigger(
            SensitiveActionCommands.DISABLE_SCREEN_CAPTURE,
        ))

        assertEquals(
            ActionResult.Rejected("decision_denied:PROFILE_OWNER_NOT_ALLOWED"),
            result,
        )
        assertTrue(backend.writes.isEmpty())
    }

    @Test
    fun `unavailable policy service is denied before policy writer`() {
        val backend = RecordingBackend(
            authorization = authorized.copy(policyServiceAvailable = false),
        )

        val result = controller(backend).submit(trigger(
            SensitiveActionCommands.DISABLE_SCREEN_CAPTURE,
        ))

        assertEquals(
            ActionResult.Rejected("decision_denied:SERVICE_UNAVAILABLE"),
            result,
        )
        assertTrue(backend.writes.isEmpty())
    }

    @Test
    fun `malformed and excessive lifetime requests cannot reach policy writer`() {
        val backend = RecordingBackend()
        val controller = controller(backend)

        val malformed = controller.submit(
            Trigger("unknown", "correlation", 2_000L),
        )
        val excessiveLifetime = controller.submit(
            Trigger(
                SensitiveActionCommands.DISABLE_SCREEN_CAPTURE,
                "correlation",
                61_001L,
            ),
        )

        assertTrue(malformed is ActionResult.Rejected)
        assertTrue(excessiveLifetime is ActionResult.Rejected)
        assertTrue(backend.writes.isEmpty())
    }

    @Test
    fun `backend denial and failure are preserved with correlation ID`() {
        val deniedBackend = RecordingBackend(
            mutationResult = PolicyMutationResult.Denied("validation_changed"),
        )
        val failedBackend = RecordingBackend(
            mutationResult = PolicyMutationResult.Failed("security_exception"),
        )

        val denied = controller(deniedBackend).submit(trigger(
            SensitiveActionCommands.DISABLE_SCREEN_CAPTURE,
        ))
        val failed = controller(failedBackend).submit(trigger(
            SensitiveActionCommands.ENABLE_SCREEN_CAPTURE,
        ))

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
        val writes = mutableListOf<Boolean>()

        override fun currentAuthorization(): SensitiveActionAuthorization = authorization

        override fun applyScreenCaptureDisabled(
            disabled: Boolean,
            correlationId: String,
        ): PolicyMutationResult {
            writes += disabled
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
