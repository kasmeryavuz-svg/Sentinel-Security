package com.example.devicemanagement.destructive

import com.example.devicemanagement.integration.MonotonicTimeSource
import com.example.devicemanagement.logging.StructuredLogger

/**
 * Synchronous Checkpoint 17A executor chain:
 * consume capability → pre-execution simulation evidence + commit proof →
 * live final validation+permit issuance → immediate non-destructive
 * simulation sink.
 *
 * Pre-execution evidence is committed durably before live validation. The
 * in-process simulation log is a secondary fail-closed mirror. There is no
 * Android policy-service call and no reusable Boolean allow.
 *
 * This is the simulation executor only. It does not consume
 * [RuntimeDestructiveSafetyDurability] and is not a real destructive
 * chain.
 */
internal class SimulatedDestructiveExecutor(
    private val authorizationAuthority: DestructiveAuthorizationAuthority,
    private val gate: DestructiveFinalExecutionGate,
    private val preExecutionAuthority: PreExecutionEvidenceCommitAuthority,
    private val evidenceWriter: DestructiveSimulationEvidenceWriter,
    private val sink: Checkpoint17ASimulationSink,
    private val cleanup: DestructiveTerminalCleanup,
    private val monotonicTimeSource: MonotonicTimeSource,
    private val logger: StructuredLogger,
    private val presentationWallClockMillis: () -> Long = { 0L },
) {
    fun execute(
        capability: DestructiveCapability,
        expectedBinding: DestructiveTargetBinding,
        attemptLease: DestructiveAttemptLease,
    ): DestructiveSimulationStatus {
        val correlationId = expectedBinding.correlationId.value
        var armToken: DestructiveArmingToken? = null
        var proof: ConsumedDestructiveAuthorizationProof? = null
        var preExecutionProof: PreExecutionEvidenceCommitProof? = null
        try {
            val consumed = when (
                val consumption = authorizationAuthority.consume(
                    capability = capability,
                    expectedBinding = expectedBinding,
                    expectedLease = attemptLease,
                )
            ) {
                is DestructiveCapabilityConsumption.Rejected -> {
                    return closed(
                        reason = consumption.reason,
                        correlationId = correlationId,
                        expired = consumption.reason.contains("stale") ||
                            consumption.reason.contains("negative_monotonic"),
                    )
                }
                is DestructiveCapabilityConsumption.Accepted -> consumption
            }
            armToken = consumed.armToken
            proof = consumed.proof

            val committed = preExecutionAuthority.commit(
                evidence = simulationEvidence(
                    correlationId = correlationId,
                    phase = DestructiveEvidencePhase.PRE_EXECUTION_COMMITTED,
                    presentationWallClockMillis = presentationWallClockMillis(),
                    binding = consumed.binding,
                ),
                binding = consumed.binding,
                attemptLease = consumed.attemptLease,
            )
            if (committed is PreExecutionEvidenceCommitResult.Failed) {
                logger.warn(
                    event = "destructive_pre_execution_evidence_failed",
                    fields = mapOf("correlation_id" to correlationId),
                )
                return closedSimulationStatus(
                    outcome = DestructiveSimulationOutcome.FAILED_PRE_EXECUTION,
                    reason = committed.reason,
                    correlationId = correlationId,
                    state = DestructiveExecutionState.FAILED_PRE_EXECUTION,
                )
            }
            preExecutionProof = (committed as PreExecutionEvidenceCommitResult.Committed).proof

            val gated = try {
                gate.validateAndIssue(
                    binding = consumed.binding,
                    armToken = consumed.armToken,
                    attemptLease = consumed.attemptLease,
                    consumptionProof = consumed.proof,
                    preExecutionProof = preExecutionProof,
                    nowMonotonicMillis = monotonicTimeSource.nowMillis(),
                )
            } catch (_: Throwable) {
                FinalExecutionGateResult.Failed("final_validation_unavailable")
            }
            if (gated is FinalExecutionGateResult.Failed) {
                evidenceWriter.append(
                    simulationEvidence(
                        correlationId = correlationId,
                        phase = DestructiveEvidencePhase.FAILED_PRE_EXECUTION,
                        presentationWallClockMillis = presentationWallClockMillis(),
                        reasonCode = gated.reason,
                        binding = consumed.binding,
                    ),
                )
                return closedSimulationStatus(
                    outcome = DestructiveSimulationOutcome.FAILED_PRE_EXECUTION,
                    reason = gated.reason,
                    correlationId = correlationId,
                    state = DestructiveExecutionState.FAILED_PRE_EXECUTION,
                )
            }
            val permit = (gated as FinalExecutionGateResult.Issued).permit
            return when (val sinkResult = sink.invoke(permit, consumed.binding)) {
                is SimulationSinkResult.Denied -> closedSimulationStatus(
                    outcome = DestructiveSimulationOutcome.FAILED_PRE_EXECUTION,
                    reason = sinkResult.reason,
                    correlationId = correlationId,
                    state = DestructiveExecutionState.FAILED_PRE_EXECUTION,
                )
                is SimulationSinkResult.Invoked -> {
                    evidenceWriter.append(
                        simulationEvidence(
                            correlationId = correlationId,
                            phase = DestructiveEvidencePhase.SIMULATED,
                            presentationWallClockMillis = presentationWallClockMillis(),
                            reasonCode = sinkResult.message,
                            binding = consumed.binding,
                        ),
                    )
                    logger.info(
                        event = sinkResult.message,
                        fields = mapOf(
                            "mode" to "checkpoint_17a_simulation_only",
                            "correlation_id" to correlationId,
                        ),
                    )
                    closedSimulationStatus(
                        outcome = DestructiveSimulationOutcome.SIMULATED_WOULD_EXECUTE,
                        reason = sinkResult.message,
                        correlationId = correlationId,
                        state = DestructiveExecutionState.SIMULATED,
                    )
                }
            }
        } finally {
            preExecutionProof?.let { leftover ->
                preExecutionAuthority.invalidate(leftover)
            }
            cleanup.close(attemptLease, armToken, proof)
        }
    }

    private fun closed(
        reason: String,
        correlationId: String,
        expired: Boolean,
    ): DestructiveSimulationStatus {
        val outcome = if (expired) {
            DestructiveSimulationOutcome.EXPIRED
        } else {
            DestructiveSimulationOutcome.REJECTED
        }
        val state = when (outcome) {
            DestructiveSimulationOutcome.EXPIRED -> DestructiveExecutionState.EXPIRED
            DestructiveSimulationOutcome.CANCELLED -> DestructiveExecutionState.CANCELLED
            else -> DestructiveExecutionState.REJECTED
        }
        evidenceWriter.append(
            simulationEvidence(
                correlationId = correlationId,
                phase = when (outcome) {
                    DestructiveSimulationOutcome.EXPIRED -> DestructiveEvidencePhase.EXPIRED
                    DestructiveSimulationOutcome.CANCELLED -> DestructiveEvidencePhase.CANCELLED
                    else -> DestructiveEvidencePhase.REJECTED
                },
                presentationWallClockMillis = presentationWallClockMillis(),
                reasonCode = reason,
                binding = null,
            ),
        )
        return closedSimulationStatus(outcome, reason, correlationId, state)
    }
}
