package com.example.devicemanagement.destructive

/**
 * Distinct real-chain proof that a PRE_EXECUTION_COMMITTED append happened
 * through [RuntimeDestructiveSafetyDurability], not through a generic
 * simulation store.
 *
 * This is not [PreExecutionEvidenceCommitProof]. Caller-constructed
 * instances are not registered. Checkpoint 18 does not add a production
 * issuer that writes durable rows: [UnwiredRuntimeDurablePreExecutionCommitSource]
 * returns null so this checkpoint cannot mint fake durable evidence.
 */
internal class RuntimeDurablePreExecutionCommitProof private constructor() {
    companion object {
        fun create(): RuntimeDurablePreExecutionCommitProof = RuntimeDurablePreExecutionCommitProof()
    }
}

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
 * Would-be registrar for [RuntimeDurablePreExecutionCommitProof].
 *
 * The constructor structurally requires [RuntimeDestructiveSafetyDurability]
 * so generic [DestructivePreExecutionDurableStore] / in-memory stores cannot
 * satisfy this authority. [commit] does not append and does not issue a
 * proof: no fake durable evidence is recorded in this checkpoint.
 */
internal class RuntimeDurablePreExecutionCommitAuthority(
    private val durability: RuntimeDestructiveSafetyDurability,
) {
    fun commit(
        binding: DestructiveTargetBinding,
        attemptLease: DestructiveAttemptLease,
    ): RuntimeDurablePreExecutionCommitResult {
        requireDurabilityBound()
        if (binding.scope != DestructiveScope.DEVICE_FACTORY_RESET) {
            return RuntimeDurablePreExecutionCommitResult.Failed("runtime_pre_execution_scope_denied")
        }
        if (binding.correlationId.value.isBlank()) {
            return RuntimeDurablePreExecutionCommitResult.Failed("runtime_pre_execution_correlation_blank")
        }
        if (attemptLease.javaClass != DestructiveAttemptLease::class.java) {
            return RuntimeDurablePreExecutionCommitResult.Failed("runtime_pre_execution_lease_type")
        }
        return RuntimeDurablePreExecutionCommitResult.Failed("runtime_pre_execution_issuer_unwired")
    }

    fun consume(
        proof: RuntimeDurablePreExecutionCommitProof,
        expectedBinding: DestructiveTargetBinding,
        expectedLease: DestructiveAttemptLease,
    ): RuntimeDurablePreExecutionCheck {
        requireDurabilityBound()
        if (proof.javaClass != RuntimeDurablePreExecutionCommitProof::class.java) {
            return RuntimeDurablePreExecutionCheck.Rejected("runtime_pre_execution_proof_type")
        }
        if (expectedLease.javaClass != DestructiveAttemptLease::class.java) {
            return RuntimeDurablePreExecutionCheck.Rejected("runtime_pre_execution_lease_type")
        }
        if (expectedBinding.scope != DestructiveScope.DEVICE_FACTORY_RESET) {
            return RuntimeDurablePreExecutionCheck.Rejected("runtime_pre_execution_scope_denied")
        }
        return RuntimeDurablePreExecutionCheck.Rejected(
            "runtime_pre_execution_not_committed_or_already_consumed",
        )
    }

    private fun requireDurabilityBound() {
        durability.cooldown
        durability.preExecution
    }
}

/**
 * Production issuer for runtime-durable pre-execution proofs. Not invoked
 * by DeviceManagement. Always returns null so no disposable-device or
 * fake durable evidence can be recorded here.
 */
internal object UnwiredRuntimeDurablePreExecutionCommitSource {
    fun commit(
        durability: RuntimeDestructiveSafetyDurability,
        binding: DestructiveTargetBinding,
        attemptLease: DestructiveAttemptLease,
    ): RuntimeDurablePreExecutionCommitProof? {
        durability.cooldown
        durability.preExecution
        if (binding.scope != DestructiveScope.DEVICE_FACTORY_RESET) {
            return null
        }
        if (attemptLease.javaClass != DestructiveAttemptLease::class.java) {
            return null
        }
        return null
    }
}
