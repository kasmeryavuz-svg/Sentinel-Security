package com.example.devicemanagement.destructive

import java.util.IdentityHashMap

/**
 * Opaque process-local destructive attempt / admission lease.
 *
 * Issued only after cooldown admission, deny-only marker write, and marker
 * readback all succeed. Never serialized or persisted. Process death
 * destroys every lease; a surviving deny-only marker can only deny.
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
) {
    private val liveLeases = IdentityHashMap<DestructiveAttemptLease, AttemptLeaseRecord>()

    @Synchronized
    fun admit(
        correlationId: DestructiveCorrelationId,
        requestedScope: DestructiveScope,
    ): AttemptAdmissionResult {
        when (val decision = cooldown.canAcceptNewRequest()) {
            is CooldownDecision.Deny -> return AttemptAdmissionResult.Rejected(decision.reason)
            CooldownDecision.NotDenied -> Unit
        }
        when (val recorded = cooldown.recordAttempt()) {
            is CooldownRecordResult.Failed -> return AttemptAdmissionResult.Rejected(recorded.reason)
            CooldownRecordResult.Recorded -> Unit
        }
        val lease = DestructiveAttemptLease.create()
        liveLeases[lease] = AttemptLeaseRecord(
            correlationId = correlationId,
            requestedScope = requestedScope,
            binding = null,
            phase = AttemptLeasePhase.ADMITTED,
        )
        return AttemptAdmissionResult.Admitted(lease)
    }

    @Synchronized
    fun bindTarget(
        lease: DestructiveAttemptLease,
        binding: DestructiveTargetBinding,
    ): AttemptBindResult {
        val record = liveLeases[lease]
            ?: return AttemptBindResult.Rejected("attempt_lease_not_issued_or_already_consumed")
        mismatchReason(record, binding)?.let { reason ->
            return AttemptBindResult.Rejected(reason)
        }
        if (record.binding != null && record.binding != binding) {
            return AttemptBindResult.Rejected("attempt_lease_target_mismatch")
        }
        if (record.phase == AttemptLeasePhase.ARMED && record.binding != binding) {
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
    ): AttemptLeaseCheck {
        val record = liveLeases[lease]
            ?: return AttemptLeaseCheck.Dead("attempt_lease_not_issued_or_already_consumed")
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
    ): AttemptLeaseCheck {
        val record = liveLeases[lease]
            ?: return AttemptLeaseCheck.Dead("attempt_lease_not_issued_or_already_consumed")
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
    )

    private enum class AttemptLeasePhase {
        ADMITTED,
        TARGET_BOUND,
        ARMED,
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
