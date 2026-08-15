package com.example.devicemanagement.destructive

import java.util.UUID

/**
 * Separate Checkpoint 17A simulation evidence. This is not the production
 * durable audit schema and must never authorize, arm, create a capability
 * or permit, replay, or invoke the simulation sink.
 *
 * Production [com.example.devicemanagement.audit.AuditEventPhase.APPLIED]
 * is never used here. A later 17B schema change is reserved in
 * docs/WIPE_17A_PREFLIGHT.md.
 */
internal sealed interface DestructiveEvidenceAppendResult {
    data class Persisted(val eventId: String) : DestructiveEvidenceAppendResult

    data object Failed : DestructiveEvidenceAppendResult
}

internal interface DestructiveSimulationEvidenceWriter {
    fun append(evidence: DestructiveSimulationEvidence): DestructiveEvidenceAppendResult

    fun records(): List<DestructiveSimulationEvidence>
}

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
        val persisted = if (evidence.eventId.isBlank()) {
            evidence.copy(eventId = eventIdGenerator())
        } else {
            evidence
        }
        stored += persisted
        return DestructiveEvidenceAppendResult.Persisted(persisted.eventId)
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
