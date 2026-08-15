package com.example.devicemanagement.destructive

import com.example.devicemanagement.integration.MonotonicTimeSource
import java.util.IdentityHashMap

/**
 * Opaque process-local proof that [DestructiveEvidencePhase.PRE_EXECUTION_COMMITTED]
 * was appended by this authority. Evidence records themselves are never
 * authorization. Caller-constructed tokens are not registered and cannot
 * pass the final gate.
 */
internal class PreExecutionEvidenceCommitProof private constructor() {
    companion object {
        fun create(): PreExecutionEvidenceCommitProof = PreExecutionEvidenceCommitProof()
    }
}

/**
 * Paired pre-execution evidence writer. The only issuer of
 * [PreExecutionEvidenceCommitProof]. A failed append creates no proof.
 */
internal class PreExecutionEvidenceCommitAuthority(
    private val writer: DestructiveSimulationEvidenceWriter,
    private val monotonicTimeSource: MonotonicTimeSource,
    private val maxAgeMillis: Long = MAX_PROOF_AGE_MILLIS,
) {
    private val issued = IdentityHashMap<PreExecutionEvidenceCommitProof, CommitRecord>()

    @Synchronized
    fun commit(
        evidence: DestructiveSimulationEvidence,
        binding: DestructiveTargetBinding,
        attemptLease: DestructiveAttemptLease,
    ): PreExecutionEvidenceCommitResult {
        if (evidence.phase != DestructiveEvidencePhase.PRE_EXECUTION_COMMITTED) {
            return PreExecutionEvidenceCommitResult.Failed("pre_execution_phase_required")
        }
        if (evidence.correlationId != binding.correlationId.value) {
            return PreExecutionEvidenceCommitResult.Failed("pre_execution_correlation_mismatch")
        }
        return when (writer.append(evidence)) {
            DestructiveEvidenceAppendResult.Failed -> {
                PreExecutionEvidenceCommitResult.Failed("audit_persistence_unavailable")
            }
            is DestructiveEvidenceAppendResult.Recorded -> {
                val proof = PreExecutionEvidenceCommitProof.create()
                issued[proof] = CommitRecord(
                    binding = binding,
                    attemptLease = attemptLease,
                    correlationId = binding.correlationId,
                    issuedAtMonotonicMillis = monotonicTimeSource.nowMillis(),
                )
                PreExecutionEvidenceCommitResult.Committed(proof)
            }
        }
    }

    @Synchronized
    fun consume(
        proof: PreExecutionEvidenceCommitProof,
        expectedBinding: DestructiveTargetBinding,
        expectedLease: DestructiveAttemptLease,
        nowMonotonicMillis: Long = monotonicTimeSource.nowMillis(),
    ): PreExecutionEvidenceCheck {
        val record = issued.remove(proof)
            ?: return PreExecutionEvidenceCheck.Rejected(
                "pre_execution_evidence_not_committed_or_already_consumed",
            )
        if (record.attemptLease !== expectedLease) {
            return PreExecutionEvidenceCheck.Rejected("pre_execution_attempt_lease_mismatch")
        }
        if (record.correlationId != expectedBinding.correlationId) {
            return PreExecutionEvidenceCheck.Rejected("pre_execution_correlation_mismatch")
        }
        if (record.binding != expectedBinding) {
            return PreExecutionEvidenceCheck.Rejected("pre_execution_target_mismatch")
        }
        val age = nowMonotonicMillis - record.issuedAtMonotonicMillis
        if (age < 0L) {
            return PreExecutionEvidenceCheck.Rejected("pre_execution_negative_monotonic_delta")
        }
        if (age > maxAgeMillis) {
            return PreExecutionEvidenceCheck.Rejected("pre_execution_evidence_stale")
        }
        return PreExecutionEvidenceCheck.Accepted
    }

    @Synchronized
    fun invalidate(proof: PreExecutionEvidenceCommitProof) {
        issued.remove(proof)
    }

    @Synchronized
    fun invalidateForLease(lease: DestructiveAttemptLease) {
        val stale = issued.entries.filter { it.value.attemptLease === lease }.map { it.key }
        stale.forEach { issued.remove(it) }
    }

    private data class CommitRecord(
        val binding: DestructiveTargetBinding,
        val attemptLease: DestructiveAttemptLease,
        val correlationId: DestructiveCorrelationId,
        val issuedAtMonotonicMillis: Long,
    )

    internal companion object {
        const val MAX_PROOF_AGE_MILLIS = 5_000L
    }
}

internal sealed interface PreExecutionEvidenceCommitResult {
    data class Committed(val proof: PreExecutionEvidenceCommitProof) : PreExecutionEvidenceCommitResult

    data class Failed(val reason: String) : PreExecutionEvidenceCommitResult
}

internal sealed interface PreExecutionEvidenceCheck {
    data object Accepted : PreExecutionEvidenceCheck

    data class Rejected(val reason: String) : PreExecutionEvidenceCheck
}
