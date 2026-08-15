package com.example.devicemanagement.destructive

import com.example.devicemanagement.integration.MonotonicTimeSource
import java.util.IdentityHashMap

/**
 * Opaque process-local destructive attempt / admission lease.
 *
 * Issued only after cooldown admission, deny-only marker write, and marker
 * readback all succeed. Never serialized or persisted. Process death
 * destroys every lease; a surviving deny-only marker can only deny.
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
 * Authoritative issuer of [DestructiveAttemptLease] values. The persisted
 * deny-only marker is never a lease and can never arm, authorize, or
 * execute.
 */
internal class DestructiveAttemptAdmissionAuthority(
    private val cooldown: DestructiveDenyOnlyCooldown,
    private val monotonicTimeSource: MonotonicTimeSource,
    private val maxAgeMillis: Long = MAX_LEASE_AGE_MILLIS,
) {
    private val liveLeases = IdentityHashMap<DestructiveAttemptLease, AttemptLeaseRecord>()

    @Synchronized
    fun recordCountedAttempt(): CooldownRecordResult {
        when (val decision = cooldown.canAcceptNewRequest()) {
            is CooldownDecision.Deny -> return CooldownRecordResult.Failed(decision.reason)
            CooldownDecision.NotDenied -> Unit
        }
        return cooldown.recordAttempt()
    }

    @Synchronized
    fun admit(
        correlationId: DestructiveCorrelationId,
        requestedScope: DestructiveScope,
    ): AttemptAdmissionResult {
        if (liveLeases.isNotEmpty()) {
            return AttemptAdmissionResult.Rejected("attempt_lease_already_live")
        }
        when (val recorded = recordCountedAttempt()) {
            is CooldownRecordResult.Failed -> return AttemptAdmissionResult.Rejected(recorded.reason)
            CooldownRecordResult.Recorded -> Unit
        }
        return issueLeaseAfterRecorded(correlationId, requestedScope)
    }

    @Synchronized
    fun issueLease(
        correlationId: DestructiveCorrelationId,
        requestedScope: DestructiveScope,
    ): AttemptAdmissionResult {
        if (liveLeases.isNotEmpty()) {
            return AttemptAdmissionResult.Rejected("attempt_lease_already_live")
        }
        when (val marker = cooldown.assertCurrentAttemptMarkerPresent()) {
            is CooldownUsable.Unusable -> return AttemptAdmissionResult.Rejected(marker.reason)
            CooldownUsable.Usable -> Unit
        }
        return issueLeaseAfterRecorded(correlationId, requestedScope)
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

    private fun issueLeaseAfterRecorded(
        correlationId: DestructiveCorrelationId,
        requestedScope: DestructiveScope,
    ): AttemptAdmissionResult {
        val lease = DestructiveAttemptLease.create()
        liveLeases[lease] = AttemptLeaseRecord(
            correlationId = correlationId,
            requestedScope = requestedScope,
            binding = null,
            phase = AttemptLeasePhase.ADMITTED,
            issuedAtMonotonicMillis = monotonicTimeSource.nowMillis(),
        )
        return AttemptAdmissionResult.Admitted(lease)
    }

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
    }
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
