package com.example.devicemanagement.destructive

import com.example.devicemanagement.integration.MonotonicTimeSource
import java.util.IdentityHashMap

/**
 * Opaque process-local destructive attempt / admission lease.
 *
 * Issued only after a [CountedAttemptProof] is consumed. That proof is
 * issued only after cooldown admission, deny-only marker write, and marker
 * readback all succeed. The persisted marker is never the proof and never
 * the lease. Never serialized or persisted. Process death destroys every
 * lease and every unused proof; a surviving deny-only marker can only deny.
 *
 * At most one non-terminal lease exists per authority. Terminal close is
 * explicit and never clears the deny-only cooldown.
 */
internal class DestructiveAttemptLease private constructor() {
    companion object {
        fun create(): DestructiveAttemptLease = DestructiveAttemptLease()
    }
}

/**
 * Opaque one-time bridge from a just-recorded counted attempt to lease
 * issuance. Caller-constructed tokens are not registered. A Present marker
 * is not this proof.
 */
internal class CountedAttemptProof private constructor() {
    companion object {
        fun create(): CountedAttemptProof = CountedAttemptProof()
    }
}

/**
 * Authoritative issuer of [DestructiveAttemptLease] values. The persisted
 * deny-only marker is never a lease and can never arm, authorize, or
 * execute. [issueLease] requires and consumes a [CountedAttemptProof];
 * there is no marker-only issuance path.
 */
