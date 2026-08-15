package com.example.devicemanagement.destructive

import java.util.IdentityHashMap
import java.util.UUID

/**
 * Distinct real-chain proof that a PRE_EXECUTION_COMMITTED append happened
 * through [RuntimeDestructiveSafetyDurability] after a consumed
 * destructive capability. This is not [PreExecutionEvidenceCommitProof].
 *
 * No companion mint. Caller-constructed instances are not registered.
 * The proof is minted only after [RuntimeDurablePreExecutionCommitAuthority]
 * successfully appends an exact-binding row through the trusted runtime
 * durability capability.
 */
internal sealed interface RuntimeDurablePreExecutionCommitProof

private class IssuedRuntimeDurablePreExecutionCommitProof :
    RuntimeDurablePreExecutionCommitProof

internal sealed interface RuntimeDurablePreExecutionCommitResult {
    data class Committed(val proof: RuntimeDurablePreExecutionCommitProof) :
        RuntimeDurablePreExecutionCommitResult

    data class Failed(val reason: String) : RuntimeDurablePreExecutionCommitResult
}

internal sealed interface RuntimeDurablePreExecutionCheck {
    data object Accepted : RuntimeDurablePreExecutionCheck

    data class Rejected(val reason: String) : RuntimeDurablePreExecutionCheck
}

/**
 * Registrar for [RuntimeDurablePreExecutionCommitProof].
 *
 * The constructor structurally requires [RuntimeDestructiveSafetyDurability]
 * so generic [DestructivePreExecutionDurableStore] / in-memory stores cannot
 * satisfy this authority.
 *
 * [commitAfterConsumedAuthorization] is the only commit entry. It must be
 * invoked after capability consumption and is bound to the exact consumed
 * authorization, binding, lease, and arm token. A pre-created or
 * pre-banked proof cannot be supplied to the real-chain boundary.
 *
 * [commitAfterConsumedAuthorization] issues a proof only after the exact
 * runtime pre-execution row has been durably appended. Append failure means
 * no proof.
 */
internal class RuntimeDurablePreExecutionCommitAuthority(
    private val durability: RuntimeDestructiveSafetyDurability,
    private val presentationWallClockMillis: () -> Long = { System.currentTimeMillis() },
    private val eventIdGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    private val issued = IdentityHashMap<RuntimeDurablePreExecutionCommitProof, CommitRecord>()
    private val committedConsumptions =
        IdentityHashMap<ConsumedDestructiveAuthorizationProof, Unit>()
    private val durableRepository =
        DurableDestructivePreExecutionRepository(durability.preExecution.durableStore())

    @Synchronized
    fun commitAfterConsumedAuthorization(
        consumedProof: ConsumedDestructiveAuthorizationProof,
        expectedBinding: DestructiveTargetBinding,
        expectedLease: DestructiveAttemptLease,
        expectedArmToken: DestructiveArmingToken,
        authorizationAuthority: DestructiveAuthorizationAuthority,
    ): RuntimeDurablePreExecutionCommitResult {
        requireDurabilityBound()
        when (
            val pending = authorizationAuthority.requirePendingConsumption(
                proof = consumedProof,
                expectedBinding = expectedBinding,
                expectedLease = expectedLease,
                expectedArmToken = expectedArmToken,
            )
        ) {
            is ConsumedAuthorizationCheck.Rejected -> {
                return RuntimeDurablePreExecutionCommitResult.Failed(pending.reason)
            }
            is ConsumedAuthorizationCheck.Accepted -> Unit
        }
        if (expectedBinding.scope != DestructiveScope.DEVICE_FACTORY_RESET) {
            return RuntimeDurablePreExecutionCommitResult.Failed("runtime_pre_execution_scope_denied")
        }
        if (expectedBinding.correlationId.value.isBlank()) {
            return RuntimeDurablePreExecutionCommitResult.Failed("runtime_pre_execution_correlation_blank")
        }
        if (expectedLease.javaClass != DestructiveAttemptLease::class.java) {
            return RuntimeDurablePreExecutionCommitResult.Failed("runtime_pre_execution_lease_type")
        }
        if (consumedProof.javaClass != ConsumedDestructiveAuthorizationProof::class.java) {
            return RuntimeDurablePreExecutionCommitResult.Failed("runtime_pre_execution_consumed_proof_type")
        }
        if (committedConsumptions.containsKey(consumedProof)) {
            return RuntimeDurablePreExecutionCommitResult.Failed(
                "runtime_pre_execution_consumption_already_committed",
            )
        }
        val proof = IssuedRuntimeDurablePreExecutionCommitProof()
        val durableRecord = try {
            DestructivePreExecutionDurableRecord(
                eventId = eventIdGenerator(),
                correlationId = expectedBinding.correlationId.value,
                actionName = DestructiveRuntimeActionNames.FUTURE_REAL_CHAIN_FACTORY_RESET,
                phase = DestructiveEvidencePhase.PRE_EXECUTION_COMMITTED,
                presentationWallClockMillis = presentationWallClockMillis(),
                boundPackage = expectedBinding.runningPackage,
                boundAdminComponent = expectedBinding.expectedAdminComponent,
                boundScope = expectedBinding.scope,
                reasonCode = null,
            )
        } catch (_: Throwable) {
            return RuntimeDurablePreExecutionCommitResult.Failed(
                "runtime_pre_execution_append_failed",
            )
        }
        when (durableRepository.append(durableRecord)) {
            DestructiveEvidenceAppendResult.Failed -> {
                return RuntimeDurablePreExecutionCommitResult.Failed(
                    "runtime_pre_execution_append_failed",
                )
            }
            is DestructiveEvidenceAppendResult.Recorded -> Unit
        }
        committedConsumptions[consumedProof] = Unit
        issued[proof] = CommitRecord(
            binding = expectedBinding,
            attemptLease = expectedLease,
            consumedProof = consumedProof,
        )
        return RuntimeDurablePreExecutionCommitResult.Committed(proof)
    }

    @Synchronized
    fun consume(
        proof: RuntimeDurablePreExecutionCommitProof,
        expectedBinding: DestructiveTargetBinding,
        expectedLease: DestructiveAttemptLease,
        expectedConsumedProof: ConsumedDestructiveAuthorizationProof,
    ): RuntimeDurablePreExecutionCheck {
        requireDurabilityBound()
        val record = issued.remove(proof)
            ?: return RuntimeDurablePreExecutionCheck.Rejected(
                "runtime_pre_execution_not_committed_or_already_consumed",
            )
        if (record.attemptLease !== expectedLease) {
            return RuntimeDurablePreExecutionCheck.Rejected("runtime_pre_execution_lease_mismatch")
        }
        if (record.consumedProof !== expectedConsumedProof) {
            return RuntimeDurablePreExecutionCheck.Rejected(
                "runtime_pre_execution_consumed_proof_mismatch",
            )
        }
        if (record.binding != expectedBinding) {
            return RuntimeDurablePreExecutionCheck.Rejected("runtime_pre_execution_target_mismatch")
        }
        if (expectedBinding.scope != DestructiveScope.DEVICE_FACTORY_RESET) {
            return RuntimeDurablePreExecutionCheck.Rejected("runtime_pre_execution_scope_denied")
        }
        return RuntimeDurablePreExecutionCheck.Accepted
    }

    private fun requireDurabilityBound() {
        durability.cooldown
        durability.preExecution
    }

    private data class CommitRecord(
        val binding: DestructiveTargetBinding,
        val attemptLease: DestructiveAttemptLease,
        val consumedProof: ConsumedDestructiveAuthorizationProof,
    )
}
