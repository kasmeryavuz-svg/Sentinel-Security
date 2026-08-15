package com.example.devicemanagement.destructive

import com.example.devicemanagement.integration.MonotonicTimeSource
import java.util.IdentityHashMap

/**
 * Opaque in-chain hand-off issued only after durable pre-execution evidence
 * and live final validation. Consumable once, only by the paired sink.
 */
internal class FinalExecutionPermit private constructor() {
    companion object {
        fun create(): FinalExecutionPermit = FinalExecutionPermit()
    }
}

internal class FinalExecutionPermitAuthority(
    private val monotonicTimeSource: MonotonicTimeSource,
    private val maxAgeMillis: Long = MAX_PERMIT_AGE_MILLIS,
) {
    private val issued = IdentityHashMap<FinalExecutionPermit, PermitRecord>()

    @Synchronized
    fun issue(binding: DestructiveTargetBinding): FinalExecutionPermit {
        val permit = FinalExecutionPermit.create()
        issued[permit] = PermitRecord(
            binding = binding,
            issuedAtMonotonicMillis = monotonicTimeSource.nowMillis(),
        )
        return permit
    }

    @Synchronized
    fun consume(
        permit: FinalExecutionPermit,
        expectedBinding: DestructiveTargetBinding,
        nowMonotonicMillis: Long = monotonicTimeSource.nowMillis(),
    ): PermitConsumption {
        val record = issued.remove(permit)
            ?: return PermitConsumption.Rejected("permit_not_issued_or_already_consumed")
        if (record.binding != expectedBinding) {
            return PermitConsumption.Rejected("permit_target_mismatch")
        }
        val age = nowMonotonicMillis - record.issuedAtMonotonicMillis
        if (age < 0L) {
            return PermitConsumption.Rejected("permit_negative_monotonic_delta")
        }
        if (age > maxAgeMillis) {
            return PermitConsumption.Rejected("permit_stale")
        }
        return PermitConsumption.Accepted(record.binding)
    }

    private data class PermitRecord(
        val binding: DestructiveTargetBinding,
        val issuedAtMonotonicMillis: Long,
    )

    internal companion object {
        const val MAX_PERMIT_AGE_MILLIS = 5L
    }
}

internal sealed interface PermitConsumption {
    data class Accepted(val binding: DestructiveTargetBinding) : PermitConsumption

    data class Rejected(val reason: String) : PermitConsumption
}
