package com.example.devicemanagement.destructive

import com.example.devicemanagement.integration.MonotonicTimeSource
import java.util.IdentityHashMap

/**
 * Single final-execution authority. Live validation and permit issuance are
 * one atomic step. There is no Boolean/Passed result that can be honored
 * later, and no [issue] API that accepts only a binding.
 */
internal class DestructiveFinalExecutionGate(
    private val liveFactsSource: DestructiveLiveFactsSource,
    private val armingAuthority: DestructiveArmingAuthority,
    private val authorizationAuthority: DestructiveAuthorizationAuthority,
    private val admissionAuthority: DestructiveAttemptAdmissionAuthority,
    private val cooldown: DestructiveDenyOnlyCooldown,
    private val monotonicTimeSource: MonotonicTimeSource,
    private val maxPermitAgeMillis: Long = MAX_PERMIT_AGE_MILLIS,
) : FinalExecutionPermitConsumer {
    private val issued = IdentityHashMap<FinalExecutionPermit, PermitRecord>()

    fun validateAndIssue(
        binding: DestructiveTargetBinding,
        armToken: DestructiveArmingToken,
        attemptLease: DestructiveAttemptLease,
        consumptionProof: ConsumedDestructiveAuthorizationProof,
        nowMonotonicMillis: Long = monotonicTimeSource.nowMillis(),
    ): FinalExecutionGateResult {
        denyReason(
            binding = binding,
            armToken = armToken,
            attemptLease = attemptLease,
            consumptionProof = consumptionProof,
            nowMonotonicMillis = nowMonotonicMillis,
        )?.let { reason ->
            return FinalExecutionGateResult.Failed(reason)
        }
        val permit = FinalExecutionPermit.create()
        issued[permit] = PermitRecord(
            binding = binding,
            issuedAtMonotonicMillis = nowMonotonicMillis,
        )
        return FinalExecutionGateResult.Issued(permit)
    }

    override fun consume(
        permit: FinalExecutionPermit,
        expectedBinding: DestructiveTargetBinding,
    ): PermitConsumption {
        val now = monotonicTimeSource.nowMillis()
        val record = issued.remove(permit)
            ?: return PermitConsumption.Rejected("permit_not_issued_or_already_consumed")
        if (record.binding != expectedBinding) {
            return PermitConsumption.Rejected("permit_target_mismatch")
        }
        val age = now - record.issuedAtMonotonicMillis
        if (age < 0L) {
            return PermitConsumption.Rejected("permit_negative_monotonic_delta")
        }
        if (age > maxPermitAgeMillis) {
            return PermitConsumption.Rejected("permit_stale")
        }
        return PermitConsumption.Accepted(record.binding)
    }

    private fun denyReason(
        binding: DestructiveTargetBinding,
        armToken: DestructiveArmingToken,
        attemptLease: DestructiveAttemptLease,
        consumptionProof: ConsumedDestructiveAuthorizationProof,
        nowMonotonicMillis: Long,
    ): String? {
        val facts = try {
            liveFactsSource.currentFacts()
        } catch (_: Throwable) {
            return "live_facts_unavailable"
        }
        DestructiveTargetRules.denyReason(binding, facts)?.let { return it }
        when (
            val consumed = authorizationAuthority.requireConsumedFresh(
                proof = consumptionProof,
                expectedBinding = binding,
                expectedArmToken = armToken,
                expectedLease = attemptLease,
                nowMonotonicMillis = nowMonotonicMillis,
            )
        ) {
            is ConsumedAuthorizationCheck.Rejected -> return consumed.reason
            is ConsumedAuthorizationCheck.Accepted -> Unit
        }
        when (val admitted = admissionAuthority.requireLive(attemptLease, binding, nowMonotonicMillis)) {
            is AttemptLeaseCheck.Dead -> return admitted.reason
            is AttemptLeaseCheck.Live -> Unit
        }
        when (
            val arm = armingAuthority.requireLive(
                token = armToken,
                expectedBinding = binding,
                expectedLease = attemptLease,
                nowMonotonicMillis = nowMonotonicMillis,
            )
        ) {
            is ArmingCheck.Dead -> return arm.reason
            is ArmingCheck.Live -> Unit
        }
        return when (val marker = cooldown.assertCurrentAttemptMarkerPresent()) {
            is CooldownUsable.Unusable -> marker.reason
            CooldownUsable.Usable -> null
        }
    }

    private data class PermitRecord(
        val binding: DestructiveTargetBinding,
        val issuedAtMonotonicMillis: Long,
    )

    internal companion object {
        const val MAX_PERMIT_AGE_MILLIS = 5L
    }
}

internal sealed interface FinalExecutionGateResult {
    data class Issued(val permit: FinalExecutionPermit) : FinalExecutionGateResult

    data class Failed(val reason: String) : FinalExecutionGateResult
}
