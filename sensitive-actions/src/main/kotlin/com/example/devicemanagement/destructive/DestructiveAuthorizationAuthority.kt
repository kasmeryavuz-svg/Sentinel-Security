package com.example.devicemanagement.destructive

import com.example.devicemanagement.integration.MonotonicTimeSource
import java.util.IdentityHashMap

/**
 * Separate destructive authorization domain. Opaque, identity-bound,
 * single-use, process-local. Cannot accept a reversible Approval and cannot
 * be consumed by the reversible policy executor.
 */
internal class DestructiveCapability private constructor() {
    companion object {
        fun create(): DestructiveCapability = DestructiveCapability()
    }
}

internal class DestructiveAuthorizationAuthority(
    private val armingAuthority: DestructiveArmingAuthority,
    private val monotonicTimeSource: MonotonicTimeSource,
    private val maxAgeMillis: Long = MAX_CAPABILITY_AGE_MILLIS,
) {
    private val issued = IdentityHashMap<DestructiveCapability, CapabilityRecord>()

    @Synchronized
    fun authorize(
        armToken: DestructiveArmingToken,
        binding: DestructiveTargetBinding,
    ): DestructiveAuthorizationResult {
        when (
            val arm = armingAuthority.requireLive(
                token = armToken,
                expectedBinding = binding,
            )
        ) {
            is ArmingCheck.Dead -> return DestructiveAuthorizationResult.Rejected(arm.reason)
            is ArmingCheck.Live -> Unit
        }
        val capability = DestructiveCapability.create()
        issued[capability] = CapabilityRecord(
            binding = binding,
            armToken = armToken,
            issuedAtMonotonicMillis = monotonicTimeSource.nowMillis(),
        )
        return DestructiveAuthorizationResult.Authorized(capability, binding, armToken)
    }

    @Synchronized
    fun consume(
        capability: DestructiveCapability,
        expectedBinding: DestructiveTargetBinding,
        nowMonotonicMillis: Long = monotonicTimeSource.nowMillis(),
    ): DestructiveCapabilityConsumption {
        val record = issued.remove(capability)
            ?: return DestructiveCapabilityConsumption.Rejected(
                "capability_not_issued_or_already_consumed",
            )
        if (record.binding != expectedBinding) {
            return if (record.binding.scope != expectedBinding.scope) {
                DestructiveCapabilityConsumption.Rejected("capability_scope_mismatch")
            } else {
                DestructiveCapabilityConsumption.Rejected("capability_target_mismatch")
            }
        }
        val age = nowMonotonicMillis - record.issuedAtMonotonicMillis
        if (age < 0L) {
            return DestructiveCapabilityConsumption.Rejected("capability_negative_monotonic_delta")
        }
        if (age > maxAgeMillis) {
            return DestructiveCapabilityConsumption.Rejected("capability_stale")
        }
        when (
            val arm = armingAuthority.requireLive(
                token = record.armToken,
                expectedBinding = record.binding,
                nowMonotonicMillis = nowMonotonicMillis,
            )
        ) {
            is ArmingCheck.Dead -> return DestructiveCapabilityConsumption.Rejected(arm.reason)
            is ArmingCheck.Live -> Unit
        }
        return DestructiveCapabilityConsumption.Accepted(
            binding = record.binding,
            armToken = record.armToken,
        )
    }

    private data class CapabilityRecord(
        val binding: DestructiveTargetBinding,
        val armToken: DestructiveArmingToken,
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
    ) : DestructiveAuthorizationResult

    data class Rejected(val reason: String) : DestructiveAuthorizationResult
}

internal sealed interface DestructiveCapabilityConsumption {
    data class Accepted(
        val binding: DestructiveTargetBinding,
        val armToken: DestructiveArmingToken,
    ) : DestructiveCapabilityConsumption

    data class Rejected(val reason: String) : DestructiveCapabilityConsumption
}
