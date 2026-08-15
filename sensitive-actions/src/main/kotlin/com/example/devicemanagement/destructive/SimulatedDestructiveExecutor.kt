package com.example.devicemanagement.destructive

import com.example.devicemanagement.integration.MonotonicTimeSource
import com.example.devicemanagement.logging.StructuredLogger

/**
 * Synchronous Checkpoint 17A executor chain:
 * consume capability → pre-execution simulation evidence → live final
 * validation → immediate non-destructive simulation sink.
 *
 * Simulation evidence proves ordering and fail-closed behavior only. It is
 * not a durable production audit. There is no Android policy-service call
 * and no reusable Boolean allow.
 */
internal class SimulatedDestructiveExecutor(
    private val authorizationAuthority: DestructiveAuthorizationAuthority,
    private val validator: DestructiveFinalValidator,
    private val evidenceWriter: DestructiveSimulationEvidenceWriter,
    private val permitAuthority: FinalExecutionPermitAuthority,
    private val sink: Checkpoint17ASimulationSink,
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

        val preExecution = evidenceWriter.append(
            simulationEvidence(
                correlationId = correlationId,
                phase = DestructiveEvidencePhase.PRE_EXECUTION_COMMITTED,
                presentationWallClockMillis = presentationWallClockMillis(),
                binding = consumed.binding,
            ),
        )
        if (preExecution is DestructiveEvidenceAppendResult.Failed) {
            logger.warn(
                event = "destructive_pre_execution_evidence_failed",
                fields = mapOf("correlation_id" to correlationId),
            )
            return closedSimulationStatus(
                outcome = DestructiveSimulationOutcome.FAILED_PRE_EXECUTION,
                reason = "audit_persistence_unavailable",
                correlationId = correlationId,
                state = DestructiveExecutionState.FAILED_PRE_EXECUTION,
            )
        }

        val validation = try {
            validator.validate(
                binding = consumed.binding,
                armToken = consumed.armToken,
                attemptLease = consumed.attemptLease,
                consumptionProof = consumed.proof,
                nowMonotonicMillis = monotonicTimeSource.nowMillis(),
            )
        } catch (_: Throwable) {
            FinalValidation.Failed("final_validation_unavailable")
        }
        if (validation is FinalValidation.Failed) {
            evidenceWriter.append(
                simulationEvidence(
                    correlationId = correlationId,
                    phase = DestructiveEvidencePhase.FAILED_PRE_EXECUTION,
                    presentationWallClockMillis = presentationWallClockMillis(),
                    reasonCode = validation.reason,
                    binding = consumed.binding,
                ),
            )
            return closedSimulationStatus(
                outcome = DestructiveSimulationOutcome.FAILED_PRE_EXECUTION,
                reason = validation.reason,
                correlationId = correlationId,
                state = DestructiveExecutionState.FAILED_PRE_EXECUTION,
            )
        }

        val permit = permitAuthority.issue(consumed.binding)
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
