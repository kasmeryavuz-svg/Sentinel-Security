package com.example.devicemanagement.destructive

/**
 * Live final validation used only after pre-execution simulation evidence
 * and only inside the simulated executor chain. This is not a reusable allow
 * API and does not return a Boolean.
 *
 * After the evidence append, this validator must still prove that
 * authorization was genuinely consumed from the paired authority, is
 * single-use, remains inside the original capability freshness window, and
 * matches correlation / target / scope / arm / attempt lease.
 */
internal class DestructiveFinalValidator(
    private val liveFactsSource: DestructiveLiveFactsSource,
    private val armingAuthority: DestructiveArmingAuthority,
    private val authorizationAuthority: DestructiveAuthorizationAuthority,
    private val admissionAuthority: DestructiveAttemptAdmissionAuthority,
    private val cooldown: DestructiveDenyOnlyCooldown,
) {
    fun validate(
        binding: DestructiveTargetBinding,
        armToken: DestructiveArmingToken,
        attemptLease: DestructiveAttemptLease,
        consumptionProof: ConsumedDestructiveAuthorizationProof,
        nowMonotonicMillis: Long,
    ): FinalValidation {
        val facts = try {
            liveFactsSource.currentFacts()
        } catch (_: Throwable) {
            return FinalValidation.Failed("live_facts_unavailable")
        }
        DestructiveTargetRules.denyReason(binding, facts)?.let { reason ->
            return FinalValidation.Failed(reason)
        }
        when (val admitted = admissionAuthority.requireLive(attemptLease, binding)) {
            is AttemptLeaseCheck.Dead -> return FinalValidation.Failed(admitted.reason)
            is AttemptLeaseCheck.Live -> Unit
        }
        when (
            val consumed = authorizationAuthority.requireConsumedFresh(
                proof = consumptionProof,
                expectedBinding = binding,
                expectedArmToken = armToken,
                expectedLease = attemptLease,
                nowMonotonicMillis = nowMonotonicMillis,
            )
        ) {
            is ConsumedAuthorizationCheck.Rejected -> return FinalValidation.Failed(consumed.reason)
            is ConsumedAuthorizationCheck.Accepted -> Unit
        }
        when (
            val arm = armingAuthority.requireLive(
                token = armToken,
                expectedBinding = binding,
                expectedLease = attemptLease,
                nowMonotonicMillis = nowMonotonicMillis,
            )
        ) {
            is ArmingCheck.Dead -> return FinalValidation.Failed(arm.reason)
            is ArmingCheck.Live -> Unit
        }
        when (val usable = cooldown.assertUsable()) {
            is CooldownUsable.Unusable -> return FinalValidation.Failed(usable.reason)
            CooldownUsable.Usable -> Unit
        }
        return FinalValidation.Passed
    }
}

internal sealed interface FinalValidation {
    data object Passed : FinalValidation

    data class Failed(val reason: String) : FinalValidation
}
