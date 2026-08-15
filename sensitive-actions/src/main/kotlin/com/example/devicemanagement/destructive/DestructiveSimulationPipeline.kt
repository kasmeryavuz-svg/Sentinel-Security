package com.example.devicemanagement.destructive

import com.example.devicemanagement.logging.StructuredLogger
import java.util.UUID

/**
 * Trusted Checkpoint 17A request acceptor. Creates the authoritative
 * correlation ID, records a deny-only attempt, then walks lease issuance →
 * assessment → arming → authorization → simulated executor. Never reaches
 * an Android policy service.
 *
 * Not wired into production DeviceManagement composition.
 */
internal class DestructiveSimulationPipeline(
    private val liveFactsSource: DestructiveLiveFactsSource,
    private val admissionAuthority: DestructiveAttemptAdmissionAuthority,
    private val armingAuthority: DestructiveArmingAuthority,
    private val authorizationAuthority: DestructiveAuthorizationAuthority,
    private val cooldown: DestructiveDenyOnlyCooldown,
    private val executor: SimulatedDestructiveExecutor,
    private val evidenceWriter: DestructiveSimulationEvidenceWriter,
    private val cleanup: DestructiveTerminalCleanup,
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
        var lease: DestructiveAttemptLease? = null
        var armToken: DestructiveArmingToken? = null
        var handedToExecutor = false
        val correlationId = DestructiveCorrelationId.generate(correlationIdGenerator)
        try {
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

            when (val recorded = admissionAuthority.recordCountedAttempt()) {
                is CooldownRecordResult.Failed -> return reject(correlationId.value, recorded.reason)
                CooldownRecordResult.Recorded -> Unit
            }

            val scope = request.requestedScope
            if (scope == null) {
                return reject(correlationId.value, "unspecified_scope")
            }
            if (scope != DestructiveScope.DEVICE_FACTORY_RESET) {
                return reject(correlationId.value, "unsupported_scope")
            }

            val admitted = when (val issued = admissionAuthority.issueLease(correlationId, scope)) {
                is AttemptAdmissionResult.Rejected -> return reject(correlationId.value, issued.reason)
                is AttemptAdmissionResult.Admitted -> issued
            }
            lease = admitted.lease

            val facts = try {
                liveFactsSource.currentFacts()
            } catch (_: Throwable) {
                return reject(correlationId.value, "live_facts_unavailable", lease)
            }
            val binding = DestructiveTargetRules.bindingFromAssessedFacts(
                facts = facts,
                scope = scope,
                correlationId = correlationId,
            )
            DestructiveTargetRules.denyReason(binding, facts)?.let { reason ->
                return reject(correlationId.value, reason, lease)
            }
            when (val bound = admissionAuthority.bindTarget(admitted.lease, binding)) {
                is AttemptBindResult.Rejected -> return reject(correlationId.value, bound.reason, lease)
                is AttemptBindResult.Bound -> Unit
            }

            val armed = when (val arm = armingAuthority.arm(binding, admitted.lease)) {
                is ArmingIssueResult.Rejected -> return reject(correlationId.value, arm.reason, lease)
                is ArmingIssueResult.Armed -> arm
            }
            armToken = armed.token
            val authorized = when (
                val authorization = authorizationAuthority.authorize(
                    armToken = armed.token,
                    binding = binding,
                    attemptLease = admitted.lease,
                )
            ) {
                is DestructiveAuthorizationResult.Rejected -> {
                    return reject(correlationId.value, authorization.reason, lease, armed.token)
                }
                is DestructiveAuthorizationResult.Authorized -> authorization
            }
            handedToExecutor = true
            return executor.execute(
                capability = authorized.capability,
                expectedBinding = binding,
                attemptLease = authorized.attemptLease,
            )
        } catch (_: Throwable) {
            if (!handedToExecutor) {
                cleanup.close(lease, armToken)
            }
            return reject(correlationId.value, "evaluation_error", lease, armToken)
        }
    }

    private fun reject(
        correlationId: String,
        reason: String,
        lease: DestructiveAttemptLease? = null,
        armToken: DestructiveArmingToken? = null,
    ): DestructiveSimulationStatus {
        cleanup.close(lease, armToken)
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
