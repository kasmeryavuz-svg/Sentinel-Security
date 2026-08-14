package com.example.devicemanagement.recovery

import com.example.devicemanagement.audit.AuditEvent
import com.example.devicemanagement.audit.AuditEventPhase
import com.example.devicemanagement.audit.AuditHistoryProvider
import com.example.devicemanagement.audit.AuditSchema
import com.example.devicemanagement.logging.StructuredLogger

/**
 * Presentation-time recovery inspection.
 *
 * This class reads durable audit history and classifies unmatched REQUESTED
 * events as interrupted. It has no execution capability: it cannot submit
 * triggers, issue or consume approvals, call ActionExecutor, mutate
 * DevicePolicyManager, or append/delete audit records.
 *
 * Interrupted records are left unchanged. Classification does not imply that
 * the original action completed, and it never becomes a retry.
 */
class AuditRecoveryInspector(
    private val history: AuditHistoryProvider,
    private val logger: StructuredLogger,
) : RecoveryInspectionProvider {
    override fun inspect(): RecoveryInspection {
        return try {
            val events = history.latest(AuditSchema.RETENTION_BOUND).events
            val interrupted = classifyInterrupted(events)
            RecoveryInspection(
                health = RecoveryInspectionHealth.HEALTHY,
                interruptedCount = interrupted.size,
                interruptedCorrelationIds = interrupted.map { it.correlationId },
                interruptedRequests = interrupted,
            )
        } catch (error: Throwable) {
            logger.warn(
                event = "recovery_inspection_failed",
                fields = mapOf(
                    "error_type" to error.javaClass.simpleName,
                    "executed" to false,
                ),
            )
            RecoveryInspection(
                health = RecoveryInspectionHealth.UNAVAILABLE,
                interruptedCount = 0,
                interruptedCorrelationIds = emptyList(),
                interruptedRequests = emptyList(),
            )
        }
    }

    private fun classifyInterrupted(events: List<AuditEvent>): List<InterruptedRequest> {
        return events
            .groupBy { it.correlationId }
            .values
            .mapNotNull { sequence ->
                val requested = sequence
                    .filter { it.phase == AuditEventPhase.REQUESTED }
                    .minByOrNull { it.sequence }
                    ?: return@mapNotNull null
                if (sequence.any { it.phase in TERMINAL_PHASES }) {
                    null
                } else {
                    InterruptedRequest(
                        correlationId = requested.correlationId,
                        actionName = requested.actionName,
                        sequence = requested.sequence,
                        presentationWallClockMillis = requested.presentationWallClockMillis,
                    )
                }
            }
            .sortedBy { it.sequence }
    }

    private companion object {
        val TERMINAL_PHASES = setOf(
            AuditEventPhase.APPLIED,
            AuditEventPhase.REJECTED,
            AuditEventPhase.FAILED,
            AuditEventPhase.SIMULATED,
        )
    }
}

/**
 * Production composition entry point. This implementation module is not present
 * on the app compile classpath; app receives only RecoveryInspectionProvider.
 */
object DeviceManagementRecoveryInspectionFactory {
    fun create(
        history: AuditHistoryProvider,
        logger: StructuredLogger,
    ): RecoveryInspectionProvider {
        return AuditRecoveryInspector(
            history = history,
            logger = logger,
        )
    }
}