internal class DestructiveAttemptAdmissionAuthority(
    private val cooldown: DestructiveDenyOnlyCooldown,
    private val monotonicTimeSource: MonotonicTimeSource,
    private val maxAgeMillis: Long = MAX_LEASE_AGE_MILLIS,
    private val maxCountedAttemptProofAgeMillis: Long = MAX_COUNTED_ATTEMPT_PROOF_AGE_MILLIS,
) {
    private val liveLeases = IdentityHashMap<DestructiveAttemptLease, AttemptLeaseRecord>()
    private val pendingCountedAttempts = IdentityHashMap<CountedAttemptProof, CountedAttemptRecord>()

    @Synchronized
    fun recordCountedAttempt(
        correlationId: DestructiveCorrelationId,
        requestedScope: DestructiveScope?,
    ): CountedAttemptRecordResult {
        when (val decision = cooldown.canAcceptNewRequest()) {
            is CooldownDecision.Deny -> return CountedAttemptRecordResult.Failed(decision.reason)
            CooldownDecision.NotDenied -> Unit
        }
        return when (val recorded = cooldown.recordAttempt()) {
            is CooldownRecordResult.Failed -> CountedAttemptRecordResult.Failed(recorded.reason)
            CooldownRecordResult.Recorded -> {
                val proof = CountedAttemptProof.create()
                pendingCountedAttempts[proof] = CountedAttemptRecord(
                    correlationId = correlationId,
                    requestedScope = requestedScope,
                    issuedAtMonotonicMillis = monotonicTimeSource.nowMillis(),
                )
                CountedAttemptRecordResult.Recorded(proof)
            }
        }
    }

    @Synchronized
    fun discardCountedAttempt(proof: CountedAttemptProof) {
        pendingCountedAttempts.remove(proof)
    }

    @Synchronized
    fun admit(
        correlationId: DestructiveCorrelationId,
        requestedScope: DestructiveScope,
    ): AttemptAdmissionResult {
        if (liveLeases.isNotEmpty()) {
            return AttemptAdmissionResult.Rejected("attempt_lease_already_live")
        }
        return when (val recorded = recordCountedAttempt(correlationId, requestedScope)) {
            is CountedAttemptRecordResult.Failed -> AttemptAdmissionResult.Rejected(recorded.reason)
            is CountedAttemptRecordResult.Recorded -> issueLease(
                proof = recorded.proof,
                correlationId = correlationId,
                requestedScope = requestedScope,
            )
        }
    }

    @Synchronized
    fun issueLease(
        proof: CountedAttemptProof,
        correlationId: DestructiveCorrelationId,
        requestedScope: DestructiveScope,
        nowMonotonicMillis: Long = monotonicTimeSource.nowMillis(),
    ): AttemptAdmissionResult {
        val counted = pendingCountedAttempts.remove(proof)
            ?: return AttemptAdmissionResult.Rejected(
                "counted_attempt_not_issued_or_already_consumed",
            )
        val proofAge = nowMonotonicMillis - counted.issuedAtMonotonicMillis
        if (proofAge < 0L) {
            return AttemptAdmissionResult.Rejected("counted_attempt_negative_monotonic_delta")
        }
        if (proofAge > maxCountedAttemptProofAgeMillis) {
            return AttemptAdmissionResult.Rejected("counted_attempt_stale")
        }
        if (counted.correlationId != correlationId) {
            return AttemptAdmissionResult.Rejected("counted_attempt_correlation_mismatch")
        }
        if (counted.requestedScope != requestedScope) {
            return AttemptAdmissionResult.Rejected("counted_attempt_scope_mismatch")
        }
        if (liveLeases.isNotEmpty()) {
            return AttemptAdmissionResult.Rejected("attempt_lease_already_live")
        }
        when (val marker = cooldown.assertCurrentAttemptMarkerPresent()) {
            is CooldownUsable.Unusable -> return AttemptAdmissionResult.Rejected(marker.reason)
            CooldownUsable.Usable -> Unit
        }
        val lease = DestructiveAttemptLease.create()
        liveLeases[lease] = AttemptLeaseRecord(
            correlationId = correlationId,
            requestedScope = requestedScope,
            binding = null,
            phase = AttemptLeasePhase.ADMITTED,
            issuedAtMonotonicMillis = nowMonotonicMillis,
        )
        return AttemptAdmissionResult.Admitted(lease)
    }

    @Synchronized
    fun bindTarget(
        lease: DestructiveAttemptLease,
        binding: DestructiveTargetBinding,
        nowMonotonicMillis: Long = monotonicTimeSource.nowMillis(),
    ): AttemptBindResult {
        val record = liveLeases[lease]
            ?: return AttemptBindResult.Rejected("attempt_lease_not_issued_or_already_consumed")
        freshnessReason(record, nowMonotonicMillis)?.let { reason ->
            return AttemptBindResult.Rejected(reason)
        }
        mismatchReason(record, binding)?.let { reason ->
            return AttemptBindResult.Rejected(reason)
        }
        if (record.binding != null && record.binding != binding) {
            return AttemptBindResult.Rejected("attempt_lease_target_mismatch")
        }
        liveLeases[lease] = record.copy(
            binding = binding,
            phase = if (record.phase == AttemptLeasePhase.ARMED) {
                AttemptLeasePhase.ARMED
            } else {
                AttemptLeasePhase.TARGET_BOUND
            },
        )
        return AttemptBindResult.Bound(lease, binding)
    }

    @Synchronized
    fun reserveForArm(
        lease: DestructiveAttemptLease,
        expectedBinding: DestructiveTargetBinding,
        nowMonotonicMillis: Long = monotonicTimeSource.nowMillis(),
    ): AttemptLeaseCheck {
        val record = liveLeases[lease]
            ?: return AttemptLeaseCheck.Dead("attempt_lease_not_issued_or_already_consumed")
        freshnessReason(record, nowMonotonicMillis)?.let { reason ->
            return AttemptLeaseCheck.Dead(reason)
        }
        mismatchReason(record, expectedBinding)?.let { reason ->
            return AttemptLeaseCheck.Dead(reason)
        }
        val bound = record.binding
            ?: return AttemptLeaseCheck.Dead("attempt_lease_target_unbound")
        if (bound != expectedBinding) {
            return AttemptLeaseCheck.Dead("attempt_lease_target_mismatch")
        }
        if (record.phase == AttemptLeasePhase.ARMED) {
            return AttemptLeaseCheck.Dead("attempt_lease_already_armed")
        }
        liveLeases[lease] = record.copy(phase = AttemptLeasePhase.ARMED)
        return AttemptLeaseCheck.Live(lease, bound)
    }

    @Synchronized
    fun requireLive(
        lease: DestructiveAttemptLease,
        expectedBinding: DestructiveTargetBinding,
        nowMonotonicMillis: Long = monotonicTimeSource.nowMillis(),
    ): AttemptLeaseCheck {
        val record = liveLeases[lease]
            ?: return AttemptLeaseCheck.Dead("attempt_lease_not_issued_or_already_consumed")
        freshnessReason(record, nowMonotonicMillis)?.let { reason ->
            return AttemptLeaseCheck.Dead(reason)
        }
        when (val marker = cooldown.assertCurrentAttemptMarkerPresent()) {
            is CooldownUsable.Unusable -> return AttemptLeaseCheck.Dead(marker.reason)
            CooldownUsable.Usable -> Unit
        }
        mismatchReason(record, expectedBinding)?.let { reason ->
            return AttemptLeaseCheck.Dead(reason)
        }
        val bound = record.binding
            ?: return AttemptLeaseCheck.Dead("attempt_lease_target_unbound")
        if (bound != expectedBinding) {
            return AttemptLeaseCheck.Dead("attempt_lease_target_mismatch")
        }
        return AttemptLeaseCheck.Live(lease, bound)
    }

    @Synchronized
    fun close(lease: DestructiveAttemptLease): AttemptCloseResult {
        liveLeases.remove(lease)
            ?: return AttemptCloseResult.AlreadyClosed
        return AttemptCloseResult.Closed
    }

    @Synchronized
    fun hasNonTerminalLease(): Boolean = liveLeases.isNotEmpty()

    private fun freshnessReason(
        record: AttemptLeaseRecord,
        nowMonotonicMillis: Long,
    ): String? {
        val age = nowMonotonicMillis - record.issuedAtMonotonicMillis
        if (age < 0L) {
            return "attempt_lease_negative_monotonic_delta"
        }
        if (age > maxAgeMillis) {
            return "attempt_lease_stale"
        }
        return null
    }

    private fun mismatchReason(
        record: AttemptLeaseRecord,
        binding: DestructiveTargetBinding,
    ): String? {
        if (record.correlationId != binding.correlationId) {
            return "attempt_lease_correlation_mismatch"
        }
        if (record.requestedScope != binding.scope) {
            return "attempt_lease_scope_mismatch"
        }
        return null
    }

    private data class CountedAttemptRecord(
        val correlationId: DestructiveCorrelationId,
        val requestedScope: DestructiveScope?,
        val issuedAtMonotonicMillis: Long,
    )

    private data class AttemptLeaseRecord(
        val correlationId: DestructiveCorrelationId,
        val requestedScope: DestructiveScope,
        val binding: DestructiveTargetBinding?,
        val phase: AttemptLeasePhase,
        val issuedAtMonotonicMillis: Long,
    )

    private enum class AttemptLeasePhase {
        ADMITTED,
        TARGET_BOUND,
        ARMED,
    }

    internal companion object {
        const val MAX_LEASE_AGE_MILLIS = 15_000L
        const val MAX_COUNTED_ATTEMPT_PROOF_AGE_MILLIS = 15_000L
    }
}

internal sealed interface CountedAttemptRecordResult {
    data class Recorded(val proof: CountedAttemptProof) : CountedAttemptRecordResult

    data class Failed(val reason: String) : CountedAttemptRecordResult
}

internal sealed interface AttemptAdmissionResult {
    data class Admitted(val lease: DestructiveAttemptLease) : AttemptAdmissionResult

    data class Rejected(val reason: String) : AttemptAdmissionResult
}

internal sealed interface AttemptBindResult {
    data class Bound(
        val lease: DestructiveAttemptLease,
        val binding: DestructiveTargetBinding,
    ) : AttemptBindResult

    data class Rejected(val reason: String) : AttemptBindResult
}

internal sealed interface AttemptLeaseCheck {
    data class Live(
        val lease: DestructiveAttemptLease,
        val binding: DestructiveTargetBinding,
    ) : AttemptLeaseCheck

    data class Dead(val reason: String) : AttemptLeaseCheck
}

internal sealed interface AttemptCloseResult {
    data object Closed : AttemptCloseResult

    data object AlreadyClosed : AttemptCloseResult
}
