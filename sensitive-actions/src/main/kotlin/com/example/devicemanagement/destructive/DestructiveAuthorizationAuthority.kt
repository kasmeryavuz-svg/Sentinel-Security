package com.example.devicemanagement.destructive

import com.example.devicemanagement.integration.MonotonicTimeSource
import java.util.IdentityHashMap

/**
 * Separate destructive authorization domain. Opaque, identity-bound,
 * single-use, process-local. Cannot accept a reversible Approval and cannot
 * be consumed by the reversible policy executor.
 *
 * One arm yields at most one destructive authorization. Freshness of the
 * original issuance is revalidated after pre-execution evidence via an
 * opaque consumed-authorization proof. That proof is not a
 * caller-constructible data object.
 */
internal class DestructiveCapability private constructor() {
    companion object {
        fun create(): DestructiveCapability = DestructiveCapability()
    }
}

internal class ConsumedDestructiveAuthorizationProof private constructor() {
    companion object {
        fun create(): ConsumedDestructiveAuthorizationProof = ConsumedDestructiveAuthorizationProof()
    }
}

internal class DestructiveAuthorizationAuthority(
    private val armingAuthority: DestructiveArmingAuthority,
    private val monotonicTimeSource: MonotonicTimeSource,
    private val admissionAuthority: DestructiveAttemptAdmissionAuthority,
    private val maxAgeMillis: Long = MAX_CAPABILITY_AGE_MILLIS,
) {
    private val issued = IdentityHashMap<DestructiveCapability, CapabilityRecord>()
    private val pendingConsumption =
        IdentityHashMap<ConsumedDestructiveAuthorizationProof, PendingConsumptionRecord>()

    @Synchronized
    fun authorize(
        armToken: DestructiveArmingToken,
        binding: DestructiveTargetBinding,
        attemptLease: DestructiveAttemptLease,
    ): DestructiveAuthorizationResult {
        when (val admitted = admissionAuthority.requireLive(attemptLease, binding)) {
            is AttemptLeaseCheck.Dead -> return DestructiveAuthorizationResult.Rejected(admitted.reason)
            is AttemptLeaseCheck.Live -> Unit
        }
        when (
            val reserved = armingAuthority.reserveForAuthorization(
                token = armToken,
                expectedBinding = binding,
                expectedLease = attemptLease,
            )
        ) {
            is ArmAuthorizationReservation.Rejected -> {
                return DestructiveAuthorizationResult.Rejected(reserved.reason)
            }
            is ArmAuthorizationReservation.Reserved -> Unit
        }
        val capability = DestructiveCapability.create()
        issued[capability] = CapabilityRecord(
            binding = binding,
            armToken = armToken,
            attemptLease = attemptLease,
            issuedAtMonotonicMillis = monotonicTimeSource.nowMillis(),
        )
        return DestructiveAuthorizationResult.Authorized(
            capability = capability,
            binding = binding,
            armToken = armToken,
            attemptLease = attemptLease,
        )
    }

    @Synchronized
    fun consume(
        capability: DestructiveCapability,
        expectedBinding: DestructiveTargetBinding,
        expectedLease: DestructiveAttemptLease,
        nowMonotonicMillis: Long = monotonicTimeSource.nowMillis(),
    ): DestructiveCapabilityConsumption {
        val record = issued.remove(capability)
            ?: return DestructiveCapabilityConsumption.Rejected(
                "capability_not_issued_or_already_consumed",
            )
        if (record.attemptLease !== expectedLease) {
            return DestructiveCapabilityConsumption.Rejected("capability_attempt_lease_mismatch")
        }
        if (record.binding != expectedBinding) {
            return if (record.binding.scope != expectedBinding.scope) {
                DestructiveCapabilityConsumption.Rejected("capability_scope_mismatch")
            } else {
                DestructiveCapabilityConsumption.Rejected("capability_target_mismatch")
            }
        }
        freshnessReason(record.issuedAtMonotonicMillis, nowMonotonicMillis)?.let { reason ->
            return DestructiveCapabilityConsumption.Rejected(reason)
        }
        when (val admitted = admissionAuthority.requireLive(record.attemptLease, record.binding)) {
            is AttemptLeaseCheck.Dead -> return DestructiveCapabilityConsumption.Rejected(admitted.reason)
            is AttemptLeaseCheck.Live -> Unit
        }
        when (
            val arm = armingAuthority.requireLive(
                token = record.armToken,
                expectedBinding = record.binding,
                expectedLease = record.attemptLease,
                nowMonotonicMillis = nowMonotonicMillis,
            )
        ) {
            is ArmingCheck.Dead -> return DestructiveCapabilityConsumption.Rejected(arm.reason)
            is ArmingCheck.Live -> Unit
        }
        val proof = ConsumedDestructiveAuthorizationProof.create()
        pendingConsumption[proof] = PendingConsumptionRecord(
            binding = record.binding,
            armToken = record.armToken,
            attemptLease = record.attemptLease,
            issuedAtMonotonicMillis = record.issuedAtMonotonicMillis,
        )
        return DestructiveCapabilityConsumption.Accepted(
            proof = proof,
            binding = record.binding,
            armToken = record.armToken,
            attemptLease = record.attemptLease,
        )
    }

    @Synchronized
    fun requireConsumedFresh(
        proof: ConsumedDestructiveAuthorizationProof,
        expectedBinding: DestructiveTargetBinding,
        expectedArmToken: DestructiveArmingToken,
        expectedLease: DestructiveAttemptLease,
        nowMonotonicMillis: Long,
    ): ConsumedAuthorizationCheck {
        val record = pendingConsumption.remove(proof)
            ?: return ConsumedAuthorizationCheck.Rejected(
                "consumed_authorization_not_issued_or_already_consumed",
            )
        if (record.attemptLease !== expectedLease) {
            return ConsumedAuthorizationCheck.Rejected("consumed_authorization_attempt_lease_mismatch")
        }
        if (record.armToken !== expectedArmToken) {
            return ConsumedAuthorizationCheck.Rejected("consumed_authorization_arm_mismatch")
        }
        if (record.binding.correlationId != expectedBinding.correlationId) {
            return ConsumedAuthorizationCheck.Rejected("consumed_authorization_correlation_mismatch")
        }
        if (record.binding != expectedBinding) {
            return if (record.binding.scope != expectedBinding.scope) {
                ConsumedAuthorizationCheck.Rejected("consumed_authorization_scope_mismatch")
            } else {
                ConsumedAuthorizationCheck.Rejected("consumed_authorization_target_mismatch")
            }
        }
        freshnessReason(record.issuedAtMonotonicMillis, nowMonotonicMillis)?.let { reason ->
            return ConsumedAuthorizationCheck.Rejected(reason)
        }
        return ConsumedAuthorizationCheck.Accepted(
            binding = record.binding,
            armToken = record.armToken,
            attemptLease = record.attemptLease,
        )
    }

    private fun freshnessReason(
        issuedAtMonotonicMillis: Long,
        nowMonotonicMillis: Long,
    ): String? {
        val age = nowMonotonicMillis - issuedAtMonotonicMillis
        if (age < 0L) {
            return "capability_negative_monotonic_delta"
        }
        if (age > maxAgeMillis) {
            return "capability_stale"
        }
        return null
    }

    private data class CapabilityRecord(
        val binding: DestructiveTargetBinding,
        val armToken: DestructiveArmingToken,
        val attemptLease: DestructiveAttemptLease,
        val issuedAtMonotonicMillis: Long,
    )

    private data class PendingConsumptionRecord(
        val binding: DestructiveTargetBinding,
        val armToken: DestructiveArmingToken,
        val attemptLease: DestructiveAttemptLease,
        val issuedAtMonotonicMillis: Long,
    )

    internal companion object {
        const val MAX_CAPABILITY_AGE_MILLIS = 5_000L
    }
}

internal sealed interface DestructiveAuthorizationResult {
    data class Authorized(
        val capability: DestructiveCapability,
        val binding: DestructiveTargetBinding,
        val armToken: DestructiveArmingToken,
        val attemptLease: DestructiveAttemptLease,
    ) : DestructiveAuthorizationResult

    data class Rejected(val reason: String) : DestructiveAuthorizationResult
}

internal sealed interface DestructiveCapabilityConsumption {
    data class Accepted(
        val proof: ConsumedDestructiveAuthorizationProof,
        val binding: DestructiveTargetBinding,
        val armToken: DestructiveArmingToken,
        val attemptLease: DestructiveAttemptLease,
    ) : DestructiveCapabilityConsumption

    data class Rejected(val reason: String) : DestructiveCapabilityConsumption
}

internal sealed interface ConsumedAuthorizationCheck {
    data class Accepted(
        val binding: DestructiveTargetBinding,
        val armToken: DestructiveArmingToken,
        val attemptLease: DestructiveAttemptLease,
    ) : ConsumedAuthorizationCheck

    data class Rejected(val reason: String) : ConsumedAuthorizationCheck
}
