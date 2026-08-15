package com.example.devicemanagement.destructive

import com.example.devicemanagement.logging.StructuredLogger
import java.util.UUID

/**
 * Trusted Checkpoint 17A request acceptor. Creates the authoritative
 * correlation ID, walks assessment → arming → authorization → simulated
 * executor, and never reaches an Android policy service.
 *
 * Not wired into production DeviceManagement composition.
 */
internal class DestructiveSimulationPipeline(
    private val liveFactsSource: DestructiveLiveFactsSource,
    private val armingAuthority: DestructiveArmingAuthority,
    private val authorizationAuthority: DestructiveAuthorizationAuthority,
    private val cooldown: DestructiveDenyOnlyCooldown,
    private val executor: SimulatedDestructiveExecutor,
    private val evidenceWriter: DestructiveSimulationEvidenceWriter,
    private val logger: StructuredLogger,
    private val correlationIdGenerator: () -> String = { UUID.randomUUID().toString() },
    private val presentationWallClockMillis: () -> Long = { 0L },
) {
    private var inFlight: Boolean = false

    fun submit(request: DestructiveSimulationRequest): DestructiveSimulationStatus {
        synchronized(this) {
            if (inFlight) {
                return closedSimulationStatus(
                    outcome = DestructiveSimulationOutcome.REJECTED,
                    reason = "duplicate_in_flight",
                    correlationId = null,
                    state = DestructiveExecutionState.IDLE,
                )
            }
            when (val decision = cooldown.canAcceptNewRequest()) {
                is CooldownDecision.Deny -> {
                    return closedSimulationStatus(
                        outcome = DestructiveSimulationOutcome.REJECTED,
                        reason = decision.reason,
                        correlationId = null,
                        state = DestructiveExecutionState.IDLE,
                    )
                }
                CooldownDecision.NotDenied -> Unit
            }
            inFlight = true
        }
        return try {
            runPipeline(request)
        } catch (_: Throwable) {
            closedSimulationStatus(
                outcome = DestructiveSimulationOutcome.REJECTED,
                reason = "evaluation_error",
                correlationId = null,
                state = DestructiveExecutionState.REJECTED,
            )
        } finally {
            synchronized(this) {
                inFlight = false
            }
        }
    }

    private fun runPipeline(request: DestructiveSimulationRequest): DestructiveSimulationStatus {
        val correlationId = DestructiveCorrelationId.generate(correlationIdGenerator)
        val requested = evidenceWriter.append(
            simulationEvidence(
                correlationId = correlationId.value,
                phase = DestructiveEvidencePhase.REQUESTED,
                presentationWallClockMillis = presentationWallClockMillis(),
                callerRequestId = request.callerRequestId,
            ),
        )
        if (requested is DestructiveEvidenceAppendResult.Failed) {
            return closedSimulationStatus(
                outcome = DestructiveSimulationOutcome.REJECTED,
                reason = "audit_persistence_unavailable",
                correlationId = correlationId.value,
                state = DestructiveExecutionState.REJECTED,
            )
        }

        when (val recorded = cooldown.recordAttempt()) {
            is CooldownRecordResult.Failed -> {
                return reject(
                    correlationId = correlationId.value,
                    reason = recorded.reason,
                )
            }
            CooldownRecordResult.Recorded -> Unit
        }

        val scope = request.requestedScope
            ?: return reject(correlationId.value, "unspecified_scope")
        val facts = try {
            liveFactsSource.currentFacts()
        } catch (_: Throwable) {
            return reject(correlationId.value, "live_facts_unavailable")
        }
        val binding = DestructiveTargetRules.bindingFromAssessedFacts(
            facts = facts,
            scope = scope,
            correlationId = correlationId,
        )
        DestructiveTargetRules.denyReason(binding, facts)?.let { reason ->
            return reject(correlationId.value, reason)
        }

        val armed = when (val arm = armingAuthority.arm(binding)) {
            is ArmingIssueResult.Rejected -> return reject(correlationId.value, arm.reason)
            is ArmingIssueResult.Armed -> arm
        }
        val authorized = when (
            val authorization = authorizationAuthority.authorize(armed.token, binding)
        ) {
            is DestructiveAuthorizationResult.Rejected -> {
                armingAuthority.disarm(armed.token)
                return reject(correlationId.value, authorization.reason)
            }
            is DestructiveAuthorizationResult.Authorized -> authorization
        }
        return executor.execute(
            capability = authorized.capability,
            expectedBinding = binding,
        )
    }

    private fun reject(
        correlationId: String,
        reason: String,
    ): DestructiveSimulationStatus {
        evidenceWriter.append(
            simulationEvidence(
                correlationId = correlationId,
                phase = DestructiveEvidencePhase.REJECTED,
                presentationWallClockMillis = presentationWallClockMillis(),
                reasonCode = reason,
                callerRequestId = null,
            ),
        )
        logger.warn(
            event = "destructive_simulation_rejected",
            fields = mapOf(
                "correlation_id" to correlationId,
                "reason" to reason,
            ),
        )
        return closedSimulationStatus(
            outcome = DestructiveSimulationOutcome.REJECTED,
            reason = reason,
            correlationId = correlationId,
            state = DestructiveExecutionState.REJECTED,
        )
    }
}
