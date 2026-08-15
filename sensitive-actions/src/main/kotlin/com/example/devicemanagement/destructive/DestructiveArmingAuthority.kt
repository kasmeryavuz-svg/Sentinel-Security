package com.example.devicemanagement.destructive

import com.example.devicemanagement.integration.MonotonicTimeSource
import java.util.IdentityHashMap

/**
 * Non-executing, process-local destructive arming. Arming does not authorize
 * and cannot reach a policy service. Tokens die with this instance.
 *
 * Arming requires a live attempt/admission lease. One arm may be reserved
 * for at most one destructive authorization.
 */
internal class DestructiveArmingToken private constructor() {
    companion object {
        fun create(): DestructiveArmingToken = DestructiveArmingToken()
    }
}

internal class DestructiveArmingAuthority(
    private val monotonicTimeSource: MonotonicTimeSource,
    private val admissionAuthority: DestructiveAttemptAdmissionAuthority,
    private val maxAgeMillis: Long = MAX_ARM_AGE_MILLIS,
) {
    private val liveArms = IdentityHashMap<DestructiveArmingToken, ArmRecord>()

    @Synchronized
    fun arm(
        binding: DestructiveTargetBinding,
        attemptLease: DestructiveAttemptLease,
    ): ArmingIssueResult {
        if (liveArms.isNotEmpty()) {
            return ArmingIssueResult.Rejected("arm_already_live")
        }
        when (
            val reserved = admissionAuthority.reserveForArm(
                lease = attemptLease,
                expectedBinding = binding,
            )
        ) {
            is AttemptLeaseCheck.Dead -> return ArmingIssueResult.Rejected(reserved.reason)
            is AttemptLeaseCheck.Live -> Unit
        }
        val token = DestructiveArmingToken.create()
        liveArms[token] = ArmRecord(
            binding = binding,
            issuedAtMonotonicMillis = monotonicTimeSource.nowMillis(),
            attemptLease = attemptLease,
            authorizationState = ArmAuthorizationState.UNUSED,
        )
        return ArmingIssueResult.Armed(token, binding, attemptLease)
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
        expectedLease: DestructiveAttemptLease,
        nowMonotonicMillis: Long = monotonicTimeSource.nowMillis(),
    ): ArmingCheck {
        val record = liveArms[token]
            ?: return ArmingCheck.Dead("arm_not_issued_or_already_consumed")
        if (record.attemptLease !== expectedLease) {
            return ArmingCheck.Dead("arm_attempt_lease_mismatch")
        }
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
        return ArmingCheck.Live(record.binding, token, record.attemptLease)
    }

    @Synchronized
    fun reserveForAuthorization(
        token: DestructiveArmingToken,
        expectedBinding: DestructiveTargetBinding,
        expectedLease: DestructiveAttemptLease,
        nowMonotonicMillis: Long = monotonicTimeSource.nowMillis(),
    ): ArmAuthorizationReservation {
        when (
            val live = requireLive(
                token = token,
                expectedBinding = expectedBinding,
                expectedLease = expectedLease,
                nowMonotonicMillis = nowMonotonicMillis,
            )
        ) {
            is ArmingCheck.Dead -> return ArmAuthorizationReservation.Rejected(live.reason)
            is ArmingCheck.Live -> Unit
        }
        val record = liveArms[token]
            ?: return ArmAuthorizationReservation.Rejected("arm_not_issued_or_already_consumed")
        if (record.authorizationState != ArmAuthorizationState.UNUSED) {
            return ArmAuthorizationReservation.Rejected("arm_already_authorized")
        }
        liveArms[token] = record.copy(authorizationState = ArmAuthorizationState.AUTHORIZATION_ISSUED)
        return ArmAuthorizationReservation.Reserved(
            binding = record.binding,
            token = token,
            attemptLease = record.attemptLease,
        )
    }

    private data class ArmRecord(
        val binding: DestructiveTargetBinding,
        val issuedAtMonotonicMillis: Long,
        val attemptLease: DestructiveAttemptLease,
        val authorizationState: ArmAuthorizationState,
    )

    private enum class ArmAuthorizationState {
        UNUSED,
        AUTHORIZATION_ISSUED,
    }

    internal companion object {
        const val MAX_ARM_AGE_MILLIS = 15_000L
    }
}

internal sealed interface ArmingIssueResult {
    data class Armed(
        val token: DestructiveArmingToken,
        val binding: DestructiveTargetBinding,
        val attemptLease: DestructiveAttemptLease,
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
        val attemptLease: DestructiveAttemptLease,
    ) : ArmingCheck

    data class Dead(val reason: String) : ArmingCheck
}

internal sealed interface ArmAuthorizationReservation {
    data class Reserved(
        val binding: DestructiveTargetBinding,
        val token: DestructiveArmingToken,
        val attemptLease: DestructiveAttemptLease,
    ) : ArmAuthorizationReservation

    data class Rejected(val reason: String) : ArmAuthorizationReservation
}
