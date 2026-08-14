package com.example.devicemanagement.recovery

import com.example.devicemanagement.audit.AuditActionNames
import com.example.devicemanagement.audit.AuditEvent
import com.example.devicemanagement.audit.AuditEventPhase
import com.example.devicemanagement.audit.AuditHistory
import com.example.devicemanagement.audit.AuditHistoryProvider
import com.example.devicemanagement.audit.AuditSchema
import com.example.devicemanagement.audit.DurableAuditRepository
import com.example.devicemanagement.audit.InMemoryAuditRecordStore
import com.example.devicemanagement.audit.InMemoryAuditState
import com.example.devicemanagement.logging.StructuredLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

class AuditRecoveryInspectorTest {
    private val logger = RecordingLogger()

    @Test
    fun `REQUESTED without terminal is interrupted after simulated restart`() {
        val state = InMemoryAuditState()
        DurableAuditRepository(InMemoryAuditRecordStore(state), logger)
            .append(requested("interrupted-1"))

        val restarted = DurableAuditRepository(InMemoryAuditRecordStore(state), logger)
        val inspection = AuditRecoveryInspector(restarted, logger).inspect()

        assertEquals(RecoveryInspectionHealth.HEALTHY, inspection.health)
        assertEquals(1, inspection.interruptedCount)
        assertEquals(listOf("interrupted-1"), inspection.interruptedCorrelationIds)
        assertEquals(AuditActionNames.DISABLE_CAMERA, inspection.interruptedRequests.single().actionName)
        assertEquals(AuditEventPhase.REQUESTED, restarted.latest(10).events.single().phase)
    }

    @Test
    fun `completed sequences are not classified as interrupted`() {
        val history = RecordingHistory(
            event("done", AuditEventPhase.REQUESTED, 1L),
            event("done", AuditEventPhase.APPLIED, 2L),
            event("denied", AuditEventPhase.REQUESTED, 3L),
            event("denied", AuditEventPhase.REJECTED, 4L),
            event("failed", AuditEventPhase.REQUESTED, 5L),
            event("failed", AuditEventPhase.FAILED, 6L),
            event("sim", AuditEventPhase.REQUESTED, 7L, AuditActionNames.MOCK_WIPE),
            event("sim", AuditEventPhase.SIMULATED, 8L, AuditActionNames.MOCK_WIPE),
        )

        val inspection = AuditRecoveryInspector(history, logger).inspect()

        assertEquals(0, inspection.interruptedCount)
        assertTrue(inspection.interruptedCorrelationIds.isEmpty())
        assertTrue(inspection.interruptedRequests.isEmpty())
    }

    @Test
    fun `inspection failure is fail-safe and does not execute anything`() {
        val inspection = AuditRecoveryInspector(ThrowingHistory(), logger).inspect()

        assertEquals(RecoveryInspectionHealth.UNAVAILABLE, inspection.health)
        assertEquals(0, inspection.interruptedCount)
        assertTrue(inspection.interruptedCorrelationIds.isEmpty())
        assertTrue(inspection.interruptedRequests.isEmpty())
        assertTrue(logger.events.contains("recovery_inspection_failed"))
        assertFalse(logger.events.any { it.contains("submit") || it.contains("execute") })
    }

    @Test
    fun `recovery API exposes no mutation or execution methods`() {
        val forbidden = setOf(
            "append",
            "insert",
            "update",
            "delete",
            "clear",
            "execute",
            "approve",
            "retry",
            "submit",
            "issue",
            "consume",
        )
        val names = RecoveryInspectionProvider::class.java.methods
            .filter { Modifier.isPublic(it.modifiers) }
            .map { it.name }
        assertEquals(listOf("inspect"), names.filter { it in forbidden || it == "inspect" })
        forbidden.forEach { method ->
            assertFalse(names.contains(method))
        }
    }

    private fun requested(correlationId: String) =
        com.example.devicemanagement.audit.AuditAppendRequest(
            eventId = "event-$correlationId",
            correlationId = correlationId,
            actionName = AuditActionNames.DISABLE_CAMERA,
            phase = AuditEventPhase.REQUESTED,
            presentationWallClockMillis = 1_700L,
            reasonCode = null,
        )

    private fun event(
        correlationId: String,
        phase: AuditEventPhase,
        sequence: Long,
        actionName: String = AuditActionNames.DISABLE_CAMERA,
    ) = AuditEvent(
        sequence = sequence,
        eventId = "event-$sequence",
        correlationId = correlationId,
        actionName = actionName,
        phase = phase,
        presentationWallClockMillis = sequence * 10L,
        reasonCode = null,
    )

    private class RecordingHistory(
        private vararg val events: AuditEvent,
    ) : AuditHistoryProvider {
        override fun latest(limit: Int): AuditHistory {
            return AuditHistory(
                events = events.take(limit).toList(),
                storedCount = events.size,
                retentionBound = AuditSchema.RETENTION_BOUND,
            )
        }
    }

    private class ThrowingHistory : AuditHistoryProvider {
        override fun latest(limit: Int): AuditHistory {
            error("audit history unavailable")
        }
    }

    private class RecordingLogger : StructuredLogger {
        val events = mutableListOf<String>()
        override fun info(event: String, fields: Map<String, Any?>) {
            events += event
        }
        override fun warn(event: String, fields: Map<String, Any?>) {
            events += event
        }
        override fun error(
            event: String,
            fields: Map<String, Any?>,
            throwable: Throwable?,
        ) {
            events += event
        }
    }
}
