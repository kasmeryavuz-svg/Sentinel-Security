package com.example.devicemanagement.action

import com.example.devicemanagement.logging.StructuredLogger
import com.example.devicemanagement.trigger.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveActionControllerPipelineTest {
    private val logger = RecordingLogger()

    @Test
    fun `malformed request is denied with a human-readable reason`() {
        val result = SensitiveActionController.createSimulation(logger).submit(
            Trigger(
                command = "not-a-command",
                requestId = "correlation-malformed",
                expiresAtEpochMillis = Long.MAX_VALUE,
            ),
        )

        assertEquals(
            SensitiveActionResult.Denied(
                correlationId = "correlation-malformed",
                reason = "The request command is not recognized.",
            ),
            result,
        )
        assertFalse(logger.events.any { it.event == SafeMockWipeAction.WIPE_LOG_MESSAGE })
    }

    @Test
    fun `expired request is denied with a human-readable reason`() {
        val result = SensitiveActionController.createSimulation(logger).submit(
            Trigger(
                command = "mock_wipe",
                requestId = "correlation-expired",
                expiresAtEpochMillis = 1,
            ),
        )

        assertEquals(
            SensitiveActionResult.Denied(
                correlationId = "correlation-expired",
                reason = "The request has expired.",
            ),
            result,
        )
        assertFalse(logger.events.any { it.event == SafeMockWipeAction.WIPE_LOG_MESSAGE })
    }

    @Test
    fun `valid request is denied when simulation policy is unavailable`() {
        val result = SensitiveActionController.createFailSafe(logger).submit(
            Trigger(
                command = "mock_wipe",
                requestId = "correlation-denied",
                expiresAtEpochMillis = Long.MAX_VALUE,
            ),
        )

        assertEquals(
            SensitiveActionResult.Denied(
                correlationId = "correlation-denied",
                reason = "The management service is unavailable.",
            ),
            result,
        )
        assertFalse(logger.events.any { it.event == SafeMockWipeAction.WIPE_LOG_MESSAGE })
    }

    @Test
    fun `valid simulation traverses the complete pipeline and executes only the mock`() {
        val result = SensitiveActionController.createSimulation(logger).submit(
            Trigger(
                command = "mock_wipe",
                requestId = "correlation-valid",
                expiresAtEpochMillis = Long.MAX_VALUE,
            ),
        )

        assertEquals(
            SensitiveActionResult.Approved(
                correlationId = "correlation-valid",
                message = SafeMockWipeAction.WIPE_LOG_MESSAGE,
            ),
            result,
        )
        assertEquals(
            1,
            logger.events.count { it.event == SafeMockWipeAction.WIPE_LOG_MESSAGE },
        )
        assertTrue(
            logger.events.any {
                it.event == "action_decision" && it.fields["outcome"] == "approved"
            },
        )
        assertTrue(logger.events.any { it.event == "action_execution_completed" })
    }

    @Test
    fun `every request receives a displayed and logged correlation id`() {
        val result = SensitiveActionController.createSimulation(logger).submit(null)

        assertTrue(result.correlationId.isNotBlank())
        assertTrue(
            logger.events.any {
                it.event == "sensitive_action_submitted" &&
                    it.fields["correlation_id"] == result.correlationId
            },
        )
        assertTrue(
            logger.events.any {
                it.event == "sensitive_action_completed" &&
                    it.fields["correlation_id"] == result.correlationId
            },
        )
    }

    private class RecordingLogger : StructuredLogger {
        val events = mutableListOf<LogEvent>()

        override fun info(event: String, fields: Map<String, Any?>) {
            events += LogEvent(event, fields)
        }

        override fun warn(event: String, fields: Map<String, Any?>) {
            events += LogEvent(event, fields)
        }

        override fun error(
            event: String,
            fields: Map<String, Any?>,
            throwable: Throwable?,
        ) {
            events += LogEvent(event, fields)
        }
    }

    private data class LogEvent(
        val event: String,
        val fields: Map<String, Any?>,
    )
}
