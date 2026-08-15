package com.example.devicemanagement.destructive

import com.example.devicemanagement.integration.MonotonicTimeSource
import java.util.IdentityHashMap

/**
 * Non-executing, process-local destructive arming. Arming does not authorize
 * and cannot reach a policy service. Tokens die with this instance.
 */
internal class DestructiveArmingToken private constructor() {
    companion object {
        fun create(): DestructiveArmingToken = DestructiveArmingToken()
    }
}

internal class DestructiveArmingAuthority(
    private val monotonicTimeSource: MonotonicTimeSource,
    private val maxAgeMillis: Long = MAX_ARM_AGE_MILLIS,
) {
    private val liveArms = IdentityHashMap<DestructiveArmingToken, ArmRecord>()

    @Synchronized
    fun arm(binding: DestructiveTargetBinding): ArmingIssueResult {
        if (liveArms.isNotEmpty()) {
            return ArmingIssueResult.Rejected("arm_already_live")
        }
        val token = DestructiveArmingToken.create()
        liveArms[token] = ArmRecord(
            binding = binding,
            issuedAtMonotonicMillis = monotonicTimeSource.nowMillis(),
        )
        return ArmingIssueResult.Armed(token, binding)
    }

    @Synchronized
    fun disarm(token: DestructiveArmingToken): ArmingCancelResult {
        val record = liveArms.remove(token)
            ?: return ArmingCancelResult.Rejected("arm_not_issued_or_already_consumed")
        return ArmingCancelResult.Cancelled(record.binding.correlationId.value)
    }

    @Synchronized
    fun requireLive(
        token: DestructiveArmingToken,
        expectedBinding: DestructiveTargetBinding,
        nowMonotonicMillis: Long = monotonicTimeSource.nowMillis(),
    ): ArmingCheck {
        val record = liveArms[token]
            ?: return ArmingCheck.Dead("arm_not_issued_or_already_consumed")
        if (record.binding != expectedBinding) {
            return if (record.binding.scope != expectedBinding.scope) {
                ArmingCheck.Dead("arm_scope_mismatch")
            } else {
                ArmingCheck.Dead("arm_target_mismatch")
            }
        }
        val age = nowMonotonicMillis - record.issuedAtMonotonicMillis
        if (age < 0L) {
            return ArmingCheck.Dead("arm_negative_monotonic_delta")
        }
        if (age > maxAgeMillis) {
            return ArmingCheck.Dead("arm_stale")
        }
        return ArmingCheck.Live(record.binding, token)
    }

    private data class ArmRecord(
        val binding: DestructiveTargetBinding,
        val issuedAtMonotonicMillis: Long,
    )

    internal companion object {
        const val MAX_ARM_AGE_MILLIS = 15_000L
    }
}

internal sealed interface ArmingIssueResult {
    data class Armed(
        val token: DestructiveArmingToken,
        val binding: DestructiveTargetBinding,
    ) : ArmingIssueResult

    data class Rejected(val reason: String) : ArmingIssueResult
}

internal sealed interface ArmingCancelResult {
    data class Cancelled(val correlationId: String) : ArmingCancelResult

    data class Rejected(val reason: String) : ArmingCancelResult
}

internal sealed interface ArmingCheck {
    data class Live(
        val binding: DestructiveTargetBinding,
        val token: DestructiveArmingToken,
    ) : ArmingCheck

    data class Dead(val reason: String) : ArmingCheck
}
