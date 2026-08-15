package com.example.devicemanagement.destructive

/**
 * Live final validation used only after durable pre-execution evidence and
 * only inside the simulated executor chain. This is not a reusable allow
 * API and does not return a Boolean.
 */
internal class DestructiveFinalValidator(
    private val liveFactsSource: DestructiveLiveFactsSource,
    private val armingAuthority: DestructiveArmingAuthority,
    private val cooldown: DestructiveDenyOnlyCooldown,
) {
    fun validate(
        binding: DestructiveTargetBinding,
        armToken: DestructiveArmingToken,
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
        when (
            val arm = armingAuthority.requireLive(
                token = armToken,
                expectedBinding = binding,
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
