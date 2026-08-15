package com.example.devicemanagement.destructive

import java.util.UUID

/**
 * Separate Checkpoint 17A simulation evidence. This is not the production
 * durable audit schema and must never authorize, arm, create a capability
 * or permit, replay, or invoke the simulation sink.
 *
 * 17A evidence proves ordering and fail-closed behavior only. It is not a
 * runtime persistence implementation. Real durable destructive
 * pre-execution evidence remains a Checkpoint 17B blocker.
 *
 * Production [com.example.devicemanagement.audit.AuditEventPhase.APPLIED]
 * is never used here. A later 17B schema change is reserved in
 * docs/WIPE_17A_PREFLIGHT.md.
 */
internal sealed interface DestructiveEvidenceAppendResult {
    data class Recorded(val eventId: String) : DestructiveEvidenceAppendResult

    data object Failed : DestructiveEvidenceAppendResult
}

internal interface DestructiveSimulationEvidenceWriter {
    fun append(evidence: DestructiveSimulationEvidence): DestructiveEvidenceAppendResult

    fun records(): List<DestructiveSimulationEvidence>
}

/**
 * Process-local simulation evidence writer. Not durable. Not a production
 * audit adapter. Used only to prove append ordering and fail-closed
 * rejection.
 */
internal class InMemoryDestructiveSimulationEvidenceWriter(
    private val eventIdGenerator: () -> String = { UUID.randomUUID().toString() },
) : DestructiveSimulationEvidenceWriter {
    private val stored = mutableListOf<DestructiveSimulationEvidence>()
    var failNext: Boolean = false
    var failAlways: Boolean = false
    var appendHook: ((DestructiveSimulationEvidence) -> Unit)? = null

    override fun append(evidence: DestructiveSimulationEvidence): DestructiveEvidenceAppendResult {
        appendHook?.invoke(evidence)
        if (failAlways || failNext) {
            failNext = false
            return DestructiveEvidenceAppendResult.Failed
        }
        val recorded = if (evidence.eventId.isBlank()) {
            evidence.copy(eventId = eventIdGenerator())
        } else {
            evidence
        }
        stored += recorded
        return DestructiveEvidenceAppendResult.Recorded(recorded.eventId)
    }

    override fun records(): List<DestructiveSimulationEvidence> = stored.toList()
}

internal fun simulationEvidence(
    correlationId: String,
    phase: DestructiveEvidencePhase,
    presentationWallClockMillis: Long,
    reasonCode: String? = null,
    binding: DestructiveTargetBinding? = null,
    callerRequestId: String? = null,
    eventId: String = "",
): DestructiveSimulationEvidence {
    return DestructiveSimulationEvidence(
        eventId = eventId,
        correlationId = correlationId,
        actionName = DestructiveSimulationActionNames.FACTORY_RESET_SIMULATION,
        phase = phase,
        presentationWallClockMillis = presentationWallClockMillis,
        reasonCode = reasonCode,
        boundPackage = binding?.runningPackage,
        boundAdminComponent = binding?.expectedAdminComponent,
        boundScope = binding?.scope,
        callerRequestId = callerRequestId,
    )
}
